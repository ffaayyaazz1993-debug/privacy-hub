package com.privacyhub.app.data.db

import androidx.room.TypeConverter
import com.privacyhub.app.domain.model.NodeStatus
import com.privacyhub.app.domain.model.SessionStatus
import com.privacyhub.app.domain.model.SessionType

class Converters {
    @TypeConverter fun nodeStatusToString(v: NodeStatus): String = v.name
    @TypeConverter fun stringToNodeStatus(v: String): NodeStatus = NodeStatus.from(v)
    @TypeConverter fun sessionStatusToString(v: SessionStatus): String = v.name
    @TypeConverter fun stringToSessionStatus(v: String): SessionStatus =
        SessionStatus.entries.firstOrNull { it.name == v } ?: SessionStatus.STOPPED
    @TypeConverter fun sessionTypeToString(v: SessionType): String = v.name
    @TypeConverter fun stringToSessionType(v: String): SessionType =
        SessionType.entries.firstOrNull { it.name == v } ?: SessionType.FOCUS
}
