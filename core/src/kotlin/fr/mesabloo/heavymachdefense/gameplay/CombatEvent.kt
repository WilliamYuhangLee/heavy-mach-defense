package fr.mesabloo.heavymachdefense.gameplay

import fr.mesabloo.heavymachdefense.data.MachineKind

/** One-shot presentation events. Rendering never changes combat damage or targeting. */
sealed class CombatEvent {
    data class Shot(val sourceId: Int?, val team: Team, val kind: MachineKind,
                    val x: Float, val y: Float, val angle: Float, val twinBarrels: Boolean) : CombatEvent()
    data class Hit(val targetId: Int?, val team: Team, val x: Float, val y: Float,
                   val angle: Float, val damage: Float) : CombatEvent()
    data class Impact(val kind: MachineKind, val x: Float, val y: Float, val angle: Float,
                      val radius: Float, val emp: Boolean = false) : CombatEvent()
    data class Destroyed(val unitId: Int?, val team: Team, val kind: MachineKind,
                         val x: Float, val y: Float) : CombatEvent()
}
