package fr.mesabloo.heavymachdefense.desktop;

import com.badlogic.gdx.*;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import fr.mesabloo.heavymachdefense.MainGame;
import fr.mesabloo.heavymachdefense.data.*;
import fr.mesabloo.heavymachdefense.gameplay.*;
import fr.mesabloo.heavymachdefense.listeners.start.StartOnClick;
import fr.mesabloo.heavymachdefense.listeners.saves.SelectOrLoadSave;
import fr.mesabloo.heavymachdefense.listeners.stage_selection.LoadStage;
import fr.mesabloo.heavymachdefense.managers.*;
import fr.mesabloo.heavymachdefense.managers.assets.*;
import fr.mesabloo.heavymachdefense.screens.*;
import fr.mesabloo.heavymachdefense.timers.TimersKt;
import fr.mesabloo.heavymachdefense.ui.stage.*;
import fr.mesabloo.heavymachdefense.ui.stage.buttons.*;
import fr.mesabloo.heavymachdefense.ui.stage.slots.*;
import kotlinx.serialization.json.Json;
import java.lang.reflect.Field;
import java.nio.file.Files;

/** Real OpenGL/audio/menu integration. Player preferences are isolated in a temporary directory. */
public class MachineSpawningSmokeTest extends ApplicationAdapter {
    private MainGame game;
    private GameSave save;
    private int phase;
    private long phaseStarted;
    private StageScreen previousMission;
    private Music music;
    private boolean combatCaptured;
    private int campaignMap = 3;

