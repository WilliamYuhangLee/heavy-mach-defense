package fr.mesabloo.heavymachdefense.managers

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.utils.Disposable
import ktx.assets.load

class BackgroundMusicManager : Disposable {
    companion object {
        const val INTRO = "sfx/misc/intro.mp3"
        const val GAMEPLAY = "sfx/misc/gameplay.mp3"
    }

    private val manager = AssetManager()
    private var activePath: String? = null

    fun play(path: String, volume: Float = 0.6f) {
        if (activePath != path) {
            activePath?.let { load(it).stop() }
            activePath = path
        }
        load(path).also {
            it.isLooping = true
            it.volume = volume.coerceIn(0f, 1f)
            it.play()
        }
    }

    fun setVolume(path: String, volume: Float) {
        if (manager.isLoaded(path)) load(path).volume = volume.coerceIn(0f, 1f)
    }

    fun pause(path: String) {
        if (activePath == path) load(path).pause()
    }

    fun stop(path: String) {
        if (activePath == path) {
            load(path).stop()
            activePath = null
        }
    }

    fun preload() {
        this.manager.load<Music>(INTRO)
        this.manager.load<Music>(GAMEPLAY)
    }

    fun update() {
        this.manager.update()
    }

    fun isFullyLoaded() = this.manager.isLoaded(INTRO) && this.manager.isLoaded(GAMEPLAY)

    fun load(path: String) : Music = this.manager.get(path)

    override fun dispose() {
        this.manager.dispose()
    }
}

val backgroundMusicManager by lazy { BackgroundMusicManager() }

class SoundEffectManager : Disposable {
    private val shot = lazy { com.badlogic.gdx.Gdx.audio.newSound(com.badlogic.gdx.Gdx.files.internal("sfx/gameplay/shot.wav")) }
    private val explosion = lazy { com.badlogic.gdx.Gdx.audio.newSound(com.badlogic.gdx.Gdx.files.internal("sfx/gameplay/explosion.wav")) }

    fun playShot(volume: Float) {
        if (volume > 0f) shot.value.play(volume.coerceIn(0f, 1f) * 0.25f)
    }

    fun playExplosion(volume: Float) {
        if (volume > 0f) explosion.value.play(volume.coerceIn(0f, 1f) * 0.4f)
    }

    override fun dispose() {
        if (shot.isInitialized()) shot.value.dispose()
        if (explosion.isInitialized()) explosion.value.dispose()
    }
}

val soundEffectManager by lazy { SoundEffectManager() }
