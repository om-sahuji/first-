# Test report

Date: 2026-10-26
Build: `1.0.0` (debug), `com.focuslock`
APK: `artifacts/focuslock-debug.apk` — 13,140,416 bytes —
`sha256 6cedaf7552c0192cfeec71409f40dfe903da7d8f14e6b123d622e97836d08f40`

## Environment

| | |
|---|---|
| Build host | Ubuntu 24.04 cloud VM, no physical Android device |
| JDK | Temurin 17.0.21 |
| Gradle / AGP | 8.14.6 / 8.13.2 |
| SDK | platform 36, build-tools 36.0.0, minSdk 26, targetSdk 36 |
| Emulator | not attempted: `/dev/kvm` exists but is not accessible to this user, and no device was attached. Software emulation was judged too unreliable to call verification. |

**Consequence:** everything that requires Android runtime behaviour (accessibility redirects,
launcher takeover, alarms, VPN/tun I/O, Compose rendering, light/dark appearance) is
**unverified**. No performance numbers are reported. This document separates what was actually
executed from what must still be checked on hardware.

## Automated results (executed here)

```
$ ./gradlew clean :app:assembleDebug :app:testDebugUnitTest
BUILD SUCCESSFUL
tests=57 failures=0 errors=0

$ ./gradlew :app:lintDebug
BUILD SUCCESSFUL — 0 errors, 12 warnings
```

Lint warnings are all non-behavioural: six "a newer dependency version exists" (deliberate
pinning for AGP 8.x / compile SDK 36), one AGP-version suggestion, and `allowBackup=false`
advisory. The two lint **errors** found earlier (an API-29-only theme attribute) were fixed.

### Test inventory (57 tests, all passing)

| Class | Tests | What it proves |
|---|---:|---|
| `FocusRepositoryTest` | 11 | Session start persists an absolute end time; state survives a new repository instance (process recreation); a running session cannot be replaced/extended; expired sessions stop enforcing and can be replaced; reconcile keeps live sessions; Cat's LIFE ends the session, is limited to one per local day, persists across a new repository, is not consumed without a session; scheduled sessions keep their source/schedule id; ending clears only the session; schedule CRUD; website filter disabled without domains |
| `ScheduleMathTest` | 9 | Next-start for same day / next day / weekend wrap; disabled and day-less schedules never fire; **DST transition keeps 09:00 at 09:00** (regression fixed here); earliest of several schedules; due-window tolerance; no double fire in one local day |
| `BlockingPolicyTest` | 7 | Nothing blocked without a session or after expiry; own package ignored; system surfaces allowed; user-allowed apps allowed; unlisted apps, Settings and launcher blocked; blank packages ignored |
| `SessionRulesTest` | 6 | Five-extra-app cap; permanent apps not counted; dedupe/blank removal; duration clamping; start validation (second session, uninstalled app); presets valid |
| `EscapePolicyTest` | 5 | Escape available when unused; blocked for the rest of the local day (Kolkata boundary); yesterday's use does not block; clock rollback cannot refresh; session across midnight grants the new day |
| `DomainRulesTest` | 4 | Normalisation of pasted URLs/ports/trailing dots; invalid input rejected; subdomain-only matching with lookalike rejection; case/trailing-dot tolerance |
| `DnsPacketTest` | 5 | Query parsing (A/AAAA), malformed/compressed/truncated rejection, NXDOMAIN reply flags/counts/question echo/transaction id, allowlist decision |
| `IpUdpPacketTest` | 5 | UDP/IPv4 build↔parse round-trip, valid header checksum, non-UDP/truncated rejection, fragment rejection, payload truncation |
| `TimeFormatTest` | 5 | Countdown rounding, >60 min `H:MM:SS`, negative clamp, duration labels, clock labels |

Bugs found and fixed by these tests during development:

1. **DST shift in schedules.** `ScheduleMath` added minutes to midnight, so a 09:00 schedule
   became 10:00 on the spring-forward day. Now resolved as a wall-clock time on the target date.
2. **Escape day used the system zone, not the app clock's zone.** `FocusRepository` called
   `EscapePolicy` without the injected `Clock` zone, so the "one escape per local day" boundary
   could disagree with the session clock. Both call sites now pass the clock's zone.

## Acceptance criteria matrix

Legend: **Automated** = proven by an executed test; **Implemented** = code path exists and
compiles, runtime behaviour needs hardware; **Pending** = must be done on a phone.

| # | Criterion | Status | Evidence / what remains |
|---|---|---|---|
| 1 | Manual session starts reliably; timer survives process recreation | Automated (logic) + Pending (UI) | `FocusRepositoryTest` proves absolute end time and re-read across instances; starting from the Compose start button and process death on device is pending |
| 2 | Scheduled session starts at the correct time when Android permits | Automated (math) + Pending (delivery) | 9 schedule tests incl. DST; `AlarmManager` delivery, Doze behaviour pending |
| 3 | Allowed apps accessible, blocked apps redirected promptly | Automated (decision) + Pending (redirect) | `BlockingPolicyTest`; measured redirect latency pending |
| 4 | Home, Back, Recents, split-screen, PiP and app-launch bypass tested | Implemented, Pending | Back consumed by `BackHandler`; launcher/Recents blocked; activity opts out of resize/PiP; strict mode uses lock task. All need device confirmation |
| 5 | Return from permitted app restores Focus without losing time | Automated (state) + Pending (UI) | Session end time is absolute and re-derived; UI restore pending |
| 6 | Five extra apps selectable; three permanent always available | Automated (rules) + Pending (resolution) | `SessionRulesTest`; permanent app resolution depends on device defaults |
| 7 | Website filtering tested against supported browsers; limits documented | Automated (packets/rules) + Pending (VPN) | DNS/packet codecs tested; `VpnService` consent, tun I/O and browser scoping need hardware. Limits documented |
| 8 | Cat's LIFE obeys daily limit and persists | Automated | `FocusRepositoryTest` + `EscapePolicyTest`, including midnight crossing and clock rollback |
| 9 | Permission failures, reboot recovery, disabled services handled | Implemented, Pending | Status reader, banners, `BootReceiver`, VPN/authorization guards; reboot and revocation pending |
| 10 | UI works in light and dark themes; stays responsive | Implemented, Pending | Full token-based light/dark schemes, no visual verification possible here (no screenshot/emulator) |
| 11 | Build and automated tests pass | **Automated** | Clean build + 57 tests + lint |
| 12 | Physical-device report distinguishes verified from assumed | **Not possible here** | Template below; all device rows are unverified |

