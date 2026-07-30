# Absolute Cinema Camera

A client-side Fabric mod that turns Minecraft into a film. One key puts the game into cinema
mode: black bars slide in, the HUD goes, the picture is graded and softened, and the camera
either smooths out your own movement or detaches entirely and shoots the scene for you.

Built for roleplay servers — nothing is required on the server side, and other players do not
need the mod.

## What it does

**Letterbox and HUD.** Cinemascope mattes at the top and bottom, sliding in over a configurable
fade. The HUD, the held item and the hand can all be hidden — each separately. The chat stays
visible by default and is lifted clear of the bottom matte, because on a roleplay server half the
scene happens in text; hide it too if you want a completely clean frame. The F3 debug overlay
deliberately stays visible so you can still read the framerate while filming.

**Colour grading.** Twenty looks, applied through a three-way colour corrector (lift / gamma /
gain) plus saturation, contrast, glow, grain, chromatic aberration, exposure flicker, image warp
and double vision. They are ordered by the kind of look they give, so cycling through them with
the key stays predictable:

| Preset | Look |
| --- | --- |
| No grade | Untouched picture, filters still available |
| *Bright* | |
| Golden hour | Amber low sun, soft contrast, light hazing off every surface |
| Noble study | Candlelit gold on the faces, warm shadows, the light slowly breathing |
| Grand ball | Chandeliered hall: lifted gold, airy contrast, glow everywhere |
| By the campfire | Warm firelight, cool deep shadows, gentle grain |
| Deep frost | Pale, cold and flat, light bouncing off snow |
| *Distorted* | |
| Drunk | The room sways, edges split into a second faint copy, lights smear |
| Fever | Sickly yellow-green over bruised shadows, the picture swimming |
| *Colour led* | |
| Romance | Rose shadows, magenta highlights, a glow on everything bright |
| Bloodlust | Everything drowns in red — the shot before someone draws a blade |
| Enchantment | Violet shadows against cyan light, glowing and fringing |
| *Dark* | |
| Overcast morning | Cold, flat, milky lifted shadows |
| Foul weather | Cold grey-blue, hard contrast, everything wet and glinting |
| Moonlight | Blue, dim, desaturated, quiet |
| Conspiracy | Blue-green gloom with one warm candle holding the faces |
| Interrogation | One harsh lamp: bleached colour, brutal contrast, dark corners |
| Bad dream | Sickly green-magenta, crushed, heavy grain and vignette |
| *Stylised stock* | |
| Memory | Soft sepia, lifted, glowing at the edges |
| Old film | Scratched sepia, heavy grain, gate flicker, hard vignette |
| Black and white | Full desaturation with a hint of warmth in the highlights |

Vignette and film grain are separate toggles that apply to every preset, and the whole grade has
a strength slider.

**Combat interrupt.** Taking damage drops the mode instantly, with no fade — so nobody can
ambush you while you are watching a camera orbit. It can be switched off.

**Depth of field.** A real depth-buffer effect, not a screen-space fake: the distance to what
the camera is aimed at is measured every frame and the focus is pulled smoothly towards it. The
foreground is left sharp by default (blurring it ghosts the ground at your feet), and can be
softened separately if you want it. It only applies to the presets a soft background actually
suits — the bright and stylised looks stay sharp across the whole frame even with the filter on.

**Six camera modes.**

- *First person* — you keep control. The camera follows your mouse with a configurable lag and
  the position is smoothed, which kills head bob and step jitter.
- *Dynamic camera* — the camera detaches and shoots the whole scene: you and every player within
  the scene radius (named creatures can be counted in as well, off by default). It composes
  around the group centre at a distance wide enough to hold everyone, and picks from orbits,
  wide frames, tracking moves, dollies, cranes and locked-off frames. It never approaches a
  face, and never swings behind the group — that is reserved for the third mode.
- *Focus on speaker* — the same scene coverage while the room is quiet, but the moment
  [Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat) reports someone talking, the
  camera cuts to their face: a close-up or a push-in from 1.35–2.3 blocks, angled off their own
  gaze so you see the face, not the back of the head. It holds them until they have been silent
  for a couple of seconds, then returns to the wide coverage.
