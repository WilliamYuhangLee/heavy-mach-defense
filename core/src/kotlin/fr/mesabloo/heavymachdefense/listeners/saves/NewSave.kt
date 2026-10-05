package fr.mesabloo.heavymachdefense.listeners.saves

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import fr.mesabloo.heavymachdefense.data.GameSave
import fr.mesabloo.heavymachdefense.screens.SavesSelectionScreen
import fr.mesabloo.heavymachdefense.ui.showGameDialog
import java.util.Date

class NewSave(private val screen: SavesSelectionScreen) : ClickListener() {
    override fun clicked(event: InputEvent?, x: Float, y: Float) {
        if (screen.numberOfSaves < 5) {
            showGameDialog(screen.ui, "Enter your username", prompt = true,
                confirmLabel = "Create", cancelLabel = "Cancel") { name ->
                val save = GameSave(creationDate = Date(), lastAccessedDate = Date())
                save.name = name
                screen.addSave(save)
            }
        } else {
            showGameDialog(screen.ui, "Not enough slots to create a new save")
        }
    }
}
