package fr.mesabloo.heavymachdefense.managers.assets

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import ktx.assets.load

class MenuAssetsManager : Disposable {
    companion object {
        const val BACKGROUND = "gfx/ui/slots/background.jpg"

        const val SLOT = "gfx/ui/slots/slots.atlas"
    }

    fun preload() {
        if (!assetManager.isLoaded(BACKGROUND)) assetManager.load<Texture>(BACKGROUND)

        if (!assetManager.isLoaded(SLOT)) assetManager.load<TextureAtlas>(SLOT)
    }

    fun getSlot(isSelected: Boolean): TextureRegion =
        assetManager.get<TextureAtlas>(SLOT)
            .findRegion("slot${if (isSelected) "-selected" else ""}")

    fun get(path: String): TextureRegion = TextureRegion(assetManager.get<Texture>(path))

    fun isFullyLoaded(): Boolean = listOf(BACKGROUND, SLOT).all { assetManager.isLoaded(it) }

    override fun dispose() = Unit // Shared assets are released by MainGame.

}

val menuAssetsManager by lazy { MenuAssetsManager() }