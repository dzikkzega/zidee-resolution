package com.zidee.ress

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.presentation.dashboard.DashboardScreen
import com.zidee.ress.presentation.dashboard.DashboardUiEffect
import com.zidee.ress.presentation.dashboard.DashboardUiEvent
import com.zidee.ress.presentation.dashboard.DashboardUiState
import com.zidee.ress.presentation.dashboard.DashboardViewModel
import com.zidee.ress.presentation.share.DirectShareIntentRouter
import com.zidee.ress.presentation.share.DirectShareTarget
import com.zidee.ress.presentation.share.ShareDispatchOutcome
import com.zidee.ress.presentation.share.SharePayload
import com.zidee.ress.presentation.studio.StudioEditorScreen
import com.zidee.ress.presentation.studio.StudioEditorUiEffect
import com.zidee.ress.presentation.studio.StudioEditorUiEvent
import com.zidee.ress.presentation.studio.StudioEditorUiState
import com.zidee.ress.presentation.studio.StudioEditorViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

private enum class MainDestination {
    DASHBOARD,
    STUDIO,
}

private data class StudioHandoffPayload(
    val uris: List<MediaUri>,
    val platform: TargetPlatform?,
)

class MainActivity : ComponentActivity() {
    private val shareRouter: DirectShareIntentRouter by inject()
    private var pendingHandoff by mutableStateOf<StudioHandoffPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) {
            pendingHandoff = extractStudioHandoff(intent)
        }
        setContent {
            ZideeTheme {
                ZideeMainHost(
                    shareRouter = shareRouter,
                    pendingHandoff = pendingHandoff,
                    onConsumeHandoff = { pendingHandoff = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractStudioHandoff(intent)?.let { handoff ->
            pendingHandoff = handoff
        }
    }

    private fun extractStudioHandoff(intent: Intent?): StudioHandoffPayload? {
        val rawUris = intent?.getStringArrayListExtra(StudioEditorViewModel.KEY_SOURCE_URIS).orEmpty()
        val uris = rawUris.filter { it.isNotBlank() }.map(::MediaUri)
        if (uris.isEmpty()) return null
        val platformName = intent?.getStringExtra(StudioEditorViewModel.KEY_INITIAL_PLATFORM)
        val platform = TargetPlatform.entries.firstOrNull { it.name == platformName }
        return StudioHandoffPayload(uris = uris, platform = platform)
    }
}

@Composable
private fun ZideeMainHost(
    shareRouter: DirectShareIntentRouter,
    pendingHandoff: StudioHandoffPayload?,
    onConsumeHandoff: () -> Unit,
) {
    val dashboardVm: DashboardViewModel = koinViewModel()
    val studioVm: StudioEditorViewModel = koinViewModel()
    val dashboardState by dashboardVm.state.collectAsStateWithLifecycle()
    val studioState by studioVm.state.collectAsStateWithLifecycle()

    var destination by rememberSaveable { mutableStateOf(MainDestination.DASHBOARD) }
    var pendingPlatform by rememberSaveable { mutableStateOf(TargetPlatform.WA_STORY) }
    var appendToStudio by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var updateCode by rememberSaveable { mutableStateOf(0) }
    var updateName by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        checkForUpdate(context.applicationContext)?.let {
            updateCode = it.code
            updateName = it.name
        }
    }
    if (updateCode > 0 && destination == MainDestination.DASHBOARD) {
        val dismiss = {
            context.getSharedPreferences("update_check", Context.MODE_PRIVATE).edit().putInt("dismissed", updateCode).apply()
            updateCode = 0
        }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Pembaruan tersedia") },
            text = { Text("Versi $updateName tersedia. Pembaruan bersifat opsional.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_URL)))
                        dismiss()
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "Tidak dapat membuka tautan pembaruan.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Lihat pembaruan") }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text("Nanti") } },
        )
    }

    val launchMediaPicker =
        rememberMediaPickerLauncher(
            onPicked = { mediaUris ->
                if (appendToStudio && destination == MainDestination.STUDIO) {
                    val combined = (studioState.queue.map { it.uri } + mediaUris).distinctBy { it.value }
                    studioVm.onEvent(StudioEditorUiEvent.LoadSources(combined, studioState.selectedPlatform))
                } else {
                    dashboardVm.onEvent(DashboardUiEvent.MediaPicked(mediaUris, pendingPlatform))
                }
            },
        )

    LaunchedEffect(pendingHandoff) {
        if (pendingHandoff != null) {
            studioVm.onEvent(StudioEditorUiEvent.LoadSources(pendingHandoff.uris, pendingHandoff.platform))
            destination = MainDestination.STUDIO
            onConsumeHandoff()
        }
    }

    DashboardEffectObserver(
        dashboardVm = dashboardVm,
        shareRouter = shareRouter,
        onOpenPicker = { platform ->
            pendingPlatform = platform
            appendToStudio = false
            launchMediaPicker()
        },
        onNavigateStudio = { uris, platform ->
            studioVm.onEvent(StudioEditorUiEvent.LoadSources(uris, platform))
            destination = MainDestination.STUDIO
        },
    )
    StudioEffectObserver(studioVm = studioVm, shareRouter = shareRouter)

    BackHandler(enabled = destination == MainDestination.STUDIO) {
        if (studioState.isAdvancedExpanded) {
            studioVm.onEvent(StudioEditorUiEvent.ToggleAdvancedPanel)
        } else {
            destination = MainDestination.DASHBOARD
        }
    }

    ZideeMainNavigation(
        destination = destination,
        dashboardState = dashboardState,
        studioState = studioState,
        shareRouter = shareRouter,
        onDashboardEvent = dashboardVm::onEvent,
        onStudioEvent = studioVm::onEvent,
        onOpenDashboardPicker = { platform ->
            pendingPlatform = platform
            appendToStudio = false
            launchMediaPicker()
        },
        onAddStudioMedia = {
            appendToStudio = true
            launchMediaPicker()
        },
        onNavigateDashboard = { destination = MainDestination.DASHBOARD },
    )
}

