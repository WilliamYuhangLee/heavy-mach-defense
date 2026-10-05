package fr.mesabloo.heavymachdefense.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Dialog
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.Actor

fun showChoiceDialog(stage: Stage, title: String, message: String, choices: List<Pair<String, () -> Unit>>) {
    val skin = dialogSkin()
    val dialog = object : Dialog(title, skin) {
        private var released = false
        override fun remove(): Boolean {
            val removed = super.remove()
            if (removed && !released) { released = true; skin.dispose() }
            return removed
        }
    }
    dialog.isModal = true; dialog.isMovable = false
    dialog.pad(38f,18f,18f,18f)
    val content = Table()
    val label = Label(message, skin); label.setWrap(true)
    content.add(label).width(480f).pad(10f).row()
    choices.forEach { (labelText, action) ->
        val button = TextButton(labelText, skin)
        button.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                dialog.hide(null)
                Gdx.app.postRunnable { action() }
            }
        })
        content.add(button).width(480f).height(48f).pad(5f).row()
    }
    dialog.contentTable.add(ScrollPane(content).also { it.setScrollingDisabled(true,false) })
        .width(510f).height(minOf(content.prefHeight,660f))
    dialog.show(stage, null)
    dialog.setPosition((stage.width - dialog.width) / 2f, (stage.height - dialog.height) / 2f)
}
