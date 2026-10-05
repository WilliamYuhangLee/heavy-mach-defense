package fr.mesabloo.heavymachdefense.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*

// Render prompts inside the game: Swing dialogs hang with macOS GLFW's first-thread loop.
private fun dialogSkin(): Skin = Skin().apply {
    val pixel = Pixmap(1, 1, Pixmap.Format.RGBA8888)
    pixel.setColor(Color.WHITE)
    pixel.fill()
    add("pixel", Texture(pixel))
    pixel.dispose()
    val font = BitmapFont()
    font.data.setScale(1.4f)
    add("font", font)
    add("default", Window.WindowStyle(font, Color.WHITE, newDrawable("pixel", Color(0.08f, 0.12f, 0.17f, 1f))))
    add("default", Label.LabelStyle(font, Color.WHITE))
    add("default", TextButton.TextButtonStyle(
        newDrawable("pixel", Color(0.2f, 0.3f, 0.4f, 1f)),
        newDrawable("pixel", Color(0.3f, 0.5f, 0.6f, 1f)), null, font))
    add("default", TextField.TextFieldStyle(font, Color.WHITE,
        newDrawable("pixel", Color.WHITE), newDrawable("pixel", Color.GRAY),
        newDrawable("pixel", Color(0.15f, 0.2f, 0.25f, 1f))))
}

fun showGameDialog(stage: Stage, message: String, prompt: Boolean = false,
                   confirmLabel: String = "OK", cancelLabel: String? = null,
                   onConfirm: (String) -> Unit = {}) {
    val skin = dialogSkin()
    val field = if (prompt) TextField("", skin).apply { maxLength = 32 } else null
    val dialog = object : Dialog("Heavy MACH: Defense", skin) {
        private var disposed = false
        override fun result(value: Any?) {
            if (value == true) {
                val name = field?.text?.trim() ?: ""
                if (field != null && name.isEmpty()) {
                    cancel()
                    stage.keyboardFocus = field
                    return
                }
                onConfirm(name)
            }
        }
        override fun remove(): Boolean {
            val removed = super.remove()
            if (removed && !disposed) {
                disposed = true
                skin.dispose()
            }
            return removed
        }
    }
    dialog.isModal = true
    dialog.isMovable = false
    dialog.pad(40f, 24f, 24f, 24f)
    dialog.contentTable.defaults().pad(12f)
    dialog.text(message)
    if (field != null) {
        dialog.contentTable.row()
        dialog.contentTable.add(field).width(380f).height(44f)
    }
    dialog.buttonTable.defaults().minWidth(110f).height(44f).pad(8f)
    if (cancelLabel != null) dialog.button(cancelLabel, false)
    dialog.button(confirmLabel, true)
    dialog.key(Input.Keys.ESCAPE, false)
    dialog.key(Input.Keys.ENTER, true)
    dialog.show(stage)
    if (field != null) stage.keyboardFocus = field
}
