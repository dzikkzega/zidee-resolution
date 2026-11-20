package com.zidee.ress.engine.video

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.zidee.ress.engine.MediaInputUriGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FfmpegExecutionResult(
    val returnCode: Int,
    val logs: String,
)

/** Abstraction over FFmpegKit so VideoProcessingPipeline can be tested on JVM with fakes. */
interface FfmpegCommandExecutor {
    fun resolveInputPath(sourceUri: String): String

    suspend fun execute(
        arguments: List<String>,
        onElapsedMs: (Double) -> Unit,
    ): FfmpegExecutionResult
}

class FFmpegKitCommandExecutor(
    private val context: Context,
) : FfmpegCommandExecutor {
    override fun resolveInputPath(sourceUri: String): String {
        val uri = Uri.parse(sourceUri)
        MediaInputUriGuard.ensureSafeInputUri(context, uri)
        return when (uri.scheme?.lowercase()) {
            ContentResolver.SCHEME_CONTENT -> FFmpegKitConfig.getSafParameterForRead(context, uri)
            ContentResolver.SCHEME_FILE -> requireNotNull(uri.path) { "File URI missing path" }
            else -> throw IllegalArgumentException("Unsupported URI scheme: ${uri.scheme}")
        }
    }

    override suspend fun execute(
        arguments: List<String>,
        onElapsedMs: (Double) -> Unit,
    ): FfmpegExecutionResult {
        val sessionFinished = CompletableDeferred<Unit>()
        return try {
            suspendCancellableCoroutine { continuation ->
                val session =
                    FFmpegKit.executeWithArgumentsAsync(
                        arguments.toTypedArray(),
                        { completedSession ->
                            sessionFinished.complete(Unit)
                            if (!continuation.isActive) return@executeWithArgumentsAsync
                            val rc = completedSession.returnCode
                            val logs = completedSession.allLogsAsString.orEmpty()
                            when {
                                ReturnCode.isCancel(rc) -> continuation.cancel()
                                rc != null -> continuation.resume(FfmpegExecutionResult(rc.value, logs))
                                else ->
                                    continuation.resumeWithException(
                                        IllegalStateException("FFmpeg session ended without return code: $logs"),
                                    )
                            }
                        },
                        { /* log callback unused when statistics callback supplies elapsed time */ },
                        { statistics ->
                            val timeMs = statistics.time
                            if (timeMs >= 0.0) {
                                onElapsedMs(timeMs)
                            }
                        },
                    )
                continuation.invokeOnCancellation {
                    session.cancel()
                }
            }
        } catch (e: CancellationException) {
            // Wait briefly for native FFmpegKit to flush/close output handles before staging dir deletion.
            withContext(NonCancellable) {
                withTimeoutOrNull(CANCEL_DRAIN_TIMEOUT_MS) {
                    sessionFinished.await()
                }
            }
            throw e
        }
    }

    private companion object {
        const val CANCEL_DRAIN_TIMEOUT_MS = 3_000L
    }
}
