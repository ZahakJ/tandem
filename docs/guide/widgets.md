# Widgets

Widgets are small live cards: your free storage, the battery, a clock, play stats, what's running and more, and [widgets from your other apps](#android-widgets). They sit in a row on the home screen (under **Continue playing**) and on the bottom screen whenever nothing is highlighted.

## Adding and arranging

Open **Settings › Home & layout › Dock & widgets › Customize widgets**, or press **Y** on any widget and pick **All widgets…**.

| Button | In the widget editor |
| --- | --- |
| <kbd>L1</kbd> / <kbd>R1</kbd> | Switch between the top screen and the bottom screen |
| <kbd>A</kbd> | Customize the highlighted widget, or add one on **+ Add a widget** |
| <kbd>Y</kbd> | The highlighted widget's options |
| <kbd>B</kbd> | Back |

Every widget has:

- **Size**: Small, Medium, Wide or Tall. On the bottom screen (two columns) Wide is a big card two rows high.
- **Show on**: the home screen, the bottom screen, or both.
- Its own settings, such as the clock style or which list the storage widget shows. Left and right change a setting too.
- **Move**: the widget wiggles; move it with the D-pad and press A (or B) when it's in place.
- **Remove widget**. The message offers Undo for 6 seconds.

On the home screen the widget area has one row, or two when a Tall widget is there. You can fix it at one or two rows in the editor.

## On the icon grid

With the icon grid as home, widgets can also sit on the grid itself, among the icons. Press A on an empty spot and pick **Add here › Widget…**. A widget on the grid covers 1×1, 2×1, 1×2 or 2×2 cells; Small, Medium, Tall and Wide sizes draw as those. Y on it gives **Widget options…** (the same settings as above), **Move**, **Resize** and **Remove widget** (under More). While moving it, Y changes its size to the next one that fits.

**Settings › Home & layout › Dock & widgets › Widgets on the grid** chooses where Add here › Widget… puts a new widget: **In cells** (the default) or **As a row**, in the widget row at the end of an up-and-down grid. Widgets already placed stay where they are.

## The widgets

| Widget | What it shows | A does |
| --- | --- | --- |
| Storage | Internal and SD card usage rings, the biggest systems or folders, art and cache sizes | Measures again |
| Battery | Charge, charging, temperature and time left (when Android knows it) | Opens quick settings |
| Clock | Digital, analog or big and friendly; seconds and 12/24-hour optional | Switches the style |
| Now playing | The game waiting in the background (needs [Shizuku](resume.md)) | Resumes it |
| Continue playing, Favorites, Recently added | Your games in a strip; left and right pick one | Plays it |
| Surprise me | A random game (never played, everything or favorites) | Plays it; the 🎲 picks another |
| Play stats | Today, the last 7 days, your streak and a little chart | Opens play stats |
| Achievements | Your latest RetroAchievements and Steam unlocks (only when signed in; Steam's from what Tandem last looked up) | Opens the achievements tab |
| Pip | Pip, reacting to the time of day and your streak | Pip says something else |
| Up next | Your list of games to play next | Plays the game (or edits a note) |
| System monitor | Free memory, CPU temperature when Android shares it, uptime | Nothing |
| App shortcuts | Your dock apps | Opens the app |
| Greeting | Pip, the time and your last game (the bottom screen's default) | Plays your last game |
| Android widget | A widget from one of your other apps, see [below](#android-widgets) | Presses the widget's main action |

To put a game on **Up next**, press **Y** on it and choose **Add to Up next**, or press A on the **+** at the end of the widget and type a name. A name that matches one game adds that game; anything else is kept as a note. The widget's Y menu removes entries and clears played games.

On the bottom screen widgets work by touch: tap a game to play it, tap the 🎲 to reroll.

## Android widgets

Widgets from your other apps (a weather forecast, a calendar, a music player, a to-do list) sit beside Tandem's own, on the home screen, on the bottom screen and in the icon grid's cells, in the same card with the same frame and highlight.

To add one, pick **Android widget** wherever you add a Tandem widget: **+ Add a widget** in the widget editor (**Settings › Home & layout › Dock & widgets › Customize widgets**, or **Start › New › Widget…**), and **Add here › Widget…** on an empty spot of the icon grid. A picker lists every widget your installed apps offer, grouped by app, with its preview picture and the room it will take (**2×1**, say). Press **A** on one:

1. The first time, Android asks whether Tandem may create widgets and read their content. Tick **Always allow** so it doesn't ask again.
2. If the widget has a set-up screen of its own (which city, which calendar), it opens now. Backing out of it adds nothing.
3. The widget lands where you were adding it, as big as it asks to be: on the home row and the bottom screen in the nearest of Small, Medium, Wide or Tall; on the grid 1 or 2 cells each way.

From then on it is a widget like any other: **Y** on it gives Size (or **Resize** on the grid), **Show on**, **Move** and **Remove widget** (with Undo), and **Set up…** when its app lets you change its settings again. Touch works as in the app that made it: tap a button in the widget and the widget does its thing. On the controller, **A** presses the first thing in the widget that can be pressed (usually its main action, such as opening the app), and nothing else; the D-pad never gets lost inside a widget.

Android widgets update only while Tandem is in front; while a game plays they rest with everything else. When the app behind a widget is uninstalled, the widget goes by itself, with a note. Tandem never reads what a widget shows: the content comes from its app, straight to the screen.

## Battery and privacy

Widgets only update while Tandem is on screen; nothing runs while you play. Storage sizes are measured in the background when you come back to Tandem, at most every 15 minutes, and the measuring stops the moment you start a game. Tandem's widgets never use the network, except Achievements, which uses your RetroAchievements account the same way the stats screen does. An Android widget belongs to its app, which fetches whatever it shows under its own permissions.
