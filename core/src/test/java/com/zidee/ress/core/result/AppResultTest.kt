package com.zidee.ress.core.result

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AppResultTest {
    @Test
    fun `appResultOf wraps returned value as Success`() {
        val result = appResultOf { 42 }

        assertEquals(AppResult.Success(42), result)
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
        assertNull(result.errorOrNull())
    }

    @Test
    fun `appResultOf maps thrown exception through mapError`() {
        val result =
            appResultOf(mapError = { AppError.StorageWrite("disk", it) }) {
                throw IOException("boom")
            }

        val error = result.errorOrNull()
        assertTrue(error is AppError.StorageWrite)
        assertEquals("disk", error?.message)
        assertTrue(error?.cause is IOException)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `appResultOf defaults to Unknown error with throwable message`() {
        val result = appResultOf<Int> { throw IllegalStateException("bad state") }

        assertEquals("bad state", result.errorOrNull()?.message)
        assertTrue(result.errorOrNull() is AppError.Unknown)
    }

    @Test
    fun `appResultOf rethrows CancellationException`() =
        runTest {
            try {
                appResultOf<Unit> { throw CancellationException("cancelled") }
                fail("CancellationException must propagate")
            } catch (e: CancellationException) {
                assertEquals("cancelled", e.message)
            }
        }

    @Test
    fun `map transforms Success and passes Failure through`() {
        val failure: AppResult<Int> = AppResult.Failure(AppError.Processing("x"))

        assertEquals(AppResult.Success(4), AppResult.Success(2).map { it * 2 })
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun `flatMap chains results and short-circuits on Failure`() {
        val error = AppError.InvalidInput("negative")
        val chained =
            AppResult.Success(-1).flatMap {
                if (it < 0) AppResult.Failure(error) else AppResult.Success(it)
            }

        assertEquals(AppResult.Failure(error), chained)
    }

    @Test
    fun `fold onSuccess and onFailure invoke the matching branch only`() {
        val success: AppResult<String> = AppResult.Success("ok")
        val failure: AppResult<String> = AppResult.Failure(AppError.Unknown("nope"))
        val seen = mutableListOf<String>()

        success.onSuccess { seen += "s:$it" }.onFailure { seen += "f:${it.message}" }
        failure.onSuccess { seen += "s:$it" }.onFailure { seen += "f:${it.message}" }

        assertEquals(listOf("s:ok", "f:nope"), seen)
        assertEquals(2, success.fold({ it.length }, { -1 }))
        assertEquals(-1, failure.fold({ it.length }, { -1 }))
    }
}