## Measuring on a device (next step)

```bash
# install
adb install -r artifacts/focuslock-debug.apk

# grant the accessibility service without tapping through Settings (debug/QA only)
adb shell settings put secure enabled_accessibility_services \
  com.focuslock/com.focuslock.enforcement.FocusAccessibilityService
adb shell settings put secure accessibility_enabled 1

# stream diagnostics (debug builds only)
adb logcat -s FocusLock ActivityTaskManager:I

# launch timing: TotalTime / WaitTime for cold start
adb shell am start -W -n com.focuslock/.ui.MainActivity
```

Record, ten runs each, median and worst case:

1. **Cold start with no session** — `am start -W` TotalTime, plus `FocusLock: first frame … ms
   after application create` and `reportFullyDrawn()` output.
2. **Cold start with an active session** — Focus screen first frame instead of the home screen.
3. **Redirect latency** — start a session, open a blocked app three different ways (launcher
   icon, notification, adb `am start`), then read
   `FocusLock: redirect of <pkg> took <x> ms (attempt n)`.
4. **Warm return** — switch to a permitted app and back; confirm remaining time is correct.
5. **Scheduled start** — create a schedule two minutes ahead, screen off, and observe the
   session becoming active (`dumpsys activity` / first blocked app).

Do not compare these numbers with any claim in the README: none are made.

## Manual QA checklist (to run on hardware)

- [ ] Install, open, Setup shows the exact permission states.
- [ ] Enable accessibility; blocking banner disappears.
- [ ] Start 5-minute session; countdown starts at 05:00, ends at the right wall-clock time.
- [ ] Rotate, background, kill from Recents, reopen → same remaining time.
- [ ] Reboot mid-session → session still active if its end time is in the future.
- [ ] Home / Recents / Back → all return to, or stay on, the Focus screen.
- [ ] Blocked app from launcher, from notification, from a deep link → redirected.
- [ ] Permitted app opens; returning restores Focus and correct time.
- [ ] Split screen and PiP attempts → documented behaviour observed.
- [ ] Blocked app's PiP window becoming active → redirected.
- [ ] Accessibility disabled mid-session → Focus screen shows "Enforcement is off".
- [ ] Cat's LIFE: confirmation dialog, ends session, second attempt same day refused.
- [ ] Escape the day after → available again. Session across midnight → new day's escape.
- [ ] Create/edit/disable/delete schedule; verify next-run label and actual start.
- [ ] Schedule while another session runs → skipped, running session unchanged.
- [ ] Website allowlist: grant VPN, allow a browser + one domain, confirm allowed site loads and
      another domain does not resolve. Check Private DNS warning, other-VPN behaviour.
- [ ] Exact alarms denied → inexact scheduling still starts the session (late is acceptable).
- [ ] Light and dark theme, large font scale (accessibility), 1.3×/2× display size.
- [ ] Strict mode (only on a provisioning device): toggle unavailable without device owner;
      with device owner, lock task prevents Home/Recents and Cat's LIFE releases it.

## Device test report template

| Test | Expected | Result | Notes / measurements |
|---|---|---|---|
| Cold start (no session) | Focus home ≤ 1 s perceived | *unverified* | |
| Cold start (active session) | Focus screen restored | *unverified* | |
| Blocked app redirect (launcher) | Redirect within one frame–ish | *unverified* | |
| Blocked app redirect (notification/deep link) | Redirect | *unverified* | |
| Home during session | Focus screen | *unverified* | |
| Recents during session | Focus screen | *unverified* | |
| Back during session | Session retained | *unverified* | |
| Reboot mid-session | Session restored | *unverified* | |
| Cat's LIFE second use same day | Refused | *unverified* | |
| Scheduled start (screen off) | Session active on unlock | *unverified* | |
| Website allowlist allowed site | Loads | *unverified* | |
| Website allowlist blocked site | NXDOMAIN | *unverified* | |
| Website allowlist + other VPN | Filter off, apps still blocked | *unverified* | |
| Dark theme / large font | Readable, no clipping | *unverified* | |

## Checks that could not run

- Instrumented (`androidTest`) and Compose UI tests — no device/emulator.
- Screenshots and visual review — no device/emulator.
- VPN tunnel establishment, DNS forwarding and browser scoping — requires `VpnService` runtime.
- Accessibility redirect, launcher takeover, alarm delivery, boot recovery — require Android
  runtime and a real notification/window stack.
- OEM-specific behaviour (MIUI/Samsung/ColorOS background management) — requires those devices.

These are reported as pending, not as passes.
