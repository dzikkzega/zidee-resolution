package com.zidee.ress.data.mediastore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalUriValidatorTest {
    @Test
    fun `allows external content URI and rejects internal app directory or file scheme`() {
        val appDataDir = "/data/user/0/com.zidee.ress"

        assertTrue(ExternalUriValidator.isSafeUri("content", "/external/images/media/42", appDataDir))
        assertFalse(ExternalUriValidator.isSafeUri("file", "/storage/emulated/0/DCIM/photo.jpg", appDataDir))
        assertFalse(ExternalUriValidator.isSafeUri("content", "/data/user/0/com.zidee.ress/files/secret", appDataDir))
        assertFalse(ExternalUriValidator.isSafeUri("content", "/../../data/user/0/com.zidee.ress/db", appDataDir))
        assertFalse(ExternalUriValidator.isSafeUri("content", null, appDataDir))
    }

    @Test
    fun `detects supported image and video MIME types only`() {
        assertTrue(ExternalUriValidator.isSupportedMime("image/jpeg"))
        assertTrue(ExternalUriValidator.isSupportedMime("video/mp4"))
        assertFalse(ExternalUriValidator.isSupportedMime("application/pdf"))
        assertFalse(ExternalUriValidator.isSupportedMime(null))
    }
}
