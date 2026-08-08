# Forge

An Android fitness app that tracks what you eat, programmes what you do, and refuses to let a
training day go quietly.

Three things:

1. **Calorie tracking by barcode.** Point the camera at a packet, confirm how much of it you ate,
   done. Products come from [Open Food Facts](https://world.openfoodfacts.org/) — free, no API key,
   good coverage of Australian and global groceries.
2. **AI-backed nutrition for everything else.** Home-cooked, takeaway, or a barcode nobody has
   catalogued: describe it and Gemini answers with web-grounded numbers. Estimates are always
   labelled as estimates.
3. **A daily routine, and enforcement.** Cardio, plyometrics, and bodyweight work, programmed
   against your training days and fitness level, with escalating reminders that end in a
   full-screen alarm you cannot dismiss without either logging the session or writing down why you
   are skipping it.

## Getting started

```bash
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests
./gradlew :app:lintDebug            # lint
adb install app/build/outputs/apk/debug/app-debug.apk
```

The build needs the Android SDK (compileSdk 35, JDK 17). Point `local.properties` at it with
`sdk.dir=/path/to/android-sdk`, or set `ANDROID_HOME`.

### Adding a Gemini key

The AI fallback is off until you give it a key. Get one from
[Google AI Studio](https://aistudio.google.com/apikey), then in the app go to **Settings → AI
nutrition lookup → Gemini API key**. The key is stored in DataStore on the device and is excluded
from cloud backup and device transfer. Everything except AI lookups works without it.

The model defaults to `gemini-2.5-flash` and is editable in the same screen.

## How it works

### Food lookup

A barcode goes through three stages, in this order, stopping at the first hit:

| Stage | Source | Cost |
| --- | --- | --- |
| 1 | Local Room cache | free, instant |
| 2 | Open Food Facts `api/v2/product/{barcode}` | free, no key |
| 3 | Gemini `generateContent` with the Google Search tool | your API quota |

Nutrition is always stored per 100 g and scaled at the moment you log a portion, so one product row
serves every portion size you ever enter against it. Diary entries snapshot their macros, which
means correcting a product's numbers tomorrow does not silently rewrite what yesterday's diary said.

Gemini is asked for strict JSON and its reply is parsed defensively — grounded generation cannot
also request a JSON response type, so the model tends to wrap its answer in prose or code fences.
When the returned calories disagree with the returned macros by more than 25%, the macro-derived
(Atwater) figure wins and the substitution is noted on the food.

### Routine generation

`RoutineGenerator` builds a session out of the ~70-movement library in `ExerciseLibrary`, all of it
doable with a floor, a wall, and a sturdy chair. A session is laid out warm-up → power → main
circuit → finisher → cool-down, with:

- **Focus rotation** across full body / upper / conditioning / lower, advancing per *training* day
  so a rest day does not shift the cycle.
- **Variety steering** away from whatever the last two sessions used.
- **Determinism per date** — opening the app twice on the same day gives you the same routine, so a
  half-finished session does not reshuffle underneath you.
- **Level and impact filters** — beginners never see advanced movements, and turning off high
  impact drops every jumping variation.

### Enforcement

On a training day, at your reminder time, an exact alarm fires the first notification. Each firing
re-reads the current state and decides what happens next, so finishing the session stops the chain
immediately and changing the nag interval takes effect on the very next hop.

```
reminder ──20m──> nag 1 ──20m──> nag 2 ──20m──> nag 3 ──20m──> full-screen alarm
   │                 │              │              │                  │
   └─────────────────┴──────────────┴──────────────┴──────────────────┘
              any of these stops the moment the session is logged
```

The final stage is an activity that shows over the lock screen, wakes the display, and ignores the
back button. Its only exits are logging the session or recording a skip reason of at least five
characters. Skips are kept and shown on the stats screen next to the streak, because a compliance
number that hides its misses is not a compliance number.

The whole chain dies at midnight; tomorrow gets its own first reminder. Rest days are never nagged
and never break a streak. A daily `WorkManager` job re-arms everything in case an alarm was dropped
by a force-stop or an OEM battery manager, and a boot receiver handles reboots and clock changes.

Every part of this is configurable in Settings, including turning the full-screen stage off
entirely if you only want to be nagged.

## Layout

```
app/src/main/java/com/qwertimer/forge/
├── data/
│   ├── db/          Room entities, DAOs, and the seed exercise library
│   ├── prefs/       DataStore-backed settings
│   ├── remote/      Open Food Facts and Gemini clients
│   └── repo/        Food, diary, and training repositories
├── domain/
│   ├── model/       Macros, foods, exercises, plans — no Android imports
│   └── program/     Routine generation and streak arithmetic
├── enforce/         Alarms, notifications, receivers, the enforcement screen
├── di/              Hilt modules
└── ui/              Compose screens and view models
```

Kotlin, Jetpack Compose with Material 3, Room, Hilt, WorkManager, CameraX + ML Kit for scanning,
Retrofit with kotlinx.serialization. minSdk 26, targetSdk 35.

Tests cover the parts where being wrong is expensive and invisible: routine generation, streak and
compliance arithmetic, reminder scheduling and escalation, Open Food Facts response mapping
(including the kJ-vs-kcal trap that would quadruple every calorie count), and the Gemini JSON
extractor.

## Prior contents of this repository

This repo previously held `boxkit`, a skeleton for building toolbox/distrobox container images.
`Containerfile`, `extra-packages`, `cosign.pub`, and the container build workflow in `.github/` are
still here but are no longer what this repository is for.
