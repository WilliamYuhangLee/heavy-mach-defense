package fr.mesabloo.heavymachdefense.listeners.stage
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Dialog
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import fr.mesabloo.heavymachdefense.screens.StageScreen
class SaveAndExitStage(private val screen: StageScreen, private val dialog: Dialog) : ClickListener() {
    override fun clicked(event: InputEvent?, x: Float, y: Float) {
        dialog.hide()
        screen.saveAndExit()
    }
}
