# Build and ADB commands (PowerShell)

Use only sections relevant to the requested phase. Run from the repository root. Resolve real values before copying commands; the variable assignments below are descriptive values, not device discovery automation.

## Resolve tools and target

Resolve the SDK using the configured `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `sdk.dir` in `local.properties`. Read only the needed property; that file may contain other secrets. Check the directory exists, then use that SDK's `platform-tools/adb.exe` and installed build-tools. Do not download a second SDK, change project configuration, or use a global Gradle installation.

```powershell
java -version
.\gradlew.bat --version
$androidSdkPath = 'verified Android SDK directory'
$adbPath = Join-Path $androidSdkPath 'platform-tools/adb.exe'
& $adbPath version
& $adbPath devices -l
```

Identify the Thor from the device listing and confirm its model. All following device commands require the chosen serial. Stop on an ambiguous target, missing authorization, or offline status; bounded reads do not justify resetting the device or ADB server.

```powershell
$thorSerial = 'verified Thor serial'
& $adbPath -s $thorSerial get-state
& $adbPath -s $thorSerial shell getprop ro.product.model
& $adbPath -s $thorSerial shell getprop ro.build.version.release
& $adbPath -s $thorSerial shell getprop ro.build.version.sdk
& $adbPath -s $thorSerial shell getprop ro.product.cpu.abilist
```

For local evidence, choose a unique timestamped directory and verify that it is ignored by Git. Retain raw evidence locally only; redact before sharing. Build and device outputs may contain personal paths.

```powershell
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path (Get-Location) "app/build/thor-test/$runId"
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
git check-ignore $runDir
```

## Build and tests

Run each command independently and save `$LASTEXITCODE` immediately after it. A PowerShell pipeline's success is not the Gradle process's exit status.

```powershell
.\gradlew.bat assembleDebug 2>&1 | Tee-Object -FilePath (Join-Path $runDir 'assembleDebug.log')
$buildExitCode = $LASTEXITCODE
```

```powershell
.\gradlew.bat testDebugUnitTest 2>&1 | Tee-Object -FilePath (Join-Path $runDir 'testDebugUnitTest.log')
$testExitCode = $LASTEXITCODE
```

Current reports: `app/build/test-results/testDebugUnitTest/TEST-*.xml` and `app/build/reports/tests/testDebugUnitTest/index.html`. Verify report timestamps belong to this run; a failed task can leave old output behind. Count failures, errors, and skipped tests from the XML, and identify affected test methods.

On failure, capture the failed task and first causal compiler, dependency, SDK/JDK, or test error. Use `--stacktrace` for one targeted rerun when it will clarify the cause. Avoid `--debug`, build scans, blanket cache deletion, or repeated identical retries. Do not change wrapper versions, suppress tests, or treat `clean` as a default fix. A disconnected Thor does not prevent local build/unit-test work.

## APK inspection, installation, and launch

After a successful build, inspect `output-metadata.json` under the APK output directory. Verify the APK's application ID and launchable activity using the installed SDK build-tools' `aapt.exe dump badging`; inspect signing certificates with `apksigner.bat verify --print-certs` if needed. Use an installed version appropriate to the project.

```powershell
$apkPath = (Resolve-Path 'app/build/outputs/apk/debug/app-debug.apk').Path
Get-FileHash -LiteralPath $apkPath -Algorithm SHA256
$packageName = 'io.github.zahakj.tandem'
$launchComponent = 'io.github.zahakj.tandem/.MainActivity'
& $adbPath -s $thorSerial shell pm path $packageName
& $adbPath -s $thorSerial shell dumpsys package $packageName
```

The package and component above are current project defaults: replace them if inspection shows different values. Inspect installed version/signing information and preserve the pre-install state in local evidence. If package dumps cannot establish signing compatibility, report uncertainty; do not claim a guaranteed compatible update.

Only when installation is requested:

```powershell
& $adbPath -s $thorSerial install -r $apkPath
$installExitCode = $LASTEXITCODE
```

Proceed only if exit status and output confirm success. On `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `INSTALL_FAILED_VERSION_DOWNGRADE`, insufficient space, or permission rejection, report the exact reason and stop installation attempts until there is a justified safe remedy. Do not add downgrade/grant-all flags, uninstall, or clear data. Do not let an old installed version stand in for the APK that failed to install.

