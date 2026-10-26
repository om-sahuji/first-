# Focus Lock

A native Android focus environment: start a timed session, and everything except a small
allowlist of apps disappears behind an immersive Focus screen. Built with Kotlin and Jetpack
Compose, no account, no analytics, no backend.

| | |
|---|---|
| **Debug APK** | [`artifacts/focuslock-debug.apk`](artifacts/focuslock-debug.apk) (12.5 MiB) |
| **SHA-256** | `6cedaf7552c0192cfeec71409f40dfe903da7d8f14e6b123d622e97836d08f40` |
| **Application ID** | `com.focuslock` |
| **Min / target SDK** | 26 (Android 8.0) / 36 (Android 16) |
| **Source** | `app/src/main/java/com/focuslock/` (48 Kotlin files, ~5.7k lines) |
| **Tests** | 57 JVM unit tests, all passing (see [docs/TESTING.md](docs/TESTING.md)) |

> **Verification status.** The project builds cleanly, passes its automated tests and lint, and
> is packaged as an installable debug APK. It has **not** been run on a physical Android phone
> or an emulator in this environment (no KVM, no attached device), so every claim that depends
> on device runtime behaviour is marked as *unverified* in
> [docs/TESTING.md](docs/TESTING.md). No on-device latency numbers are claimed.

## What it does

**Before a session**
- Duration wheel (5–180 min) with 15/25/45/60/90 presets.
- Three permanent apps — Phone, Messages, Camera — plus up to **five** additional apps.
- Website allowlist for permitted browsers (`rollcall.iitd.ac.in` and similar).
- Multiple schedules with weekdays, start time, duration, permitted apps and sites; edit,
  enable/disable, delete.
- Setup screen for every permission, with explanations and a debug diagnostics panel.

**During a session**
- Immersive Focus screen: large countdown, segmented progress, end time, permitted app grid.
- Cat's LIFE — one confirmed emergency exit per local calendar day.
- No settings, app pickers, schedule editing or exit controls.

**After a session**
- Session state is derived from a persisted absolute end time, so process death, activity
  recreation, reboot or a slow alarm can never reset or extend the timer.

## How enforcement works

Nothing relies on a full-screen overlay, and there is no polling loop.

1. **Accessibility service (primary).** An enabled `AccessibilityService` receives
   `TYPE_WINDOW_STATE_CHANGED` / `TYPE_WINDOWS_CHANGED` events with the foreground package
   name. It reads a pre-computed allowlist from memory (no package-manager query, no disk I/O),
   and starts the Focus activity over the offending app. Accessibility services are permitted
   to start activities from the background; that is the mechanism ordinary apps cannot
   otherwise use. `canRetrieveWindowContent="false"` — the service never reads screen content.
2. **Home takeover.** The launcher package is not in the allowlist, so a Home or Recents press
   produces a launcher window event and lands back on the Focus screen. Setting Focus Lock as
   the home app is optional, not required.
3. **Settings is blocked during a session.** Disabling enforcement is not a one-tap escape;
   Cat's LIFE is the supported exit and the timer is the fallback.
4. **Essential surfaces stay reachable.** System UI, telecom/InCallUI, emergency, the default
   dialer, the default SMS app, every camera handler, and all enabled input methods.
5. **Immersive system bars** hide the status and navigation bars during a session (swipe to
   reveal). This is presentation, not enforcement.
