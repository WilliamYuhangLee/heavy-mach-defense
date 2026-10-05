package fr.mesabloo.heavymachdefense.screens

import aurelienribon.tweenengine.Timeline
import aurelienribon.tweenengine.Tween
import aurelienribon.tweenengine.TweenCallback.COMPLETE
import aurelienribon.tweenengine.TweenManager
import aurelienribon.tweenengine.equations.Quart
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import fr.mesabloo.heavymachdefense.MainGame
import fr.mesabloo.heavymachdefense.ifDebug
import fr.mesabloo.heavymachdefense.tweens.ActorAccessor
import fr.mesabloo.heavymachdefense.tweens.ActorAccessor.Companion.POSITION
import fr.mesabloo.heavymachdefense.tweens.ActorAccessor.Companion.SCALE
import fr.mesabloo.heavymachdefense.ui.debug.DebugInfo
import fr.mesabloo.heavymachdefense.ui.loading.*
import fr.mesabloo.heavymachdefense.world.UIWorld
import fr.mesabloo.heavymachdefense.world.UI_HEIGHT
import fr.mesabloo.heavymachdefense.world.UI_WIDTH
import ktx.app.KtxScreen

abstract class AbstractScreen(val game: MainGame, isLoading: Boolean = false) : KtxScreen {
    val ui = UIWorld()
    val background = Group()
    private val foreground = Group()

    private lateinit var debugInfo: DebugInfo

    init {
        this.ui.addActor(this.background)
        this.ui.addActor(this.foreground)

        ifDebug {
            this.foreground.addActor(DebugInfo().also {
                this.debugInfo = it

                it.setPosition(0f, UI_HEIGHT - it.prefHeight)
                it.touchable = Touchable.disabled
            })
        }
    }

    private var loadingAnimationDone = false
    private var readyToSwitch: (() -> Boolean)? = null
    private var nextScreen: (MainGame.() -> Unit)? = null
    private var loadingElapsed = 0f
    private var openingElapsed: Float? = null

    protected val mux = InputMultiplexer()

    private val tweenManager = TweenManager()

    private companion object {
        const val LOADING_DURATION = 0.65f
        const val LOADING_END_DELAY = 0.5f

        private val easeEquation = Quart.INOUT
    }

    var isLoading: Boolean = isLoading
        private set

    init {
        Tween.registerAccessor(Actor::class.java, ActorAccessor())
    }

    override fun hide() {
        Gdx.app.debug(this.javaClass.simpleName, "Hiding application")
    }

    override fun show() {
        Gdx.app.debug(this.javaClass.simpleName, "Showing application")

        if (this.isLoading)
            return

        if (!this.mux.processors.contains(this.ui, true)) this.mux.addProcessor(this.ui)
    }

    override fun pause() {
        Gdx.app.debug(this.javaClass.simpleName, "Pausing application")

        Gdx.input.inputProcessor = null
    }

    override fun resume() {
        Gdx.app.debug(this.javaClass.simpleName, "Resuming application")

        Gdx.input.inputProcessor = this.mux
    }

    override fun dispose() {
        Gdx.app.debug(this.javaClass.simpleName, "Disposing screen")

        super.dispose()

        this.ui.dispose()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)

