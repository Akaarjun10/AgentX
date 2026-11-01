package com.newoether.agora.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateFloatAsState
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

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) { page ->
        when (page) {
            PAGE_WELCOME -> WelcomePage(onGetStarted = { scope.launch { pagerState.animateScrollToPage(PAGE_PROVIDER_CONFIG) } })
            PAGE_PROVIDER_CONFIG -> ProviderConfigPage(
                viewModel = viewModel,
                preferencesManager = preferencesManager,
                initialProvider = selectedProvider,
                onProviderSelected = { selectedProvider = it },
                onApiKeyChanged = { apiKey = it },
                onContinue = { scope.launch { pagerState.animateScrollToPage(PAGE_MODEL_CONFIG) } },
                onBack = { scope.launch { pagerState.animateScrollToPage(PAGE_WELCOME) } },
            )
            PAGE_MODEL_CONFIG -> ModelConfigPage(
                viewModel = viewModel,
                isFetching = isFetchingModels,
                selectedModel = selectedModel,
                onModelSelected = { selectedModel = it },
                onContinue = {
                    if (selectedProvider != null) {
                        scope.launch {
                            preferencesManager.setSelectedModel(selectedProvider!!, selectedModel)
                            pagerState.animateScrollToPage(PAGE_COMPLETE)
                        }
                    }
                },
                onBack = { scope.launch { pagerState.animateScrollToPage(PAGE_PROVIDER_CONFIG) } },
            )
            PAGE_COMPLETE -> CompletionPage(
                skipWelcome = skipWelcome,
                onSkipWelcomeChanged = { skipWelcome = it },
                onFinish = {
                    scope.launch {
                        preferencesManager.setWelcomeSkipped(skipWelcome)
                        onFinish()
                    }
                },
                onBack = { scope.launch { pagerState.animateScrollToPage(PAGE_MODEL_CONFIG) } },
            )
        }
    }
}

@Composable
private fun WelcomePage(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome to Agora", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Your private AI assistant", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGetStarted) { Text("Get started") }
    }
}

@Composable
private fun ProviderConfigPage(
    viewModel: ChatViewModel,
    preferencesManager: PreferencesManager,
    initialProvider: String?,
    onProviderSelected: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    var provider by remember { mutableStateOf(initialProvider ?: Constants.PROVIDER_OPENAI) }
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(provider) {
        onProviderSelected(provider)
        key = preferencesManager.getApiKey(provider) ?: ""
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Configure provider", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        listOf(Constants.PROVIDER_OPENAI, Constants.PROVIDER_ANTHROPIC, Constants.PROVIDER_GEMINI, Constants.PROVIDER_OPENROUTER).forEach { item ->
            Row(Modifier.fillMaxWidth().clickable { provider = item }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = provider == item, onClick = { provider = item })
                Text(item)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (showKey) key else "•".repeat(key.length), modifier = Modifier.weight(1f))
            Switch(checked = showKey, onCheckedChange = { showKey = it })
        }
        androidx.compose.material3.OutlinedTextField(
            value = key,
            onValueChange = { key = it; onApiKeyChanged(it) },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = {
                scope.launch {
                    preferencesManager.setApiKey(provider, key)
                    onContinue()
                }
            }) { Text("Continue") }
        }
    }
}

@Composable
private fun ModelConfigPage(
    viewModel: ChatViewModel,
    isFetching: Boolean,
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val models by viewModel.availableModels.collectAsState(initial = emptyList())
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Choose a model", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (isFetching) CircularProgressIndicator()
        models.forEach { model ->
            Row(Modifier.fillMaxWidth().clickable { onModelSelected(model) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedModel == model, onClick = { onModelSelected(model) })
                Text(model)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = onContinue) { Text("Continue") }
        }
    }
}

@Composable
private fun CompletionPage(skipWelcome: Boolean, onSkipWelcomeChanged: (Boolean) -> Unit, onFinish: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("You're all set", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Skip welcome next time", modifier = Modifier.weight(1f))
            Switch(checked = skipWelcome, onCheckedChange = onSkipWelcomeChanged)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("Back") }
            Button(onClick = onFinish) { Text("Finish") }
        }
    }
}

private const val PAGE_WELCOME = 0
private const val PAGE_PROVIDER_CONFIG = 1
private const val PAGE_MODEL_CONFIG = 2
private const val PAGE_COMPLETE = 3
private const val PAGE_COUNT = 4