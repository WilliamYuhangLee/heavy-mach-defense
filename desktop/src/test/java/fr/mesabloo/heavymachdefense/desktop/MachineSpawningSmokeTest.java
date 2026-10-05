package fr.mesabloo.heavymachdefense.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.audio.Music;
import fr.mesabloo.heavymachdefense.managers.SoundManagerKt;
import fr.mesabloo.heavymachdefense.managers.BackgroundMusicManager;
import fr.mesabloo.heavymachdefense.screens.StartScreen;
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
    Music playbackCheck;
    float playbackElapsed;
    static Slider firstSlider(Group group) {
        for (Actor actor : group.getChildren()) {
            if (actor instanceof Slider) return (Slider)actor;
            if (actor instanceof Group) {
                Slider slider = firstSlider((Group)actor);
                if (slider != null) return slider;
            }
        }
        return null;
    }
    @Override public void render() {
        if (playbackCheck == null) return;
        playbackElapsed += Gdx.graphics.getDeltaTime();
        if (playbackElapsed >= 0.5f) {
            check(playbackCheck.isPlaying() && playbackCheck.getPosition() > 0f,
                "Audio stream must be playing and advance through the track");
            System.out.println("PASS: music stream playback and return-to-title reuse.");
            Gdx.app.exit();
        }
    }
    @Override public void create() {
        try {
            MainGame game = new MainGame(); game.create();
            StageAssetsManagerKt.getStageAssetsManager().preload(1);
            ButtonAssetsManagerKt.getButtonAssetsManager().preload();
            ManagerKt.getAssetManager().finishLoading();
            while (!SoundManagerKt.getBackgroundMusicManager().isFullyLoaded())
                SoundManagerKt.getBackgroundMusicManager().update();
            GameSave save = Json.Default.decodeFromString(GameSave.Companion.serializer(),
                "{\"creationDate\":0,\"lastAccessedDate\":0}");
            StageScreen screen = new StageScreen(game, 1, save, -1, false);
            screen.show();
            Music music = SoundManagerKt.getBackgroundMusicManager().load(BackgroundMusicManager.GAMEPLAY);
            check(music.isPlaying() && music.isLooping(), "Entering a mission must start looping gameplay music");
            Slider musicSlider = firstSlider((Group)field(screen, "systemMenu"));
            check(musicSlider != null, "Mission must have a music volume slider");
            musicSlider.setValue(0.2f);
            check(Math.abs(music.getVolume() - 0.2f) < 0.001f, "Slider must control actual music volume");
            musicSlider.setValue(0f);
            check(music.getVolume() == 0f, "Slider must mute music");
            musicSlider.setValue(0.6f);
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
            Body moving = null;
            for (Body b : bodies) if (b.getUserData() instanceof Machine) { moving = b; break; }
            float originX = moving.getPosition().x, originY = moving.getPosition().y;
            float displacement = 0f;
            for (int fps : new int[]{30, 60, 120}) {
                moving.setTransform(originX, originY, 0f);
                for (int frame = 0; frame < fps; frame++) world.render(1f / fps);
                float distance = moving.getPosition().y - originY;
                check(distance > 0f, "Unit must advance toward enemy base");
                if (displacement > 0f) check(Math.abs(distance - displacement) < 0.001f,
                    "Movement must be consistent at 30/60/120 FPS");
                displacement = distance;
            }
            TimersKt.getCellMiningTimer().pause();
            float pausedY = moving.getPosition().y;
            world.render(0.1f);
            check(moving.getPosition().y == pausedY, "Paused mission must freeze movement");
            TimersKt.getCellMiningTimer().resume();
            screen.pause();
            check(!music.isPlaying(), "Application pause must pause music");
            world.render(0.1f);
            check(moving.getPosition().y == pausedY, "Stopped mission must freeze movement");
            screen.resume();
            check(music.isPlaying(), "Application resume must resume music");
            world.render(0.1f);
            check(moving.getPosition().y > pausedY, "Movement must resume");
            for (int frame = 0; frame < 3600; frame++) world.render(1f / 60f);
            float arrivalY = moving.getPosition().y;
            world.render(0.1f);
            check(Math.abs(moving.getPosition().y - arrivalY) < 0.001f && arrivalY > originY,
                "Units must stop before enemy base instead of leaving battlefield");
            screen.render(1f / 60f);
            screen.hide();
            check(!music.isPlaying(), "Leaving mission must stop gameplay track");
            StartScreen title = new StartScreen(game, false);
            title.show();
            title.dispose();
            StartAssetsManagerKt.getStartAssetsManager().preload();
            ManagerKt.getAssetManager().finishLoading();
            new StartScreen(game, false).show();
            playbackCheck = SoundManagerKt.getBackgroundMusicManager().load(BackgroundMusicManager.INTRO);
            System.out.println("PASS: unit advancement, 30/60/120 FPS, pause/resume, destination stop, gameplay music, live volume/mute, music lifecycle.");
            System.out.println("PASS: first-mission spawn, queue artwork, exactly-once completion, pause/stop, six simultaneous completions, deployment positions, shared textures, render.");
            TimersKt.getCellMiningTimer().clear();
        } catch (Throwable t) { t.printStackTrace(); System.exit(1); }
    }
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(768, 1024);
        config.setTitle("HM Defense spawn verification");
        new Lwjgl3Application(new MachineSpawningSmokeTest(), config);
    }
}
