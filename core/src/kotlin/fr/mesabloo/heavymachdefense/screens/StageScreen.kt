package fr.mesabloo.heavymachdefense.screens

import aurelienribon.tweenengine.TweenManager
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.ui.Label
import fr.mesabloo.heavymachdefense.gameplay.*
import fr.mesabloo.heavymachdefense.ui.showChoiceDialog
import fr.mesabloo.heavymachdefense.managers.assets.assetManager
import fr.mesabloo.heavymachdefense.managers.assets.levelSelectionAssetsManager
import fr.mesabloo.heavymachdefense.managers.fontManager
import fr.mesabloo.heavymachdefense.managers.FontManager
import kotlinx.serialization.encodeToString
import kotlin.math.floor
import kotlin.math.min
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import fr.mesabloo.heavymachdefense.MainGame
import fr.mesabloo.heavymachdefense.data.*
import fr.mesabloo.heavymachdefense.entities.createBases
import fr.mesabloo.heavymachdefense.entities.createTerrainBody
import fr.mesabloo.heavymachdefense.data.Specials
import fr.mesabloo.heavymachdefense.listeners.stage.*
import fr.mesabloo.heavymachdefense.managers.BackgroundMusicManager
import fr.mesabloo.heavymachdefense.managers.backgroundMusicManager
import fr.mesabloo.heavymachdefense.managers.animationManager
import fr.mesabloo.heavymachdefense.managers.assets.StageAssetsManager
import fr.mesabloo.heavymachdefense.managers.assets.stageAssetsManager
import fr.mesabloo.heavymachdefense.timers.cellMiningTimer
import fr.mesabloo.heavymachdefense.ui.stage.*
import fr.mesabloo.heavymachdefense.ui.stage.buttons.*
import fr.mesabloo.heavymachdefense.ui.stage.dialog.SystemMenu
import fr.mesabloo.heavymachdefense.ui.stage.game.AllyBase
import fr.mesabloo.heavymachdefense.ui.stage.slots.MachineBuildSlot
import fr.mesabloo.heavymachdefense.ui.stage.slots.SpecialBuildSlot
import fr.mesabloo.heavymachdefense.ui.stage.slots.TurretBuildSlot
import fr.mesabloo.heavymachdefense.world.GameWorld
import fr.mesabloo.heavymachdefense.world.UI_HEIGHT
import fr.mesabloo.heavymachdefense.world.UI_WIDTH
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ktx.actors.alpha
import ktx.actors.setScrollFocus
import kotlin.math.pow
import kotlin.properties.Delegates

