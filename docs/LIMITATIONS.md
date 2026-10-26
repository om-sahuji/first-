# Known limitations

Focus Lock aims to be dependable, not magic. Everything below is a real constraint of Android
or of the chosen design, and the app is written so that a failure degrades visibly instead of
claiming protection that is not there.

## Platform constraints (ordinary mode)

**Android does not give installed apps system-level control.** There is no public permission
that stops app launching. Ordinary mode uses an accessibility service, which is the strongest
legitimate mechanism available to a normal app — but it is still a redirect, not a wall.

- **Enforcement requires the accessibility service.** If it is off, killed by the OEM, or its
  "restricted setting" is not permitted for a sideloaded build (Android 13+), nothing is
  blocked. Sessions, timer, schedules and Cat's LIFE still work, and the UI reports that
  enforcement is off.
- **Redirect latency is not zero.** A blocked app can appear for one frame before the Focus
  screen takes over. The service reacts to window-state events, throttles bursts to one redirect
  per 400 ms and backs off to 1.5 s if redirects stop landing. Exact latency depends on the
  device, OEM window animations and how the app was launched; no numbers are claimed here (see
  [TESTING.md](TESTING.md#measuring)).
- **A determined user can disable the service** through system settings apps, ADB, a reboot with
  safe mode, or a force-stop. Settings is blocked during a session as a speed bump, not as a
  guarantee.
- **The notification shade and system dialogs remain usable.** System UI is deliberately allowed
  so calls, alarms, emergency dialogs and permission prompts keep working. In ordinary mode the
  shade can therefore still be opened.
- **Multi-window and picture-in-picture** are opted out for Focus Lock's own activity
  (`resizeableActivity=false`, `supportsPictureInPicture=false`). Android 16+ ignores the
  resizeable opt-out on large-screen devices, and another app's PiP window can still exist; a
  PiP window from a blocked app triggers a redirect when it becomes the active window.
- **Screenshots and Recents previews are allowed.** `FLAG_SECURE` is not set so the Focus screen
  can be captured for debugging and accessibility tools.
- **Root, custom ROMs, ADB and device-wide policy** can always override the app. Not defended.
- **Uninstalling removes protection and state** (including today's used escape and any running
  session). There is no tamper-proof storage.
- **Clock manipulation.** Wall-clock time drives sessions and the daily escape. Moving the clock
  backwards cannot refresh an already-used escape, but moving it forwards can make the next day
  arrive early. Android does not let a normal app use a trusted clock.
- **OEM background management** (Xiaomi/MIUI, Samsung, Oppo, Vivo, OnePlus, Huawei) can stop the
  accessibility service or delay alarms. Exempt the app from battery optimisation and allow
  autostart where the OEM requires it.
- **English only, portrait-first UI.** No localisation or tablet-specific layout in this build.

## What is blocked and what is not

- Everything not explicitly allowed is a blocking target, including the launcher and the system
  Settings app.
- Always allowed: system UI, telecom/InCallUI, emergency dialer, the default dialer, the default
  SMS app, every app that handles image capture, and all enabled input methods. Emergency
  calling is never blocked.
- "Phone", "Messages" and "Camera" are resolved from the device's defaults at session start. If
  a device has no default SMS app or no camera handler, that tile is shown as unavailable rather
  than pretending to work.
- Blocking is package-based. A permitted app that embeds another service (for example a browser
  inside a chat app, or a web view) is allowed as part of that app.

## Strict mode (device owner)

- Requires one-time ADB provisioning (`dpm set-device-owner`), typically on a freshly reset
  phone with no accounts or work profile. The app never provisions itself.
- While a session runs it uses Android lock task: Home, Recents, notification shade and other
  apps are locked out except the session's allowlist. That is real kiosk enforcement, but it is
  only active during a session, and Cat's LIFE still ends the session.
- Recovery requires ending the session (Cat's LIFE) or
  `adb shell dpm remove-active-admin com.focuslock/.admin.FocusDeviceAdminReceiver`.
  A forgotten device can require a factory reset if ADB is unavailable.
- Device owner has broader device powers than this app uses. The receiver declares only
  `force-lock`; nothing destructive is performed.

## Website allowlist (DNS filtering)

The allowlist is a local DNS resolver, applied to the browser apps a session permits. It is
honest about what DNS can see:

- **Encrypted DNS bypasses it.** If the user or the browser uses DoH/DoT, or Android's Private
  DNS is on, lookups never reach the filter. The app detects Private DNS and warns; it cannot
  change it without device-owner privileges.
- **IP-literal URLs bypass it** (`https://1.1.1.1/...`), as do cached DNS answers and
  connection reuse from before the session started.
- **Only permitted browsers are filtered.** Other permitted apps keep normal DNS on purpose, so a
  focus session cannot break messaging or maps. A browser that is itself blocked does not need
  filtering.
- **Only one VPN can be active.** Another VPN (including a corporate or privacy VPN) wins or
  conflicts; in that case the filter stops and app blocking continues.
- **No TLS inspection, no URL paths, no app-level traffic shaping.** Only hostnames, only DNS.
- **Failures are silent by design** for lookups that cannot be forwarded: the query is dropped
  and the client retries rather than receiving a fabricated answer.
- **DNS latency** is one extra local hop per lookup (upstream query with a 2.5 s timeout).

If website enforcement is not possible in a given environment, the app disables the filter and
says so. App blocking is never dependent on it.

## Schedules and background execution

- Exact start requires the "Alarms & reminders" permission (Android 12+). Without it, alarms are
  batched and can be late, especially in Doze.
- Sessions do not start while the phone is powered off; missed occurrences are not replayed.
- Android may delay broadcasts under heavy Doze or OEM power saving even with exact alarms.
- A schedule that becomes due while another session is running is consumed and skipped (the
  running session's configuration is never modified).
- Editing a schedule re-arms the next occurrence; deleting one cancels nothing that is already
  running.
- If the VPN or accessibility service is disabled, scheduled sessions still start and the timer
  still runs; only the corresponding enforcement layer is missing.

## Cat's LIFE

- One escape per **local calendar day**, charged to the day it is used. A session that crosses
  midnight can therefore be escaped on each side of midnight (once per day).
- The escape ends the session immediately and cannot be undone; the session does not resume.
- Uninstalling, clearing app data, or switching device/profile resets usage to "unused".
- The escape is deliberately not configurable (no "2 per day" option) so the rule stays
  predictable.

## Observations about this build

- No statistics, history or usage reporting exist by design. Debug builds log enforcement events
  (package names, blocked domain names, timings) to logcat only; nothing is stored or
  transmitted. Release builds do not log.
- The app has no backend, no account and no analytics SDK.
- Tablet/foldable layout and landscape layout are functional but not specifically designed.
- The launcher takeover works by blocking the launcher package. On unusual launchers that host
  system surfaces (or on devices where the recents UI is a system package), behaviour can differ;
  this has not been device-tested.
- **No physical-device verification was performed for this build.** See
  [TESTING.md](TESTING.md) for exactly what was and was not verified.
