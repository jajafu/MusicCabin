package com.metrolist.music.tv

import com.metrolist.music.photo.FrameError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FrameErrorGraceTest {
    @Test
    fun `unreadable error shows only after persisting through the grace window`() = runBlocking {
        val errors = MutableStateFlow<FrameError?>(null)
        val grace = CompletableDeferred<Unit>()
        var enteredGrace = 0
        val shown = mutableListOf<FrameError?>()
        val job = launch {
            errors.shownAfterGrace(5_000, wait = { enteredGrace++; grace.await() })
                .collect { shown += it }
        }
        withTimeout(5_000) { while (shown.isEmpty()) yield() }
        assertEquals(listOf<FrameError?>(null), shown)

        errors.value = FrameError.UNREADABLE
        withTimeout(5_000) { while (enteredGrace == 0) yield() }
        assertTrue(shown.none { it == FrameError.UNREADABLE })

        grace.complete(Unit)
        withTimeout(5_000) { while (shown.lastOrNull() != FrameError.UNREADABLE) yield() }
        job.cancel()
    }

    @Test
    fun `unreadable error cleared during the grace window never shows`() = runBlocking {
        val errors = MutableStateFlow<FrameError?>(null)
        val grace = CompletableDeferred<Unit>()
        var enteredGrace = 0
        val shown = mutableListOf<FrameError?>()
        val job = launch {
            errors.shownAfterGrace(5_000, wait = { enteredGrace++; grace.await() })
                .collect { shown += it }
        }
        withTimeout(5_000) { while (shown.isEmpty()) yield() }
        errors.value = FrameError.UNREADABLE
        withTimeout(5_000) { while (enteredGrace == 0) yield() }
        errors.value = null
        withTimeout(5_000) { while (shown.size < 2) yield() }
        assertEquals(listOf<FrameError?>(null, null), shown)
        job.cancel()
    }

    @Test
    fun `unreadable error replaced during the grace window never shows`() = runBlocking {
        val errors = MutableStateFlow<FrameError?>(null)
        val grace = CompletableDeferred<Unit>()
        var enteredGrace = 0
        val shown = mutableListOf<FrameError?>()
        val job = launch {
            errors.shownAfterGrace(5_000, wait = { enteredGrace++; grace.await() })
                .collect { shown += it }
        }
        withTimeout(5_000) { while (shown.isEmpty()) yield() }
        errors.value = FrameError.UNREADABLE
        withTimeout(5_000) { while (enteredGrace == 0) yield() }
        errors.value = FrameError.STORAGE
        withTimeout(5_000) { while (shown.lastOrNull() != FrameError.STORAGE) yield() }
        assertEquals(listOf<FrameError?>(null, FrameError.STORAGE), shown)
        job.cancel()
    }

    @Test
    fun `other errors pass through immediately`() = runBlocking {
        val errors = MutableStateFlow<FrameError?>(null)
        val shown = mutableListOf<FrameError?>()
        val job = launch {
            errors.shownAfterGrace(5_000, wait = { fail("UNREADABLE-only wait must not run") })
                .collect { shown += it }
        }
        withTimeout(5_000) { while (shown.isEmpty()) yield() }
        errors.value = FrameError.STORAGE
        withTimeout(5_000) { while (shown.lastOrNull() != FrameError.STORAGE) yield() }
        job.cancel()
    }
}
