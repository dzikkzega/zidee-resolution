package com.zidee.ress.engine

import org.junit.Assert.assertThrows
import org.junit.Test

class MediaInputUriGuardTest {
    @Test
    fun `allows content and external file URIs`() {
        MediaInputUriGuard.ensureSafeInputUri(
            scheme = "content",
            path = "/external/images/media/42",
            appDataDir = "/data/user/0/com.zidee.ress",
        )
        MediaInputUriGuard.ensureSafeInputUri(
            scheme = "CONTENT",
            path = "/external/video/media/99",
            appDataDir = "/data/user/0/com.zidee.ress",
        )
        MediaInputUriGuard.ensureSafeInputUri(
            scheme = "file",
            path = "/storage/emulated/0/DCIM/Camera/photo.jpg",
            appDataDir = "/data/user/0/com.zidee.ress",
        )
    }

    @Test
    fun `rejects network and missing schemes`() {
        for (scheme in listOf("http", "https", "rtsp", "ftp", "asset", null)) {
            assertThrows(IllegalArgumentException::class.java) {
                MediaInputUriGuard.ensureSafeInputUri(
                    scheme = scheme,
                    path = "/remote/video.mp4",
                    appDataDir = "/data/user/0/com.zidee.ress",
                )
            }
        }
    }

    @Test
    fun `rejects traversal and blank paths`() {
        assertThrows(SecurityException::class.java) {
            MediaInputUriGuard.ensureSafeInputUri(
                scheme = "file",
                path = "/storage/emulated/0/../../data/secret.db",
                appDataDir = "/data/user/0/com.zidee.ress",
            )
        }
        assertThrows(SecurityException::class.java) {
            MediaInputUriGuard.ensureSafeInputUri(
                scheme = "content",
                path = "   ",
                appDataDir = "/data/user/0/com.zidee.ress",
            )
        }
        assertThrows(SecurityException::class.java) {
            MediaInputUriGuard.ensureSafeInputUri(
                scheme = "content",
                path = null,
                appDataDir = "/data/user/0/com.zidee.ress",
            )
        }
    }

    @Test
    fun `rejects URIs targeting internal app data directory`() {
        assertThrows(SecurityException::class.java) {
            MediaInputUriGuard.ensureSafeInputUri(
                scheme = "file",
                path = "/data/user/0/com.zidee.ress/databases/zidee.db",
                appDataDir = "/data/user/0/com.zidee.ress",
            )
        }
        assertThrows(SecurityException::class.java) {
            MediaInputUriGuard.ensureSafeInputUri(
                scheme = "file",
                path = "//data/user/0/com.zidee.ress/cache/staging.jpg",
                appDataDir = "/data/user/0/com.zidee.ress/",
            )
        }
    }
}
