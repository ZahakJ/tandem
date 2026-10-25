# Android apps

Your other apps live in the dock, the **All apps** drawer (the last dock tile), search results, widgets and, on the icon grid, as tiles of their own. They behave the same everywhere: they open on the screen you chose, and **Y** on any of them opens the same menu.

## The app menu

| Action | What it does |
| --- | --- |
| Open | Opens the app on its screen (shown on the right) |
| Open on the other screen | Opens it on the other screen this once |
| Always open on | Auto, Top or Bottom; A steps through them |
| Add to home / Remove from home | Puts the app on the icon grid, or takes it off |
| Pin to dock / Unpin from dock | The same for the dock |
| Rename | A name of your own; clear it to go back to the app's name |
| Choose icon | A picture from Tandem's Files or Gallery, or a system icon from your theme packs |
| Treat as game | Adds the app to the Android Games system (play time, artwork, Continue playing) |
| Hide from drawer | Keeps it out of All apps |
| App info, Uninstall… | Android's own screens |

The dock's menu can also move the app left or right. Names and icons you choose show everywhere the app shows, Android Games included.

## The All apps drawer

- **Search** at the top (or **X**) opens the on-screen keyboard; the list narrows as you type.
- **On the dock** comes first, then every app by name.
- **Show hidden apps** appears once you have hidden one; hidden apps show faded until you switch it off again.
- When a search finds nothing, one button clears it (or shows hidden apps, when a hidden one matches).

## Choosing a screen for apps

Without a choice of its own, an app opens on the screen you start it from:

- touched on the bottom screen, it opens on the bottom screen; touched on the top one, on the top;
- with the controller, it opens on the screen the library is on (the bottom one while the screens are swapped).

**Settings › Two screens › Apps open on** sets another default (Top or Bottom) for every app; **Always open on** in an app's menu overrides both. Games keep their own rules: an Android game opens on the screen set for the game (Y › Start on), else the one set in its app menu, else its system's.

### Moving an open app

Android brings an app that is already open back on the screen it is on. To move it:

- **Hold Back** for a moment (about 0.6 s) while the app is in front. It moves to the other screen and opens there from then on; hold Back again within 10 seconds to undo both. A short press is an ordinary Back.
- Moving is a **swap**. An app moved up puts what was on top on the bottom screen: another app moves down, and Tandem's library shows down there, ready to use. Moved down, the app brings the library back on top.
- On the bottom screen's own panel (shown when the library can't be, see below), tap **Move *app* here**.
- **Open on the other screen**, or a screen you chose, moves the app over if it was open on the wrong one.

### The library on the bottom screen

While an app has the top screen, the bottom screen shows Tandem's library (Tandem must be the Home app). Touch it to take the controller there. What you start from it follows the usual rules: a game takes the top screen over the app, and an app opens on the bottom screen unless its own screen is set to Top. **Hold Back** swaps back, and **Home** puts everything back as usual. Without the library (Tandem isn't running) the bottom screen offers **Back to Tandem**, which moves the app down rather than closing it, and **Android apps**.

Moving needs Shizuku (see [Resume and Shizuku](resume.md)), and holding Back needs the Controller Home button service (**Settings › Controls**). Games are never moved by holding Back unless **Settings › Two screens › More options › Move games too** is on. **Hold Back to move apps** switches the shortcut off.

## Where it is kept

Per-app choices are kept in `app-prefs.json` next to Tandem's other data. **Settings › Data & about › Back up** saves them with **Systems & emulators**.
