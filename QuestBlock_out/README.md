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
