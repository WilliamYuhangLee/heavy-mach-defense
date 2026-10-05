package fr.mesabloo.heavymachdefense.desktop

import com.badlogic.gdx.*
import com.badlogic.gdx.backends.lwjgl3.*
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.utils.ScreenUtils
import fr.mesabloo.heavymachdefense.MainGame
import fr.mesabloo.heavymachdefense.data.*
import fr.mesabloo.heavymachdefense.gameplay.*
import fr.mesabloo.heavymachdefense.managers.assets.*
import fr.mesabloo.heavymachdefense.managers.backgroundMusicManager
import fr.mesabloo.heavymachdefense.screens.StageScreen
import fr.mesabloo.heavymachdefense.timers.cellMiningTimer
import fr.mesabloo.heavymachdefense.ui.stage.Machine
import fr.mesabloo.heavymachdefense.ui.stage.Terrain
import fr.mesabloo.heavymachdefense.ui.stage.game.ReactiveBase
import fr.mesabloo.heavymachdefense.ui.stage.game.EnemyBase
import java.nio.file.Files
import java.util.Date
import kotlin.math.*

/** Rendered regression for recoil, repeated shots, camera culling, impacts and base destruction. */
object CombatEffectsSmokeTest {
    private inline fun <reified T : Actor> find(root: Actor): T? {
        return descendants(root).filterIsInstance<T>().firstOrNull()
    }
    private fun descendants(root: Actor): List<Actor> = listOf(root) +
        if (root is Group) root.children.flatMap { descendants(it) } else emptyList()

