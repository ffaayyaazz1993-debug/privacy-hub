package com.privacyhub.app.ui

object Routes {
    const val HOME = "home"
    const val MAPS = "maps"
    const val FOCUS = "focus"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val REVIEW = "review"
    const val REVIEW_NODE = "review/node/{nodeId}"
    fun reviewNode(nodeId: Long) = "review/node/$nodeId"
    const val CANVAS = "canvas/{projectId}"
    fun canvas(projectId: Long) = "canvas/$projectId"
}
