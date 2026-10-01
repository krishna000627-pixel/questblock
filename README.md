# QuestBlock

RPG-style distraction blocker. Finish quests, earn coins, spend coins to open locked apps.
Works with any launcher because it is a background enforcer, not a launcher.

Kotlin, Jetpack Compose, Hilt, Room. Enforcement is an AccessibilityService that launches a lock screen
(`BlockActivity`) when a locked app comes to the foreground and no unlock timer is active.

## First-time setup from Termux

```bash
pkg install -y unzip
unzip QuestBlock.zip -d ~ && cd ~/QuestBlock
bash termux/setup.sh            # optional arg: repo name (default: questblock)
```

`setup.sh` installs git/gh/JDK, generates a keystore in `~/.questblock/`, creates the private GitHub repo,
uploads the keystore as Actions secrets, pushes, waits for CI, then downloads and opens the signed APK.

## What's new in v11

- **No more paying twice to open an app.** A second purchase for the same app within 5 s is ignored (no charge); the lock screen submits once, closes itself if the app is already unlocked, and the blocker re-checks the unlock right before showing the lock screen.

## What's new in v10

- **Days ≠ recurring.** Days only mean "available on these days"; **Recurring** is its own switch. Days only = one-time on those days. Days + Recurring = comes back on the next selected day. Recurring only works as before (a recurring quest with no due time now comes back the next day / week).
- **Tabs fixed.** Tapping a tab always opens its main page (Add Quest no longer comes back). Sub-pages highlight the tab you opened them from.
- **Coins per 5 min** (Settings, optional per-app rate in Vault ⚙) replaces the fixed tiers. Lock screen has a − / + time picker (hold to repeat) with live cost, then hold to buy.
- **Live tracker.** Vault countdown ticks every second; ongoing notification "Instagram · 12:30 left"; warning at 1 minute; Expired state.
- **Coin log** (Profile → Coin log): every earn and spend, plus zero-coin "free" opens with the reason (daily limit, study app, Focus Quest, outside lock hours).
- **Reliability page** (Settings → Reliability): battery, per-brand autostart, lock-in-recents guide, keep-alive switch.
- **Keep-alive service + watchdog:** small persistent notification; "Blocking is off, tap to fix" if the blocker stops; Settings shows when the blocker was last active.
- **Quest types:** + opens a type picker (Daily, Weekly, Challenge, Routine, Focus, One-time). Quests are grouped by type, dailies on top with "3/5 dailies done today" and a streak on each daily.
- DB migrates 7 -> 8 without wiping data.

## What's new in v9

- **Quest days:** pick Mon–Sun (or Weekdays / Weekends). The quest only shows and can only be completed on those days, then comes back on the next one. Quests can now be **edited** (pencil icon).
- **Settings** (Profile → Settings): profile, accent colour, editable unlock prices / focus bonus / study rate / daily login, lock-screen hold time and quotes, quest reminders, strict mode, live permission status, backup / restore / reset.
- **Stats** has its own screen (Profile and Home): quests by weekday, focus minutes, study coins, 28-day activity calendar.
- **Gift for me** lives in the Shop: make, edit and price your own rewards, optionally once per day.
- **Per-app options** (Vault, tune icon on a locked app): daily time limit, lock only during set hours, unlock price %.
- **Reminders:** notification before a quest's due time (Settings → Reminders).
- DB migrates 6 -> 7 without wiping data.

## What's new in v7

- **Focus = Timer Quest.** One flow: pick "Quick focus" or a Focus Quest, start, finish. Finishing pays +15🪙 (and completes the quest + its reward). Linked apps unlock for the session.
- **Vault** is one page: Study Apps section (never lock, 1🪙 / 5 min), locked apps, all apps. Stats now live on the Profile page.
- **Unlock time** is bought in 5-minute steps on the lock screen (v10: coins per 5 min).
- **Daily login** +5🪙. **Shop** reachable from Profile (or tap the coins on Home); custom items under *My Rewards*.
- DB migrates 5 -> 6 without wiping data.

## Every change after that

```bash
bash termux/release.sh "message"
```

## On the phone

1. Install the APK, open QuestBlock, go to the Setup tab.
2. Enable the accessibility service. If it is greyed out (Android 13+ sideload): App info -> ⋮ -> Allow restricted settings.
3. Grant overlay, battery exemption, and optionally Device Admin.
4. On the Apps tab, toggle the apps to lock. Tap the pencil to change coin cost and minutes.
5. Turn on Strict mode to get bounced out of Settings pages that show QuestBlock.

## Notes

- Back up `~/.questblock/`. The same keystore is needed for every future update of the installed app.
- Without the keystore secrets CI falls back to debug signing, so builds never fail for that reason.
- No Gradle wrapper is committed; CI installs Gradle 8.10.2. Run `gradle wrapper` once if you want a local one.
- It is a self-discipline tool: safe mode or ADB can still get around it.
