# Phase 9 — Implementation Plan (Active Recall / Flashcards / Spaced Repetition)

## Phase 1 audit (source-verified)

- Single `:app` module, Kotlin 2.1.0, AGP 8.7.3, Compose BOM 2024.12.01, Material3,
  Room 2.6.1 via KSP, Navigation Compose 2.8.5, Gson 2.10.1. No DI framework.
- `FocusMapDatabase` is version **1**, `exportSchema = true`, four tables:
  `projects`, `nodes`, `sessions`, `user_settings`. No migrations exist yet.
- `NodeEntity` already holds `projectId`, `parentId`, title/description/notes,
  position, estimated/completed sessions, focus seconds, status, priority, collapsed.
  Node deletion cascades sessions via `FocusMapRepository.deleteNodeSubtree`.
- `FocusMapRepository` is the single facade used by all ViewModels; `repository()`
  (`ui/ViewModelExt.kt`) supplies it. ViewModels use `viewModelFactory { initializer { } }`.
- Pomodoro is timestamp-based (`Pomodoro.kt`, `FocusSessionManager.kt`) with a
  foreground service and process-death restoration. It touches only `sessions`
  and node focus counters. It must stay untouched.
- Navigation is a 5-tab `Scaffold` + `NavHost` in `ui/FocusMapApp.kt`; canvas route
  is `canvas/{projectId}`. Tab navigation must not use `restoreState` (see c398567).
- `BackupManager` uses an open-JSON `Backup` data class (version 1) with Gson,
  remaps node ids per imported project, preserves sessions, throws on malformed
  or newer backups. Old backups have no flashcards, so new fields must default.
- UI conventions: `MaterialTheme`, `AssistChip` node actions, `Card` panels,
  `OutlinedTextField`, `Button`/`TextButton`, `Icons.Rounded.*` from the extended
  icon set. Dark/light theme already driven by `UserSettingsEntity.theme`.
- Analytics is hand-rolled flows + `@Query` aggregates, not a chart library.

## Scheduler decision

Verified candidates on Maven Central (2026-11-21):
- `ru.lorddarthart.fsrs:fsrs:0.1.0` — Apache-2.0, zero deps, FSRS-5, single 0.1.0
  release and one star; maintenance signal too weak for a memory engine.
- `io.github.meko123456:srs:0.1.0` — not actually resolvable (Maven Central signing
  key still blocked).
- `open-spaced-repetition/FSRS-Kotlin` (v6) — no published stable artifact found.

Decision: implement the **published FSRS-6 formulas** from the maintained
`open-spaced-repetition/py-fsrs` reference (`fsrs/scheduler.py`, 21 weights,
decay = w[20]) as a pure Kotlin, dependency-free, JVM-testable module under
`domain/spaced/`. No arbitrary intervals. No external AI/cloud SDK. The wrapper
adds the documented learning/relearning steps (1m/10m) and epoch-millisecond due
timestamps; the core has no Android or Room dependency. Scheduler version is
stored per card and per review log.

## Database changes (Room migration 1 -> 2, never destructive)

- `flashcards`: id, projectId, nodeId, question, answer, hint, explanation,
  contentType (extensible), timestamps, dueTimestamp, lastReviewTimestamp,
  stability, difficulty, repetitions, lapses, learningState, learningStep,
  intervalDays, schedulerVersion, reviewStatus, archived. FKs to nodes/projects,
  indices on nodeId, projectId, dueTimestamp, archived.
- `review_logs`: id, flashcardId (FK cascade), reviewedAt, rating, previous/
  next due, elapsed days, stability/difficulty before+after, duration seconds,
  schedulerVersion. Indices on flashcardId, reviewedAt.
- `scheduler_settings`: id=1 singleton, desiredRetention, maximumInterval,
  newCardsPerDay, reviewLimitPerDay, reminderEnabled, preferredReviewHour.
- `MIGRATION_1_2` uses `CREATE TABLE IF NOT EXISTS` + indices; old rows untouched.

## Implementation sequence

M9.1 schema + DAO + repository + flashcard CRUD UI + node association.
M9.2 pure FSRS-6 core + learning-step wrapper + JUnit tests.
M9.3 Review tab: counts, queue, reveal-answer, four ratings, session summary,
      filters, pause/resume.
M9.4 Mind-map integration: node chips (Create Recall Question, Review Questions,
      Learning Progress), compact due indicators (toggleable).
M9.5 Daily reminder via AlarmManager + notification channel + settings toggle,
      deduplicated by a fixed request code; review works with notifications off.
M9.6 Knowledge Analytics: counts, accuracy, per-project/per-node, weak concepts,
      upcoming workload, focus-vs-learning side by side, predicted retention
      labelled as scheduler prediction (not measured memory strength).
M9.7 Backup v2: flashcards, review logs, scheduler settings, id remapping; old
      backups still import; malformed/newer backups rejected.
M9.8 Full test run, debug + signed release build, on-device smoke test.

## Non-goals (strict scope)

No AI generation, chat, cloud sync, social, billing, ads, or analytics SDKs.
Focus time is never treated as evidence of memory retention; a review never
marks a whole mind-map branch complete.