    @JvmStatic fun main(args: Array<String>) {
        val config = Lwjgl3ApplicationConfiguration()
        config.setInitialVisible(false)
        config.setWindowPosition(0, 0)
        config.setWindowedMode(384, 512)
        config.setForegroundFPS(60)
        config.setPreferencesConfig(Files.createTempDirectory("hm-effects-test-").toString(), com.badlogic.gdx.Files.FileType.Absolute)
        Lwjgl3Application(object : ApplicationAdapter() {
            private lateinit var game: MainGame
            private lateinit var screen: StageScreen
            private lateinit var terrain: Terrain
            private lateinit var pane: ScrollPane
            private lateinit var rifle: Machine
            private var frame = 0
            private var captureIndex = 0
            private var startWeaponX = 0f
            private var recoilSeen = false
            private var baseFlashSeen = false
            private val captureDir = System.getProperty("hm.effects.capture")

            override fun create() {
                try {
                    game = MainGame(); game.create(); game.justStarted = false
                    stageAssetsManager.preload(2); buttonAssetsManager.preload(); assetManager.finishLoading()
                    while (!combatEffectsAssets.isLoaded) combatEffectsAssets.loadNext()
                    while (!backgroundMusicManager.isFullyLoaded()) backgroundMusicManager.update()
                    screen = StageScreen(game, 2, GameSave(Date(0), Date(0)), -1)
                    game.replaceScreen(screen)
                    terrain = find<Terrain>(screen.background)!!
                    pane = find<ScrollPane>(screen.background)!!
                    val battle = screen.gameWorld.battle
                    val kinds = listOf(MachineKind.RIFLE, MachineKind.MISSILE, MachineKind.PLASMA)
                    kinds.forEachIndexed { i, kind ->
                        battle.spawn(kind, 1, Team.ALLY, atX = 100f + i * 155f, atY = 380f).hp = 10000f
                        battle.spawn(kind, 1, Team.ENEMY, atX = 100f + i * 155f, atY = 510f).hp = 10000f
                    }
                    screen.gameWorld.render(0f)
                    rifle = descendants(terrain).filterIsInstance<Machine>().first()
                    startWeaponX = rifle.children[1].x
                    check(screen.gameWorld.effects.width == terrain.width && screen.gameWorld.effects.height == terrain.height)
                } catch (failure: Throwable) { fail(failure) }
            }

            private fun capture(name: String? = null) {
                if (captureDir == null) return
                val pixels = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
                val writer = PixmapIO.PNG()
                try {
                    writer.setFlipY(true)
                    val file = name ?: "frame${(captureIndex++).toString().padStart(4, '0')}"
                    writer.write(Gdx.files.absolute("$captureDir/$file.png"), pixels)
                } finally { writer.dispose(); pixels.dispose() }
            }

            private fun checkScrolledTracer() {
                val shot = screen.gameWorld.battle.projectiles.first { it.team == Team.ALLY && it.kind == MachineKind.RIFLE && it.y > 1100f }
                val angle = atan2(shot.targetY - shot.y, shot.targetX - shot.x)
                val lead = min(18f, hypot(shot.targetX - shot.x, shot.targetY - shot.y))
                val point = terrain.localToStageCoordinates(Vector2(shot.x + cos(angle) * lead - sin(angle) * 10f,
                    shot.y + sin(angle) * lead + cos(angle) * 10f))
                screen.ui.stageToScreenCoordinates(point)
                val sx = (point.x * Gdx.graphics.backBufferWidth / Gdx.graphics.width).toInt()
                val sy = ((Gdx.graphics.height - point.y) * Gdx.graphics.backBufferHeight / Gdx.graphics.height).toInt()
                val pixels = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
                try {
                    var brightPixels = 0
                    for (x in sx - 4..sx + 4) for (y in sy - 4..sy + 4) {
                        if (x !in 0 until pixels.width || y !in 0 until pixels.height) continue
                        val color = Color(pixels.getPixel(x, y))
                        if (color.r > 0.9f && color.g > 0.7f && color.b < 0.85f) brightPixels++
                    }
                    check(brightPixels > 2) { "Projectile was culled after scrolling; gold tracer absent at $sx,$sy" }
                } finally { pixels.dispose() }
            }

            override fun render() {
                try {
                    Gdx.gl.glClearColor(0.03f, 0.04f, 0.05f, 1f)
                    Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)
                    val world = screen.gameWorld
                    val battle = world.battle
                    when (frame) {
                        85 -> {
                            cellMiningTimer.pause()
                            val time = world.effects.visualTime
                            repeat(20) { world.render(0.1f) }
                            check(world.effects.visualTime == time) { "Pause must freeze presentation" }
                            cellMiningTimer.resume()
                        }
                        180 -> battle.special(100f, 510f, 20000f, 48f)
                        230 -> {
                            // Exercise sustained firing, then put a rifle duel away from the origin.
                            val shots = world.effects.presentedShots
                            repeat(120) { world.render(0.25f) }
                            check(world.effects.presentedShots > shots + 50) { "Firing stopped after the initial volleys" }
                            battle.spawn(MachineKind.RIFLE, 1, Team.ALLY, atX = 256f, atY = 1200f).hp = 10000f
                            battle.spawn(MachineKind.RIFLE, 1, Team.ENEMY, atX = 256f, atY = 1325f).hp = 10000f
                            pane.scrollY = 500f; pane.updateVisualScroll()
                        }
                        290 -> {
                            pane.scrollY = 0f; pane.updateVisualScroll()
                            battle.special(256f, 1904f, 3400f, 100f)
                        }
                        370 -> battle.special(256f, 1904f, 2000f, 100f)
                    }
                    screen.render(1f / 60f)
                    screen.render(0f) // Draw the state just simulated, including newly emitted effects.
                    if (frame < 180 && abs(rifle.children[1].x - startWeaponX) > 1f) recoilSeen = true
                    if (frame in 291..299 && find<EnemyBase>(terrain)!!.damageFlash > 0f) baseFlashSeen = true
                    if (frame == 237) { capture("scrolled-projectiles"); checkScrolledTracer(); }
                    if (frame == 193) capture("unit-destruction")
                    if (frame == 300) capture("base-damage")
                    if (frame == 393) capture("base-destruction")
                    if (frame % 2 == 0) capture()
                    if (++frame >= 450) {
                        check(recoilSeen) { "Weapon barrels did not recoil" }
                        check(baseFlashSeen) { "Enemy base did not react to damage" }
                        check(battle.result == BattleResult.VICTORY)
                        check(world.effects.presentedImpacts > 40 && world.effects.presentedDestructions >= 2)
                        check(world.effects.activeEffectCount <= 720) { "Visual effects exceeded their bound" }
                        check(descendants(terrain).filterIsInstance<ReactiveBase>().any { it.ruined })
                        println("PASS: weapon recoil, hit/death effects, 30s sustained firing, rendered projectiles after camera scroll, pause, base damage/burning/destruction, bounded effects, delayed victory.")
                        Gdx.app.exit()
                    }
                } catch (failure: Throwable) { fail(failure) }
            }

            override fun dispose() { game.dispose() }
            private fun fail(failure: Throwable) { failure.printStackTrace(); System.exit(1) }
        }, config)
    }
}
