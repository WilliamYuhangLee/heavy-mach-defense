package fr.mesabloo.heavymachdefense.listeners.stage_selection
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import fr.mesabloo.heavymachdefense.screens.StageSelectionScreen
class LoadStage(private val screen: StageSelectionScreen) : ClickListener() {
    override fun clicked(event: InputEvent?, x: Float, y: Float) {
        val index = screen.scrollPane.selected
        if (screen.isLoading || index > screen.save.lastStageCompleted) return
        screen.beginMission(index)
    }
}
