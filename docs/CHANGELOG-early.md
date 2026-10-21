# QuestBlock early changelog (v7 to v18)

Moved out of the README. Notes for every later version are in the app: Profile > Version info (`data/VersionLog.kt`).

## What's new in v18

- **Build fix.** v17 failed to compile: its two new crash-safe unlock-tracking settings (`activeUnlockPkg`, `activeUnlockEnteredAt`) were written after the closing brace of `Prefs`, outside the class. They are now inside it. No other change.

## What's new in v16

- **Profile:** the Hero info box moved to the top, right under the name and level, and has no button at all. It is edited only from Settings (the Settings button on Profile → Hero info).

## What's new in v15

- **Cleaner lock screen.** Time and cost on one line (− 15m · 15 🪙 +); the big button just says **Hold to unlock**; coin balance is a small chip in the corner; the app's real icon replaces the letter box; "per 5 min" only shows when the app has its own rate.
- With too few coins the unlock button greys out ("Need N more 🪙") and **Do a quest instead** becomes the main button.
- UI only: hold-to-unlock, the charge guards and the unlock logic are unchanged.

## What's new in v14

- **Hero info is edited only in Settings.** Settings → Hero info now has Name, Goal and Date of birth. The Hero info box on Profile is read-only and has an "Edit in Settings" button.

## What's new in v13

- **Quest add screen** shows only the chosen type (no row of type bubbles). To use another type, go back to the picker.
- **About me** (Profile → About me): the developer's page with name, bio, why QuestBlock exists, links (GitHub, email) and the app version. Works offline. Its text is in `data/AboutMe.kt`: edit that file and rebuild to change it.
- No database change (still version 8).

## What's new in v12

- **One quest form per type.** The type now decides repeat, days and due date, so you can't save a contradictory combination.
  Daily: optional time of day only (always every day). Weekly: pick days (none preselected). One-time: due date. Challenge: length in days.
  Routine / Focus: steps or focus session plus one **Repeat: Once / Every day / Chosen days** control. Type can be changed at the top.
  Notes, category (and due date for repeating-less Routine/Focus) sit under a collapsed **More options**. Difficulty and Priority are compact rows.
- **Editing old quests.** A one-time quest that was recurring opens as Daily/Weekly; a Daily limited to some days opens as Weekly; a recurring Routine/Focus on some days opens as "Chosen days".
- **Home.** Today's quests sits directly under the hero card. Today's list = due today/overdue + recurring quests, sorted High → Low priority, then due time, then type. Dated and undated quests follow under Coming up / No date. Focus and Stats are one compact row. Empty state is a single line.
- **Counters fixed.** "x/y done" now counts quests completed today against today's quests (not all quests ever).
- **Routine on Home** now tells you to tick every step instead of silently doing nothing.
- **Quests tab.** Completed shows the 10 newest with Show all; no empty "📅" chip on dailies with a time.
- No database change (still version 8).

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

