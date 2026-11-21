# Phase 11 status

## Implemented
- M11.1 Three home-screen widgets (framework `AppWidgetProvider` + `RemoteViews`):
  Focus Timer, Today's Study Plan, Daily Progress. Resizable via `resizeMode`;
  conservative 30-minute update period; actions delegate to the existing
  `FocusSessionManager` (start/pause/resume/open). No second timer.
- M11.2 Quick Settings tile using `TileService`; state syncs from Room; tapping
  delegates start/pause/resume to the existing manager, so repeated taps cannot
  create duplicate sessions.
- M11.3 Share target for text/plain, application/pdf and image/*; capture screen
  requires an explicit project + node choice before saving a `ReferenceEntity`.
  Persistable URI permission is taken best-effort; nothing is uploaded or scraped.
- M11.4 OPML export and import (hierarchy + titles + descriptions), Markdown
  export with notes/references and focus statistics.
- M11.5 PDF export via Android `PdfDocument`; iCalendar `.ics` export of planned
  study blocks with VALARM reminders, shared through SAF.
- M11.6 Routines with weekdays, duration, weekly target, reminder, active flag,
  generated planned activities, duplicate-slot prevention, missed marking,
  controlled +1-day reschedule.
- M11.7 Weekly review with planned vs actual focus, unfinished blocks, projects
  progressed and four user-written reflection fields (no automated interpretation).
- M11.8 Unified local search across projects, node titles/descriptions/notes,
  references and flashcard questions, grouped by content type.
- M11.9 Room v3 with non-destructive `MIGRATION_2_3`; JSON backup v3 preserves
  references, routines, planned activities and reflections; v1/v2 backups parse.
- M11.10 49 JVM tests; debug + signed release builds; on-device smoke test.

## Verified
- 49 JVM tests pass: OPML round-trip/escape/malformed-document rejection,
  Markdown structure, ICS alarm/UTC fields, backup v2/v3 compatibility, plus all
  earlier Pomodoro, layout, zoom, FSRS and backup tests.
- Real v2 → v3 upgrade on Nokia C12 Pro: app opened with seven tabs, no crash.
  Planner and Weekly review render real stored data.
- Share intent opened the capture screen with the shared text pre-filled; the
  capture UI lists projects/nodes and requires an explicit destination.
- Widget providers and tile service are registered in the installed package.

## Not verified / limitations
- Widgets were not placed on the actual home screen; rendering, resizing and
  tile state changes in Quick Settings were not exercised on-device.
- PDF output was produced in code but not opened in a PDF viewer; OPML/MD/ICS
  export files were not saved through the SAF picker via automation.
- PDF/OPML exports preserve node notes and references as text only; the widget
  layout uses a dark background rather than following the app's theme setting.
- Routine schedule generation was exercised through code/unit paths but not by
  creating a routine with touch input on the device.
- Search navigation opens the project canvas, not a preselected node.
