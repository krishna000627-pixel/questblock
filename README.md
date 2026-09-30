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
- **Unlock tiers** on the lock screen: 15m 20🪙 · 30m 35🪙 · 1h 60🪙 · 3h 120🪙 · 10h 300🪙.
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
