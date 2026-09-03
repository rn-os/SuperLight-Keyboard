package com.thelightphone.lp3Keyboard.ui.layout

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thelightphone.lp3Keyboard.ui.DefaultRow
import com.thelightphone.lp3Keyboard.ui.FinalRow
import com.thelightphone.lp3Keyboard.ui.FirstRow
import com.thelightphone.lp3Keyboard.ui.IconKey
import com.thelightphone.lp3Keyboard.ui.Key
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.LP3_KEYBOARD_HEIGHT_DP
import com.thelightphone.lp3Keyboard.ui.LocalKeyEdgeExtend
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardCallback
import com.thelightphone.lp3Keyboard.ui.MEDIUM_KEY_WIDTH_DP
import com.thelightphone.lp3Keyboard.ui.MultiLabelKey
import com.thelightphone.lp3Keyboard.ui.R
import com.thelightphone.lp3Keyboard.ui.SecondRow
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.ThirdRow

/** Layouts and data generally shared across English keyboards. */
object EnShared {
    object NumberLayout : Layout {
        @Composable
        override fun ColumnScope.Render(
            options: KeyboardOptions,
            callback: Lp3KeyboardCallback
        ) {
            FirstRow("1234567890", callback, swipeConfig, options.enableKeyAnimation)
            SecondRow("-/:;()$&@\"", callback, swipeConfig, options.enableKeyAnimation)
            ThirdRow(".,?!'", callback, swipeConfig, options) {
                MultiLabelKey("#+=", SpecialKey.Symbols, callback, options.enableKeyAnimation)
            }
            FinalRow(options, callback) {
                MultiLabelKey("ABC", SpecialKey.Letters, callback, options.enableKeyAnimation)
            }
        }
    }

