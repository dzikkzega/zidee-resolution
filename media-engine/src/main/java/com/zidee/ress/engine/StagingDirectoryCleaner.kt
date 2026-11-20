package com.zidee.ress.engine

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Owns temporary files inside `cacheDir/zidee_staging` so failed or cancelled renders
 * never leave stray data on low-storage devices.
 */
class StagingDirectoryCleaner(private val cacheDirProvider: () -> File) {
    constructor(context: Context) : this({ context.cacheDir })

    private val rootDir: File
        get() = File(cacheDirProvider(), STAGING_DIR_NAME).apply { mkdirs() }

    fun createStagingFile(extension: String): File {
        val cleanExt = extension.trimStart('.').ifBlank { "tmp" }
        return File(rootDir, "zidee_${UUID.randomUUID()}.$cleanExt")
    }

    fun createJobDirectory(): File = File(rootDir, "job_${UUID.randomUUID()}").apply { mkdirs() }

    fun deleteDirectory(dir: File) {
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    fun deleteOrphanedOlderThan(
        maxAgeMs: Long = ORPHAN_MAX_AGE_MS,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val children = rootDir.listFiles() ?: return
        for (child in children) {
            if (nowMs - child.lastModified() >= maxAgeMs) {
                child.deleteRecursively()
            }
        }
    }

    private companion object {
        const val STAGING_DIR_NAME = "zidee_staging"
        const val ORPHAN_MAX_AGE_MS = 2 * 60 * 60 * 1000L
    }
}
