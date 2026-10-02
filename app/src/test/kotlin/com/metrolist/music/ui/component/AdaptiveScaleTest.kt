package com.metrolist.music.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveScaleTest {
    @Test
    fun `phone baseline stays at one regardless of selected limit`() {
        assertEquals(1f, adaptiveUiScale(360f, maxScale = 3f), 0.001f)
    }

    @Test
    fun `720dp short edge reaches selected scale including fractional values`() {
        assertEquals(2f, adaptiveUiScale(720f, maxScale = 2f), 0.001f)
        assertEquals(2.5f, adaptiveUiScale(720f, maxScale = 2.5f), 0.001f)
        assertEquals(3f, adaptiveUiScale(720f, maxScale = 3f), 0.001f)
    }

    @Test
    fun `scale grows continuously from the phone baseline and stops at the limit`() {
        assertEquals(2f, adaptiveUiScale(540f, maxScale = 3f), 0.001f)
        assertEquals(3f, adaptiveUiScale(900f, maxScale = 3f), 0.001f)
        assertEquals(1f, adaptiveUiScale(720f, maxScale = 1f), 0.001f)
    }

    @Test
    fun `legacy scale limits above the restored maximum reset to the new default`() {
        assertEquals(2f, normalizeAdaptiveScaleMax(5f), 0.001f)
        assertEquals(3f, normalizeAdaptiveScaleMax(3f), 0.001f)
    }

    @Test
    fun `unusable stored limits fall back to the default`() {
        assertEquals(2f, normalizeAdaptiveScaleMax(Float.NaN), 0.001f)
        assertEquals(2f, normalizeAdaptiveScaleMax(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(2f, normalizeAdaptiveScaleMax(Float.NEGATIVE_INFINITY), 0.001f)
        assertEquals(2f, adaptiveUiScale(720f, maxScale = Float.NaN), 0.001f)
    }

    @Test
    fun `limits below the minimum are clamped up to one`() {
        assertEquals(1f, normalizeAdaptiveScaleMax(0.5f), 0.001f)
        assertEquals(1f, normalizeAdaptiveScaleMax(0f), 0.001f)
        assertEquals(1f, adaptiveUiScale(720f, maxScale = 0.5f), 0.001f)
    }
}
