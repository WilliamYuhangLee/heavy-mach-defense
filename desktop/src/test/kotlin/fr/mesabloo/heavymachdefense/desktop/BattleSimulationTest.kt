package fr.mesabloo.heavymachdefense.desktop

import fr.mesabloo.heavymachdefense.gameplay.*
import fr.mesabloo.heavymachdefense.data.*
import java.util.Date
import kotlin.math.abs
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object BattleSimulationTest {
    private fun run(b: Battle, seconds: Int) { repeat(seconds * 60) { b.advance(1f/60f) } }
    private fun checkCampaignBalance() {
        val builds = Json { allowStructuredMapKeys = true }.decodeFromString<Builds>(
            javaClass.getResourceAsStream("/data/build-info.json")!!.bufferedReader().readText())
        val upgrades = Json.decodeFromString<Upgrades>(
            javaClass.getResourceAsStream("/data/upgrades.json")!!.bufferedReader().readText())
        // Affordable early/mid/late-game loadouts use the shipping build costs and build times.
        for (stage in listOf(8, 20, 40, 60, 80)) {
            val unitLevel = (1 + stage / 12).coerceAtMost(7)
            val economyLevel = (1 + stage / 10).coerceAtMost(8)
            val defenseLevel = (1 + stage / 15).coerceAtMost(6)
            val battle = Battle(stage, upgrades.base_defense[defenseLevel - 1].defense / 10f)
            battle.cannonLevel = defenseLevel
            val capacity = upgrades.cell_storage[economyLevel - 1].storage.toFloat()
            val income = 20f * upgrades.cell_research[economyLevel - 1].multiplier
            var cells = capacity * 0.4f
            val roster = if (stage < 20) listOf(MachineKind.RIFLE, MachineKind.SHOTGUN)
                else listOf(MachineKind.SHOTGUN, MachineKind.SHOTGUN, MachineKind.PLASMA, MachineKind.MISSILE)
            var next = 0
            val pending = mutableListOf<Pair<MachineKind, Float>>()
            for (tick in 0 until 300 * 60) {
                if (battle.result != BattleResult.RUNNING) break
                cells = (cells + income / 60f).coerceAtMost(capacity)
                val kind = roster[next % roster.size]
                val build = builds.machines[kind to unitLevel]!!
                if (cells >= build.cellCost && pending.count { it.first == kind } < build.maxAllowed) {
                    cells -= build.cellCost
                    pending.add(kind to build.time * upgrades.build_time[economyLevel - 1].multiplier)
                    next++
                }
                for (index in pending.indices.reversed()) {
                    val (builtKind, remaining) = pending[index]
                    if (remaining <= 1f / 60f) {
                        battle.spawn(builtKind, unitLevel)
                        pending.removeAt(index)
                    } else pending[index] = builtKind to remaining - 1f / 60f
                }
                battle.advance(1f / 60f)
            }
            println("Campaign stage $stage: ${battle.result} in ${battle.elapsed.toInt()}s, ally HP ${battle.allyHp}, enemy HP ${battle.enemyHp}")
            check(battle.result == BattleResult.VICTORY) { "Campaign strategy stalled at stage $stage" }
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        val duel = Battle(1, enemiesEnabled = false)
        val rifle = duel.spawn(MachineKind.RIFLE,1,Team.ALLY,atX=256f,atY=800f)
        val enemy = duel.spawn(MachineKind.RIFLE,1,Team.ENEMY,atX=256f,atY=1000f)
        run(duel,12)
        check(duel.defeatedEnemies == 1 && rifle.hp < rifle.stats.health && enemy !in duel.units) { "Units must engage, exchange damage, and die" }
        check(duel.creditsEarned == 55L)
        val credits = duel.creditsEarned; run(duel,2); check(duel.creditsEarned == credits)

        val pausedY=rifle.y; val pausedTime=duel.elapsed; duel.advance(0.2f,true)
        check(rifle.y==pausedY && duel.elapsed==pausedTime)
        val distances = listOf(30,60,120).map { fps ->
            val b=Battle(1,enemiesEnabled=false);val u=b.spawn(MachineKind.RIFLE,1)
            repeat(fps*2){b.advance(1f/fps)};u.y
        }
        check(distances.all { abs(it-distances[0])<0.01f }) { "Frame rate changed simulation" }

        val priority=Battle(1,enemiesEnabled=false)
        priority.spawn(MachineKind.RIFLE,1,atX=256f,atY=1770f)
        val guard=priority.spawn(MachineKind.RIFLE,1,Team.ENEMY,atX=256f,atY=1830f)
        priority.advance(1f/60f)
        check(priority.projectiles.any { it.team==Team.ALLY && it.targetId==guard.id && !it.targetsBase })

        val stationary=Battle(1,enemiesEnabled=false)
        val turret=stationary.spawn(MachineKind.RIFLE,1,turret=TurretKind.RIFLE,atX=200f,atY=900f)
        stationary.spawn(MachineKind.RIFLE,1,Team.ENEMY,atX=200f,atY=1030f)
        run(stationary,10);check(turret.y==900f && stationary.defeatedEnemies==1)

        val emp=Battle(1,enemiesEnabled=false)
        val stunned=emp.spawn(MachineKind.HMG,1,Team.ENEMY,atX=250f,atY=900f)
        emp.special(250f,900f,0f,100f,3f);run(emp,2);check(stunned.y==900f)

        val mission=Battle(1)
        var cells=200f;var buildRemaining=0f;var queued=false
        for (tick in 0 until 300*60) {
            if(mission.result!=BattleResult.RUNNING)break
            cells=(cells+20f/60f).coerceAtMost(500f)
            if(!queued && cells>=60f){cells-=60f;buildRemaining=3f;queued=true}
            if(queued){buildRemaining-=1f/60f;if(buildRemaining<=0f){mission.spawn(MachineKind.RIFLE,1);queued=false}}
            mission.advance(1f/60f)
        }
        check(mission.result==BattleResult.VICTORY && mission.defeatedEnemies>0) { "Mission policy failed: ${mission.result}, ally=${mission.allyHp}, enemy=${mission.enemyHp}" }
        val frozen=mission.enemyHp;mission.advance(1f);check(mission.enemyHp==frozen)
        val idle=Battle(1);run(idle,300)
        check(idle.result==BattleResult.DEFEAT) { "An undefended base must lose: ${idle.allyHp}" }
        val save=GameSave(Date(0),Date(0));val reward=MissionProgress.victory(save,1)
        check(save.lastStageCompleted==1 && save.credits==reward)
        val replay=MissionProgress.victory(save,1);check(replay<reward && save.lastStageCompleted==1)
        MissionProgress.victory(save,2);check(save.buildSlots.any { it is MachineSlot && it.kind==MachineKind.MISSILE })
        checkCampaignBalance()
        println("PASS: targeting, mutual combat, deaths, one-time kill rewards, pause, 30/60/120 FPS, turrets, EMP, first-mission victory (${mission.elapsed.toInt()}s), idle defeat (${idle.elapsed.toInt()}s), progression and replay rewards.")
    }
}