When launch is in scope, record the local time, start the verified main activity, and inspect the result:

```powershell
Get-Date -Format o
& $adbPath -s $thorSerial shell am start -W -n $launchComponent
& $adbPath -s $thorSerial shell pidof $packageName
```

## Permissions, displays, and storage

These are read-only diagnostics. They supplement app UI checks; they do not prove the app can use a chosen destination.

```powershell
& $adbPath -s $thorSerial shell dumpsys package $packageName
& $adbPath -s $thorSerial shell cmd appops get $packageName
& $adbPath -s $thorSerial shell dumpsys display
& $adbPath -s $thorSerial shell dumpsys input
& $adbPath -s $thorSerial shell sm list-volumes all
& $adbPath -s $thorSerial shell df -h
```

Inspect the current manifest and source to distinguish:

- Runtime permissions, such as microphone access if that feature is being tested. Check denied and granted states through normal Android UI in an appropriate test setup.
- Normal install-time permissions, including `INTERNET` and `ACCESS_NETWORK_STATE`; these are not user-granted runtime permissions.
- Special access, including the currently declared `MANAGE_EXTERNAL_STORAGE`. It cannot be treated as an ordinary runtime permission or fixed with a blanket `pm grant`.
- Persisted SAF URI permissions for folders selected through Android's document picker. Verify retention after relaunch and actual app read/write behavior. Inspect URI-grant dumps only when needed and supported by that OS; do not dump app preferences or credentials to inspect them.

Do not issue permission grants/revocations or `appops set` as routine diagnostics. A real card must be present for SD validation. Use app-selected isolated fixtures, record free space, and avoid protected Android directories and assumptions about removable-volume UUIDs. Simulate full/unavailable storage in mocks rather than filling or unmounting a user's volume.

Use display dumps and physical observation to map actual top/bottom IDs and active roles. For an authorized screenshot, consult the installed tool's help and use host-side capture without creating files on the device; avoid binary redirection through Windows PowerShell 5.1, which may corrupt PNG output. Redact private library art/names and address/token entry screens.

## Logcat and crash investigation

Record the reproduction start/end times without clearing log buffers. Capture bounded history with app and crash-related tags:

```powershell
& $adbPath -s $thorSerial logcat -d -b main -b system -b crash -v threadtime -t 1500 'Tandem:V' 'AndroidRuntime:E' 'ActivityManager:I' 'libc:F' 'DEBUG:F' '*:S' |
    Set-Content -LiteralPath (Join-Path $runDir 'logcat-raw.txt')
$logcatExitCode = $LASTEXITCODE
```

Inspect this raw file locally and create a redacted excerpt before reporting. A tag filter reduces unrelated output; it is not a privacy guarantee. Do not use `logcat -c`, force crashes, or collect a full bugreport by default.

For a missing crash, request bounded crash-buffer history:

```powershell
& $adbPath -s $thorSerial logcat -d -b crash -v threadtime -t 200
```

Correlate package, PID, and time; crash buffers can contain other apps' events. For a live-process investigation, obtain `pidof` again and optionally filter by that PID. Use `$appProcessId`, not PowerShell's read-only `$PID`; PID filters miss messages after a process restart and some system/native crash output.

- Java/Kotlin: capture `FATAL EXCEPTION`, exception type, the `Caused by` chain, and the first relevant project frame.
- Native: capture fatal signal/backtrace and build identity; use existing matching symbols if present. Do not root the Thor to retrieve protected tombstones.
- ANR: inspect bounded ActivityManager output for the package and time; a missing Java stack does not rule out an ANR.
- Process exit: use supported `dumpsys activity exit-info $packageName` when available to distinguish crash/ANR from low-memory eviction or user termination. Check local command help if unsupported.

Collect evidence before restarting anything. Reproduce only enough to isolate the failure; do not force-stop during a write or change app data as a diagnostic shortcut. Link a suspected source location only after inspecting it, and distinguish a hypothesis from a confirmed cause.