        this.ui.resize(width, height)
    }

    override fun render(delta: Float) {
        super.render(delta)

        ifDebug {
            this.debugInfo.zIndex = 5000000
        }

        this.ui.act(delta)
        this.tweenManager.update(delta)
        openingElapsed?.let { elapsed ->
            val next = elapsed + delta
            openingElapsed = next
            if (next >= LOADING_DURATION + LOADING_END_DELAY) {
                openingElapsed = null
                this.isLoading = false
                this.show()
                this.removeLoadingOverlayEnd()
            }
        }
        readyToSwitch?.let { ready ->
            loadingElapsed += delta
            try {
                if ((loadingAnimationDone || loadingElapsed > 1.5f) && ready()) {
                    val switch = nextScreen
                    readyToSwitch = null; nextScreen = null
                    if (switch != null) Gdx.app.postRunnable { switch(game) }
                } else if (loadingElapsed > 20f) {
                    failLoading("Loading took too long. Please try again.")
                }
            } catch (failure: Exception) {
                Gdx.app.error("Loading", "Unable to open screen", failure)
                failLoading("Could not load the mission. Please restart the game and try again.")
            }
        }
        this.ui.draw()
    }

    fun addLoadingOverlay(finishedLoading: () -> Boolean, switchScreen: MainGame.() -> Unit) {
        if (readyToSwitch != null || openingElapsed != null) return
        this.isLoading = true
        this.loadingAnimationDone = false
        this.loadingElapsed = 0f
        this.readyToSwitch = finishedLoading
        this.nextScreen = switchScreen
        Gdx.input.inputProcessor = null

        val tweens = mutableListOf<Tween>()

        this.foreground.addActor(LeftPane().also {
            it.setPosition(-it.width, 0f)

            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(0f, 0f)
                    .ease(easeEquation)
            )
        })
        this.foreground.addActor(RightPane().also {
            it.setPosition(UI_WIDTH, 0f)

            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(UI_WIDTH - it.width, 0f)
                    .ease(easeEquation)
            )
        })
        this.foreground.addActor(TopPane().also {
            it.setPosition(0f, UI_HEIGHT + it.height)

            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(0f, UI_HEIGHT - it.height)
                    .ease(easeEquation)
            )
        })
        this.foreground.addActor(BottomPane().also {
            it.setPosition(0f, -2f * it.height)

            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(0f, 0f)
                    .ease(easeEquation)
            )
        })
        this.foreground.addActor(CenterDot().also {
            it.setPosition(UI_WIDTH / 2f - it.width / 2f, -1158f)

            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(UI_WIDTH / 2f - it.width / 2f, UI_HEIGHT / 2f - it.height / 2.5f + 2f)
                    .ease(easeEquation)
            )
        })

        tweens.fold(Timeline.createParallel()) { timeline, tween -> timeline.push(tween) }
            .setCallbackTriggers(COMPLETE)
            .setCallback { _, _ -> this.loadingAnimationDone = true }
            .start(this.tweenManager)
    }

    fun addLoadingOverlayEnd() {
        this.isLoading = true
        this.openingElapsed = 0f

        val tweens = mutableListOf<Tween>()

        this.foreground.addActor(LeftPane().also {
            it.setPosition(0f, 0f)

            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(-it.width, 0f)
                    .ease(easeEquation)
                    .delay(LOADING_END_DELAY)
            )
        })
        this.foreground.addActor(RightPane().also {
            it.setPosition(UI_WIDTH - it.width, 0f)

            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(UI_WIDTH, 0f)
                    .ease(easeEquation)
                    .delay(LOADING_END_DELAY)
            )
        })
        this.foreground.addActor(TopPane().also {
            it.setPosition(0f, UI_HEIGHT - it.height)

            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(0f, UI_HEIGHT + it.height)
                    .ease(easeEquation)
                    .delay(LOADING_END_DELAY)
            )
        })
        this.foreground.addActor(BottomPane().also {
            it.setPosition(0f, 0f)

            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(0f, -2f * it.height)
                    .ease(easeEquation)
                    .delay(LOADING_END_DELAY)
            )
        })
        this.foreground.addActor(CenterDot().also {
            it.setPosition(UI_WIDTH / 2f - it.width / 2f, UI_HEIGHT / 2f - it.height / 2.5f + 2f)

            tweens.add(Tween.to(it, SCALE, 0.25f)
                .target(0.75f, 0.75f)
                .ease(easeEquation)
                )
            tweens.add(
                Tween.to(it, POSITION, LOADING_DURATION)
                    .target(UI_WIDTH / 2f - it.width / 2f, -1158f)
                    .ease(easeEquation)
                    .delay(LOADING_END_DELAY)
            )
        })

        tweens.fold(Timeline.createParallel()) { timeline, tween -> timeline.push(tween) }
            .setCallbackTriggers(COMPLETE)
            .setCallback { _, _ -> Unit }
            .start(this.tweenManager)
    }

    private fun failLoading(message: String) {
        readyToSwitch = null; nextScreen = null; openingElapsed = null
        tweenManager.killAll(); isLoading = false
        background.touchable = Touchable.enabled
        removeLoadingOverlayEnd()
        fr.mesabloo.heavymachdefense.ui.showGameDialog(ui, message)
    }

    fun removeLoadingOverlayEnd() {
        Gdx.app.debug(this.javaClass.simpleName, "Removing loading screen actors")

        this.foreground.children
            .filter { it is BottomPane || it is CenterDot || it is LeftPane || it is RightPane || it is TopPane }
            .forEach { it.remove() }

        Gdx.input.inputProcessor = this.mux
    }
}