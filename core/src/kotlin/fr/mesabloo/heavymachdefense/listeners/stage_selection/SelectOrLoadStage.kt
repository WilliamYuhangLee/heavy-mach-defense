package fr.mesabloo.heavymachdefense.listeners.stage_selection
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import fr.mesabloo.heavymachdefense.screens.StageSelectionScreen
class SelectOrLoadStage(private val screen: StageSelectionScreen, private val index: Int) : ClickListener() {
    override fun clicked(event: InputEvent?, x: Float, y: Float) {
        if (screen.isLoading || index > screen.save.lastStageCompleted) return
        if (screen.scrollPane.selected == index) screen.beginMission(index)
        else screen.scrollPane.selected = index
    }
}
