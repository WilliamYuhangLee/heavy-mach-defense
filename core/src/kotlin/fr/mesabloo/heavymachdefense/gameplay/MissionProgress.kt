package fr.mesabloo.heavymachdefense.gameplay

import fr.mesabloo.heavymachdefense.data.*

object MissionProgress {
    const val LAST_STAGE = 80
    private val unlocks = listOf(MachineKind.RIFLE, MachineKind.MISSILE, MachineKind.HMG,
        MachineKind.SHOTGUN, MachineKind.PLASMA, MachineKind.ION, MachineKind.HEAVY_MISSILE, MachineKind.TANKER)
    fun unlockStage(kind: MachineKind) = 1 + unlocks.indexOf(kind) * 2
    fun unlocked(save: GameSave, kind: MachineKind) = save.lastStageCompleted + 1 >= unlockStage(kind)
    fun victory(save: GameSave, level: Int): Long {
        val firstClear = level > save.lastStageCompleted
        save.lastStageCompleted = maxOf(save.lastStageCompleted, level.coerceAtMost(LAST_STAGE))
        val reward = if (firstClear) 700L + level * 150L else 150L + level * 25L
        save.credits += reward
        unlocks.filter { unlocked(save,it) }.forEach { kind ->
            if (save.buildSlots.size < 6 && save.buildSlots.none { it is MachineSlot && it.kind == kind })
                save.buildSlots.add(MachineSlot(kind))
        }
        if (firstClear) save.specialCount[SpecialKind.AIRSTRIKE_BOMB] = (save.specialCount[SpecialKind.AIRSTRIKE_BOMB] ?: 0) + 1
        return reward
    }
}
