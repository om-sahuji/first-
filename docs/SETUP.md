# Setup guide

## 1. Requirements

- Android 8.0 (API 26) or newer. Targeting Android 16 (API 36).
- The debug APK: `artifacts/focuslock-debug.apk`
  (`sha256 6cedaf7552c0192cfeec71409f40dfe903da7d8f14e6b123d622e97836d08f40`).

## 2. Install

```bash
adb install -r artifacts/focuslock-debug.apk
# or copy the APK to the phone and open it (allow "install unknown apps" for the file manager)
```

Launch **Focus Lock**. The home screen shows a warning banner while blocking is off.

## 3. Required permission: accessibility service

This is the enforcement mechanism. It needs to observe which app is in the foreground and to
bring the Focus screen forward.

1. Focus Lock → **⚙ Setup** → **Accessibility service** → **ENABLE**
   (or system Settings → Accessibility → Downloaded apps → **Focus Lock blocking**).
2. Toggle it on and accept Android's warning dialog.
3. Return to Focus Lock; the Setup row turns orange and the warning banner disappears.

What it can see: the package name of the window that comes to the front. What it cannot see:
screen content, text, passwords, keystrokes (`canRetrieveWindowContent="false"`, no view
inspection flags).

You can revoke it any time in the same system screen. If it is revoked during a session, the
Focus screen shows an "Enforcement is off" notice and points back to system settings.

## 4. Recommended permissions

| Setting | Why | Without it |
|---|---|---|
| **Ignore battery optimisation** (Setup → Reliability) | Stops OEM/Doze from killing the accessibility service and alarms | Enforcement can silently stop in the background; scheduled sessions may start late |
| **Exact alarms** (Alarms & reminders) | Schedules fire at the chosen minute | Android batches the alarm; start can be delayed by minutes |
| **Notifications** | Ongoing notice while the website filter runs | Filter works silently |

All three are optional. The app never blocks on them and always reports the current state in
**Setup → Reliability**.

## 5. Optional: website allowlist (VPN)

Only needed if you want browsers restricted to specific domains.

1. Add domains on the home screen (**Websites → Manage sites**). Subdomains are included; see
   the matching rules on that screen.
2. Turn on **Allow only these sites**.
3. Permit the VPN: **Setup → Website filtering → GRANT**, or the prompt that appears when a
   session with an allowlist starts. Android shows its standard "connection request" dialog;
   nothing is sent to a third-party server — the tunnel is local and only DNS is routed.
4. Grant the filter a real chance to work:
   - **Private DNS must be Off** (Network & internet → Private DNS → Off). The Setup screen warns
     when it is on, but Android does not let an ordinary app change it.
   - Do not run another VPN at the same time; Android allows one active VPN.
   - The browser you use must be selected as a permitted app for the session.
5. During the session, permitted browsers can only resolve allowlisted domains. Other permitted
   apps are not affected.

If any prerequisite is missing, the filter does not start; **app blocking still works** and the
app tells you why in Setup.

## 6. Schedules

1. Home → **Schedules** → **+**.
2. Choose name (optional), days, start time, duration, extra apps, sites.
3. Save. The next occurrence is shown on the home screen.

How it starts: an exact alarm (or an inexact one without the permission) fires
`ScheduleReceiver`, which starts the session in the background with the saved app/site set. A
scheduled session does not steal focus; the Focus screen appears the first time you open Focus
Lock or touch a blocked app, and enforcement is already active before that.

Guards:
- A session already running is never replaced or reconfigured by a schedule.
- A schedule fires at most once per local day, even if Android re-delivers the alarm.
- If the phone is off at the scheduled minute, that occurrence is skipped (no catch-up run).
- Missed/blocked alarms are re-armed after boot, app update, and time/timezone changes.

## 7. Home behaviour during a session

The launch screen and Recents are treated as blocking targets, so pressing Home returns to the
Focus screen. You do **not** have to set Focus Lock as your home app. If you want a purpose-built
home screen, **Setup → Home screen → CHOOSE** opens the system home-app picker.

