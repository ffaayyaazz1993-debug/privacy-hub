# Digital Detox — privacy & security

- Local-first: no network calls, accounts, AI/LLM/RAG, or external APIs were added.
- The guard observes **package identity of window-state events only**. It never
  reads window content, text, keystrokes, notifications, or clipboard.
- Restricted package names plus labels are stored in Room; labels are display-only.
- The overlay always offers "Return to FocusMap", two emergency overrides, and
  "End Detox session"; it never traps the user silently.
- When the accessibility service is off, the UI says so explicitly: sessions are
  recorded but apps are **not** blocked. No claim of enforcement is made.
- DND is opt-in and restores the prior interruption filter.
- No credentials or secrets are stored in the app or repository.
