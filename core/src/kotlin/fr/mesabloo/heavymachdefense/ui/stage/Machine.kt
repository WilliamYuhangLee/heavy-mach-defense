package fr.mesabloo.heavymachdefense.ui.stage

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import fr.mesabloo.heavymachdefense.data.MachineKind
import fr.mesabloo.heavymachdefense.data.TurretKind
import fr.mesabloo.heavymachdefense.data.models.MachineModel
import fr.mesabloo.heavymachdefense.managers.assets.StageAssetsManager
import fr.mesabloo.heavymachdefense.managers.assets.stageAssetsManager

interface RecoilingSprite {
    fun setWeaponRecoil(amount: Float)
}

class Machine(kind: MachineKind, level: Int) : Group(), RecoilingSprite {
    private val weapons = mutableListOf<Pair<Image, Float>>()
    override fun setWeaponRecoil(amount: Float) {
        weapons.forEach { (weapon, restX) -> weapon.x = restX - amount }
    }
    init {
        val name = "${kind.machineName}-${level.coerceIn(1,10).toString().padStart(2,'0')}"
        val body = Image(stageAssetsManager.unsafeRegion(StageAssetsManager.MACHINE_BODIES, name))
        width = body.width; height = body.height
        addActor(body)
        // Only rifle-01 has a model JSON upstream. Other sprites use their actual dimensions.
        if (kind != MachineKind.TANKER) {
            val model = if (Gdx.files.internal("data/models/machines/$name.json").exists()) MachineModel(kind.machineName,level) else null
            val region = stageAssetsManager.unsafeRegion(StageAssetsManager.MACHINE_WEAPONS,name)
            val left = Image(TextureRegion(region))
            val right = Image(TextureRegion(region).also { it.flip(false,true) })
            addActor(left); addActor(right)
            left.setPosition(width/2f + (model?.leftWeaponOffset?.first ?: width*0.2f) - left.width/2f,
                height/2f + (model?.leftWeaponOffset?.second ?: height*0.48f) - left.height/2f)
            right.setPosition(width/2f + (model?.rightWeaponOffset?.first ?: width*0.2f) - right.width/2f,
                height/2f + (model?.rightWeaponOffset?.second ?: -height*0.48f) - right.height/2f)
            weapons.add(left to left.x)
            weapons.add(right to right.x)
        }
    }
}

class Turret(kind: TurretKind, level: Int) : Group(), RecoilingSprite {
    private val weapon: Image
    private var restX = 0f
    override fun setWeaponRecoil(amount: Float) { weapon.x = restX - amount }
    init {
        val body = Image(stageAssetsManager.unsafeRegion(StageAssetsManager.ALLY_TURRET_BODIES,
            "body-${level.coerceIn(1,5).toString().padStart(2,'0')}"))
        width = body.width; height = body.height; addActor(body)
        weapon = Image(stageAssetsManager.unsafeRegion(StageAssetsManager.ALLY_TURRET_WEAPONS,
            "${kind.turretName}-${level.coerceIn(1,5).toString().padStart(2,'0')}"))
        weapon.setPosition((width-weapon.width)/2f,(height-weapon.height)/2f); addActor(weapon)
        restX = weapon.x
    }
}


class EnemyMachine(kind: MachineKind) : Group(), RecoilingSprite {
    private val weapon: Image
    private val restX: Float
    init {
        val index = (kind.ordinal + 1).toString().padStart(2, '0')
        val body = Image(stageAssetsManager.unsafeRegion(StageAssetsManager.TANK_BODIES, index))
        setSize(body.width, body.height)
        addActor(body)
        weapon = Image(stageAssetsManager.unsafeRegion(StageAssetsManager.TANK_WEAPONS, index))
        restX = (width - weapon.width) / 2f
        weapon.setPosition(restX, (height - weapon.height) / 2f)
        addActor(weapon)
    }
    override fun setWeaponRecoil(amount: Float) { weapon.x = restX - amount }
}
