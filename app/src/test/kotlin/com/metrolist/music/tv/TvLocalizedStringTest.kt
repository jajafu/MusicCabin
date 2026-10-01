/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards TV string lookup: the formatting overload must only be used when format
 * arguments are present. Reading a template with the no-argument overload is safe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TvLocalizedStringTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `tv update strings format their arguments`() {
        assertTrue(
            tvLocalizedString(
                context,
                R.string.tv_update_downloading,
                R.string.tv_update_downloading_zh_tw,
                42,
            ).contains("42"),
        )
        assertTrue(
            tvLocalizedString(
                context,
                R.string.tv_update_failed,
                R.string.tv_update_failed_zh_tw,
                "boom",
            ).contains("boom"),
        )
    }

    @Test
    fun `format strings can be read as templates without arguments`() {
        assertTrue(
            tvLocalizedString(
                context,
                R.string.tv_update_downloading,
                R.string.tv_update_downloading_zh_tw,
            ).contains("%1\$d"),
        )
        assertTrue(
            tvLocalizedString(
                context,
                R.string.tv_update_failed,
                R.string.tv_update_failed_zh_tw,
            ).contains("%1\$s"),
        )
    }

    @Test
    fun `check failure string accepts the exception message`() {
        val template = context.getString(R.string.failed_to_check_updates)
        assertTrue(template.contains("%s"))
        assertTrue(String.format(template, "boom").contains("boom"))
    }

    @Test
    fun `update page strings without format specifiers read as plain text`() {
        assertTrue(
            tvLocalizedString(
                context,
                R.string.tv_update_no_apk,
                R.string.tv_update_no_apk_zh_tw,
            ).isNotBlank(),
        )
    }
}
