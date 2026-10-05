package fr.mesabloo.heavymachdefense.gameplay

import fr.mesabloo.heavymachdefense.data.MachineKind
import fr.mesabloo.heavymachdefense.data.TurretKind
import kotlin.math.*

enum class Team { ALLY, ENEMY }
enum class BattleResult { RUNNING, VICTORY, DEFEAT }

data class UnitStats(val health: Float, val damage: Float, val range: Float, val reload: Float,
                     val speed: Float, val projectileSpeed: Float = 460f, val splash: Float = 0f)

object CombatBalance {
    fun baseCannonDamage(level: Int) = 5f + (level.coerceIn(1, 7) - 1) * 4f

    // New balance for this remake; the original game's combat tables are not in the repository.
    fun stats(kind: MachineKind, level: Int, turret: Boolean = false): UnitStats {
        val base = when (kind) {
            MachineKind.RIFLE -> UnitStats(150f, 22f, 135f, 0.75f, 66f)
            MachineKind.HMG -> UnitStats(230f, 13f, 155f, 0.25f, 54f)
            MachineKind.MISSILE -> UnitStats(180f, 75f, 245f, 2.1f, 50f, 250f, 45f)
            MachineKind.HEAVY_MISSILE -> UnitStats(270f, 135f, 285f, 2.8f, 40f, 220f, 65f)
            MachineKind.ION -> UnitStats(280f, 95f, 210f, 1.5f, 48f, 620f)
            MachineKind.PLASMA -> UnitStats(310f, 52f, 170f, 0.85f, 50f, 330f, 35f)
            MachineKind.SHOTGUN -> UnitStats(320f, 100f, 90f, 1.2f, 76f, 560f, 40f)
            MachineKind.TANKER -> UnitStats(1000f, 12f, 100f, 1.2f, 36f)
        }
        val factor = 1f + (level.coerceIn(1, 10) - 1) * 0.3f
        return base.copy(health = base.health * factor * if (turret) 2f else 1f,
            damage = base.damage * factor, speed = if (turret) 0f else base.speed,
            range = base.range * if (turret) 1.25f else 1f)
    }
    fun machineFor(kind: TurretKind): MachineKind = when (kind) {
        TurretKind.RIFLE -> MachineKind.RIFLE
        TurretKind.MISSILE -> MachineKind.MISSILE
        TurretKind.VULCAN -> MachineKind.HMG
        TurretKind.PLASMA -> MachineKind.PLASMA
        TurretKind.ION, TurretKind.LASER -> MachineKind.ION
    }
}

class CombatUnit(val id: Int, val team: Team, val kind: MachineKind, val level: Int,
                 var x: Float, var y: Float, val stats: UnitStats, val turretKind: TurretKind? = null) {
    var hp = stats.health
    var cooldown = 0f
    var stunnedFor = 0f
    var angle = if (team == Team.ALLY) 90f else 270f
    var moving = false
    var firing = 0f
}

class Projectile(val team: Team, var x: Float, var y: Float, val targetId: Int?,
                 var targetX: Float, var targetY: Float, val damage: Float,
                 val speed: Float, val splash: Float, val targetsBase: Boolean) {
    var age = 0f
}
class Blast(val x: Float, val y: Float, val radius: Float, var life: Float = 0.4f)

class Battle(val level: Int, allyMaxHp: Float = 1000f, val enemiesEnabled: Boolean = true) {
    val units = mutableListOf<CombatUnit>()
    val projectiles = mutableListOf<Projectile>()
    val blasts = mutableListOf<Blast>()
    var allyMaxHp = allyMaxHp; private set
    var allyHp = allyMaxHp; private set
    val enemyMaxHp = 900f + level.coerceIn(1, 80) * 100f
    var enemyHp = enemyMaxHp; private set
    var result = BattleResult.RUNNING; private set
    var elapsed = 0f; private set
    var defeatedEnemies = 0; private set
    var creditsEarned = 0L; private set
    var cannonLevel = 1
    var shotsFired = 0; private set
    private var nextId = 1
    private var allyDeployments = 0
    private var enemyDeployments = 0
    private var waveClock = 5f
    private var waveNumber = 0
    private var allyCannonClock = 0f
    private var enemyCannonClock = 0f
    private var accumulator = 0.0

