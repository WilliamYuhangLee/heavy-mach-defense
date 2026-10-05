package fr.mesabloo.heavymachdefense.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.physics.box2d.*
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import fr.mesabloo.heavymachdefense.PPM
import fr.mesabloo.heavymachdefense.data.*
import fr.mesabloo.heavymachdefense.gameplay.*
import fr.mesabloo.heavymachdefense.managers.assets.StageAssetsManager
import fr.mesabloo.heavymachdefense.managers.assets.stageAssetsManager
import fr.mesabloo.heavymachdefense.timers.cellMiningTimer
import fr.mesabloo.heavymachdefense.ui.stage.Machine
import fr.mesabloo.heavymachdefense.ui.stage.Terrain
import ktx.box2d.body
import ktx.box2d.box
import ktx.box2d.createWorld
import kotlin.math.*

class GameWorld(private val terrain: Terrain, val battle: Battle = Battle(1)) : Disposable {
    val world: World = createWorld()
    private val bodies = Array<Body>()
    private val deployed = mutableMapOf<Int, Body>()
    private val pixel: Texture
    private val effects: Actor

    init {
        val p = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        p.setColor(Color.WHITE); p.fill(); pixel = Texture(p); p.dispose()
        effects = object : Actor() {
            override fun draw(batch: Batch, parentAlpha: Float) {
                battle.projectiles.forEach { shot ->
                    batch.setColor(if (shot.team == Team.ALLY) Color.YELLOW else Color.ORANGE)
                    val angle = atan2(shot.targetY - shot.y, shot.targetX - shot.x) * 180f / PI.toFloat()
                    batch.draw(pixel, shot.x, shot.y, 0f, 1.5f, 10f, 3f, 1f, 1f, angle, 0, 0, 1, 1, false, false)
                }
                battle.blasts.forEach { blast ->
                    val size = blast.radius * (1f - blast.life.coerceAtMost(0.4f) / 0.5f)
                    batch.setColor(1f, 0.6f, 0.15f, (blast.life * 2f).coerceIn(0f, 0.7f))
                    batch.draw(pixel, blast.x - size / 2f, blast.y - size / 2f, size, size)
                }
                batch.color = Color.WHITE
            }
        }
        effects.touchable = Touchable.disabled
        terrain.addActor(effects)
    }

    fun spawnMachine(kind: MachineKind, level: Int): Body = attach(battle.spawn(kind, level))
    fun spawnTurret(kind: TurretKind, level: Int): Body =
        attach(battle.spawn(CombatBalance.machineFor(kind), level, turret = kind))

    private fun attach(unit: CombatUnit): Body {
        val actor = CombatActor(unit, pixel)
        val body = world.body {
            type = BodyDef.BodyType.KinematicBody
            position.set(unit.x / PPM, unit.y / PPM)
            box(width = 32f / PPM, height = 32f / PPM) { isSensor = true }
            userData = actor
        }
        deployed[unit.id] = body
        terrain.addActor(actor)
        actor.setPosition(unit.x - 16f, unit.y - 16f)
        return body
    }

    fun render(deltaTime: Float) {
        battle.advance(deltaTime, cellMiningTimer.isPaused || cellMiningTimer.isStopped)
        val alive = battle.units.map { it.id }.toSet()
        deployed.keys.filter { it !in alive }.forEach { id ->
            val body = deployed.remove(id)!!
            (body.userData as Actor).remove(); world.destroyBody(body)
        }
        battle.units.forEach { unit ->
            val body = deployed[unit.id] ?: attach(unit)
            body.setTransform(unit.x / PPM, unit.y / PPM, 0f)
            (body.userData as CombatActor).update(battle.elapsed)
        }
        world.getBodies(bodies)
        bodies.forEach { body ->
            (body.userData as? Actor)?.also { actor ->
                actor.setPosition(body.position.x * PPM - actor.width / 2f, body.position.y * PPM - actor.height / 2f)
                if (actor.parent == null) terrain.addActor(actor)
            }
        }
        effects.toFront()
    }

    override fun dispose() { world.dispose(); pixel.dispose() }
}

private class CombatActor(val unit: CombatUnit, private val pixel: Texture) : Group() {
    private val sprite: Actor = if (unit.turretKind != null) {
        fr.mesabloo.heavymachdefense.ui.stage.Turret(unit.turretKind,unit.level)
    } else if (unit.team == Team.ENEMY) {
        Group().apply {
            val index = (unit.kind.ordinal + 1).toString().padStart(2, '0')
            addActor(Image(stageAssetsManager.unsafeRegion(StageAssetsManager.TANK_BODIES, index)).also {
                width = it.width; height = it.height
            })
            addActor(Image(stageAssetsManager.unsafeRegion(StageAssetsManager.TANK_WEAPONS, index)).also {
                it.setPosition((width - it.width) / 2f, (height - it.height) / 2f)
            })
        }
    } else Machine(unit.kind, unit.level)
    init {
        setSize(32f, 32f); touchable = Touchable.disabled
        sprite.setOrigin(sprite.width / 2f, sprite.height / 2f)
        sprite.setPosition(16f - sprite.width / 2f, 16f - sprite.height / 2f)
        if (unit.turretKind != null) sprite.setScale(1.25f)
        addActor(sprite)
    }
    fun update(time: Float) {
        sprite.rotation = unit.angle
        sprite.y = 16f - sprite.height / 2f + if (unit.moving) sin(time * 18f) * 1.2f else 0f
        sprite.color = when { unit.stunnedFor > 0 -> Color.CYAN; unit.firing > 0 -> Color(1f,1f,0.65f,1f); else -> Color.WHITE }
    }
    override fun draw(batch: Batch, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        batch.setColor(0.08f,0.08f,0.08f,parentAlpha)
        batch.draw(pixel,x-4f,y+39f,40f,5f)
        batch.setColor(if (unit.team == Team.ALLY) Color.GREEN else Color.RED)
        batch.draw(pixel,x-4f,y+39f,40f * (unit.hp/unit.stats.health).coerceIn(0f,1f),5f)
        batch.color = Color.WHITE
    }
}
