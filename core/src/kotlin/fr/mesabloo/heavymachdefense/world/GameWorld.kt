package fr.mesabloo.heavymachdefense.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.physics.box2d.*
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import fr.mesabloo.heavymachdefense.PPM
import fr.mesabloo.heavymachdefense.data.*
import fr.mesabloo.heavymachdefense.gameplay.*
import fr.mesabloo.heavymachdefense.timers.cellMiningTimer
import fr.mesabloo.heavymachdefense.ui.stage.*
import fr.mesabloo.heavymachdefense.ui.stage.game.AllyBase
import fr.mesabloo.heavymachdefense.ui.stage.game.EnemyBase
import fr.mesabloo.heavymachdefense.ui.stage.game.ReactiveBase
import ktx.box2d.body
import ktx.box2d.box
import ktx.box2d.createWorld
import kotlin.math.*

class GameWorld(private val terrain: Terrain, val battle: Battle = Battle(1)) : Disposable {
    val world: World = createWorld()
    private val bodies = Array<Body>()
    private val deployed = mutableMapOf<Int, Body>()
    private val wrecks = mutableListOf<CombatActor>()
    private val baseReactions = mapOf(Team.ALLY to BaseReaction(), Team.ENEMY to BaseReaction())
    private val pixel: Texture
    val effects: BattleEffects

    init {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE); pixmap.fill()
        pixel = Texture(pixmap); pixmap.dispose()
        effects = BattleEffects(battle, pixel)
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
        actor.setPosition(unit.x - actor.width / 2f, unit.y - actor.height / 2f)
        return body
    }

    fun render(deltaTime: Float) {
        val paused = cellMiningTimer.isPaused || cellMiningTimer.isStopped
        battle.advance(deltaTime, paused)
        // Outcome freezes gameplay, while smoke/debris and the base destruction sequence finish.
        val presentationPaused = paused && battle.result == BattleResult.RUNNING
        val dt = if (presentationPaused) 0f else deltaTime.coerceIn(0f, 0.25f)
        effects.update(dt, presentationPaused)
        baseReactions.values.forEach { it.update(dt) }
        battle.units.forEach { if (it.id !in deployed) attach(it) }
        battle.drainEvents().forEach { event ->
            effects.present(event)
            when (event) {
                is CombatEvent.Shot -> {
                    if (event.sourceId != null) (deployed[event.sourceId]?.userData as? CombatActor)?.fire()
                    else baseReactions.getValue(event.team).kick = 0.16f
                }
                is CombatEvent.Hit -> {
                    if (event.targetId != null) (deployed[event.targetId]?.userData as? CombatActor)?.hit(event.angle)
                    else baseReactions.getValue(event.team).apply { flash = 0.16f; shake = 0.28f }
                }
                is CombatEvent.Destroyed -> {
                    if (event.unitId != null) (deployed[event.unitId]?.userData as? CombatActor)?.destroy()
                    else baseReactions.getValue(event.team).apply { destroyed = true; flash = 0.3f; shake = 1.1f }
                }
                is CombatEvent.Impact -> Unit
            }
        }
        val alive = battle.units.map { it.id }.toSet()
        deployed.keys.filter { it !in alive }.forEach { id ->
            val body = deployed.remove(id)!!
            val actor = body.userData as CombatActor
            actor.destroy()
            actor.zIndex = 2 // Wreckage lies under living units and the effects layer.
            wrecks.add(actor)
            world.destroyBody(body)
        }
        wrecks.forEach { it.update(dt, effects.visualTime) }
        wrecks.filter { it.wreckAge > 3f }.forEach { it.remove() }
        wrecks.removeAll { it.wreckAge > 3f }
        while (wrecks.size > 64) wrecks.removeAt(0).remove()
        battle.units.forEach { unit ->
            val body = deployed.getValue(unit.id)
            body.setTransform(unit.x / PPM, unit.y / PPM, 0f)
            (body.userData as CombatActor).update(dt, effects.visualTime, battle.result == BattleResult.RUNNING)
        }
        world.getBodies(bodies)
        bodies.forEach { body ->
            (body.userData as? Actor)?.also { actor ->
                var x = body.position.x * PPM - actor.width / 2f
                var y = body.position.y * PPM - actor.height / 2f
                if (actor is ReactiveBase) {
                    val team = if (actor is AllyBase) Team.ALLY else Team.ENEMY
                    val reaction = baseReactions.getValue(team)
                    val intensity = reaction.shake.coerceAtMost(0.3f) / 0.3f
                    x += sin(effects.visualTime * 105f) * intensity * 3.5f
                    y += cos(effects.visualTime * 88f) * intensity * 2f
                    y += (if (team == Team.ALLY) -1 else 1) * reaction.kick * 14f
                    actor.damageFlash = (reaction.flash / 0.16f).coerceIn(0f, 1f)
                    if (reaction.destroyed && !actor.ruined) {
                        actor.ruined = true
                        tintTree(actor, Color(0.23f, 0.19f, 0.17f, 1f))
                    }
                }
                actor.setPosition(x, y)
                if (actor.parent == null) terrain.addActor(actor)
            }
        }
        effects.toFront()
    }

    override fun dispose() { world.dispose(); pixel.dispose() }
}

