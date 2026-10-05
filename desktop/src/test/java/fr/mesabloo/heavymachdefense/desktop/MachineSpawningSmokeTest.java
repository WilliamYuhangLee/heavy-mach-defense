package fr.mesabloo.heavymachdefense.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import fr.mesabloo.heavymachdefense.MainGame;
import fr.mesabloo.heavymachdefense.data.*;
import fr.mesabloo.heavymachdefense.managers.assets.*;
import fr.mesabloo.heavymachdefense.screens.StageScreen;
import fr.mesabloo.heavymachdefense.timers.TimersKt;
import fr.mesabloo.heavymachdefense.ui.stage.*;
import fr.mesabloo.heavymachdefense.world.GameWorld;
import kotlinx.serialization.json.Json;
import java.lang.reflect.Field;
import java.util.HashSet;

public class MachineSpawningSmokeTest extends ApplicationAdapter {
    static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }
    static int machines(GameWorld world) {
        Array<Body> bodies = new Array<>(); world.getWorld().getBodies(bodies);
        int n = 0; for (Body b : bodies) if (b.getUserData() instanceof Machine) n++; return n;
    }
    static boolean hasTodo(Actor a) {
        if (a instanceof Label && ((Label)a).getText().toString().contains("TODO")) return true;
        if (a instanceof Group) for (Actor child : ((Group)a).getChildren()) if (hasTodo(child)) return true;
        return false;
    }
    @Override public void create() {
        try {
            MainGame game = new MainGame(); game.create();
            StageAssetsManagerKt.getStageAssetsManager().preload(1);
            ButtonAssetsManagerKt.getButtonAssetsManager().preload();
            ManagerKt.getAssetManager().finishLoading();
            GameSave save = Json.Default.decodeFromString(GameSave.Companion.serializer(),
                "{\"creationDate\":0,\"lastAccessedDate\":0}");
            StageScreen screen = new StageScreen(game, 1, save, -1, false);
            screen.show();
            BuildQueue queue = (BuildQueue)field(screen, "buildQueue");
            Builds builds = (Builds)field(screen, "builds");
            GameWorld world = (GameWorld)field(screen, "gameWorld");
            Terrain terrain = (Terrain)field(screen, "terrain");
            TextureRegion region = StageAssetsManagerKt.getStageAssetsManager().unsafeRegion(
                StageAssetsManager.MACHINE_WEAPONS, "rifle-01");
            boolean flip = region.isFlipY();
            check(machines(world) == 0, "New mission must start with no deployed machines");
            queue.build(new BuildMachineItem(MachineKind.RIFLE, 1, builds));
            check(!hasTodo(queue), "Queue must show unit artwork, not TODO");
            TimersKt.getCellMiningTimer().pause();
            queue.act(100f);
            check(machines(world) == 0 && queue.getNumberOf(MachineKind.RIFLE) == 1,
                "Paused queue must not complete or deploy");
            TimersKt.getCellMiningTimer().resume();
            queue.act(100f);
            check(machines(world) == 1 && queue.getNumberOf(MachineKind.RIFLE) == 0,
                "One completed rifle must produce exactly one body");
            queue.act(100f);
            check(machines(world) == 1, "Completed item must not deploy again");
            for (int i = 0; i < 6; i++) queue.build(new BuildMachineItem(MachineKind.RIFLE, 1, builds));
            TimersKt.getCellMiningTimer().stop();
            queue.act(100f);
            check(machines(world) == 1, "Stopped queue must not deploy");
            TimersKt.getCellMiningTimer().start();
            queue.act(100f);
            check(machines(world) == 7 && queue.getNumberOf(MachineKind.RIFLE) == 0,
                "Simultaneous completions must not skip or duplicate units");
            check(region.isFlipY() == flip, "Repeated previews/spawns must not mutate shared artwork");
            Array<Body> bodies = new Array<>(); world.getWorld().getBodies(bodies);
            HashSet<String> positions = new HashSet<>();
            for (Body b : bodies) if (b.getUserData() instanceof Machine) {
                Machine m = (Machine)b.getUserData();
                check(m.getParent() == terrain, "Spawn must attach a visible terrain actor");
                check(m.getX() >= 0 && m.getY() >= 144 && m.getX() + m.getWidth() <= 512
                    && m.getY() + m.getHeight() <= 844, "Initial deployments must be visible above allied base");
                check(positions.add(b.getPosition().toString()), "Deployments must have separate positions");
            }
            screen.render(1f / 60f);
            System.out.println("PASS: first-mission spawn, queue artwork, exactly-once completion, pause/stop, six simultaneous completions, deployment positions, shared textures, render.");
            TimersKt.getCellMiningTimer().clear();
            Gdx.app.exit();
        } catch (Throwable t) { t.printStackTrace(); System.exit(1); }
    }
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(768, 1024);
        config.setTitle("HM Defense spawn verification");
        new Lwjgl3Application(new MachineSpawningSmokeTest(), config);
    }
}