Consequence to be aware of: while a session runs, the system Settings app is blocked too, so
permissions cannot be changed mid-session. That is intentional; Cat's LIFE and the timer are the
exits.

## 8. Strict mode (optional, device owner)

Ordinary mode is what the app does by default. Android does not let a normal installed app
prevent app switching, hide the notification shade, or make Home do nothing. Genuine kiosk
behaviour requires the app to be **device owner**, which can only be granted over ADB, usually
on a freshly reset phone.

**Warnings**
- Read the whole section before running the command. Provisioning device owner is not something
  the app does silently.
- The command only works if the device has **no accounts** (no Google/Samsung account, no work
  profile) and no other device owner. On a used phone this generally means a factory reset.
- Removing device owner later requires the ADB command below, or a factory reset.
- Strict mode is only entered while a session is running and only if you enable the toggle.

**Provision**

```bash
adb shell dpm set-device-owner com.focuslock/.admin.FocusDeviceAdminReceiver
```

Then open Focus Lock → **Setup → Strict mode**, confirm **Device owner: Provisioned**, and turn
on **Use lock task during sessions**.

**What strict mode does while a session runs**
- Pins the Focus activity with Android lock task.
- Only Focus Lock and the session's permitted apps can be launched; Home and Recents do not
  work.
- The session's timer still ends everything automatically, and Cat's LIFE still exits after its
  confirmation (it ends the session, which releases lock task).

**Recovery**

```bash
# leave lock task by ending the session in-app (Cat's LIFE), or:
adb shell dpm remove-active-admin com.focuslock/.admin.FocusDeviceAdminReceiver
```

If the phone is unresponsive, reboot it; a session's end time has passed after reboot it is
cleared by `BootReceiver`, and lock task only exists while the Focus activity is pinned.

## 9. Uninstall / reset

```bash
adb uninstall com.focuslock
```

If device owner is still provisioned, remove the admin first (command above), otherwise Android
refuses the uninstall. Uninstalling deletes the local configuration, including today's used
escape and the running session.

## 10. Verification checklist (do this once after install)

1. Setup shows **Accessibility: enabled**, and the home banner is gone.
2. Start a 5-minute session. The Focus screen shows 05:00 counting down.
3. Press Home → you return to the Focus screen.
4. Open a blocked app from the launcher (or via a widget) → the Focus screen replaces it.
5. Open a permitted app from the Focus grid → it opens; return to Focus Lock → the timer is
   still correct.
6. Rotate or leave the app for a minute → the countdown reflects real remaining time.
7. Cat's LIFE → confirm → session ends; starting another session and trying again the same day
   is refused.
8. Schedules: create one two minutes ahead, lock the phone, wait → the session is active when
   you next unlock and open a blocked app.

## 11. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| No blocking at all | Accessibility service off, or killed by the OEM | Setup → Accessibility; check battery optimisation; re-enable the service |
| Blocking stops after some time | Aggressive OEM background management (Xiaomi, Samsung, Oppo, Vivo) | Allow autostart / "no restrictions" for Focus Lock, exempt from battery optimisation |
| Schedule late or skipped | Doze, missing exact-alarm permission, phone off | Grant Alarms & reminders; exempt from battery optimisation |
| Website allowlist inactive | VPN permission missing, Private DNS on, another VPN, no permitted browser, no domains | Setup → Website filtering; turn Private DNS off; permit a browser |
| Site allowed but still blocked | Subdomain of a different domain, or a link using an IP address | Add the exact domain; DNS filtering cannot filter IP literals |
| "Settings" won't open during a session | Intentional | Use Cat's LIFE or wait for the timer |
| Cat's LIFE says already used | One escape per local calendar day | It unlocks at local midnight |
| Camera/Phone tile does nothing | No launchable entry point resolved on this build | The tile is dimmed; app reports it cannot open |
| Campaign: accessibility item is greyed out after enabling something else | Android limited settings for sideloaded apps | Settings → Apps → Focus Lock → ⋮ → "Allow restricted settings", then enable again |