private class BaseReaction {
    var flash = 0f
    var shake = 0f
    var kick = 0f
    var destroyed = false
    fun update(dt: Float) {
        flash = (flash - dt).coerceAtLeast(0f)
        shake = (shake - dt).coerceAtLeast(0f)
        kick = (kick - dt).coerceAtLeast(0f)
    }
}

private fun tintTree(actor: Actor, tint: Color) {
    actor.color = tint
    if (actor is Group) actor.children.forEach { tintTree(it, tint) }
}

private class CombatActor(val unit: CombatUnit, private val pixel: Texture) : Group() {
    private val sprite: Group = when {
        unit.turretKind != null -> Turret(unit.turretKind, unit.level)
        unit.team == Team.ENEMY -> EnemyMachine(unit.kind)
        else -> Machine(unit.kind, unit.level)
    }
    private var recoil = 0f
    private var hitFlash = 0f
    private var hitAngle = 0f
    private var destroyed = false
    var wreckAge = 0f
        private set

    init {
        setSize(64f, 64f)
        touchable = Touchable.disabled
        sprite.setOrigin(sprite.width / 2f, sprite.height / 2f)
        sprite.setPosition(32f - sprite.width / 2f, 32f - sprite.height / 2f)
        if (unit.turretKind != null) sprite.setScale(1.25f)
        addActor(sprite)
    }

    fun fire() { recoil = 0.22f }
    fun hit(angle: Float) { hitFlash = 0.17f; hitAngle = angle * PI.toFloat() / 180f }
    fun destroy() {
        if (destroyed) return
        destroyed = true
        sprite.rotation += 18f
        sprite.setScale(sprite.scaleX * 0.85f)
        tintTree(sprite, Color(0.22f, 0.19f, 0.16f, 1f))
    }

    fun update(dt: Float, time: Float, running: Boolean = true) {
        if (destroyed) {
            wreckAge += dt
            color.a = (1f - wreckAge / 3f).coerceAtLeast(0f)
            return
        }
        recoil = (recoil - dt).coerceAtLeast(0f)
        hitFlash = (hitFlash - dt).coerceAtLeast(0f)
        val recoilPhase = 1f - recoil / 0.22f
        val kick = if (recoilPhase < 0.2f) recoilPhase / 0.2f else ((1f - recoilPhase) / 0.8f).pow(2)
        val strength = when (unit.kind) {
            MachineKind.HEAVY_MISSILE, MachineKind.SHOTGUN -> 6f
            MachineKind.HMG -> 2.5f
            else -> 4f
        }
        (sprite as RecoilingSprite).setWeaponRecoil(kick * strength)
        val direction = unit.angle * PI.toFloat() / 180f
        val bump = hitFlash / 0.17f * 2.5f
        sprite.rotation = unit.angle + sin(time * 110f) * bump
        val walk = if (unit.moving && running) sin(time * 18f) * 1.2f else 0f
        sprite.setPosition(32f - sprite.width / 2f - cos(direction) * kick * 1.5f + cos(hitAngle) * bump,
            32f - sprite.height / 2f - sin(direction) * kick * 1.5f + sin(hitAngle) * bump + walk)
        tintTree(sprite, if (unit.stunnedFor > 0f) Color(0.45f, 0.85f, 1f, 1f) else Color.WHITE)
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (destroyed) return
        if (hitFlash > 0f) {
            val source = batch.blendSrcFunc; val destination = batch.blendDstFunc
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
            super.draw(batch, parentAlpha * hitFlash / 0.17f * 0.85f)
            batch.setBlendFunction(source, destination)
        }
        batch.setColor(0.08f, 0.08f, 0.08f, parentAlpha)
        batch.draw(pixel, x + 12f, y + 55f, 40f, 4f)
        batch.setColor(if (unit.team == Team.ALLY) Color.GREEN else Color.RED)
        batch.draw(pixel, x + 12f, y + 55f, 40f * (unit.hp / unit.stats.health).coerceIn(0f, 1f), 4f)
        batch.color = Color.WHITE
    }
}