    fun spawn(kind: MachineKind, unitLevel: Int, team: Team = Team.ALLY,
              turret: TurretKind? = null, atX: Float? = null, atY: Float? = null): CombatUnit {
        check(result == BattleResult.RUNNING) { "Mission has ended" }
        val deployment = if (team == Team.ALLY) allyDeployments++ else enemyDeployments++
        val lanes = floatArrayOf(256f, 176f, 336f, 96f, 416f)
        val x = atX ?: lanes[deployment % lanes.size]
        val y = atY ?: if (team == Team.ALLY) 208f + (deployment / 5 % 2) * 48f else 1840f - (deployment / 5 % 2) * 48f
        val stats = CombatBalance.stats(kind, unitLevel, turret != null).let {
            if (team == Team.ENEMY) it.copy(health = it.health * 0.85f, damage = it.damage * 0.8f) else it
        }
        return CombatUnit(nextId++, team, kind, unitLevel, x, y, stats, turret).also { units.add(it) }
    }

    fun upgradeBase(maxHp: Float, cannon: Int) {
        if (maxHp > allyMaxHp) allyHp += maxHp - allyMaxHp
        allyMaxHp = maxHp
        allyHp = allyHp.coerceAtMost(maxHp)
        cannonLevel = cannon.coerceIn(1, 7)
    }

    fun advance(delta: Float, paused: Boolean = false) {
        if (paused || result != BattleResult.RUNNING || !delta.isFinite()) return
        accumulator += delta.coerceIn(0f, 0.25f).toDouble()
        val step = 1.0 / 60.0
        while (accumulator + 1e-8 >= step && result == BattleResult.RUNNING) {
            tick(step.toFloat())
            accumulator -= step
        }
    }

