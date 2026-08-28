package com.thelightphone.lp3Keyboard.ui.viewmodel

import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.LayoutOptions
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardCallback
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardSwipeCallback
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.layout.Layout
import kotlinx.coroutines.flow.StateFlow

interface Lp3KeyboardViewModel<SwipeResultType> : Lp3KeyboardCallback, Lp3KeyboardSwipeCallback<SwipeResultType> {
    val layoutFlow: StateFlow<Layout>
    val keyboardOptionsFlow: StateFlow<KeyboardOptions>
    val layoutOptionsFlow: StateFlow<LayoutOptions>
    val dictationStateFlow: StateFlow<DictationState>
    val suggestionsFlow: StateFlow<List<String>>
    val clipboardVisibleFlow: StateFlow<Boolean>
    val clipboardItemsFlow: StateFlow<List<String>>
    fun cancelHeldKeys()

    /** Called by the IME after each character to handle system-requested caps. */
    fun setCapsMode(enabled: Boolean)

    fun setDictationState(state: DictationState)
    fun setSuggestions(suggestions: List<String>)
    fun onSuggestionSelected(suggestion: String)

    /** Called by the IME whenever the underlying system clipboard changes. */
    fun setClipboardItems(items: List<String>)
    fun showClipboard()
    fun hideClipboard()
    fun onClipboardItemSelected(item: String)

    /**
     * Called by the IME when the focused field's EditorInfo declares a
     * numeric input class (or stops), to switch to/from a dedicated numeric
     * keypad rather than the normal alphabet layout.
     */
    fun setNumericPadActive(active: Boolean)
}

val defaultEmojis = listOf(
    "😅",
    "☺️",
    "🙃",
    "😍",
    "😜",
    "😂",
    "😭",
    "😎",
    "🙌",
    "👍",
    "👎",
    "🤞",
    "✌️",
    "👌",
    "👋",
    "🙏",
    "✨",
    "🔥",
    "❤️",
    "💔",
    "🏆",
    "🎯",
    "👑",
    "👀"
).map { it.codePointAt(0) }

enum class CapsMode { Off, Single, Locked }

interface Lp3RepeatableKeyboardCallback : Lp3KeyboardCallback {
    fun onKeyRepeated(code: Int)
    fun onSpecialKeyRepeated(specialKey: SpecialKey)
}
