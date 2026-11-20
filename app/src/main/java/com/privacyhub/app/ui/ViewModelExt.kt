package com.privacyhub.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.privacyhub.app.FocusMapApplication
import com.privacyhub.app.data.repo.FocusMapRepository

@Composable
fun repository(): FocusMapRepository =
    (LocalContext.current.applicationContext as FocusMapApplication).repository