6. **Strict mode (optional, device owner).** Real kiosk enforcement uses Android lock task:
   the Focus activity pins the screen and only allowlisted packages can launch. It requires
   one-time device-owner provisioning over ADB and never happens silently. See
   [docs/SETUP.md](docs/SETUP.md#strict-mode-optional-device-owner).
7. **No relaunch loops.** The activity is `singleTask`, redirects are throttled to one per
   400 ms and backed off to 1.5 s after three rapid redirects, and the service ignores its own
   package.

### Bypass resistance, honestly stated

| Attempt | Ordinary mode | Strict mode (device owner) |
|---|---|---|
| Open a blocked app | Redirected to Focus | Blocked before launch |
| Home / Home gesture | Launcher blocked → Focus | Lock task, no Home |
| Recents / Overview | Launcher/Overview blocked → Focus | Lock task, no Recents |
| Back | Consumed by Focus screen | Consumed |
| Split screen / PiP | Activity opts out of resize/PiP; other apps can still try (Android 16 ignores the opt-out on large screens) | Lock task disables multi-window |
| Notification shade | Reachable by swipe (system UI is essential) | Lock task hides it |
| Disable accessibility service | Settings is blocked during the session; the user can still wait for the timer | Device owner can restrict Settings |
| Root / ADB / OEM force-stop | Not preventable | Not preventable |

## Website allowlist

Implemented as a local DNS resolver VPN, isolated from app blocking:

- `VpnService` routes only the virtual DNS address, and only for browser apps the user permitted
  (`addAllowedApplication`). Other permitted apps keep normal DNS.
- Each lookup is checked against the allowlist; allowed queries are forwarded to the device's
  resolver over a `protect()`ed socket, blocked ones get an NXDOMAIN reply.
- Matching rule: a domain allows itself and all subdomains, never a lookalike
  (`iitd.ac.in` matches `rollcall.iitd.ac.in`, not `notiitd.ac.in`).
- If VPN permission is missing, another VPN is active, Private DNS is on, or no browser is
  permitted, the filter does not run — **app blocking is unaffected**. The app says so on
  screen instead of pretending.

DNS filtering cannot see encrypted DNS (DoH/DoT), IP-literal URLs, cached answers or traffic
from apps outside the permitted browser set. Details in [docs/LIMITATIONS.md](docs/LIMITATIONS.md).

## Build from source

Requirements: JDK 17, Android SDK platform 36 + build-tools 36, Gradle 8.14.6 (wrapper
included). `scripts/setup-toolchain.sh` installs exactly that into `~/toolchain` without root.

```bash
# one-time toolchain (Linux, no sudo required)
./scripts/setup-toolchain.sh

export JAVA_HOME=~/toolchain/jdk-17.0.21+9
export ANDROID_HOME=~/toolchain/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties   # machine-local, git-ignored

./gradlew :app:assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # 57 JVM tests
./gradlew :app:lintDebug              # 0 errors, 12 informational warnings
```

Stack: Kotlin 2.4.21, AGP 8.13.2, Gradle 8.14.6, Jetpack Compose BOM 2026.03.01
(Compose 1.10.6 / Material 3), DataStore Preferences 1.2.1, kotlinx.serialization 1.12.0,
kotlinx.coroutines 1.11.0. Versions are pinned to the newest set that still builds on AGP 8.x
and compile SDK 36; newer AndroidX releases require AGP 9.

Install and configure: see [docs/SETUP.md](docs/SETUP.md).

## Project layout

```
app/src/main/java/com/focuslock/
├── FocusLockApp.kt              Application + manual dependency graph (AppContainer)
├── core/                        Clock, debug logging/timing, startup trace, formatting
├── data/
│   ├── FocusRepository.kt       session lifecycle, escape, schedules (single source of truth)
│   ├── model/                   ActiveSession, Schedule, Settings, validation rules
│   ├── store/                   SnapshotStore contract + DataStore JSON implementation
│   └── apps/                    installed-app catalogue/cache, permanent app resolution
├── domain/                      pure rules: BlockingPolicy, ScheduleMath, DomainRules, EscapePolicy
├── enforcement/                 AccessibilityService, alarm scheduler, receivers, kiosk, status
├── session/SessionCoordinator.kt side effects of session changes (alarms, VPN, lock task)
├── web/                         VpnService DNS allowlist, DNS/IP packet codecs, controller
└── ui/                          MainActivity, view model, theme, components, screens
```

Design choices worth knowing:

- **One atomic document.** Session, schedules and settings are stored as one JSON blob in
  DataStore. Consuming Cat's LIFE and clearing the session is a single transaction, so a crash
  cannot spend an escape without ending the session.
- **Pure domain layer.** Blocking decisions, schedule arithmetic, domain matching and the daily
  escape rule are plain Kotlin objects with no Android dependencies and unit tests.
- **Manual DI.** One `AppContainer`; no annotation processors, faster builds, fewer moving parts.
- **Separate failure domains.** If the VPN cannot start, blocking still works; if the
  accessibility service is disabled, sessions and the timer still work and the UI says
  enforcement is off.

## Data model

```kotlin
ActiveSession(startedAtMillis, endAtMillis, allowedPackages, allowedDomains,
              websiteFilterEnabled, scheduleId, source)   // persisted; absolute end time
Schedule(id, label, days /* ISO 1..7 */, startMinuteOfDay, durationMinutes,
         allowedPackages, allowedDomains, websiteFilterEnabled, enabled, lastStartedEpochDay)
Settings(defaultDurationMinutes, defaultAllowedPackages, defaultAllowedDomains,
         defaultWebsiteFilterEnabled, escapeUsedOnEpochDay, setupCompleted, strictModeEnabled, …)
```

## Permissions and why

| Permission | Why | If denied |
|---|---|---|
| Accessibility service | Observe foreground package; redirect blocked apps | Sessions run but nothing is blocked; UI warns |
| `SCHEDULE_EXACT_ALARM` | Start schedules on time | Falls back to inexact alarms; UI shows status |
| `RECEIVE_BOOT_COMPLETED` | Re-arm alarms, restore session | Alarms are lost after reboot |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Forward allowed DNS queries to the device resolver; **no external server** | Website filter disabled |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the DNS tunnel alive during a session | Website filter disabled |
| `POST_NOTIFICATIONS` | Show the website-filter notification | Filter runs silently |
| VPN service binding | Answer DNS queries locally | Website filter disabled |
| Device admin (strict mode) | Lock task enforcement | Strict mode unavailable |

No contacts, SMS, call log, location, storage, camera or usage-stats permissions. No
`QUERY_ALL_PACKAGES`; package visibility is scoped to launcher apps, browsers, camera and dialer.

## Performance approach

- Enforcement is **event-driven**: no polling, no repeated app launches, no service restarts.
- The blocking path does set lookups on cached data; app names/icons are loaded once and cached
  (icons in an `LruCache`).
- Session remaining time is always recomputed from the absolute end time, so no ticking counter
  can drift while the app is backgrounded.
- UI work is limited to a 250 ms ticker while a session is on screen.
- Debug builds log timings under the `FocusLock` tag (`process created`, `first frame … ms`,
  `redirect of <pkg> took … ms`), and the activity calls `reportFullyDrawn()` so launch cost can
  be measured with `adb shell am start -W`. Measurements are **not** included here because no
  device was available — the procedure is in [docs/TESTING.md](docs/TESTING.md#measuring).

## Testing

```bash
./gradlew :app:testDebugUnitTest
```

57 tests cover session persistence/recreation, the escape limit, schedule arithmetic (including
a DST transition), domain matching, blocking decisions, duration/selection validation, packet
parsing/checksums and countdown formatting. Two bugs were found and fixed by these tests during
development: a DST shift in schedule start times, and a repository escape calculation that
ignored the injected clock's time zone.

`./gradlew :app:lintDebug` reports 0 errors and 12 warnings, all of them "a newer dependency
version exists" / data-extraction-rule suggestions.

## Scope

No statistics dashboard, accounts, sync, gamification, social features or billing. The only
network traffic is local DNS forwarding inside the VPN.

## Known limitations

Short version: Android does not let an ordinary app prevent app switching; OEM background
killers can stop enforcement; DNS filtering is bypassable by design of the internet; strict mode
needs device-owner provisioning. Full list: [docs/LIMITATIONS.md](docs/LIMITATIONS.md).

## Documentation

- [docs/SETUP.md](docs/SETUP.md) — install, permissions, strict mode, recovery
- [docs/LIMITATIONS.md](docs/LIMITATIONS.md) — platform and feature limits
- [docs/TESTING.md](docs/TESTING.md) — what was run here, acceptance matrix, measurement plan