class StageScreen(
    game: MainGame,
    private val level: Int,
    val save: GameSave,
    val saveIndex: Int,
    isLoading: Boolean = false
) :
    AbstractScreen(game, isLoading) {
    private companion object {
        const val TEMPORARY_CELL_UPGRADE_RATIO = 0.6f
        const val SLOT_MENU_WIDTH = 128f
        const val SLOT_MENU_HEIGHT = 710f
    }

    private lateinit var buildQueue: BuildQueue
    private lateinit var allyBase: AllyBase
    lateinit var gameWorld: GameWorld
        private set
    private var resultHandled = false
    private var creditedKills = 0L
    private var miningRemainder = 0f
    private var selectedSpecial: SpecialKind? = null
    private var lastShotCount = 0
    private var lastEnemyDeaths = 0
    private var effectCooldown = 0f
    private lateinit var statusLabel: Label

    private val upgrades: Upgrades = Json.decodeFromString(Gdx.files.internal("data/upgrades.json").readString())
    private val builds: Builds = Json {
        allowStructuredMapKeys = true // this is because we store 'Pair's as 'HashMap' keys
    }.decodeFromString(Gdx.files.internal("data/build-info.json").readString())
    private val specials: Specials = Json.decodeFromString(Gdx.files.internal("data/special-info.json").readString())

    private var upgradeMenuShown: Boolean = false

    private lateinit var title: Title
    private lateinit var terrain: Terrain

    private var playerLife: Long = 100L
    private var enemyLife: Long = 100L

    private val menuTweenManager = TweenManager()

    private var maxCells: Long
    private var currentCells: Long
    private var temporaryCellUpgradesCount: Long = 0
    private var temporaryCellUpgradeCost: Long

    private var cellResearchMultiplier: Float
    private var crResearchMultiplier: Float
    private var buildTimeMultiplier: Float

    private var baseDefenseLevel by Delegates.observable(1) { _, old, new ->
        if (!this.isLoading && old != new) {
            this.allyBase.defLevel = new
        }
    }
    private var baseAttackLevel by Delegates.observable(1) { _, old, new ->
        if (!this.isLoading && old != new) {
            this.allyBase.atkLevel = new
        }
    }

    private lateinit var systemMenu: SystemMenu

    private var backgroundMusicVolume: Float = 0.6f
        set(value) {
            field = value.coerceIn(0f, 1f)
            backgroundMusicManager.setVolume(BackgroundMusicManager.GAMEPLAY, field)
        }
    private var effectsVolume: Float = 1.0f

    private lateinit var upgradeEquipButton: UpgradeEquipment

    private var currentMenuKind by Delegates.observable(StageAssetsManager.UI.TitleKind.BUILD_MACH) { _, old, new ->
        if (old != new) {
            this.upgradeEquipButton.kind = new
            this.title.kind = new
        }
    }

    private lateinit var machineSlots: Group
    private lateinit var specialSlots: Group

    init {
        this.maxCells = this.upgrades.cell_storage[this.save.mainUpgrades[UpgradeKind.CELL_STORAGE]?.minus(1)
            ?: 0].storage * 2f.pow(this.temporaryCellUpgradesCount.toFloat()).toLong()
        this.temporaryCellUpgradeCost = (this.maxCells * TEMPORARY_CELL_UPGRADE_RATIO).toLong()
        this.currentCells = this.maxCells * 2 / 5

        this.cellResearchMultiplier =
            this.upgrades.cell_research[this.save.mainUpgrades[UpgradeKind.CELL_RESEARCH]?.minus(1) ?: 0].multiplier
        this.crResearchMultiplier =
            this.upgrades.cr_research[this.save.mainUpgrades[UpgradeKind.CR_RESEARCH]?.minus(1) ?: 0].multiplier
        this.buildTimeMultiplier =
            this.upgrades.build_time[this.save.mainUpgrades[UpgradeKind.BUILD_TIME]?.minus(1) ?: 0].multiplier
    }

    override fun show() {
        super.show()

        if (this.isLoading)
            return

        backgroundMusicManager.play(BackgroundMusicManager.GAMEPLAY, this.backgroundMusicVolume)
        cellMiningTimer.resetForMission()
        this.systemMenu = SystemMenu(this::backgroundMusicVolume, this::effectsVolume, this)

        lateinit var scrollpane: ScrollPane
        this.background.addActor(ScrollPane(Terrain().also {
            this.terrain = it
        }).also {
            scrollpane = it

            val yOffset = 180f

            it.setBounds(128f, yOffset, 512f, UI_HEIGHT - yOffset)
            it.setSmoothScrolling(true)
            it.setScrollbarsVisible(false)
            it.setScrollingDisabled(true, false)
            it.setOverscroll(false, false)

            it.layout()
            it.scrollTo(0f, 0f, 512f, 1024f - yOffset)
            it.updateVisualScroll()
        })

        this.background.addActor(HpGauges(this::playerLife, this::enemyLife).also {
            it.setPosition(128f, UI_HEIGHT - it.height - 3f)
        })
        this.background.addActor(BuildSlot().also {
            it.setPosition(0f, 0f)
        })
        this.background.addActor(MachSlot().also {
            it.setPosition(UI_WIDTH - it.width, 57f)
        })
        this.background.addActor(CellCounter(this::maxCells, this::currentCells).also {
            it.setPosition(652f, 995f)
        })
        this.background.addActor(
            CellTemporaryUpgrade(
                this::currentCells,
                this::temporaryCellUpgradeCost,
                this::temporaryCellUpgradesCount
            ).also {
                it.setPosition(630f, 972f)
            })

        this.background.addActor(BuildQueue(this::upgradeMenuShown, this.upgrades, this.save) { item ->
            when (item) {
                is BuildMachineItem -> this.gameWorld.spawnMachine(item.kind, item.level)
                is BuildTurretItem -> this.gameWorld.spawnTurret(item.kind, item.level)
                else -> error("Unsupported completed build: ${item.javaClass.simpleName}")
            }
        }.also {
            this.buildQueue = it

            it.height = 508f
            it.width = 64f
            it.setPosition(22f, 692f - it.height)
        })

        this.background.addActor(Group().also {
            this.machineSlots = it

            populateMachineSlots(it)

            it.setPosition(640f, 966f - SLOT_MENU_HEIGHT)
            it.setSize(SLOT_MENU_WIDTH, SLOT_MENU_HEIGHT)

            it.alpha = 1f
            it.touchable = Touchable.enabled
        })
        this.background.addActor(Group().also {
            this.specialSlots = it

            var currentY = SLOT_MENU_HEIGHT - 76f
            for (slot in this.save.specialSlots) {
                it.addActor(when (slot) {
                    is SpecialSlot -> SpecialBuildSlot(slot, this.save, this.specials).also { button ->
                        button.addListener(object : ClickListener() {
                            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                                if (!button.isDisabled && !resultHandled && !cellMiningTimer.isPaused) {
                                    selectedSpecial = slot.kind
                                    statusLabel.setText("Click the battlefield to target; Esc cancels")
                                }
                            }
                        })
                    }
                    else -> error("Unsupported equipment slot: ${slot.javaClass.simpleName}")
                }.also {
                    it.setPosition(40f, currentY)

                })

                currentY -= 102f
            }

            it.setPosition(640f, 966f - SLOT_MENU_HEIGHT)
            it.setSize(SLOT_MENU_WIDTH, SLOT_MENU_HEIGHT)

            it.alpha = 0f
            it.touchable = Touchable.disabled
        })

        this.background.addActor(UpgradeEquipment(this.currentMenuKind).also {
            this.upgradeEquipButton = it

            it.setPosition(650f, 180f)
            it.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    if (!resultHandled && !upgradeMenuShown) openWorkshop()
                }
            })
        })

        val controlsGroup = Group()

        controlsGroup.addActor(Controls().also {
            it.setPosition(0f, 512f)
        })
        controlsGroup.addActor(StageNumber(level).also {
            it.setPosition(142f - it.width / 2f, 79f + 512f)
        })
        controlsGroup.addActor(Credits(save::credits).also {
            it.setPosition(176f - it.width, 30f + 512f)
        })
        controlsGroup.addActor(MenuButton().also {
            it.setPosition(587f, 89f + 512f)

            it.addListener(ShowSystemMenu(this.systemMenu, this.ui, this::upgradeMenuShown))
        })
        controlsGroup.addActor(BaseUpgradeButton().also {
            it.setPosition(587f, 22f + 512f)
            it.addListener(ShowUpgradeMenu(this.menuTweenManager, controlsGroup, scrollpane, this::upgradeMenuShown))
        })
        controlsGroup.addActor(Title(this.currentMenuKind).also {
            this.title = it

            it.setPosition(UI_WIDTH / 2f - it.width / 2f + 3f, 130f + 512f)
        })

        controlsGroup.addActor(BuildMachMenuButton().also {
            it.setPosition(UI_WIDTH / 2f - it.width / 2f - 90f, 47f + 512f)
            it.addListener(RemoveClickIfUpgradeMenuShown(this::upgradeMenuShown))
            it.addListener(ResetAnimationsForOthers(controlsGroup, it))
            it.addListener(PlayAnimation(it))
            it.addListener(ShowBuildSideMenu(this::currentMenuKind, this.machineSlots, this.specialSlots))

            animationManager.setCurrentKeyframe(it.animationId, 7)
        })
        controlsGroup.addActor(SpecialAttackMenuButton().also {
            it.setPosition(UI_WIDTH / 2f - it.width / 2f + 2f, 47f + 512f)
            it.addListener(RemoveClickIfUpgradeMenuShown(this::upgradeMenuShown))
            it.addListener(ResetAnimationsForOthers(controlsGroup, it))
            it.addListener(PlayAnimation(it))
            it.addListener(ShowSpecialSideMenu(this::currentMenuKind, this.machineSlots, this.specialSlots))

            animationManager.setCurrentKeyframe(it.animationId, 0)
        })
        controlsGroup.addActor(
            UpgradeMenu(
                this.save,
                this.upgrades,
                ShowUpgradeMenu(this.menuTweenManager, controlsGroup, scrollpane, this::upgradeMenuShown)
            ).also {
                it.setPosition(0f, -512f + 512f)
            })

        controlsGroup.setPosition(0f, -512f)

        this.background.addActor(controlsGroup)

        this.terrain.setScrollFocus(true)

        this.gameWorld = GameWorld(this.terrain, Battle(level,
            upgrades.base_defense[(save.mainUpgrades[UpgradeKind.BASE_DEFENSE] ?: 1) - 1].defense / 10f))
        statusLabel = Label("Build units on the right. Destroy the enemy base.", Label.LabelStyle(
            fontManager.bitmapFonts[FontManager.TREBUCHET_MS_BOLD_12_WHITE], com.badlogic.gdx.graphics.Color.WHITE))
        statusLabel.setPosition(142f, 950f)
        statusLabel.touchable = Touchable.disabled
        background.addActor(statusLabel)
        terrain.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                val kind = selectedSpecial ?: return
                useSpecial(kind, x, y)
            }
        })

        createTerrainBody(this.gameWorld)
        val (allyBase, _) = createBases(this.gameWorld, this.upgrades, this::save)
        this.allyBase = allyBase

        this.background.addActor(Radar(scrollpane).also {
            it.setPosition(22f, 698f)
        })
    }

    override fun render(delta: Float) {
        this.menuTweenManager.update(delta)

        // update cell storage capacity
        this.maxCells =
            this.upgrades.cell_storage[this.save.mainUpgrades[UpgradeKind.CELL_STORAGE]?.minus(1)
                ?: 0].storage * 2f.pow(this.temporaryCellUpgradesCount.toFloat()).toLong()
        this.temporaryCellUpgradeCost = (this.maxCells * TEMPORARY_CELL_UPGRADE_RATIO).toLong()

        this.cellResearchMultiplier =
            this.upgrades.cell_research[this.save.mainUpgrades[UpgradeKind.CELL_RESEARCH]?.minus(1) ?: 0].multiplier
        this.crResearchMultiplier =
            this.upgrades.cr_research[this.save.mainUpgrades[UpgradeKind.CR_RESEARCH]?.minus(1) ?: 0].multiplier
        this.buildTimeMultiplier =
            this.upgrades.build_time[this.save.mainUpgrades[UpgradeKind.BUILD_TIME]?.minus(1) ?: 0].multiplier

        this.baseDefenseLevel = this.save.mainUpgrades[UpgradeKind.BASE_DEFENSE] ?: 1
        this.baseAttackLevel = this.save.mainUpgrades[UpgradeKind.BASE_CANNON] ?: 1

        super.render(delta)

        if (!this.isLoading) {
            val battle = gameWorld.battle
            battle.upgradeBase(upgrades.base_defense[(save.mainUpgrades[UpgradeKind.BASE_DEFENSE] ?: 1) - 1].defense / 10f,
                save.mainUpgrades[UpgradeKind.BASE_CANNON] ?: 1)
            if (!cellMiningTimer.isPaused && !cellMiningTimer.isStopped && battle.result == BattleResult.RUNNING) {
                miningRemainder += delta.coerceIn(0f,0.25f) * 20f * cellResearchMultiplier
                val mined = floor(miningRemainder).toLong()
                currentCells = min(maxCells, currentCells + mined)
                miningRemainder -= mined.toFloat()
            }
            gameWorld.render(delta)
            effectCooldown = (effectCooldown - delta).coerceAtLeast(0f)
            if (battle.shotsFired != lastShotCount && effectCooldown <= 0f) {
                fr.mesabloo.heavymachdefense.managers.soundEffectManager.playShot(effectsVolume)
                lastShotCount = battle.shotsFired; effectCooldown = 0.12f
            }
            if (battle.defeatedEnemies != lastEnemyDeaths) {
                fr.mesabloo.heavymachdefense.managers.soundEffectManager.playExplosion(effectsVolume)
                lastEnemyDeaths = battle.defeatedEnemies
            }
            playerLife = (100f * battle.allyHp / battle.allyMaxHp).toLong()
            enemyLife = (100f * battle.enemyHp / battle.enemyMaxHp).toLong()
            if (battle.creditsEarned > creditedKills) {
                save.credits += ((battle.creditsEarned - creditedKills) * crResearchMultiplier).toLong()
                creditedKills = battle.creditsEarned
            }
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
                selectedSpecial = null
                statusLabel.setText("Build units on the right. Destroy the enemy base.")
            }
            if (!resultHandled && battle.result != BattleResult.RUNNING) finishMission()
        }
    }

    private fun populateMachineSlots(it: Group) {
        it.clearChildren()
        var currentY = SLOT_MENU_HEIGHT - 76f
            for (slot in this.save.buildSlots) {
                it.addActor(when (slot) {
                    is MachineSlot -> MachineBuildSlot(
                        slot,
                        this.save,
                        this.builds,
                        this::currentCells,
                        this.buildQueue
                    )
                    is TurretSlot -> TurretBuildSlot(
                        slot,
                        this.save,
                        this.builds,
                        this::currentCells,
                        this.buildQueue
                    )
                    else -> error("Unsupported equipment slot: ${slot.javaClass.simpleName}")
                }.also {
                    it.setPosition(40f, currentY)

                    it.addListener(
                        BuildMachineIfPossible(
                            it,
                            this::currentCells,
                            this.buildQueue,
                            this.builds,
                            this::upgradeMenuShown
                        )
                    )
                })

                currentY -= 102f
            }


    }

    private fun persistSave() {
        if (saveIndex < 0) return // isolated smoke tests use an in-memory save
        val prefs = Gdx.app.getPreferences(GameSave.PREFERENCES_PATH)
        prefs.putString(saveIndex.toString(), Json.encodeToString(save))
        prefs.flush()
    }

    private fun finishMission() {
        resultHandled = true
        cellMiningTimer.pause()
        val victory = gameWorld.battle.result == BattleResult.VICTORY
        val reward = if (victory) MissionProgress.victory(save, level) else 0L
        persistSave()
        val choices = mutableListOf<Pair<String, () -> Unit>>()
        if (victory && level < MissionProgress.LAST_STAGE) choices.add("Next mission" to { openMission(level + 1) })
        choices.add("Retry mission" to { openMission(level) })
        choices.add("Mission select" to { saveAndExit() })
        showChoiceDialog(ui, if (victory) "VICTORY" else "DEFEAT",
            if (victory) "Enemy base destroyed! +$reward CR.\n${gameWorld.battle.defeatedEnemies} enemies defeated."
            else "Your base was destroyed. Spend credits on upgrades and try again.", choices)
    }

    fun openMission(nextLevel: Int) {
        Gdx.app.postRunnable {
            game.replaceScreen(MissionLoadingScreen(game, nextLevel, save, saveIndex))
        }
    }

    fun saveAndExit() {
        persistSave()
        Gdx.app.postRunnable {
            levelSelectionAssetsManager.preload()
            fr.mesabloo.heavymachdefense.managers.assets.buttonAssetsManager.preload()
            assetManager.finishLoading()
            game.replaceScreen(StageSelectionScreen(game, save, saveIndex))
        }
    }

    fun showHelp() {
        showChoiceDialog(ui, "How to play",
            "Build machines on the right using cells. They advance and fight automatically. " +
            "Destroy the enemy base to win. Cells refill during battle. Kills and victories earn CR. " +
            "Base Upgrade improves your economy and defenses. Equip/Upgrade opens the workshop. " +
            "Buy special attacks there, select one, then click the battlefield to aim.",
            listOf("Back" to {}))
    }

    private fun openWorkshop() {
        cellMiningTimer.pause()
        selectedSpecial = null
        val choices = mutableListOf<Pair<String, () -> Unit>>()
        if (currentMenuKind == StageAssetsManager.UI.TitleKind.SPECIAL_ATTACK) {
            SpecialKind.values().forEach { kind ->
                val cost = 150L + kind.ordinal * 100L
                choices.add("${kind.name.replace('_',' ')}: $cost CR" to {
                    if (save.credits >= cost) {
                        save.credits -= cost
                        save.specialCount[kind] = (save.specialCount[kind] ?: 0) + 1
                        persistSave()
                    }
                    openWorkshop()
                })
            }
        } else {
            MachineKind.values().filter { MissionProgress.unlocked(save,it) }.forEach { kind ->
                choices.add("${kind.machineName}: level ${save.machineUpgrades[kind] ?: 1}" to { editMachine(kind) })
            }
            TurretKind.values().filter { save.lastStageCompleted >= 2 + it.ordinal * 2 }.forEach { kind ->
                choices.add("${kind.turretName} turret: level ${save.turretUpgrades[kind] ?: 1}" to { editTurret(kind) })
            }
        }
        choices.add("Back to battle" to { populateMachineSlots(machineSlots); cellMiningTimer.resume() })
        showChoiceDialog(ui, "Workshop - ${save.credits} CR",
            if (currentMenuKind == StageAssetsManager.UI.TitleKind.SPECIAL_ATTACK) "Buy a charge, then select its icon and click the battlefield."
            else "Upgrade/equip machines. New types unlock every two stages. Six equipment slots are available.", choices)
    }

    private fun editMachine(kind: MachineKind) {
        val unitLevel = save.machineUpgrades[kind] ?: 1
        val cost = 150L * unitLevel * (kind.ordinal + 1)
        val choices = mutableListOf<Pair<String, () -> Unit>>()
        if (unitLevel < 10) choices.add("Upgrade to level ${unitLevel + 1}: $cost CR" to {
            if (save.credits >= cost) { save.credits -= cost; save.machineUpgrades[kind] = unitLevel + 1; persistSave() }
            editMachine(kind)
        })
        val equipped = save.buildSlots.any { it is MachineSlot && it.kind == kind }
        choices.add((if (equipped) "Unequip from build sidebar" else "Equip in build sidebar") to {
            if (equipped) {
                save.buildSlots.removeAll { it is MachineSlot && it.kind == kind }
                equipmentChanged()
            } else equipSlot(MachineSlot(kind))
        })
        choices.add("Back" to { openWorkshop() })
        showChoiceDialog(ui, kind.machineName, "Level $unitLevel. Available: ${save.credits} CR", choices)
    }

    private fun editTurret(kind: TurretKind) {
        val turretLevel = (save.turretUpgrades[kind] ?: 1).coerceIn(1, 5)
        val cost = 200L * turretLevel * (kind.ordinal + 1)
        val choices = mutableListOf<Pair<String, () -> Unit>>()
        if (turretLevel < 5) choices.add("Upgrade to level ${turretLevel + 1}: $cost CR" to {
            if (save.credits >= cost) {
                save.credits -= cost
                save.turretUpgrades[kind] = turretLevel + 1
                persistSave()
            }
            editTurret(kind)
        })
        val equipped = save.buildSlots.any { it is TurretSlot && it.kind == kind }
        choices.add((if (equipped) "Unequip from build sidebar" else "Equip in build sidebar") to {
            if (equipped) {
                save.buildSlots.removeAll { it is TurretSlot && it.kind == kind }
                equipmentChanged()
            } else equipSlot(TurretSlot(kind))
        })
        choices.add("Back" to { openWorkshop() })
        showChoiceDialog(ui, "${kind.turretName} turret",
            "Stationary defense. Level $turretLevel. Available: ${save.credits} CR", choices)
    }

    private fun equipmentChanged() {
        populateMachineSlots(machineSlots)
        persistSave()
        openWorkshop()
    }

    private fun equipSlot(slot: Slot) {
        if (save.buildSlots.size < 6) {
            save.buildSlots.add(slot)
            equipmentChanged()
            return
        }
        val choices = save.buildSlots.mapIndexed { index, existing ->
            val name = when (existing) {
                is MachineSlot -> existing.kind.machineName
                is TurretSlot -> "${existing.kind.turretName} turret"
                else -> "slot ${index + 1}"
            }
            "Replace $name" to {
                save.buildSlots[index] = slot
                equipmentChanged()
            }
        }.toMutableList()
        choices.add("Back" to { openWorkshop() })
        showChoiceDialog(ui, "Equipment full", "Choose which of your six slots to replace.", choices)
    }

    private fun useSpecial(kind: SpecialKind, x: Float, y: Float) {
        if (cellMiningTimer.isPaused || resultHandled || (save.specialCount[kind] ?: 0) <= 0) return
        val damage: Float
        val radius: Float
        var stun = 0f
        when (kind) {
            SpecialKind.AIRSTRIKE_BOMB -> { damage = 300f; radius = 150f }
            SpecialKind.AIRSTRIKE_MISSILE -> { damage = 450f; radius = 180f }
            SpecialKind.AIRSTRIKE_NUKE -> { damage = 1500f; radius = 300f }
            SpecialKind.AIRSTRIKE_EMP -> { damage = 100f; radius = 200f; stun = 12f }
            SpecialKind.CROSSFIRE_MISSILE -> { damage = 600f; radius = 130f }
        }
        gameWorld.battle.special(x, y, damage, radius, stun)
        save.specialCount[kind] = (save.specialCount[kind] ?: 0) - 1
        selectedSpecial = null
        statusLabel.setText("Special attack deployed")
    }

    override fun pause() {
        super.pause()

        cellMiningTimer.stop()
        backgroundMusicManager.pause(BackgroundMusicManager.GAMEPLAY)
    }

    override fun resume() {
        super.resume()

        cellMiningTimer.start()
        if (!this.isLoading) backgroundMusicManager.play(BackgroundMusicManager.GAMEPLAY, this.backgroundMusicVolume)
    }

    override fun hide() {
        super.hide()
        backgroundMusicManager.stop(BackgroundMusicManager.GAMEPLAY)
    }

    override fun dispose() {
        super.dispose()

        backgroundMusicManager.stop(BackgroundMusicManager.GAMEPLAY)
        this.gameWorld.dispose()

        animationManager.dispose()
        stageAssetsManager.dispose()

        cellMiningTimer.clear()
    }
}