@Composable
private fun ZideeMainNavigation(
    destination: MainDestination,
    dashboardState: DashboardUiState,
    studioState: StudioEditorUiState,
    shareRouter: DirectShareIntentRouter,
    onDashboardEvent: (DashboardUiEvent) -> Unit,
    onStudioEvent: (StudioEditorUiEvent) -> Unit,
    onOpenDashboardPicker: (TargetPlatform) -> Unit,
    onAddStudioMedia: () -> Unit,
    onNavigateDashboard: () -> Unit,
) {
    val context = LocalContext.current
    val runWithSavePermissions = rememberSavePermissionGate()

    Crossfade(
        targetState = destination,
        modifier = Modifier.fillMaxSize(),
        label = "MainNavigation",
    ) { screen ->
        when (screen) {
            MainDestination.DASHBOARD -> {
                var showHistory by rememberSaveable { mutableStateOf(false) }
                Column {
                    TextButton(onClick = { showHistory = true }) { Text("Riwayat versi") }
                    DashboardScreen(
                        state = dashboardState,
                        onEvent = onDashboardEvent,
                        onOpenPrimaryPicker = onOpenDashboardPicker,
                    )
                }
                if (showHistory) {
                    AlertDialog(
                        onDismissRequest = { showHistory = false },
                        title = { Text("Riwayat versi") },
                        text = { Text(stringResource(R.string.release_history), Modifier.verticalScroll(rememberScrollState())) },
                        confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Tutup") } },
                    )
                }
            }
            MainDestination.STUDIO ->
                StudioEditorScreen(
                    state = studioState,
                    historyItems = dashboardState.historyItems,
                    onEvent = { event ->
                        if (event is StudioEditorUiEvent.SaveToGallery || event is StudioEditorUiEvent.SaveAndShare) {
                            runWithSavePermissions { onStudioEvent(event) }
                        } else {
                            onStudioEvent(event)
                        }
                    },
                    onBack = onNavigateDashboard,
                    onAddMoreMedia = onAddStudioMedia,
                    onReShareHistoryItem = { item -> onDashboardEvent(DashboardUiEvent.ReShareItem(item)) },
                    onShareCompletedItems = { items, platform ->
                        val payload = SharePayload.fromItems(items, DirectShareTarget.defaultFor(platform))
                        dispatchShareWithFeedback(context, shareRouter, payload)
                    },
                    onDoneToDashboard = onNavigateDashboard,
                )
        }
    }
}

