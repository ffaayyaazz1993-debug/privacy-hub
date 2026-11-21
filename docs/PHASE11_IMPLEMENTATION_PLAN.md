# Phase 11 — Implementation Plan (native integrations, capture, interoperability)

## Audit (source-verified, 2026-11-21)
- Present: mind maps (custom Compose canvas), node-linked Pomodoro (timestamp
  based, foreground service), Focus Window overlay, Room v2 with migrations,
  flashcards + FSRS-6 spaced repetition, JSON v2 backup, analytics dashboard.
- Absent: home-screen widgets, Quick Settings tile, share/capture target,
  study planner or recurring routines, weekly review/reflection, unified search,
  OPML/Markdown/PDF/ICS export, captured-reference storage.
- Architecture: single `:app`; Kotlin 2.1.0; Compose BOM 2024.12.01; Room 2.6.1
  via KSP; Navigation Compose; Gson; no DI, no WorkManager, no PDF/OPML libs.
- DB v2 tables: projects, nodes, sessions, user_settings, flashcards,
  review_logs, scheduler_settings. Version 2, `exportSchema = true`.
- Navigation: 6 tabs (Home, Maps, Focus, Review, Analytics, Settings) plus
  `canvas/{projectId}` and `review/node/{nodeId}`; tab nav deliberately does not
  use `restoreState` (fix c398567).
- Timer transitions live only in `FocusSessionManager` (start/pause/resume/
  restart/stop/skip/complete); `FocusSessionService` exposes intents. Widgets and
  the tile must delegate here, never reimplement timing.

## Decisions
- Widgets use framework `AppWidgetProvider` + `RemoteViews` (no Glance dependency).
  One widget provider handles three layouts (timer, plan, progress) selected by a
  `widgetKind` extra; `updatePeriodMillis` is conservative and actions update
  immediately. Theme via resource qualifiers / `night` colors where practical.
- Quick Settings tile is a `TileService`; tapping delegates to
  `FocusSessionManager`, guarded by the active-session check so repeated taps
  cannot create duplicate timers.
- Capture is a share-target activity with persistable URI permission support;
  saving writes a `ReferenceEntity` linked to an approved node. No scraping,
  no network, no automatic save.
- OPML/Markdown/ICS are deterministic pure Kotlin builders/parsers with JUnit
  tests. PDF uses the Android `PdfDocument` canvas API (no external library).
- DB v3 adds `references`, `routines`, `planned_sessions`, `weekly_reflections`
  with FKs, indices and a non-destructive `MIGRATION_2_3`. JSON backup format
  stays primary and gains the new collections with version 3; old backups parse.
- No AI, LLM, RAG, embeddings, cloud sync, accounts or analytics SDK.

## Milestones
M11.1 widgets; M11.2 tile; M11.3 share capture; M11.4 OPML+Markdown;
M11.5 PDF+ICS; M11.6 routines; M11.7 weekly review; M11.8 unified search;
M11.9 migration+backup compatibility; M11.10 tests, debug/release builds,
on-device smoke test, documented limitations.

## Practical scope notes
- PDF and widget rendering cannot be validated by JVM unit tests; they get a
  device smoke test and are reported as such.
- Calendar `.ics` is exported as a shareable file via SAF; it is not inserted
  into the system calendar provider without user consent.


## Addendum — Integrated Detox + Performance (M11.1/M11.2)
- Detox features are already implemented (Room v4, guard service, Pomodoro
  hooks, profiles/schedules/analytics docs) and are retained, not rebuilt.
- Baseline frame timings and bottlenecks: `docs/PERFORMANCE_BASELINE.md`.
- M11.2 STATUS: done — timer isolated, Detox summary moved to ViewModel, ring paths cached, debug+release rebuilt, 53 tests pass. Remeasurement in PERFORMANCE_BASELINE.md.\n- M11.2 fix order: (1) collapse Focus ticker flows and isolate timer
  recomposition to the ring, (2) move Detox state collection into
  `FocusViewModel`, (3) re-measure, (4) only then consider canvas work.
- Every later milestone must re-run the same gfxinfo script and the JVM suite.
