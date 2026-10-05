# Playing Heavy MACH: Defense

## Start a game

On macOS, run `./play-macos.command` from the repository. Choose **Start**, create or
select a save, select an unlocked mission, then press **OK**. Existing saves work.

## Win a mission

Your base is at the bottom of the battlefield; the enemy base is at the top.
Destroy the enemy base before your own base reaches zero health.

- Click a machine on the right to spend cells and add it to the build queue on the left.
- Completed machines deploy above your base and advance automatically. They stop
  to shoot enemies within range, then continue toward the enemy base.
- Enemy units arrive in waves. Both bases have defensive cannons. Health bars,
  projectiles, and explosions show the battle's progress. Weapons recoil and flash
  when firing; hits produce sparks, flashes, and explosions. Damaged bases shake,
  smoke, and burn.
- Scroll or drag the battlefield to follow the action. The scope on the left
  shows the part of the battlefield currently visible.
- Cells refill during battle. Enemy kills earn CR; victories give an additional
  reward, unlock the next mission, and grant a bomb charge.
- When a base is destroyed, the camera shows its explosion before the result screen.
  The result screen offers **Next mission**, **Retry mission**, and **Mission select**.
  Campaign progress and CR are saved on either victory or defeat. Mission 80 is
  the final mission; completed missions can be replayed for smaller rewards.

For mission 1, build rifles regularly as cells become available. Keep reinforcing
your army while it advances.

## Upgrades and equipment

**Base Upgrade** pauses the battle and opens the existing upgrade panel:

| Upgrade | Effect |
| --- | --- |
| Base cannon | More defensive cannon damage |
| Base defense | More base health, including the extra health immediately |
| Build time | Faster construction |
| CR research | More credits from kills |
| Cell research | Faster cell production |
| Cell storage | Larger cell capacity and a larger starting reserve next mission |

The cell upgrade near the top right spends cells to double storage for the current
mission only.

The wrench/gear button beneath the build sidebar opens the **Workshop**. It pauses
the battle while you upgrade, equip, or unequip machines and turrets. There are six
equipment slots; if they are full, choose which slot to replace. Each type keeps
its purchased level even when unequipped. Already queued units keep their original
level and construction time.

| Machine | Available when entering | Role |
| --- | --- | --- |
| Rifle | Mission 1 | Affordable general infantry |
| Missile | Mission 3 | Long-range splash damage |
| HMG | Mission 5 | Rapid fire |
| Shotgun | Mission 7 | Fast, durable close-range attacker |
| Plasma | Mission 9 | Medium-range splash damage |
| Ion | Mission 11 | Strong direct fire |
| Heavy missile | Mission 13 | Slow, long-range splash damage |
| Tanker | Mission 15 | High-health front-line unit |

Machines have ten upgrade levels. Rifle, missile, vulcan, plasma, ion, and laser
turrets unlock every two missions beginning with mission 3. Turrets have five
upgrade levels, deploy near your base, and remain stationary while firing at
nearby enemies.

## Special attacks

Select **Special Attack** at the bottom, then use the wrench/gear button to buy
charges with CR. Select a charged icon and click a point on the battlefield to
fire. **Esc** cancels targeting without spending a charge.

Bombs, missiles, nuclear strikes, and crossfire deal area damage; EMP also stuns
enemies. Strikes near the enemy base deal reduced damage to it. Specials do not
damage your own units. The counters show the remaining charges.

## Pause, sound, and saves

**Menu** pauses the simulation, construction, and cell production. It includes
help, separate music/effects volume sliders, **Resume Game**, and
**Save & Mission Select**. Music loops during missions; firing and destruction
have sound effects. Minimizing or pausing the application also pauses gameplay.

Saving keeps credits, upgrades, equipment, charges, and completed missions. An
unfinished battlefield is not stored; entering that mission again starts it over.

## About this remake

The automatic advance / engagement / base-destruction loop follows the original
gameplay shown in the [publisher's Heavy Mach: Defense trailer](https://www.youtube.com/watch?v=ShGz79Nkf1E).
Combat statistics, enemy waves, unlocks, and rewards are newly implemented balance
for this desktop remake. They are not a claim to reproduce the original iOS
game's exact balance, scripted enemy placements, or animations.

The simulation uses a fixed 60 Hz step. Construction, economy, attacks, and enemy
waves pause together. Mission loading has progress and an error/timeout path,
and shared graphics remain available across retries and menu transitions.