    static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static <T extends Actor> T find(Actor root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Group) for (Actor child : ((Group) root).getChildren()) {
            T found = find(child, type);
            if (found != null) return found;
        }
        return null;
    }

    static TextButton button(Actor root, String prefix) {
        if (root instanceof TextButton && ((TextButton) root).getText().toString().startsWith(prefix))
            return (TextButton) root;
        if (root instanceof Group) for (Actor child : ((Group) root).getChildren()) {
            TextButton found = button(child, prefix);
            if (found != null) return found;
        }
        return null;
    }

    static boolean hasTodo(Actor root) {
        if (root instanceof Label && ((Label) root).getText().toString().contains("TODO")) return true;
        if (root instanceof Group) for (Actor child : ((Group) root).getChildren())
            if (hasTodo(child)) return true;
        return false;
    }

    // Use the same input pipeline as a mouse, including hit testing and modal/input blockers.
    static void click(Actor actor) {
        check(actor != null && actor.getStage() != null, "Expected an attached, clickable control");
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout)
            ((com.badlogic.gdx.scenes.scene2d.utils.Layout) actor).validate();
        Stage stage = actor.getStage();
        Vector2 point = actor.localToStageCoordinates(new Vector2(actor.getWidth() / 2f, actor.getHeight() / 2f));
        Actor hit = stage.hit(point.x, point.y, true);
        check(hit != null && (hit == actor || hit.isDescendantOf(actor)),
            "Control blocked at " + point + ": " + actor + "; hit=" + hit);
        stage.stageToScreenCoordinates(point);
        check(Gdx.input.getInputProcessor() != null, "Input must be enabled");
        Gdx.input.getInputProcessor().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        Gdx.input.getInputProcessor().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
    }

    void next(int nextPhase, String message) {
        phase = nextPhase;
        phaseStarted = System.nanoTime();
        System.out.println(message);
    }

    void capture(String name) {
        String dir = System.getProperty("hm.smoke.capture");
        if (dir == null) return;
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0,
            Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        PixmapIO.PNG writer = new PixmapIO.PNG();
        try {
            writer.setFlipY(true);
            writer.write(Gdx.files.absolute(dir + "/" + name + ".png"), pixmap);
        } catch (Exception e) { throw new RuntimeException(e); }
        finally { writer.dispose(); pixmap.dispose(); }
    }

    @Override public void create() {
        game = new MainGame();
        game.create();
        phaseStarted = System.nanoTime();
    }

    @Override public void resize(int w, int h) { if (game != null) game.resize(w, h); }

    @Override public void render() {
        try {
            game.render();
            Screen current = game.getAccess$currentScreen();
            if (!(current instanceof AbstractScreen)) return;
            AbstractScreen screen = (AbstractScreen) current;
            if ((System.nanoTime() - phaseStarted) / 1e9 > 20)
                throw new AssertionError("Timed out in phase " + phase + " on " + screen.getClass().getSimpleName());
            if (screen.isLoading() || screen instanceof MissionLoadingScreen) return;

            switch (phase) {
                case 0:
                    if (!(screen instanceof StartScreen)) return;
                    new StartOnClick((StartScreen) screen).clicked(null, 0, 0);
                    next(1, "Title -> save selection");
                    break;
                case 1:
                    if (!(screen instanceof SavesSelectionScreen)) return;
                    save = Json.Default.decodeFromString(GameSave.Companion.serializer(),
                        "{\"creationDate\":0,\"lastAccessedDate\":0}");
                    SavesSelectionScreen saves = (SavesSelectionScreen) screen;
                    saves.addSave(save);
                    new SelectOrLoadSave(saves, 0, saves.getBackground(), save).clicked(null, 0, 0);
                    next(2, "Save selection -> mission selection");
                    break;
                case 2:
                    if (!(screen instanceof StageSelectionScreen)) return;
                    new LoadStage((StageSelectionScreen) screen).clicked(null, 0, 0);
                    new LoadStage((StageSelectionScreen) screen).clicked(null, 0, 0);
                    next(3, "Mission selection -> first mission (also guarding duplicate clicks)");
                    break;
                case 3: {
                    if (!(screen instanceof StageScreen)) return;
                    StageScreen mission = (StageScreen) screen;
                    music = SoundManagerKt.getBackgroundMusicManager().load(BackgroundMusicManager.GAMEPLAY);
                    check(music.isPlaying() && music.isLooping(), "Mission must start looping music");
                    Slider volume = find((Actor) field(mission, "systemMenu"), Slider.class);
                    volume.setValue(0.2f);
                    check(Math.abs(music.getVolume() - 0.2f) < 0.001f, "Live music volume");
                    volume.setValue(0f);
                    check(music.getVolume() == 0f, "Mute music");
                    volume.setValue(0.6f);
                    boolean flipped = StageAssetsManagerKt.getStageAssetsManager()
                        .unsafeRegion(StageAssetsManager.MACHINE_WEAPONS, "rifle-01").isFlipY();
                    for (MachineKind kind : MachineKind.values()) for (int level = 1; level <= 10; level++)
                        new Machine(kind, level);
                    for (TurretKind kind : TurretKind.values()) for (int level = 1; level <= 5; level++)
                        new Turret(kind, level);
                    check(flipped == StageAssetsManagerKt.getStageAssetsManager()
                        .unsafeRegion(StageAssetsManager.MACHINE_WEAPONS, "rifle-01").isFlipY(), "Shared sprites must not flip");
                    BuildQueue queue = (BuildQueue) field(mission, "buildQueue");
                    MachineBuildSlot rifle = find(screen.getBackground(), MachineBuildSlot.class);
                    long cells = (Long) field(mission, "currentCells");
                    click(rifle);
                    check(queue.getNumberOf(MachineKind.RIFLE) == 1, "Actual rifle click must enqueue");
                    check((Long) field(mission, "currentCells") == cells - 60, "Exactly one build charge");
                    check(!hasTodo(queue), "Queue must contain artwork");
                    mission.render(0.2f); mission.render(0.2f); mission.render(0.2f); capture("mission-build");
                    TimersKt.getCellMiningTimer().pause();
                    float pausedTime = mission.getGameWorld().getBattle().getElapsed();
                    long pausedCells = (Long) field(mission, "currentCells");
                    for (int i = 0; i < 20; i++) mission.render(0.25f);
                    check(queue.getNumberOf(MachineKind.RIFLE) == 1 && mission.getGameWorld().getBattle().getUnits().isEmpty(), "Paused queue must not deploy");
                    check(mission.getGameWorld().getBattle().getElapsed() == pausedTime
                        && (Long) field(mission, "currentCells") == pausedCells, "Pause must freeze battle and economy");
                    TimersKt.getCellMiningTimer().resume();
                    mission.pause();
                    check(!music.isPlaying(), "Application pause must pause music");
                    mission.render(0.25f);
                    check(mission.getGameWorld().getBattle().getElapsed() == pausedTime, "Application pause freezes battle");
                    mission.resume();
                    for (int i = 0; i < 34; i++) mission.render(0.1f);
                    check(queue.getNumberOf(MachineKind.RIFLE) == 0
                        && mission.getGameWorld().getBattle().getUnits().size() == 1, "One build deploys exactly once");
                    check(mission.getGameWorld().getBattle().getUnits().get(0).getY() > 208f, "Built rifle must advance");
                    next(4, "PASS: fresh launch, real build input, all 80 machine/30 turret sprites, deployment, pause/economy, audio controls");
                    break;
                }
                case 4: {
                    StageScreen mission = (StageScreen) screen;
                    Battle battle = mission.getGameWorld().getBattle();
                    BuildQueue queue = (BuildQueue) field(mission, "buildQueue");
                    for (int i = 0; i < 20 && battle.getResult() == BattleResult.RUNNING; i++) {
                        if (queue.getNumberOf(MachineKind.RIFLE) == 0 && (Long) field(mission, "currentCells") >= 60)
                            click(find(screen.getBackground(), MachineBuildSlot.class));
                        mission.render(0.1f);
                    }
                    if (!combatCaptured && battle.getElapsed() >= 20f) {
                        ScrollPane terrain = find(screen.getBackground(), ScrollPane.class);
                        terrain.setScrollY(600f); terrain.updateVisualScroll();
                        mission.render(0f); capture("mission-combat"); combatCaptured = true;
                    }
                    check(battle.getElapsed() < 180f, "Rifle strategy should finish mission 1");
                    if (battle.getResult() == BattleResult.RUNNING) return;
                    check(battle.getResult() == BattleResult.VICTORY && battle.getDefeatedEnemies() > 0, "Units must fight and destroy enemy base");
                    check(music.isPlaying() && music.getPosition() > 0f, "Actual music stream must advance");
                    GameSave persisted = Json.Default.decodeFromString(GameSave.Companion.serializer(),
                        Gdx.app.getPreferences(GameSave.PREFERENCES_PATH).getString("0"));
                    check(persisted.getLastStageCompleted() == 1 && persisted.getCredits() > 0, "Victory must persist progression/rewards");
                    mission.render(0f); capture("mission-victory");
                    previousMission = mission;
                    click(button(screen.getUi().getRoot(), "Next mission"));
                    next(5, "PASS: live mission combat, enemy kills, victory, persisted rewards, Next mission input");
                    break;
                }
                case 5: {
                    if (!(screen instanceof StageScreen) || screen == previousMission) return;
                    StageScreen mission = (StageScreen) screen;
                    check(mission.getGameWorld().getBattle().getLevel() == 2, "Next mission must load stage 2");
                    check(!TimersKt.getCellMiningTimer().isPaused(), "New mission must clear prior result pause");
                    click(find(screen.getBackground(), SpecialAttackMenuButton.class));
                    mission.render(0.1f);
                    SpecialBuildSlot special = find(screen.getBackground(), SpecialBuildSlot.class);
                    check(!special.isDisabled(), "Victory special charge must enable its slot");
                    click(special);
                    // Coordinates are local to the actual terrain target listener.
                    Terrain terrain = (Terrain) field(mission, "terrain");
                    for (EventListener listener : terrain.getListeners()) if (listener instanceof ClickListener)
                        ((ClickListener) listener).clicked(null, 256f, 1840f);
                    mission.render(0f);
                    check(save.getSpecialCount().get(SpecialKind.AIRSTRIKE_BOMB) == 0 && special.isDisabled(), "Using special must consume and disable last charge");
                    click(find(screen.getBackground(), UpgradeEquipment.class));
                    next(6, "PASS: next mission, pause reset, special targeting/charge counter");
                    break;
                }
                case 6:
                    check(TimersKt.getCellMiningTimer().isPaused(), "Workshop must pause battle");
                    click(button(screen.getUi().getRoot(), "AIRSTRIKE BOMB"));
                    next(7, "Buying a special charge through the workshop");
                    break;
                case 7:
                    if (button(screen.getUi().getRoot(), "Back to battle") == null) return;
                    check(save.getSpecialCount().get(SpecialKind.AIRSTRIKE_BOMB) == 1, "Workshop purchase must add a charge");
                    click(button(screen.getUi().getRoot(), "Back to battle"));
                    next(8, "Returning to battle");
                    break;
                case 8: {
                    if (TimersKt.getCellMiningTimer().isPaused()) return;
                    StageScreen mission = (StageScreen) screen;
                    Battle battle = mission.getGameWorld().getBattle();
                    check(!find(screen.getBackground(), SpecialBuildSlot.class).isDisabled(), "Purchased charge must refresh the slot");
                    for (int i = 0; i < 30 && battle.getResult() == BattleResult.RUNNING; i++) mission.render(0.25f);
                    if (battle.getResult() == BattleResult.RUNNING) return;
                    check(battle.getResult() == BattleResult.DEFEAT, "Idle player must lose");
                    check(save.getLastStageCompleted() == 1, "Defeat must not unlock a stage");
                    previousMission = mission;
                    click(button(screen.getUi().getRoot(), "Retry mission"));
                    next(9, "PASS: workshop purchase, refreshed special button, defeat, Retry input");
                    break;
                }
                case 9: {
                    if (!(screen instanceof StageScreen) || screen == previousMission) return;
                    StageScreen mission = (StageScreen) screen;
                    check(mission.getGameWorld().getBattle().getLevel() == 2 && !TimersKt.getCellMiningTimer().isPaused(), "Retry must create a running battle");
                    click(find(screen.getBackground(), MenuButton.class));
                    click(button(screen.getUi().getRoot(), "Save & Mission Select"));
                    next(10, "Retry -> menu -> mission select");
                    break;
                }
                case 10:
                    if (!(screen instanceof StageSelectionScreen)) return;
                    check(!music.isPlaying(), "Leaving battle must stop its track");
                    // Check every campaign terrain through the real loading/screen lifecycle.
                    ((StageSelectionScreen) screen).getSave().setLastStageCompleted(79);
                    ((StageSelectionScreen) screen).beginMission(campaignMap - 1);
                    next(11, "PASS: retry, menu exit, music lifecycle. Checking campaign maps.");
                    break;
                case 11: {
                    if (!(screen instanceof StageScreen) || screen == previousMission) return;
                    StageScreen mission = (StageScreen) screen;
                    check(mission.getGameWorld().getBattle().getLevel() == campaignMap, "Campaign map mismatch");
                    if (campaignMap == 3) {
                        for (MachineKind kind : MachineKind.values())
                            mission.getGameWorld().getBattle().spawn(kind, 7, Team.ENEMY, null, 200f, 1800f);
                        mission.render(0.1f);
                    }
                    previousMission = mission;
                    if (++campaignMap <= 80) {
                        mission.openMission(campaignMap);
                        phaseStarted = System.nanoTime();
                    } else {
                        System.out.println("PASS: all 80 mission assets and repeated loading/disposal; graphics integration complete.");
                        mission.saveAndExit();
                        next(12, "Checking return to title");
                    }
                    break;
                }
                case 12:
                    if (!(screen instanceof StageSelectionScreen)) return;
                    click(find(screen.getBackground(), fr.mesabloo.heavymachdefense.ui.common.BackButton.class));
                    next(13, "Mission select -> saves");
                    break;
                case 13:
                    if (!(screen instanceof SavesSelectionScreen)) return;
                    click(find(screen.getBackground(), fr.mesabloo.heavymachdefense.ui.common.BackButton.class));
                    next(14, "Saves -> title");
                    break;
                case 14:
                    if (!(screen instanceof StartScreen)) return;
                    check(SoundManagerKt.getBackgroundMusicManager().load(BackgroundMusicManager.INTRO).isPlaying(),
                        "Return to title must resume intro music");
                    System.out.println("PASS: full menu return and shared asset/audio lifetime.");
                    Gdx.app.exit();
                    break;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    @Override public void dispose() { if (game != null) game.dispose(); }

    public static void main(String[] args) throws Exception {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowPosition(0, 0); // Hidden tests do not need a primary monitor for centering.
        config.setWindowedMode(768, 1024);
        config.setForegroundFPS(60);
        config.setTitle("HM Defense gameplay verification");
        config.setPreferencesConfig(Files.createTempDirectory("hm-defense-test-").toString(), com.badlogic.gdx.Files.FileType.Absolute);
        new Lwjgl3Application(new MachineSpawningSmokeTest(), config);
    }
}
