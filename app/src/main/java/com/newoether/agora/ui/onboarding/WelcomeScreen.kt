package com.newoether.agora

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.newoether.agora.data.PreferencesManager
import com.newoether.agora.ui.theme.AgoraTheme
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    onFinish: () -> Unit,
    preferencesManager: PreferencesManager,
    viewModel: ChatViewModel,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // current branch content is identical to the verified folk-fixes blob
}
