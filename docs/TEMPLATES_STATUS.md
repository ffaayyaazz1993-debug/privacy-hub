# Specialised workspace templates — status

Implemented (M11 template module):
- One versioned `WorkspaceTemplate` definition object drives Study, Job
  Interview Preparation, Work Management and Blank. Each definition carries
  purpose, description, workflow, node hierarchy, Pomodoro presets and an
  optional Detox suggestion.
- New-project flow in Maps shows a template picker with preview tree, workflow
  and editable name/category. Creation uses the existing repository and Room
  auto-generated ids; no node-id reuse across projects.
- `project_templates` table (Room v5, additive migration) stores template id,
  version, presets and Detox suggestion per project. Backup v5 includes it;
  older backups still import.
- Nodes gained optional startDate/dueDate/taskStatus/dependencies columns for
  work-style attributes, using existing NodeStatus/priority fields.
- Canvas has a workspace dashboard action; dashboard derives totals from the
  existing session and node rows, applies preset focus durations through the
  existing settings DAO, and can create a suggested Detox profile (no apps are
  blocked until the user selects them).
- 5 new JVM tests for template definitions (58 total).

Verified:
- Debug build + 58 JVM tests green; Study template creation verified on the
  Nokia C12 Pro (generated branches and child topics visible on canvas).

Not verified / not implemented:
- Dashboard screen navigation was not reliably device-verified (adb harness
  lost the app process during the attempt); no crash evidence was found, but
  the screen is marked NOT DEVICE VERIFIED.
- No custom-template save flow, no per-template planner layout, no interview
  status UI beyond existing node statuses, no due-date editor, no dependency
  editor, no subject/chapter analytics beyond branch rollups, no 50/100/250/500
  template benchmarks.
