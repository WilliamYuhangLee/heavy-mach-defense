package fr.mesabloo.heavymachdefense.managers.assets

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import ktx.assets.load

class LoadingAssetsManager : Disposable {
    companion object {
        const val LOADING_BOTTOM = "gfx/ui/loading/bottom.png"
        const val LOADING_TOP = "gfx/ui/loading/top.png"
        const val LOADING_LEFT = "gfx/ui/loading/left.png"
        const val LOADING_RIGHT = "gfx/ui/loading/right.png"
        const val LOADING_CENTER = "gfx/ui/loading/center.png"
    }

    fun preload() {
        if (!assetManager.isLoaded(LOADING_BOTTOM)) assetManager.load<Texture>(LOADING_BOTTOM)
        if (!assetManager.isLoaded(LOADING_TOP)) assetManager.load<Texture>(LOADING_TOP)
        if (!assetManager.isLoaded(LOADING_LEFT)) assetManager.load<Texture>(LOADING_LEFT)
        if (!assetManager.isLoaded(LOADING_RIGHT)) assetManager.load<Texture>(LOADING_RIGHT)
        if (!assetManager.isLoaded(LOADING_CENTER)) assetManager.load<Texture>(LOADING_CENTER)
    }

    fun isFullyLoaded(): Boolean =
        listOf(LOADING_BOTTOM, LOADING_RIGHT, LOADING_LEFT, LOADING_CENTER, LOADING_TOP)
            .all { assetManager.isLoaded(it) }

    override fun dispose() = Unit // Shared assets are released by MainGame.

    fun texture(name: String): TextureRegion = TextureRegion(assetManager.get<Texture>(name))
}

val loadingAssetsManager by lazy { LoadingAssetsManager() }