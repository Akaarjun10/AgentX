package com.newoether.agora.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.newoether.agora.ui.motion.MotionAwareCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newoether.agora.Constants
import com.newoether.agora.R
import com.newoether.agora.data.PreferencesManager
import com.newoether.agora.ui.theme.AgoraTheme
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    onFinish: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
    preferencesManager: PreferencesManager,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = PAGE_WELCOME, pageCount = { PAGE_COUNT })
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchJob by remember { mutableStateOf<Job?>(null) }
    var apiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var skipWelcome by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        selectedProvider = preferencesManager.getSelectedProvider()
        skipWelcome = preferencesManager.isWelcomeSkipped()
    }

    LaunchedEffect(pagerState.currentPage, selectedProvider) {
        // on every entry with the latest key; cancel on leave so an in-flight request
        // never lands off-screen (no list jump) and a stale key's result never wins.
        fetchJob?.cancel()
        val providerToFetch = selectedProvider
        if (pagerState.currentPage == PAGE_MODEL_CONFIG && providerToFetch != null && providerToFetch != Constants.PROVIDER_LOCAL) {
            isFetchingModels = true
            fetchJob = scope.launch {
                try {
                    kotlinx.coroutines.delay(300) // debounce swipe-through + let async key save commit
                    viewModel.fetchModelsForProvider(providerToFetch)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Network failure: keep whatever the list already shows.
                } finally {
                    isFetchingModels = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.availableModels.collect { models ->
            if (models.isNotEmpty() && selectedModel.isBlank()) selectedModel = models.first()
        }
    }

    // ...SENTINEL_REQUIRES_REMAINDER...