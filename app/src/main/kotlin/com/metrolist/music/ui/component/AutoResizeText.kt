/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * From https://stackoverflow.com/a/69780826
 */
@Composable
fun AutoResizeText(
    text: String,
    fontSizeRange: FontSizeRange,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    var fontSizeValue by remember(text, fontSizeRange) { mutableFloatStateOf(fontSizeRange.max.value) }
    var lowerFontSizeValue by remember(text, fontSizeRange) { mutableFloatStateOf(fontSizeRange.min.value) }
    var upperFontSizeValue by remember(text, fontSizeRange) { mutableFloatStateOf(fontSizeRange.max.value) }
    var readyToDraw by remember(text, fontSizeRange) { mutableStateOf(false) }
    // Keep the style's line-height ratio while the font size shrinks; otherwise a
    // large font keeps the original small line box and gets clipped or overlaps.
    val baseFontSize = style.fontSize.value.takeIf { it > 0f }
    val resolvedLineHeight = when {
        lineHeight != TextUnit.Unspecified -> lineHeight
        baseFontSize == null || style.lineHeight == TextUnit.Unspecified -> TextUnit.Unspecified
        else -> style.lineHeight * (fontSizeValue / baseFontSize)
    }

    Text(
        text = text,
        color = color,
        maxLines = maxLines,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = resolvedLineHeight,
        overflow = overflow,
        softWrap = softWrap,
        style = style,
        fontSize = fontSizeValue.sp,
        onTextLayout = {
            if (!readyToDraw) {
                val step = fontSizeRange.step.value
                if (it.didOverflowHeight || it.didOverflowWidth) {
                    upperFontSizeValue = fontSizeValue
                    if (fontSizeValue - lowerFontSizeValue <= step) {
                        // Settle at the largest size known to fit; when nothing above the
                        // phone baseline fits, lowerFontSizeValue is the baseline itself.
                        fontSizeValue = lowerFontSizeValue
                        readyToDraw = true
                    } else {
                        fontSizeValue = (lowerFontSizeValue + fontSizeValue) / 2f
                    }
                } else {
                    lowerFontSizeValue = fontSizeValue
                    if (upperFontSizeValue - fontSizeValue <= step) {
                        readyToDraw = true
                    } else {
                        fontSizeValue = (fontSizeValue + upperFontSizeValue) / 2f
                    }
                }
            }
        },
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
    )
}

data class FontSizeRange(
    val min: TextUnit,
    val max: TextUnit,
    val step: TextUnit = DEFAULT_TEXT_STEP,
) {
    init {
        require(min <= max) { "min should be less than or equal to max, $this" }
        require(step.value > 0) { "step should be greater than 0, $this" }
    }

    companion object {
        private val DEFAULT_TEXT_STEP = 1.sp
    }
}