    private fun tick(dt: Float) {
        elapsed += dt
        blasts.forEach { it.life -= dt }
        blasts.removeAll { it.life <= 0f }
        if (enemiesEnabled) {
            waveClock -= dt
            if (waveClock <= 0f) {
                val unlocks = arrayOf(MachineKind.RIFLE, MachineKind.HMG, MachineKind.MISSILE,
                    MachineKind.SHOTGUN, MachineKind.PLASMA, MachineKind.ION, MachineKind.HEAVY_MISSILE)
                val count = 1
                if (units.count { it.team == Team.ENEMY } < 35) {
                    repeat(count.coerceAtMost(5)) {
                        val kind = unlocks[(waveNumber + it) % (1 + (level - 1) / 3).coerceAtMost(unlocks.size)]
                        spawn(kind, (1 + (level - 1) / 12).coerceAtMost(7), Team.ENEMY)
                    }
                }
                waveNumber++
                waveClock += (8.5f - level * 0.02f).coerceAtLeast(6.5f)
            }
        }
        units.forEach { unit ->
            unit.cooldown = (unit.cooldown - dt).coerceAtLeast(0f)
            unit.firing = (unit.firing - dt).coerceAtLeast(0f)
            unit.moving = false
            if (unit.hp <= 0f) return@forEach
            if (unit.stunnedFor > 0f) { unit.stunnedFor -= dt; return@forEach }
            val target = units.filter { it.team != unit.team && it.hp > 0f }
                .minByOrNull { distance(unit.x, unit.y, it.x, it.y) }
                ?.takeIf { distance(unit.x, unit.y, it.x, it.y) <= max(360f, unit.stats.range) }
            val tx = target?.x ?: unit.x.coerceIn(152f, 360f)
            val ty = target?.y ?: if (unit.team == Team.ALLY) 1904f else 160f
            val distance = distance(unit.x, unit.y, tx, ty)
            unit.angle = atan2(ty - unit.y, tx - unit.x) * 180f / PI.toFloat()
            if (distance <= unit.stats.range) {
                if (unit.cooldown <= 0f) {
                    shoot(unit.team, unit.x, unit.y, target?.id, tx, ty, unit.stats.damage,
                        unit.stats.projectileSpeed, unit.stats.splash, target == null)
                    unit.cooldown = unit.stats.reload
                    unit.firing = 0.12f
                }
            } else if (unit.stats.speed > 0f) {
                val movement = min(unit.stats.speed * dt, distance - unit.stats.range)
                unit.x += (tx - unit.x) / distance * movement
                unit.y += (ty - unit.y) / distance * movement
                unit.moving = true
            }
        }
        // Gentle separation keeps marching units readable without solid terrain blocking the map.
        for (i in units.indices) for (j in i + 1 until units.size) {
            val a = units[i]; val b = units[j]
            if (a.team != b.team || a.stats.speed == 0f || b.stats.speed == 0f) continue
            val dx = b.x - a.x; val dy = b.y - a.y
            val d = sqrt(dx * dx + dy * dy)
            if (d < 30f) {
                val offset = min(30f - d, 24f * dt) / 2f
                val direction = if (abs(dx) < 0.01f) { if (a.id % 2 == 0) 1f else -1f } else sign(dx)
                a.x = (a.x - direction * offset).coerceIn(32f, 480f)
                b.x = (b.x + direction * offset).coerceIn(32f, 480f)
            }
        }
        allyCannonClock -= dt; enemyCannonClock -= dt
        if (allyCannonClock <= 0f && fireBase(Team.ALLY, 256f, 144f, CombatBalance.baseCannonDamage(cannonLevel))) allyCannonClock = 0.85f
        if (enemyCannonClock <= 0f && fireBase(Team.ENEMY, 256f, 1904f, 4f + level * 0.5f)) enemyCannonClock = 1f
        val iterator = projectiles.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next(); shot.age += dt
            val target = shot.targetId?.let { id -> units.firstOrNull { it.id == id && it.hp > 0f } }
            if (shot.targetId != null && target == null) { iterator.remove(); continue }
            if (target != null) { shot.targetX = target.x; shot.targetY = target.y }
            val d = distance(shot.x, shot.y, shot.targetX, shot.targetY)
            if (d <= shot.speed * dt) {
                if (shot.targetsBase) {
                    if (shot.team == Team.ALLY) enemyHp = (enemyHp - shot.damage).coerceAtLeast(0f)
                    else allyHp = (allyHp - shot.damage).coerceAtLeast(0f)
                } else if (shot.splash > 0f) {
                    units.filter { it.team != shot.team && distance(it.x,it.y,shot.targetX,shot.targetY) <= shot.splash }
                        .forEach { it.hp -= shot.damage }
                } else if (target != null) target.hp -= shot.damage
                blasts.add(Blast(shot.targetX, shot.targetY, max(8f, shot.splash)))
                iterator.remove()
            } else if (shot.age > 8f) iterator.remove()
            else { shot.x += (shot.targetX - shot.x) / d * shot.speed * dt; shot.y += (shot.targetY - shot.y) / d * shot.speed * dt }
        }
        removeDead()
        result = when { allyHp <= 0f -> BattleResult.DEFEAT; enemyHp <= 0f -> BattleResult.VICTORY; else -> BattleResult.RUNNING }
    }
    private fun fireBase(team: Team, x: Float, y: Float, damage: Float): Boolean {
        val target = units.filter { it.team != team && it.hp > 0f && distance(x,y,it.x,it.y) <= 240f }
            .minByOrNull { distance(x,y,it.x,it.y) } ?: return false
        shoot(team,x,y,target.id,target.x,target.y,damage,550f,0f,false)
        return true
    }
    private fun shoot(team: Team,x: Float,y: Float,id: Int?,tx: Float,ty: Float,damage: Float,speed: Float,splash: Float,base: Boolean) {
        projectiles.add(Projectile(team,x,y,id,tx,ty,damage,speed,splash,base)); shotsFired++
    }
    fun special(x: Float,y: Float,damage: Float,radius: Float,stun: Float = 0f) {
        if (result != BattleResult.RUNNING) return
        units.filter { it.team == Team.ENEMY && distance(x,y,it.x,it.y) <= radius }.forEach {
            it.hp -= damage; it.stunnedFor = max(it.stunnedFor, stun)
        }
        if (distance(x,y,256f,1904f) <= radius) enemyHp = (enemyHp - damage * 0.25f).coerceAtLeast(0f)
        blasts.add(Blast(x,y,radius,0.65f)); removeDead()
        if (enemyHp <= 0f) result = BattleResult.VICTORY
    }
    private fun removeDead() {
        units.filter { it.hp <= 0f }.forEach {
            blasts.add(Blast(it.x,it.y,24f))
            if (it.team == Team.ENEMY) { defeatedEnemies++; creditsEarned += 45L + it.level * 10L }
        }
        units.removeAll { it.hp <= 0f }
    }
    private fun distance(x: Float,y: Float,tx: Float,ty: Float) = hypot(tx-x,ty-y)
}
