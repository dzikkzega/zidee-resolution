package com.zidee.ress

import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zidee.ress.designsystem.theme.ZideeTheme
import com.zidee.ress.domain.model.MediaUri
import com.zidee.ress.domain.model.TargetPlatform
import com.zidee.ress.presentation.quickshare.QuickShareSheet
import com.zidee.ress.presentation.quickshare.QuickShareUiEffect
import com.zidee.ress.presentation.quickshare.QuickShareUiEvent
import com.zidee.ress.presentation.quickshare.QuickShareViewModel
import com.zidee.ress.presentation.share.DirectShareIntentRouter
import com.zidee.ress.presentation.share.DirectShareTarget
import com.zidee.ress.presentation.share.IncomingShareExtractor
import com.zidee.ress.presentation.share.SharePayload
import com.zidee.ress.presentation.studio.StudioEditorViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

/**
 * Translucent Bottom Sheet entrypoint capturing external `ACTION_SEND` and
 * `ACTION_SEND_MULTIPLE` share intents from Android Gallery/Files apps.
 */
class QuickShareActivity : ComponentActivity() {
    private val shareRouter: DirectShareIntentRouter by inject()
    private var incomingUris by mutableStateOf<List<MediaUri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val extracted = IncomingShareExtractor.extract(this, intent)
        if (extracted.isEmpty()) {
            Toast.makeText(this, "Hanya foto dan video lokal yang didukung.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        incomingUris = extracted

        setContent {
            ZideeTheme {
                QuickShareHost(
                    initialUris = incomingUris,
                    shareRouter = shareRouter,
                    onOpenFullStudio = ::handoffToFullStudio,
                    onFinish = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val nextUris = IncomingShareExtractor.extract(this, intent)
        if (nextUris.isNotEmpty()) {
            incomingUris = nextUris
        }
    }

    private fun handoffToFullStudio(
        uris: List<MediaUri>,
        platform: TargetPlatform,
    ) {
        val androidUris = uris.map { Uri.parse(it.value) }
        val studioIntent =
            Intent(this, MainActivity::class.java).apply {
                putStringArrayListExtra(
                    StudioEditorViewModel.KEY_SOURCE_URIS,
                    ArrayList(uris.map { it.value }),
                )
                putExtra(StudioEditorViewModel.KEY_INITIAL_PLATFORM, platform.name)
                if (androidUris.isNotEmpty()) {
                    val clip = ClipData.newRawUri("ZideeStudioSource", androidUris.first())
                    for (i in 1 until androidUris.size) {
                        clip.addItem(ClipData.Item(androidUris[i]))
                    }
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        startActivity(studioIntent)
        finish()
    }
}

@Composable
private fun QuickShareHost(
    initialUris: List<MediaUri>,
    shareRouter: DirectShareIntentRouter,
    onOpenFullStudio: (List<MediaUri>, TargetPlatform) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: QuickShareViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val runWithSavePermissions = rememberSavePermissionGate()

    LaunchedEffect(initialUris) {
        viewModel.onEvent(QuickShareUiEvent.LoadSources(initialUris))
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is QuickShareUiEffect.LaunchTargetAppShare -> {
                    dispatchShareWithFeedback(context, shareRouter, effect.payload)
                    onFinish()
                }
                is QuickShareUiEffect.SavedOnlyCompleted -> {
                    Toast.makeText(
                        context,
                        "Tersimpan permanen di Galeri/ZideeResolution (${effect.items.size} file)",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                QuickShareUiEffect.DismissSheet -> onFinish()
            }
        }
    }

    BackHandler {
        if (state.isProcessing) {
            viewModel.onEvent(QuickShareUiEvent.ClickCancel)
        } else {
            onFinish()
        }
    }

    QuickShareSheet(
        state = state,
        onEvent = { event ->
            if (event is QuickShareUiEvent.ClickSaveOnly || event is QuickShareUiEvent.ClickSaveAndShare) {
                runWithSavePermissions { viewModel.onEvent(event) }
            } else {
                viewModel.onEvent(event)
            }
        },
        onOpenFullStudio = {
            onOpenFullStudio(state.sourceUris, state.selectedPlatform)
        },
        onShareCompletedItems = { items, platform ->
            val payload = SharePayload.fromItems(items, DirectShareTarget.defaultFor(platform))
            dispatchShareWithFeedback(context, shareRouter, payload)
        },
        onDismissSheet = {
            if (state.isProcessing) {
                viewModel.onEvent(QuickShareUiEvent.ClickCancel)
            } else {
                onFinish()
            }
        },
    )
}
