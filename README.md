# BedProtect

A client-side Minecraft mod that keeps beds from blowing you up in the Nether and the End. It works
in singleplayer and on any server: a blocked click never leaves your client.

## What it does

- **Beds denied** (default): clicking a bed that would explode does nothing.
- **Smart protection**: when beds are allowed, every click is checked first. The mod calculates the
  blast the same way the game does: distance, blocks between you and the bed, armor and toughness,
  Protection and Blast Protection, Resistance, absorption, health and difficulty. The bed goes off only
  if it deals at most 5 hearts and at least 2 hearts remain (both adjustable).
- **Falls and hazards**: the knockback and a floor that the blast may destroy are simulated, so a
  deadly fall afterwards is caught too. TNT or end crystals nearby, lava, burning and elytra flight
  block the click as well.
- **Ping protection**: holding right-click never sets off a bed. On servers the mod waits out your
  ping, uses your lowest recent health and asks for a second click to confirm.
- **Live check**: while you look at such a bed, coloured text under the crosshair shows what a click
  would do: damage and health left in hearts, exactly as the health bar shows them.

## Commands

| Command | What it does |
|---|---|
| `/bedprotect` | Settings panel (click a value to change it) |
| `/bedprotect beds allow\|deny` | Allow or deny clicking beds that explode |
| `/bedprotect protection on\|off` | Smart protection |
| `/bedprotect ping on\|off` | Ping protection |
| `/bedprotect confirm on\|off` | Second click to confirm on servers |
| `/bedprotect hud on\|off` | Text under the crosshair |
| `/bedprotect chat on\|off` | Reminder when entering the Nether or the End |
| `/bedprotect check` | Full blast calculation for the bed you look at |
| `/bedprotect maxdamage <hearts>` | Most damage a blast may deal |
| `/bedprotect reserve <hearts>` | Health that must remain |
| `/bedprotect reset` | Default settings |
| `/bedprotect help` | List of commands |

All settings are also on the mod's page in Mod Menu. English and Russian are included.

## Source code

| Minecraft | Fabric |
|---|---|
| 26.3 | [fabric/26.3](fabric/26.3) |

Requires Fabric API. Mod Menu is optional.

To build, run `gradlew build` in [fabric/26.3](fabric/26.3). The jar appears in `build/libs`.

## License

All rights reserved. See [LICENSE](LICENSE).
