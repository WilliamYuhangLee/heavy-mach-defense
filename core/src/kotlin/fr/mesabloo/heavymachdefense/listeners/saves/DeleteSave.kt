package fr.mesabloo.heavymachdefense.listeners.saves

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import fr.mesabloo.heavymachdefense.screens.SavesSelectionScreen
import fr.mesabloo.heavymachdefense.ui.showGameDialog

class DeleteSave(private val screen: SavesSelectionScreen) : ClickListener() {
    override fun clicked(event: InputEvent?, x: Float, y: Float) {
        val saveIndex = screen.focusedIndex
        if (saveIndex != -1) {
            showGameDialog(screen.ui, "Do you really want to delete this save?",
                confirmLabel = "Yes", cancelLabel = "No") {
                screen.removeSaveFromIndex(saveIndex)
            }
        }
    }
}
