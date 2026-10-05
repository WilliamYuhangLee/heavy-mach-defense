package fr.mesabloo.heavymachdefense.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import fr.mesabloo.heavymachdefense.MainGame
import fr.mesabloo.heavymachdefense.data.GameSave
import fr.mesabloo.heavymachdefense.managers.FontManager
import fr.mesabloo.heavymachdefense.managers.fontManager
import fr.mesabloo.heavymachdefense.managers.assets.*
import fr.mesabloo.heavymachdefense.ui.showChoiceDialog

class MissionLoadingScreen(game: MainGame, private val level: Int,
                           private val save: GameSave, private val saveIndex: Int) : AbstractScreen(game) {
    private var elapsed = 0f
    private var complete = false
    private lateinit var progress: Label
    override fun show() {
        super.show()
        progress = Label("Preparing mission $level...", Label.LabelStyle(
            fontManager.bitmapFonts[FontManager.TREBUCHET_MS_BOLD_28_BLUE], Color.DARK_GRAY))
        progress.setPosition(130f, 512f); background.addActor(progress)
        stageAssetsManager.preload(level); buttonAssetsManager.preload()
        removeLoadingOverlayEnd()
    }
    override fun render(delta: Float) {
        super.render(delta)
        if (complete) return
        elapsed += delta
        try {
            assetManager.update(8)
            progress.setText("Preparing mission $level: ${(assetManager.progress * 100).toInt()}%")
            if (stageAssetsManager.isFullyLoaded() && buttonAssetsManager.isFullyLoaded()) {
                complete = true
                Gdx.app.postRunnable { game.replaceScreen(StageScreen(game, level, save, saveIndex)) }
            } else if (elapsed > 20f) fail("Mission assets did not finish loading.")
        } catch (e: Exception) {
            Gdx.app.error("Mission loading", "Asset load failed", e)
            fail("Could not load mission assets: ${e.message}")
        }
    }
    private fun fail(message: String) {
        complete = true
        showChoiceDialog(ui, "Unable to start mission", message,
            listOf("Back to mission select" to {
                levelSelectionAssetsManager.preload(); assetManager.finishLoading()
                game.replaceScreen(StageSelectionScreen(game, save, saveIndex))
            }))
    }
}
