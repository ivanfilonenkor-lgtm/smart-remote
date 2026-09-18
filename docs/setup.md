# Build and run

## Installed toolchain

This workspace was verified on Windows with:

- Rust stable `1.98.0` using the `x86_64-pc-windows-msvc` toolchain;
- Visual Studio Build Tools 2022 with the Desktop C++ workload;
- Android Studio `2026.1.3.7`;
- Microsoft OpenJDK 17;
- Android SDK Platform 37, Build Tools 36.0.0, and platform-tools.

The Android build uses AGP 9.1.1 and Gradle 9.3.1. These are the minimum compatible pair for compiling this project against API 37; the originally proposed AGP 9.0.1/Gradle 9.1 pair supports only up to API 36. Kotlin remains 2.3.21, Compose BOM 2026.08.00, and Ktor 3.5.2.

## RemoteCore

From the repository root:

```powershell
cd remote-core
cargo test --all-targets
cargo build --release
.\target\release\remote-core.exe
```

Optional arguments:

```text
remote-core.exe --bind 0.0.0.0 --port 8765 --log-level info
```

The console prints the private IPv4 addresses that a phone can use and advertises the host as `_smartremote._tcp.local.` through mDNS/DNS-SD. Keep the process non-elevated: Windows UIPI prevents a normal process from reliably injecting input into an elevated application, and running this unauthenticated MVP as administrator is intentionally unsupported.

## Windows Firewall

Open an elevated PowerShell in the repository root and add the program-scoped rule:

```powershell
.\scripts\configure-firewall.ps1 -Action Add -Executable .\remote-core\target\release\remote-core.exe
```

The helper creates two program-scoped rules, both limited to the Private profile and `LocalSubnet`: TCP 8765 for the WebSocket control channel and UDP 5353 for automatic mDNS discovery. Existing rules with the same Smart Remote names are left unchanged. Remove only those two rules with:

```powershell
.\scripts\configure-firewall.ps1 -Action Remove -Executable .\remote-core\target\release\remote-core.exe
```

## Android client

Set the SDK and Java locations for the current PowerShell session, then build:

```powershell
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk"
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\MicrosoftJdk17\jdk-17.0.20.1+1"
cd android
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

Because the checked-out path contains non-ASCII characters and AGP has affected Windows code paths, generated Android files are intentionally redirected to `%LOCALAPPDATA%\SmartRemoteBuild`. The debug APK is:

```text
%LOCALAPPDATA%\SmartRemoteBuild\app\outputs\apk\debug\app-debug.apk
```

Install it on a device with USB debugging enabled:

```powershell
adb install -r "$env:LOCALAPPDATA\SmartRemoteBuild\app\outputs\apk\debug\app-debug.apk"
```

Start RemoteCore while the phone and PC are on the same private LAN. The PC appears automatically in Smart Remote; tap its name to connect. If multicast discovery is unavailable on a particular router, expand **Connect by IP address**, enter one of RemoteCore's printed private IPv4 addresses, keep port 8765, and press Connect. During the same application run, a disconnect is retried only when the user selects a PC or presses Connect.

After the first successful manual connection, the Android client stores the endpoint and connects to it automatically on later application launches. Manual Disconnect still stops the current session without immediately reconnecting.

To use motion control, open Settings after connecting, enable **Air Mouse**, adjust motion sensitivity or horizontal/vertical inversion if needed, and return to the remote screen. Select **Air Mouse**, then hold the large aim area while rotating the phone. Horizontal inversion is enabled by default. The lock icon keeps movement active without holding; it is cleared automatically when the app leaves the remote screen.

To type from the phone, first focus a text field in any normal (non-elevated) Windows application, then select **Keyboard** in Smart Remote. Enter text on the Android keyboard and press **Send to PC**. Russian, English, emoji and other Unicode text are supported. The Backspace, Enter and Tab buttons are sent immediately. UIPI may block keyboard input into applications running as administrator.

## Video-site control (optional)

RemoteCore includes the local bridge, but the browser extension needs one manual browser confirmation:

1. Open `chrome://extensions` in Chrome or `edge://extensions` in Edge.
2. Enable Developer mode.
3. Choose **Load unpacked** and select the `browser-extension` folder.
4. Keep RemoteCore running and open an AnimeVost or YummyAnime watch page.

Smart Remote then replaces its bottom shortcut row with a contextual panel bearing the active
site's name. On AnimeVost, **Auto watch** clicks only an explicitly detected intro/opening button,
waits five seconds after the video ends, chooses the next episode, and tries to start it. On
YummyAnime, the same switch synchronizes the site's own automatic-next setting. If a site does not
expose a verified opening button, no automatic time jump is guessed.

## Instrumented and end-to-end checks

With an API 29 and an API 36/37 emulator or device attached:

```powershell
cd android
.\gradlew.bat connectedDebugAndroidTest
```

The physical two-phone, hard-disconnect, Public-profile firewall, default-audio-device, and 30-minute soak scenarios are listed in [acceptance.md](acceptance.md). They require real Windows/Android hardware and are not part of the headless build.
