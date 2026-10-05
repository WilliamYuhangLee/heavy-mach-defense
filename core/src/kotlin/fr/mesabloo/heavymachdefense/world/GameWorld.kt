package fr.mesabloo.heavymachdefense.world

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.physics.box2d.*
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Disposable
import fr.mesabloo.heavymachdefense.BG_BORDER
import fr.mesabloo.heavymachdefense.PPM
import fr.mesabloo.heavymachdefense.data.MachineKind
import fr.mesabloo.heavymachdefense.ui.stage.Machine
import com.badlogic.gdx.scenes.scene2d.Touchable
import ktx.box2d.body
import ktx.box2d.box
import fr.mesabloo.heavymachdefense.ifDebug
import fr.mesabloo.heavymachdefense.ui.stage.Terrain
import ktx.actors.contains
import fr.mesabloo.heavymachdefense.timers.cellMiningTimer
import kotlin.math.min
import ktx.box2d.createWorld

class GameWorld(private val terrain: Terrain) : Disposable {
    val world: World = createWorld()

    private val debugRenderer = Box2DDebugRenderer()

    private val bodies = Array<Body>(50)

    companion object {
        // Provisional shared speed until per-machine movement stats are available.
        const val MACHINE_SPEED_PIXELS_PER_SECOND = 64f
    }

    init {
        this.world.setContactListener(object : ContactListener {
            override fun beginContact(p0: Contact) {
                val bodyA = p0.fixtureA
                val bodyB = p0.fixtureB

                if ((bodyA.userData == BG_BORDER && !bodyB.isSensor) || (bodyB.userData == BG_BORDER && !bodyA.isSensor)) {
                    Gdx.app.debug(this.javaClass.simpleName, "Contact with border detected")
                }
            }

            override fun endContact(p0: Contact?) {

            }

            override fun preSolve(p0: Contact?, p1: Manifold?) {

            }

            override fun postSolve(p0: Contact?, p1: ContactImpulse?) {

            }
        })
    }

    /** Put a completed machine above the allied base, with separate deployment positions. */
    fun spawnMachine(kind: MachineKind, level: Int): Body {
        val machine = Machine(kind, level)
        machine.setOrigin(machine.width / 2f, machine.height / 2f)
        machine.rotation = 90f
        machine.touchable = Touchable.disabled

        world.getBodies(bodies)
        val deployed = bodies.count { it.userData is Machine }
        // Five lanes keep the initial deployments inside the visible battlefield.
        val centerX = 128f + (deployed % 5) * 64f
        val centerY = 208f + (deployed / 5) * 64f
        val body = world.body {
            type = BodyDef.BodyType.KinematicBody
            position.set(centerX / PPM, centerY / PPM)
            box(width = machine.width / PPM, height = machine.height / PPM) {
                isSensor = true
            }
            userData = machine
        }
        machine.setPosition(centerX - machine.width / 2f, centerY - machine.height / 2f)
        terrain.addActor(machine)
        return body
    }

    fun render(deltaTime: Float) {
        // Use elapsed time rather than one physics step per rendered frame.
        // Clamp long stalls and subdivide to keep Box2D steps bounded.
        if (!cellMiningTimer.isPaused && !cellMiningTimer.isStopped) {
            var remaining = deltaTime.coerceIn(0f, 0.25f)
            while (remaining > 0f) {
                val step = min(remaining, 1f / 60f)
                world.getBodies(bodies)
                val destinationY = (terrain.height - 192f) / PPM
                bodies.forEach { body ->
                    if (body.userData is Machine) {
                        val distance = (destinationY - body.position.y).coerceAtLeast(0f)
                        body.setLinearVelocity(0f, min(MACHINE_SPEED_PIXELS_PER_SECOND / PPM, distance / step))
                    }
                }
                world.step(step, 6, 2)
                remaining -= step
            }
        }

        bodies.clear()
        this.world.getBodies(bodies)

        bodies.forEach { body ->
            val pos = body.position
            (body.userData as? Actor)?.also {
                it.setPosition(pos.x * PPM - it.width / 2f, pos.y * PPM - it.height / 2f)

                if (!this.terrain.contains(it)) {
                    this.terrain.addActor(it)
                }
            }
        }

        ifDebug {
            this.debugRenderer.render(this.world, this.terrain.stage.camera.combined.cpy().translate(128f, 180f, 0f))
        }
    }

    override fun dispose() {
        this.world.dispose()
    }
}