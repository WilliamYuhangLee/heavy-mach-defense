*Heavy MACH: Defense* is a game where you need to create an army of machines in order to defend your castle from the enemy castle, in a top-down side-scroller style.

## Why? 

I was completely stunned when I recently learned that one of my favourite childhood game, 
[Heavy MACH: Defense](https://www.formidapps.com/ios/app.heavy-mach-defense-jBEDCiEx.aspx), had gone missing from the AppStore few years ago.

This decision was most likely taken because of an introduced [“bug” in recent iOS versions](https://discussions.apple.com/thread/5371577) 
(newer than iOS 7) which made the game unplayable because of the impossibility to create a new save file.
The game seems to have unfortunately been abandonned by the company who developed it, [We Made Entertainment](https://wemade.com), leading to
no hope of a new upgrade to fix this.

Many people seem to miss this game as much as I do, and I would like to try recreating it from scratch for Desktop & Android environments 
(I will not be targetting iOS directly, even though minor modifications to the codebase should make it work there).

## Installation

### Desktop

From the root of the project, run the command `./gradlew :desktop:dist` (or `gradlew.bat :desktop:dist` on Windows).
This will generate the JAR file `desktop/build/libs/desktop-VER.jar` (where `VER` is the version number).

On Windows, if Java has been installed globally, you should be able to launch it with a simple double-click in the explorer.
If not, resort to using the command-line (CMD, Powershell, or others) and type the command `java -jar path/to/desktop-VER.jar`.
On Linux and MacOS, the above command should work, provided that `java` is installed on your computer.

#### macOS

Install Java 11 (`brew install openjdk@11`), then run `./play-macos.command`
from the repository or double-click it in Finder. The launcher selects Java 11,
builds with the bundled Gradle wrapper, and starts the game with macOS's required
`-XstartOnFirstThread` option. A separately installed Gradle is not needed.

Save prompts are rendered inside the game to avoid the Swing/GLFW startup hang
on macOS.

### Android

> TODO (not yet provided)

### iOS

> TODO (not yet provided)

## Playable desktop campaign

The desktop version now supports the complete mission loop: build machines,
advance toward the enemy, engage units, destroy the enemy base, and unlock the
next mission. It includes enemy waves, defensive cannons, unit health and
projectiles, victory/defeat dialogs, saved rewards, retry/next-mission controls,
machine/turret upgrades and equipment, special attacks, and music/sound effects.
All 80 maps are available through campaign progression.

Combat feedback includes barrel recoil, muzzle flashes, outlined tracers, missile
trails, energy bolts, hit flashes, debris, and animated explosions using the
included sprite assets. Damaged bases shake, smoke, and burn; their destruction
plays before the result dialog. Effects stay visible when scrolling the battlefield.

Mission entry uses a progress screen with timeout/error handling. Shared assets
remain loaded across mission changes, and a new mission resets pause state.

Combat balance and enemy waves are new to this remake; the exact original iOS
balance, scripted enemy placements, and animation timing are not reproduced. Desktop
is the supported playable target; mobile packaging remains unfinished.

## Verification

With Java 11 selected in `JAVA_HOME`, run:

```sh
./gradlew :desktop:verifyBattleSimulation :desktop:verifyMachineSpawning :desktop:verifyCombatEffects :desktop:dist
```

The deterministic simulation test covers targeting, movement at 30/60/120 FPS,
mutual combat, damage/deaths, kill rewards, turrets, EMP, pause, victory/defeat,
progression, replay rewards, combat events, unlocked-equipment save round trips,
and later-stage strategies using the shipping build costs and times.

The graphics test requires a desktop session. It uses a hidden window and an
isolated temporary preferences directory; player saves are not read or modified.
It follows the fresh-launch menu path, clicks real build controls, wins a mission,
checks saved rewards, enters the next mission, buys/uses a special, loses, retries,
completes mission 2 with its missile unlock and enters mission 3, returns through
the menus, verifies sound playback/volume, and loads all 80 maps
and every available machine/turret sprite. Optional framebuffer captures can be
written with `-PsmokeCaptureDir=/absolute/output/directory`.

The combat-effects test also uses an isolated hidden window. It checks recoil,
hit/destruction effects, pause, base reactions, sustained firing after 30 seconds,
and visible tracer pixels after scrolling. Use
`-PeffectsCaptureDir=/absolute/output/directory` to capture PNG frames and key scenes.

## Gameplay

Described [here](docs/Gameplay.md).

## Screenshots of the original game

(Click to enlarge)

<img alt="screenshot1" height=200 src="https://lioxliu.files.wordpress.com/2012/09/20120904-182543.jpg"/>    <img alt="screenshot2" height=200 src="https://www.simonvideo.com/wp-content/uploads/2011/05/mzl.skpyknie.320x480-75.jpg"/>    <img alt="screenshot3" height=200 src="http://a2.mzstatic.com/us/r1000/107/Purple/01/eb/18/mzl.wqfmuwaf.jpg"/>

## Additional notes

- This repository holds a complete remake from scratch of the game.
  However, while the original studio has been notified about it, this may be effectively illegal.
  In such case, this repository will be either deleted or completely emptied.
- No money will be accepted from this work.
  This is all done on my free time and I do *not* want to earn money from this.
  This repository is (and will always be) free for everybody to use.

## Credits & license

Credits go to the original authors of this game, We Made Entertainment.

All the code is licensed under the BSD 3-clause license.
Copyright © 2021 Ghilain Bergeron (Mesabloo).