@Composable
private fun DashboardEffectObserver(
    dashboardVm: DashboardViewModel,
    shareRouter: DirectShareIntentRouter,
    onOpenPicker: (TargetPlatform) -> Unit,
    onNavigateStudio: (List<MediaUri>, TargetPlatform) -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(dashboardVm) {
        dashboardVm.effect.collect { effect ->
            when (effect) {
                is DashboardUiEffect.OpenMediaPicker -> onOpenPicker(effect.platform)
                is DashboardUiEffect.NavigateToStudio -> onNavigateStudio(effect.sourceUris, effect.initialPlatform)
                is DashboardUiEffect.LaunchShare -> dispatchShareWithFeedback(context, shareRouter, effect.payload)
                is DashboardUiEffect.ShowMessage -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun StudioEffectObserver(
    studioVm: StudioEditorViewModel,
    shareRouter: DirectShareIntentRouter,
) {
    val context = LocalContext.current
    LaunchedEffect(studioVm) {
        studioVm.effect.collect { effect ->
            when (effect) {
                is StudioEditorUiEffect.LaunchShare -> dispatchShareWithFeedback(context, shareRouter, effect.payload)
                is StudioEditorUiEffect.SavedSuccessfully ->
                    Toast.makeText(
                        context,
                        "Tersimpan permanen di Galeri/ZideeResolution (${effect.items.size} file)",
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }
}

@Composable
private fun rememberMediaPickerLauncher(onPicked: (List<MediaUri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
        ) { uris ->
            if (uris.isNotEmpty()) {
                onPicked(uris.map { MediaUri(it.toString()) })
            }
        }

    val documentPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                onPicked(uris.map { MediaUri(it.toString()) })
            }
        }

    return remember(photoPickerLauncher, documentPickerLauncher) {
        {
            try {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            } catch (_: ActivityNotFoundException) {
                documentPickerLauncher.launch(arrayOf("image/*", "video/*"))
            }
        }
    }
}

@Composable
internal fun rememberNotificationPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { _ -> }
    return remember(context, launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

@Composable
internal fun rememberSavePermissionGate(): ((() -> Unit)) -> Unit {
    val context = LocalContext.current
    val ensureNotificationPermission = rememberNotificationPermissionRequester()
    var pendingSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val legacyStorageLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingSaveAction
            pendingSaveAction = null
            if (granted) {
                action?.invoke()
            } else {
                Toast.makeText(
                    context,
                    "Izin penyimpanan ditolak. Aktifkan izin penyimpanan untuk menyimpan ke Galeri/ZideeResolution.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    return remember(context, ensureNotificationPermission, legacyStorageLauncher) {
        { onPermitted ->
            ensureNotificationPermission()
            if (requiresLegacyStorageWritePermission(Build.VERSION.SDK_INT)) {
                val granted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
                if (granted) {
                    onPermitted()
                } else {
                    pendingSaveAction = onPermitted
                    legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            } else {
                onPermitted()
            }
        }
    }
}

internal fun requiresLegacyStorageWritePermission(sdkInt: Int): Boolean = sdkInt < Build.VERSION_CODES.Q

internal fun dispatchShareWithFeedback(
    context: Context,
    shareRouter: DirectShareIntentRouter,
    payload: SharePayload,
) {
    when (shareRouter.launchShare(context, payload)) {
        is ShareDispatchOutcome.LaunchedDirect -> Unit
        ShareDispatchOutcome.LaunchedSystemChooser -> Unit
        is ShareDispatchOutcome.Failed ->
            Toast.makeText(context, "Gagal membuka aplikasi tujuan share.", Toast.LENGTH_SHORT).show()
    }
}
