package fr.mesabloo.heavymachdefense.listeners.stage

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import fr.mesabloo.heavymachdefense.data.Builds
import fr.mesabloo.heavymachdefense.ui.stage.BuildTurretItem
import fr.mesabloo.heavymachdefense.timers.cellMiningTimer
import fr.mesabloo.heavymachdefense.ui.stage.BuildMachineItem
import fr.mesabloo.heavymachdefense.ui.stage.BuildQueue
import fr.mesabloo.heavymachdefense.ui.stage.slots.BuildSlot
import fr.mesabloo.heavymachdefense.ui.stage.slots.MachineBuildSlot
import fr.mesabloo.heavymachdefense.ui.stage.slots.TurretBuildSlot
import kotlin.reflect.KMutableProperty0

class BuildMachineIfPossible(
    private val slot: BuildSlot,
    private val cells: KMutableProperty0<Long>,
    private val buildQueue: BuildQueue,
    private val builds: Builds,
    private val upgradeMenuShown: KMutableProperty0<Boolean>
) :
    ClickListener() {
    override fun clicked(event: InputEvent?, x: Float, y: Float) {
        slot.act(0f) // Recheck capacity after each click, including multiple clicks in one frame.
        if (!this.slot.isDisabled && !this.upgradeMenuShown.get() && !cellMiningTimer.isPaused && !cellMiningTimer.isStopped && cells.get() >= slot.cellCost) {
            val currentCells = this.cells.get()

            this.cells.set(currentCells - this.slot.cellCost)

            this.buildQueue.build(
                when (this.slot) {
                    is MachineBuildSlot -> BuildMachineItem(this.slot.kind, this.slot.level, this.builds)
                    is TurretBuildSlot -> BuildTurretItem(this.slot.kind, this.slot.level, this.builds)
                    else -> error("Unsupported build slot: ${slot.javaClass.simpleName}")
                }
            )
        }
    }
}