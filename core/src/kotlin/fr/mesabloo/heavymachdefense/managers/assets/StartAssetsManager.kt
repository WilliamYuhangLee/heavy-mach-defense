package fr.mesabloo.heavymachdefense.managers.assets

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import ktx.assets.load

class StartAssetsManager : Disposable {
    companion object {
        const val TITLE_BACKGROUND = "gfx/ui/splash/title.jpg"
        const val TAP_TO_START = "gfx/ui/splash/tap_to_start.png"
    }

    fun preload() {
        if (!assetManager.isLoaded(TITLE_BACKGROUND)) assetManager.load<Texture>(TITLE_BACKGROUND)
        if (!assetManager.isLoaded(TAP_TO_START)) assetManager.load<Texture>(TAP_TO_START)
    }

    fun isFullyLoaded(): Boolean = listOf(TITLE_BACKGROUND, TAP_TO_START).all { assetManager.isLoaded(it) }

    override fun dispose() = Unit // Shared assets are released by MainGame.

    fun texture(name: String): TextureRegion = TextureRegion(assetManager.get<Texture>(name))
}

val startAssetsManager by lazy { StartAssetsManager() }