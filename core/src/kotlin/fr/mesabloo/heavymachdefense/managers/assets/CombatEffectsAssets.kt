package fr.mesabloo.heavymachdefense.managers.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import java.util.zip.ZipInputStream

data class EffectClip(val frames: List<TextureRegion>, val additive: Boolean) {
    fun frame(progress: Float) = frames[(progress.coerceIn(0f, 0.9999f) * frames.size).toInt()]
}

/** Packs the upstream split-frame ZIPs into small GPU sheets once per application. */
class CombatEffectsAssets : Disposable {
    private val names = listOf("damage-hmg", "damage", "explode-npc", "explode-ground",
        "explode-boss", "explode-plasma", "explode-ion", "explode-02", "fire", "smoke", "plasma")
    private val clips = mutableMapOf<String, EffectClip>()
    private val textures = mutableListOf<Texture>()
    val progress: Float get() = clips.size.toFloat() / names.size
    val isLoaded: Boolean get() = clips.size == names.size
    operator fun get(name: String): EffectClip = clips.getValue(name)

    // Loading one clip per frame keeps the progress screen responsive on first mission entry.
    fun loadNext() {
        if (isLoaded) return
        val name = names[clips.size]
        val files = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(Gdx.files.internal("gfx/models/effects/$name-split.zip").read()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".png")) {
                    files.add(entry.name.substringAfterLast('/').substringBefore('.') to zip.readBytes())
                }
                entry = zip.nextEntry
            }
        }
        // Names encode column then row: 00, 10, 20, 30, 01, 11, ...
        files.sortBy { it.first.reversed() }
        val frames = files.map { (_, bytes) -> Pixmap(bytes, 0, bytes.size) }
        require(frames.isNotEmpty()) { "No animation frames in $name" }
        val w = frames.maxOf { it.width }
        val h = frames.maxOf { it.height }
        val columns = minOf(4, frames.size)
        val sheet = Pixmap(columns * (w + 2), ((frames.size + columns - 1) / columns) * (h + 2), Pixmap.Format.RGBA8888)
        sheet.setBlending(Pixmap.Blending.None)
        try {
            frames.forEachIndexed { i, frame -> sheet.drawPixmap(frame, i % columns * (w + 2) + 1, i / columns * (h + 2) + 1) }
            val texture = Texture(sheet)
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            textures.add(texture)
            clips[name] = EffectClip(frames.mapIndexed { i, frame ->
                TextureRegion(texture, i % columns * (w + 2) + 1, i / columns * (h + 2) + 1, frame.width, frame.height)
            }, name == "fire" || name == "explode-boss")
        } finally {
            frames.forEach { it.dispose() }
            sheet.dispose()
        }
    }

    override fun dispose() {
        textures.forEach { it.dispose() }
        textures.clear()
        clips.clear()
    }
}

val combatEffectsAssets by lazy { CombatEffectsAssets() }
