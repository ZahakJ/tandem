# Windows emulator checks for Tandem RoMM Dev

Use this reference for Android Emulator development/testing, not as proof of physical Thor behavior. Current
milestone acceptance and pending checks live in [the journal](../../../../docs/ROMM_PROGRESS.md); the detailed
walkthrough is [ROMM_TESTING.md](../../../../docs/ROMM_TESTING.md). Read those instead of duplicating their status here.

## Identify the environment

Reuse the installed SDK and project wrapper. Discover SDK/Gradle cache paths from environment/ignored local
configuration, avoiding unrelated properties that might hold secrets. If a tool resolves its home to a denied root
directory, point the process at the verified existing user cache/configuration rather than replacing the toolchain.
Discover devices with `adb devices -l`; target the selected emulator serial for every device command. Recheck its
installed package/version before replacing or launching it. Do not reuse a previous chat's emulator serial blindly.

The current debug variant is `io.github.zahakj.tandem.romm.dev`, with activity
`io.github.zahakj.tandem.MainActivity`. Confirm against build metadata; release identity is different. Installation
or manipulation of the running app must remain within the user's requested scope. Never uninstall/clear app data
or collect stored tokens as a fallback. An active emulator may contain the user's working server configuration.

## Get past setup and open the right menu

Inspect `ui/App.kt` (`LibraryRoot`) if menu inputs appear ineffective. Until all-files access and a games-folder
selection are present, the setup screen is rendered instead of `Main` and its menu hosts. Let the user complete
**Read your games** and **Pick your games folder** through normal Android UI. An empty folder on internal storage
is enough for a connection-only check; do not load ROMs just to unlock settings.

After setup, with Tandem foreground and other dialogs closed, inject a short Start press:

```powershell
# $adbPath and $emulatorSerial must already identify the verified SDK tool and intended emulator.
& $adbPath -s $emulatorSerial shell input keyevent 108
```

- `108` (`KEYCODE_BUTTON_START`) opens the **left Start panel** on tap/release. Android Menu `82` is also mapped
  to Start by `Pad.of` in `Bindings.kt`. MainActivity accepts these without requiring a physical gamepad source.
- In that panel choose **Settings → All settings**, or **Settings → Library & art** directly. RoMM is below Steam.
- R2 `105` on home opens the **right side panel** (quick controls/notifications), not the full Settings screen.
- Holding Start for at least the current `Bindings.HOLD_MS` (550 ms at this milestone) opens the separate quick
  panel. A generic ADB `--longpress` duration is not a reliable substitute; inspect `input` help before supplying
  a duration. Do not confuse quick-panel Android system settings with Tandem's own Settings.
- Screensavers may consume the first press, and overlays/controller-test/rebinding modes can intercept it.
- Optional touchable Menu hints are not a universal entry point: they default off and several home layouts hide
  them. Mouse-clicking a nonexistent gear or suggesting unverified function-key shortcuts will not help.

Use mouse/touch after opening the panel, or injected D-pad `19/20/21/22`, center `23`, Back `4`. Do not use fixed
row counts or screen coordinates as durable instructions; rows and display sizes vary.

## Token input and evidence

The shared `ui/TextEntry.kt` has a native editable line and a Paste key alongside the controller grid. Click the
line before PC typing/Ctrl+V. Paste reads Android's clipboard only on user action; Windows clipboard delivery
depends on emulator sharing/input configuration. Enter/Done accepts; Escape/Back cancels. Secrets stay masked.
`TextEntryEdits.kt` keeps native selection/cursor editing consistent with controller keys. Use synthetic text and
cancel for UI checks, leaving working credentials intact. Never put a real token into `adb shell input text`, shell
history, screenshots, logs or the journal.

Record observed emulator results separately from source inspection and mocked/JVM results. Emulator connection,
restart and private-mode checks were accepted by user report at the first milestone; do not fabricate exact UI or
packet-level observations. Native clipboard focus tests still need explicit observations. Virtual secondary displays
can exercise app routing, but cannot validate Thor screen roles, hinge/sleep behavior, physical buttons, SD access,
emulator-game handoff, hardware Keystore protection or the Thor's own HTTPS/Tailscale route.
