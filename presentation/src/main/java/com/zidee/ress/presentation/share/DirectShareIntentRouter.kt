package com.zidee.ress.presentation.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.zidee.ress.core.result.AppError

interface PackageAvailabilityChecker {
    fun isPackageAvailable(packageName: String): Boolean
}

class AndroidPackageAvailabilityChecker(
    private val packageManager: PackageManager,
) : PackageAvailabilityChecker {
    override fun isPackageAvailable(packageName: String): Boolean {
        val probe =
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                setPackage(packageName)
            }
        return packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) != null
    }
}

sealed interface ShareDispatchOutcome {
    data class LaunchedDirect(val packageName: String) : ShareDispatchOutcome

    data object LaunchedSystemChooser : ShareDispatchOutcome

    data class Failed(val error: AppError) : ShareDispatchOutcome
}

/**
 * Routes permanent `ZideeResolution` MediaStore URIs directly to WhatsApp, WhatsApp Business,
 * or Instagram using read-only grants (`FLAG_GRANT_READ_URI_PERMISSION`), falling back to the
 * Android system chooser when the target app is not installed.
 */
class DirectShareIntentRouter(
    private val availabilityChecker: PackageAvailabilityChecker,
) {
    constructor(context: Context) : this(AndroidPackageAvailabilityChecker(context.packageManager))

    fun resolveTargetPackage(requestedPackage: String?): String? {
        if (requestedPackage.isNullOrBlank()) return null
        if (availabilityChecker.isPackageAvailable(requestedPackage)) {
            return requestedPackage
        }
        if (requestedPackage == DirectShareTarget.WHATSAPP.packageName) {
            val businessPkg = DirectShareTarget.WHATSAPP_BUSINESS.packageName
            if (businessPkg != null && availabilityChecker.isPackageAvailable(businessPkg)) {
                return businessPkg
            }
        }
        return null
    }

    fun createShareIntent(payload: SharePayload): Pair<Intent, String?> {
        val androidUris =
            payload.uris.map { uri ->
                val parsed = Uri.parse(uri.value)
                require(parsed.scheme == ContentResolver.SCHEME_CONTENT) {
                    "Only content:// MediaStore URIs may be shared externally"
                }
                parsed
            }

        val baseIntent =
            if (androidUris.size == 1) {
                val single = androidUris.first()
                Intent(Intent.ACTION_SEND).apply {
                    type = payload.mimeType
                    putExtra(Intent.EXTRA_STREAM, single)
                    clipData = ClipData.newRawUri("ZideeResolution", single)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                val first = androidUris.first()
                val clip =
                    ClipData.newRawUri("ZideeResolution", first).apply {
                        for (i in 1 until androidUris.size) {
                            addItem(ClipData.Item(androidUris[i]))
                        }
                    }
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = payload.mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(androidUris))
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

        val resolvedPackage = resolveTargetPackage(payload.targetPackage)
        return if (resolvedPackage != null) {
            baseIntent.setPackage(resolvedPackage)
            baseIntent to resolvedPackage
        } else {
            Intent.createChooser(baseIntent, payload.chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } to null
        }
    }

    fun launchShare(
        context: Context,
        payload: SharePayload,
    ): ShareDispatchOutcome {
        val (intent, resolvedPackage) =
            try {
                createShareIntent(payload)
            } catch (t: IllegalArgumentException) {
                return ShareDispatchOutcome.Failed(AppError.InvalidInput(t.message ?: "Invalid share URI", t))
            }

        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            if (resolvedPackage != null) {
                ShareDispatchOutcome.LaunchedDirect(resolvedPackage)
            } else {
                ShareDispatchOutcome.LaunchedSystemChooser
            }
        } catch (e: ActivityNotFoundException) {
            ShareDispatchOutcome.Failed(AppError.Unknown("No share target activity available", e))
        } catch (e: SecurityException) {
            ShareDispatchOutcome.Failed(AppError.PermissionDenied("Permission denied sharing URI", e))
        }
    }
}
