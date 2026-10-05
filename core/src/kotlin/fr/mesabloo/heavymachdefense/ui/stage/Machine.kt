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

class Machine(kind: MachineKind, level: Int) : Group() {
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
        }
    }
}

class Turret(kind: TurretKind, level: Int) : Group() {
    init {
        val body = Image(stageAssetsManager.unsafeRegion(StageAssetsManager.ALLY_TURRET_BODIES,
            "body-${level.coerceIn(1,5).toString().padStart(2,'0')}"))
        width = body.width; height = body.height; addActor(body)
        val weapon = Image(stageAssetsManager.unsafeRegion(StageAssetsManager.ALLY_TURRET_WEAPONS,
            "${kind.turretName}-${level.coerceIn(1,5).toString().padStart(2,'0')}"))
        weapon.setPosition((width-weapon.width)/2f,(height-weapon.height)/2f); addActor(weapon)
    }
}
