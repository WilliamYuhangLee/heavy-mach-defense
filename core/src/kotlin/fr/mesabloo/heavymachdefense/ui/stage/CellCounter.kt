package fr.mesabloo.heavymachdefense.ui.stage

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import fr.mesabloo.heavymachdefense.managers.FontManager
import fr.mesabloo.heavymachdefense.managers.fontManager
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

class CellCounter(private val maxCells: KProperty0<Long>, private val currentCells: KMutableProperty0<Long>) : Group() {
    private val current = Label("0", Label.LabelStyle().also {
        it.font = fontManager.bitmapFonts[FontManager.STAGE]
    })
    private val max = Label("0", Label.LabelStyle().also {
        it.font = fontManager.bitmapFonts[FontManager.LEVEL]
    })

    init {
        this.addActor(this.current.also {
            it.setAlignment(Align.bottomRight)
            it.setSize(55f, it.style.font.capHeight)
            it.setPosition(0f, -4f)
        })
        this.addActor(this.max.also {
            it.setAlignment(Align.bottomLeft)
            it.setSize(46f, it.style.font.capHeight)
            it.setPosition(66f, 1f)
        })

        this.touchable = Touchable.disabled
    }

    override fun act(delta: Float) {
        this.current.setText(this.currentCells.get().toString())
        this.max.setText(this.maxCells.get().toString())

        super.act(delta)
    }
}