package com.privacyhub.app.domain.model

enum class NodeStatus { NOT_STARTED, IN_PROGRESS, COMPLETED, NEEDS_REVISION;
    companion object { fun from(s: String?): NodeStatus =
        entries.firstOrNull { it.name == s } ?: NOT_STARTED }
}

enum class SessionStatus { RUNNING, PAUSED, COMPLETED, STOPPED, SKIPPED }

enum class SessionType { FOCUS, SHORT_BREAK, LONG_BREAK }