    /**
     * A dedicated 3x4 numeric keypad. Shown automatically by the IME when the
     * focused field's EditorInfo declares a numeric input class, or manually
     * by long-pressing the "123" key - not every field that wants numbers
     * declares itself that way, so this is also a deliberate escape hatch.
     * Not a root layout (like Numbers/Symbols/Emoji): the dismiss chevron
     * returns to the alphabet on the first press rather than hiding the
     * keyboard, and it also keeps its own "ABC" key for the same exit.
     */
    object NumberPadLayout : Layout {
        @Composable
        override fun ColumnScope.Render(
            options: KeyboardOptions,
            callback: Lp3KeyboardCallback
        ) {
            val rowHeight = (LP3_KEYBOARD_HEIGHT_DP / 4).dp
            val keyWidth = 116.dp

            DefaultRow(height = rowHeight) {
                CompositionLocalProvider(LocalKeyEdgeExtend provides Alignment.Start) {
                    Key('1'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                }
                Key('2'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                CompositionLocalProvider(LocalKeyEdgeExtend provides Alignment.End) {
                    Key('3'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                }
            }
            DefaultRow(height = rowHeight) {
                CompositionLocalProvider(LocalKeyEdgeExtend provides Alignment.Start) {
                    Key('4'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                }
                Key('5'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                CompositionLocalProvider(LocalKeyEdgeExtend provides Alignment.End) {
                    Key('6'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                }
            }
            DefaultRow(height = rowHeight) {
                CompositionLocalProvider(LocalKeyEdgeExtend provides Alignment.Start) {
                    Key('7'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                }
                Key('8'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                CompositionLocalProvider(LocalKeyEdgeExtend provides Alignment.End) {
                    Key('9'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                }
            }
            DefaultRow(height = rowHeight) {
                CompositionLocalProvider(LocalKeyEdgeExtend provides Alignment.Start) {
                    MultiLabelKey(
                        "ABC",
                        SpecialKey.Letters,
                        callback,
                        options.enableKeyAnimation,
                        width = keyWidth
                    )
                }
                Key('0'.code, callback, swipeConfig, options.enableKeyAnimation, width = keyWidth)
                CompositionLocalProvider(LocalKeyEdgeExtend provides Alignment.End) {
                    IconKey(
                        R.drawable.back_lp3,
                        SpecialKey.Backspace,
                        callback,
                        options.enableKeyAnimation,
                        width = keyWidth,
                        // The icon's own intrinsic size (19x32dp) reads as
                        // oversized next to the 25sp digit glyphs here - scale
                        // it down to match the weight of the digits above it.
                        iconModifier = Modifier.size(13.dp, 22.dp)
                    )
                }
            }
        }
    }

    object SymbolsLayout : Layout {
        @Composable
        override fun ColumnScope.Render(
            options: KeyboardOptions,
            callback: Lp3KeyboardCallback
        ) {
            FirstRow("[]{}#%^*+=", callback, swipeConfig, options.enableKeyAnimation)
            SecondRow("_\\|~<>€£¥", callback, swipeConfig, options.enableKeyAnimation)
            ThirdRow(".,?!'", callback, swipeConfig, options) {
                MultiLabelKey("123", SpecialKey.Numbers, callback, options.enableKeyAnimation)
            }
            FinalRow(options, callback) {
                MultiLabelKey("ABC", SpecialKey.Letters, callback, options.enableKeyAnimation)
            }
        }
    }

    object EmojiLayout : Layout {
        @Composable
        override fun ColumnScope.Render(
            options: KeyboardOptions,
            callback: Lp3KeyboardCallback
        ) {
            // current layout supports 3 rows of 8
            val emojiRows = options.emojis?.chunked(8)?.take(3) ?: return
            for (row in emojiRows) {
                DefaultRow {
                    for (emoji in row) {
                        Key(
                            emoji,
                            callback,
                            swipeConfig,
                            options.enableKeyAnimation,
                            width = MEDIUM_KEY_WIDTH_DP.dp
                        )
                    }
                }
            }
        }
    }

    class ExtendedCharKeyboard(rootCode: Int) : Layout {
        private val rows = extendedCharMapping[rootCode]

        @Composable
        override fun ColumnScope.Render(
            options: KeyboardOptions,
            callback: Lp3KeyboardCallback
        ) {
            rows?.forEach { rowKeys ->
                DefaultRow {
                    for (char in rowKeys) {
                        Key(
                            char.code,
                            callback,
                            swipeConfig,
                            options.enableKeyAnimation,
                            width = MEDIUM_KEY_WIDTH_DP.dp
                        )
                    }
                }
            }
        }
    }

    val extendedCharMapping = mapOf(
        'A'.code to listOf(
            listOf('À', 'Á', 'Â', 'Ä', 'Æ'),
            listOf('Ã', 'Å', 'Ā', 'Ă', 'Ą'),
        ),
        'a'.code to listOf(
            listOf('à', 'á', 'â', 'ä', 'æ'),
            listOf('ã', 'å', 'ā', 'ă', 'ą'),
        ),
        'C'.code to listOf(
            listOf('Ç', 'Ć', 'Č'),
        ),
        'c'.code to listOf(
            listOf('ç', 'ć', 'č'),
        ),
        'E'.code to listOf(
            listOf('È', 'É', 'Ê', 'Ë', 'Ē', 'Ė', 'Ę'),
        ),
        'e'.code to listOf(
            listOf('è', 'é', 'ê', 'ë', 'ē', 'ė', 'ę'),
        ),
        'I'.code to listOf(
            listOf('Î', 'Ï', 'Í', 'Ī', 'Į', 'Ì'),
        ),
        'i'.code to listOf(
            listOf('î', 'ï', 'í', 'ī', 'į', 'ì'),
        ),
        'L'.code to listOf(
            listOf('Ł'),
        ),
        'l'.code to listOf(
            listOf('ł'),
        ),
        'N'.code to listOf(
            listOf('Ñ', 'Ń'),
        ),
        'n'.code to listOf(
            listOf('ñ', 'ń'),
        ),
        'O'.code to listOf(
            listOf('Ô', 'Ö', 'Ò', 'Ó', 'Œ', 'Ø', 'Ō', 'Õ'),
        ),
        'o'.code to listOf(
            listOf('ô', 'ö', 'ò', 'ó', 'œ', 'ø', 'ō', 'õ'),
        ),
        'S'.code to listOf(
            listOf('ẞ', 'Ś', 'Š'),
        ),
        's'.code to listOf(
            listOf('ß', 'ś', 'š'),
        ),
        'U'.code to listOf(
            listOf('Û', 'Ü', 'Ù', 'Ú', 'Ū'),
        ),
        'u'.code to listOf(
            listOf('û', 'ü', 'ù', 'ú', 'ū'),
        ),
        'Y'.code to listOf(
            listOf('Ÿ'),
        ),
        'y'.code to listOf(
            listOf('ÿ'),
        ),
        'Z'.code to listOf(
            listOf('Ž', 'Ź', 'Ż'),
        ),
        'z'.code to listOf(
            listOf('ž', 'ź', 'ż'),
        ),
    )
}
