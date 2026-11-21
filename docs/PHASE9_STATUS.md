# Phase 9 status

## Implemented
- M9.1 Room v2 schema: `flashcards`, `review_logs`, `scheduler_settings`,
  non-destructive `MIGRATION_1_2` (indexed, FK-cascading), DAO + repository.
  Flashcard create/edit/delete/duplicate/archive/search, node/project browse.
- M9.2 Pure Kotlin FSRS-6 core (`domain/spaced/Fsrs.kt`) using the published
  21 weights and formulas from open-spaced-repetition/py-fsrs, plus a
  learning/relearning wrapper (`ReviewScheduler.kt`) with epoch-millisecond due
  timestamps. Zero new dependencies.
- M9.3 Review tab: due/overdue/new/reviewed-today counts, estimated time,
  filters, queue, reveal-answer, four ratings with interval previews,
  pause/resume, session summary.
- M9.4 Node actions: Create Recall Question, Review Questions, Learning
  Progress (focus time kept separate from recall stats); Review screen accepts
  a node filter route.
- M9.5 Optional daily reminder: AlarmManager + BroadcastReceiver, single
  deduplicated alarm, notification channel, settings switch and hour slider,
  permission-request path. Reviews work with notifications off.
- M9.6 Knowledge analytics in the Analytics tab: counts, accuracy, review days,
  average review time, scheduler-predicted recall (labelled as an estimate),
  per-project focus vs learning, weak concepts, 7-day workload.
- M9.7 Backup v2: flashcards, review logs, scheduler settings and scheduling
  state exported/imported with node/card id remapping; v1 backups still parse.
- M9.8 Tests, debug + signed release builds, on-device smoke test.

## Verified
- `testDebugUnitTest`: 42 JVM tests pass (FSRS identities, rating ordering,
  lapse/easy behaviour, retention and max-interval config, delayed review,
  timezone independence, learning/relearning transitions, zero-stability
  regression, backup v2 round-trip and old-backup parse).
- Installed debug build over the existing v1 database: app opened with six tabs,
  zero crashes — migration ran without data loss.
- Created a card from the Review tab; card persisted across force-stop/restart.
- Full review flow on Nokia C12 Pro: Start review → Reveal → Good →
  "Session complete, reviewed 1, remembered 1" with no crash. Fixed two real
  defects found here: stale due-clock after card creation, and NaN stability on
  a zero-stability new card.
- After restart: Review shows Today = 1; Analytics shows "Knowledge analytics",
  1 card, 1 review, 100% recall accuracy and the labelled prediction.

## Not verified / limitations
- No published FSRS vector-by-vector comparison: the scheduler is validated by
  provable identities and behavioural tests against the reference formulas,
  not by value-for-value equality with Anki's implementation.
- Room migration has no instrumented `MigrationTestHelper` test; verification
  was an actual v1 → v2 upgrade on the physical device.
- Mind-map node chips compile and are wired, but selecting a specific node on
  the custom Compose canvas was not reliably addressable through uiautomator.
  The node-filtered Review route itself was not separately driven on-device.
- Notification firing was not triggered on-device (no alarms waited out);
  permission/settings code and receiver registration compile.
- SAF export/import picker remains unverified through adb; backup logic is
  covered by JVM round-trip tests.
