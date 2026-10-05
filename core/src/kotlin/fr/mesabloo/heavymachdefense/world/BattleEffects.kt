package fr.mesabloo.heavymachdefense.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import fr.mesabloo.heavymachdefense.data.MachineKind
import fr.mesabloo.heavymachdefense.gameplay.*
import fr.mesabloo.heavymachdefense.managers.assets.combatEffectsAssets
import fr.mesabloo.heavymachdefense.managers.assets.stageAssetsManager
import kotlin.math.*
import java.util.Random

/** Presentation has its own clock so the final explosion can finish after combat stops. */
class BattleEffects(private val battle: Battle, private val pixel: Texture) : Actor() {
    private data class Animation(val clip: String, val x: Float, val y: Float, val size: Float,
                                 val duration: Float, val angle: Float = 0f, val alpha: Float = 1f,
                                 var age: Float = 0f, val drift: Float = 0f)
    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float,
                                val size: Float, val duration: Float, val angle: Float,
                                val debris: Boolean = false, var age: Float = 0f)
    private val animations = mutableListOf<Animation>()
    private val particles = mutableListOf<Particle>()
    private val random = Random(42)
    private var trailClock = 0f
    private var smokeClock = 0f
    var visualTime = 0f
        private set
    val activeEffectCount: Int get() = animations.size + particles.size
    var presentedShots = 0
        private set
    var presentedImpacts = 0
        private set
    var presentedDestructions = 0
        private set

    init {
        // ScrollPane culls actors against these bounds. Zero bounds hide every effect
        // as soon as the camera scrolls away from the bottom of the battlefield.
        setBounds(0f, 0f, 512f, 2048f)
        touchable = Touchable.disabled
    }

    private fun animate(clip: String, x: Float, y: Float, size: Float, duration: Float,
                        angle: Float = 0f, alpha: Float = 1f, delay: Float = 0f, drift: Float = 0f) {
        if (animations.size >= 320) animations.removeAt(0)
        animations.add(Animation(clip, x, y, size, duration, angle, alpha, -delay, drift))
    }

    private fun burst(x: Float, y: Float, count: Int, force: Float, debris: Boolean = false) {
        repeat(count) {
            if (particles.size >= 400) particles.removeAt(0)
            val a = random.nextFloat() * 2f * PI.toFloat()
            val speed = force * (0.4f + random.nextFloat() * 0.6f)
            particles.add(Particle(x, y, cos(a) * speed, sin(a) * speed,
                if (debris) 3f + random.nextFloat() * 4f else 1.5f,
                if (debris) 0.7f + random.nextFloat() * 0.7f else 0.16f + random.nextFloat() * 0.22f,
                random.nextFloat() * 360f, debris))
        }
    }

    fun present(event: CombatEvent) {
        when (event) {
            is CombatEvent.Shot -> {
                presentedShots++
                val angle = event.angle * PI.toFloat() / 180f
                val barrels = if (event.twinBarrels) listOf(-14f, 14f) else listOf(0f)
                barrels.forEach { side ->
                    val x = event.x + cos(angle) * 24f - sin(angle) * side
                    val y = event.y + sin(angle) * 24f + cos(angle) * side
                    val energy = event.kind == MachineKind.ION || event.kind == MachineKind.PLASMA
                    animate(if (energy) "fire" else "damage-hmg", x, y,
                        if (event.kind == MachineKind.HEAVY_MISSILE) 54f else 38f,
                        if (energy) 0.14f else 0.10f, event.angle)
                    if (!energy) burst(x, y, 2, 35f)
                }
            }
            is CombatEvent.Impact -> {
                presentedImpacts++
                val clip = when (event.kind) {
                    MachineKind.ION -> "explode-ion"
                    MachineKind.PLASMA -> "explode-plasma"
                    MachineKind.MISSILE, MachineKind.HEAVY_MISSILE -> "explode-ground"
                    else -> "damage-hmg"
                }
                val size = if (event.emp) event.radius * 2f else when (event.kind) {
                    MachineKind.ION, MachineKind.PLASMA -> 68f
                    MachineKind.MISSILE, MachineKind.HEAVY_MISSILE -> (event.radius * 1.8f).coerceIn(80f, 300f)
                    MachineKind.SHOTGUN -> 50f
                    else -> 34f
                }
                animate(clip, event.x, event.y, size, if (size > 60f) 0.65f else 0.24f,
                    if (clip == "damage-hmg") event.angle + 180f else 0f)
                if (!event.emp) burst(event.x, event.y, if (size > 60f) 12 else 5, if (size > 60f) 120f else 75f)
            }
            is CombatEvent.Destroyed -> {
                presentedDestructions++
                if (event.unitId == null) {
                    animate("explode-boss", event.x, event.y, 290f, 1.25f)
                    repeat(9) { i ->
                        val x = event.x + (random.nextFloat() - 0.5f) * 180f
                        val y = event.y + (random.nextFloat() - 0.5f) * 80f
                        animate("explode-npc", x, y, 110f + random.nextFloat() * 50f, 0.85f, delay = i * 0.11f)
                        animate("smoke", x, y + 15f, 120f, 2.7f, alpha = 0.65f, delay = i * 0.11f + 0.25f, drift = 22f)
                    }
                    burst(event.x, event.y, 45, 200f, true)
                } else {
                    animate("explode-npc", event.x, event.y, if (event.kind == MachineKind.TANKER) 135f else 95f, 0.75f)
                    animate("smoke", event.x, event.y + 12f, 85f, 1.7f, alpha = 0.75f, delay = 0.18f, drift = 22f)
                    burst(event.x, event.y, 14, 130f, true)
                }
            }
            is CombatEvent.Hit -> Unit // Actors handle their own recoil, flashes, and shake.
        }
    }

    fun update(delta: Float, paused: Boolean) {
        if (paused) return
        val dt = delta.coerceIn(0f, 0.25f)
        visualTime += dt
        animations.forEach { it.age += dt }
        animations.removeAll { it.age >= it.duration }
        particles.forEach {
            it.age += dt
            it.x += it.vx * dt; it.y += it.vy * dt
            val drag = exp(-dt * if (it.debris) 2.5f else 6f)
            it.vx *= drag; it.vy *= drag
        }
        particles.removeAll { it.age >= it.duration }
        trailClock -= dt
        if (trailClock <= 0f && battle.result == BattleResult.RUNNING) {
            battle.projectiles.forEach { shot ->
                if (shot.kind == MachineKind.MISSILE || shot.kind == MachineKind.HEAVY_MISSILE) {
                    animate("smoke", shot.x, shot.y, 16f, 0.5f, alpha = 0.28f, drift = 4f)
                }
            }
            trailClock = 0.045f
        }
        smokeClock -= dt
        if (smokeClock <= 0f) {
            listOf(Team.ALLY, Team.ENEMY).forEach { team ->
                val ratio = if (team == Team.ALLY) battle.allyHp / battle.allyMaxHp else battle.enemyHp / battle.enemyMaxHp
                if (ratio < 0.55f) {
                    val x = 256f + (random.nextFloat() - 0.5f) * 110f
                    val y = if (team == Team.ALLY) 110f else 2000f
                    animate("smoke", x, y + 12f, 75f, 1.8f, alpha = 0.55f, drift = 24f)
                    if (ratio < 0.3f) animate("explode-02", x, y, 48f, 1.1f)
                }
            }
            battle.units.filter { it.hp / it.stats.health < 0.3f }.forEach {
                animate("smoke", it.x, it.y, 36f, 0.85f, alpha = 0.5f, drift = 20f)
            }
            smokeClock = 0.34f
        }
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val previousColor = Color(batch.color)
        val sourceBlend = batch.blendSrcFunc
        val destinationBlend = batch.blendDstFunc
        animations.filter { it.clip == "smoke" }.forEach { drawAnimation(batch, it, parentAlpha) }
        battle.projectiles.forEach { drawProjectile(batch, it, parentAlpha) }
        animations.filter { it.clip != "smoke" }.forEach { drawAnimation(batch, it, parentAlpha) }
        particles.forEach { particle ->
            val fade = (1f - particle.age / particle.duration).coerceIn(0f, 1f) * parentAlpha
            if (particle.debris) {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                val heat = (1f - particle.age * 2f).coerceAtLeast(0f)
                batch.setColor(0.3f + heat * 0.7f, 0.24f + heat * 0.3f, 0.16f, fade)
                batch.draw(pixel, particle.x, particle.y, particle.size / 2f, particle.size / 2f,
                    particle.size, particle.size * 0.6f, 1f, 1f, particle.angle + particle.age * 240f, 0, 0, 1, 1, false, false)
            } else {
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
                batch.setColor(1f, 0.68f, 0.18f, fade)
                line(batch, particle.x, particle.y, particle.x - particle.vx * 0.035f,
                    particle.y - particle.vy * 0.035f, particle.size)
            }
        }
        batch.setBlendFunction(sourceBlend, destinationBlend)
        batch.color = previousColor
    }

    private fun drawAnimation(batch: Batch, effect: Animation, alpha: Float) {
        if (effect.age < 0f) return
        val clip = combatEffectsAssets[effect.clip]
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, if (clip.additive) GL20.GL_ONE else GL20.GL_ONE_MINUS_SRC_ALPHA)
        val progress = effect.age / effect.duration
        val fade = if (effect.clip == "smoke") (1f - progress) else 1f
        batch.setColor(1f, 1f, 1f, effect.alpha * alpha * fade)
        val frame = clip.frame(progress)
        drawRegion(batch, frame, effect.x, effect.y + effect.age * effect.drift,
            effect.size, effect.size * frame.regionHeight / frame.regionWidth, effect.angle)
    }

    private fun drawProjectile(batch: Batch, shot: Projectile, alpha: Float) {
        val angle = atan2(shot.targetY - shot.y, shot.targetX - shot.x)
        val dx = cos(angle); val dy = sin(angle)
        val degrees = angle * 180f / PI.toFloat()
        val lead = min(18f, hypot(shot.targetX - shot.x, shot.targetY - shot.y))
        val x = shot.x + dx * lead; val y = shot.y + dy * lead
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        when (shot.kind) {
            MachineKind.MISSILE, MachineKind.HEAVY_MISSILE -> {
                val heavy = shot.kind == MachineKind.HEAVY_MISSILE
                batch.setColor(1f, 0.45f, 0.12f, 0.8f * alpha)
                drawRegion(batch, combatEffectsAssets["fire"].frame((shot.age * 8f) % 1f),
                    x - dx * 16f, y - dy * 16f, 30f, 22f, degrees)
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                batch.setColor(1f, 1f, 1f, alpha)
                drawRegion(batch, stageAssetsManager.unsafeRegion("gfx/models/bullets/ally.atlas", if (heavy) "22" else "04"),
                    x, y, if (heavy) 36f else 29f, if (heavy) 18f else 14f, degrees)
            }
            MachineKind.PLASMA, MachineKind.ION -> {
                batch.setColor(0.1f, 0.65f, 1f, 0.45f * alpha)
                line(batch, x - dx * 36f, y - dy * 36f, x, y, if (shot.kind == MachineKind.ION) 7f else 11f)
                batch.setColor(0.5f, 0.95f, 1f, alpha)
                drawRegion(batch, combatEffectsAssets["fire"].frame((shot.age * 6f) % 1f), x, y, 30f, 30f, degrees)
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                batch.setColor(0.05f, 0.3f, 0.65f, 0.85f * alpha)
                line(batch, x - dx * 24f, y - dy * 24f, x + dx * 4f, y + dy * 4f, 4.5f)
                batch.setColor(0.5f, 0.95f, 1f, alpha)
                line(batch, x - dx * 24f, y - dy * 24f, x + dx * 4f, y + dy * 4f, 2f)
            }
            else -> {
                val sides = if (shot.twinBarrels) listOf(-10f, 10f) else listOf(0f)
                sides.forEach { side ->
                    val px = x - dy * side; val py = y + dx * side
                    // An opaque rim keeps tracers readable on snow as well as dark terrain.
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
                    batch.setColor(0.22f, 0.10f, 0.035f, 0.8f * alpha)
                    line(batch, px - dx * 21f, py - dy * 21f, px + dx * 4f, py + dy * 4f, 4.8f)
                    batch.setColor(1f, if (shot.team == Team.ALLY) 0.72f else 0.4f, 0.12f, alpha)
                    line(batch, px - dx * 20f, py - dy * 20f, px + dx * 4f, py + dy * 4f, 3f)
                    batch.setColor(1f, 0.97f, 0.72f, alpha)
                    line(batch, px - dx * 12f, py - dy * 12f, px + dx * 4f, py + dy * 4f, 1.2f)
                }
            }
        }
    }

    private fun drawRegion(batch: Batch, region: TextureRegion, x: Float, y: Float, w: Float, h: Float, angle: Float) {
        batch.draw(region, x - w / 2f, y - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, angle)
    }

    private fun line(batch: Batch, x: Float, y: Float, tx: Float, ty: Float, width: Float) {
        val length = hypot(tx - x, ty - y)
        batch.draw(pixel, x, y - width / 2f, 0f, width / 2f, length, width, 1f, 1f,
            atan2(ty - y, tx - x) * 180f / PI.toFloat(), 0, 0, 1, 1, false, false)
    }
}