- *Dialogue* — shot-reverse-shot, the way films have covered conversations for a century: hold
  the speaker, cut to the listener for their reaction, and every fourth cut or so pull back into
  a two shot holding both. The camera picks one side of the line between the pair and never
  crosses it, because crossing it makes them swap sides of the screen between cuts and the scene
  stops reading. Cuts here are instant on purpose — gliding between opposite angles would fly
  straight through the people.
- *Tripod* — locked off wherever you planted it, turning only to keep the scene in frame. Put the
  camera down, walk into shot, play the scene. For speeches, trials, performances, and long talks
  where constant orbits get tiring.
- *Side track* — travels alongside the scene at a fixed angle, keeping pace. For walks,
  processions and rides. It swaps sides every twenty seconds or so, and immediately if the current
  side runs out of room.

Shots respect the room: eight rays measure the free space around the scene a few times a second
and shots are aimed at whichever side has room for them; the camera is clipped against walls
along all four corners of its near plane; a shot that stays jammed against geometry is recomposed
from a different angle. In a room too small for travelling shots the camera switches to looking
down from the ceiling, and if a wall ever squeezes it inside the people it is filming, it climbs
out of them.

## Controls

| Key | Action |
| --- | --- |
| `F7` | Toggle cinema mode |
| unbound | Open the settings screen |
| unbound | Next camera mode |
| unbound | Next colour grade |
| unbound | Next scene profile |
| unbound | Plant the tripod here |

All of them are rebindable in the vanilla controls screen, under *Absolute Cinema Camera*.

## Scene profiles

A profile is a named staging setup — camera mode, colour grade and which filters are on — that
switches in one keypress or one command. Nobody wants to open a settings screen in the middle of a
scene, and cycling grades by key means remembering the order. So: set the rig up once, save it
under a name, call it whenever that kind of scene happens.

```
/cinema scene                  list the profiles, marking the active one
/cinema scene set <name>       apply it
/cinema scene save <name>      save the current setup under that name
/cinema scene delete <name>    remove it
```

Five come ready as examples: `talks` (dialogue coverage, noble study), `ball` (dynamic, grand
ball, wider framing), `tavern` (speaker focus, campfire), `duel` (dynamic, bloodlust, closer
framing) and `stage` (tripod, grand ball). Rename, rework or delete them freely — a profile is
just a line in the config.

Only what belongs to staging is stored: mode, grade, the filter toggles, grade strength and shot
distance. Preferences like smoothing, shot length and scene radius stay global, so profiles never
fight your personal settings.

## Commands

```
/cinema                       open the settings
/cinema on | off | toggle     switch cinema mode
/cinema mode <mode>           first_person | dynamic | speaker_focus | dialogue | tripod | side_track
/cinema grade <grade>         any preset id, with tab completion
/cinema scene ...             see scene profiles above
/cinema tripod                plant the tripod where you are standing
```

## Building

Gradle 8.14.3 with Loom 1.11.8, Java 21. `./gradlew build` produces the jar in `build/libs`.
`./gradlew runClient` starts a dev client straight into a test world; pass
`-Dabsolutecinema.debug=true` (already set for that task) to have the post processor and the
director log what they are doing.

## Settings

With [Cloth Config](https://modrinth.com/mod/cloth-config) installed the settings open as a
six-category screen from [Mod Menu](https://modrinth.com/mod/modmenu); remember to press
**Save changes**, since Cloth discards edits on Escape. Without Cloth the mod falls back to its
own built-in screen, which applies everything immediately.

## Requirements

- Minecraft 1.21.6 – 1.21.8, Fabric Loader 0.16+, Fabric API
- Optional: Cloth Config, Mod Menu, Simple Voice Chat (only the speaker focus mode needs it)

Other versions need a separate build: 1.21–1.21.5 use the old shader system, and 1.21.9+
reworked the renderer again.

## Licence

LGPL-3.0-or-later. See [LICENSE](LICENSE).

