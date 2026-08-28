package com.thelightphone.lp3keyboard

import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextServicesManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardSwipeCallback
import com.thelightphone.lp3Keyboard.ui.Lp3KeyboardView
import com.thelightphone.lp3Keyboard.ui.SpecialKey
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.layout.LayoutRegistryItem
import com.thelightphone.lp3Keyboard.ui.layout.buildRootViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.DictationState
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3KeyboardViewModel
import com.thelightphone.lp3Keyboard.ui.viewmodel.Lp3RepeatableKeyboardCallback
import com.thelightphone.lp3Keyboard.ui.viewmodel.defaultEmojis
import com.thelightphone.lp3keyboard.voice.VoiceDictation
import com.thelightphone.lp3keyboard.voice.VoiceModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import android.Manifest
import android.content.Intent
import java.util.Locale

private const val MAX_CLIPBOARD_HISTORY = 5

class IMEService : LifecycleInputMethodService(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    Lp3RepeatableKeyboardCallback,
    SpellCheckerSessionListener {

    private var renderedLayout: LayoutRegistryItem? = null
    private var viewModel: Lp3KeyboardViewModel<*>? = null
    private var voice: VoiceDictation? = null
    private var spell: SpellCheckerSession? = null

    private val corrections = mutableMapOf<String, String>()
    private val suggestionsMap = mutableMapOf<String, List<String>>()
    private val pending = mutableMapOf<Int, String>()
    private var seq = 0

    private var undoFrom = ""
    private var undoTo = ""
    private var lateWord = ""
    private var lateTerminator = ""
    private var selectedWord = ""
    private var composingWord = ""

    private var lastSpaceTime = 0L
    private var clipboardOverlayVisible = false

    private var clipboardManager: ClipboardManager? = null
    private val clipboardHistory = ArrayDeque<String>()
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { refreshClipboardHistory() }

    private fun refreshClipboardHistory() {
        if (!LayoutPreferences.isClipboardEnabled(this)) return
        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        clipboardHistory.remove(text)
        clipboardHistory.addFirst(text)
        while (clipboardHistory.size > MAX_CLIPBOARD_HISTORY) {
            clipboardHistory.removeLast()
        }
        viewModel?.setClipboardItems(clipboardHistory.toList())
    }

    private fun onClipboardEnabledChanged() {
        if (LayoutPreferences.isClipboardEnabled(this)) {
            refreshClipboardHistory()
            return
        }
        viewModel?.hideClipboard()
        clipboardHistory.clear()
        viewModel?.setClipboardItems(emptyList())
    }

    private var layoutPrefs: SharedPreferences? = null
    private val layoutChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                LayoutPreferences.KEY_ACTIVE_LAYOUT -> refreshLayoutIfNeeded()
                LayoutPreferences.KEY_VOICE_ENABLED -> voice?.prepare()
                LayoutPreferences.KEY_AUTOCORRECT_ENABLED -> initSpell()
                LayoutPreferences.KEY_AUTO_CAPITALIZE_ENABLED -> updateCapsMode()
                LayoutPreferences.KEY_CLIPBOARD_ENABLED -> onClipboardEnabledChanged()
            }
        }

    private fun refreshLayoutIfNeeded() {
        if (LayoutPreferences.getActiveLayout(this) != renderedLayout) {
            setInputView(onCreateInputView())
        }
    }

    private fun buildViewModel(layout: LayoutRegistryItem): Lp3KeyboardViewModel<*> {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val dummySwipeCallback = object : Lp3KeyboardSwipeCallback<Unit> {}
                return layout.buildRootViewModel(
                    this@IMEService,
                    dummySwipeCallback,
                    haptic = ::tick
                ) as T
            }
        }
        // Key by the layout's uniqueId so each layout gets its own retained ViewModel instance.
        val vm = ViewModelProvider(store, factory)[layout.uniqueId, ViewModel::class.java]
                as Lp3KeyboardViewModel<*>

        // Listen for dictation state changes from the UI
        vm.dictationStateFlow.onEach { state ->
            if (state is DictationState.Idle) {
                stopVoice()
            }
        }.launchIn(lifecycleScope)

        vm.clipboardVisibleFlow.onEach { visible ->
            clipboardOverlayVisible = visible
        }.launchIn(lifecycleScope)
        vm.setClipboardItems(clipboardHistory.toList())

        return vm
    }

    override fun onCreateInputView(): View {
        val layout = LayoutPreferences.getActiveLayout(this)
        val vm = buildViewModel(layout)
        renderedLayout = layout
        viewModel = vm

        val view = Lp3KeyboardView(
            context = this,
            viewModel = vm,
            // don't need to remap since no external keyboard
            remapKeyCode = null
        ).apply {
            // don't need the keyboard view itself ot handle external keys, Android inputs will do it
            handleHardwareKeyboardInput = false
        }
        setCandidatesViewShown(false)
        window?.window?.let {
            it.decorView.apply {
                setViewTreeLifecycleOwner(this@IMEService)
                setViewTreeViewModelStoreOwner(this@IMEService)
                setViewTreeSavedStateRegistryOwner(this@IMEService)
            }
        }
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshLayoutIfNeeded()
        initSpell()
        undoFrom = ""
        undoTo = ""
        lateWord = ""
        lateTerminator = ""
        composingWord = ""
        corrections.clear()
        suggestionsMap.clear()
        pending.clear()
        viewModel?.setSuggestions(emptyList())
        viewModel?.hideClipboard()
    }

    private fun initSpell() {
        if (!LayoutPreferences.isAutocorrectEnabled(this)) {
            spell?.close()
            spell = null
            return
        }
        if (spell != null) return
        val tsm = getSystemService(TextServicesManager::class.java) ?: return
        spell = tsm.newSpellCheckerSession(null, Locale.getDefault(), this, false)
    }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
        results?.forEach { si ->
            val word = pending.remove(si.cookie) ?: return@forEach
            val fix = pickFix(si)
            if (fix != null) {
                corrections[word] = fix
            }
            
            val list = mutableListOf<String>()
            for (i in 0 until si.suggestionsCount) {
                list.add(si.getSuggestionAt(i))
            }
            if (list.isNotEmpty()) {
                suggestionsMap[word.lowercase(Locale.getDefault())] = list
            }

            if (word == lateWord && fix != null) {
                applyLateFix(word, fix, lateTerminator)
            }
            
            // Only show suggestions overlay if the selected word is actually a typo
            if (word.equals(selectedWord, ignoreCase = true) && fix != null) {
                viewModel?.setSuggestions(list)
            }
        }
    }

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
    }

    private fun pickFix(si: SuggestionsInfo): String? {
        val attr = si.suggestionsAttributes
        val typo = (attr and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0
        val inDict = (attr and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0
        if (!typo || inDict || si.suggestionsCount <= 0) return null
        return si.getSuggestionAt(0)
    }

    private fun applyLateFix(word: String, fix: String, terminator: String) {
        val ic = currentInputConnection ?: return
        val expected = word + terminator
        val actual = ic.getTextBeforeCursor(expected.length, 0)
        if (actual == expected) {
            val corrected = applyCase(word, fix)
            ic.deleteSurroundingText(expected.length, 0)
            ic.commitText(corrected + terminator, 1)
            undoFrom = corrected
            undoTo = word
        }
        lateWord = ""
        lateTerminator = ""
    }

    private fun applyCase(original: String, fix: String): String {
        return when {
            original.all { it.isUpperCase() } -> fix.uppercase(Locale.getDefault())
            original.firstOrNull()?.isUpperCase() == true -> fix.replaceFirstChar { it.uppercaseChar() }
            else -> fix.lowercase(Locale.getDefault())
        }
    }

    private fun requestCheck(word: String) {
        val s = spell ?: return
        if (word.length < 2 || word.length > 32 || word.any { it.isDigit() }) return
        // Ignore acronyms (mid-word caps)
        if (word.drop(1).any { it.isUpperCase() }) return

        val c = ++seq
        pending[c] = word
        s.getSuggestions(android.view.textservice.TextInfo(word, c, c), 3)
    }

    private fun trailingWord(): String {
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(50, 0) ?: return ""
        // Trim trailing non-letters to find the word being typed
        val lastWord = before.trimEnd { !it.isLetter() }.split(Regex("[^\\p{L}]")).lastOrNull() ?: ""
        return lastWord
    }

    /**
     * The return key doubles as "Enter" (submit the field's IME action, e.g.
     * search/send/go/next/done) and "Return" (insert a newline) depending on
     * what the focused field asked for - matching every other Android
     * keyboard rather than always inserting a literal newline.
     */
    private fun performReturnAction() {
        val ei = currentInputEditorInfo
        val actionId = (ei?.imeOptions ?: EditorInfo.IME_ACTION_NONE) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val hasSubmitAction = actionId != EditorInfo.IME_ACTION_NONE &&
            actionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
            !noEnterAction

        if (hasSubmitAction) {
            currentInputConnection?.finishComposingText()
            composingWord = ""
            currentInputConnection?.performEditorAction(actionId)
        } else {
            attemptAutocorrect("\n")
        }
    }

    private fun attemptAutocorrect(terminator: String) {
        val ic = currentInputConnection ?: return
        if (!LayoutPreferences.isAutocorrectEnabled(this)) {
            ic.commitText(terminator, 1)
            updateCapsMode()
            return
        }
        val word = trailingWord()
        if (word.isEmpty()) {
            ic.commitText(terminator, 1)
            updateCapsMode()
            return
        }

        val fix = corrections[word]
        if (fix != null) {
            val corrected = applyCase(word, fix)
            ic.deleteSurroundingText(word.length, 0)
            ic.commitText(corrected + terminator, 1)
            undoFrom = corrected
            undoTo = word
        } else {
            lateWord = word
            lateTerminator = terminator
            ic.commitText(terminator, 1)
        }
        updateCapsMode()
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        layoutPrefs = LayoutPreferences.registerOnChange(this, layoutChangeListener)
        voice = VoiceDictation(this).apply { prepare() }
        clipboardManager = getSystemService(ClipboardManager::class.java)?.also {
            it.addPrimaryClipChangedListener(clipboardListener)
        }
        refreshClipboardHistory()
    }

    override fun onDestroy() {
        voice?.destroy()
        layoutPrefs?.unregisterOnSharedPreferenceChangeListener(layoutChangeListener)
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        store.clear()
        super.onDestroy()
    }

    override val viewModelStore: ViewModelStore
        get() = store
    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    private val store = ViewModelStore()
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }

    private fun tick() {
        // 50ms feels good on LP3, other device motors may allow faster buzz
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onWindowHidden() {
        super.onWindowHidden()
        viewModel?.cancelHeldKeys()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        updateCapsMode()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateCapsMode()
        
        // If the cursor moved away from our composing region, reset it
        if (composingWord.isNotEmpty() && candidatesStart == -1) {
            composingWord = ""
        }

        // Only show suggestions if we are NOT currently typing a word (composing)
        if (composingWord.isNotEmpty()) {
            selectedWord = ""
            viewModel?.setSuggestions(emptyList())
            return
        }

        // If selection is a word, show suggestions
        if (newSelStart != newSelEnd) {
            val ic = currentInputConnection ?: return
            val selection = ic.getSelectedText(0)?.toString()?.trim()
            if (!selection.isNullOrBlank() && !selection.contains(" ")) {
                selectedWord = selection
                val list = suggestionsMap[selection.lowercase(Locale.getDefault())]
                val isTypo = corrections.containsKey(selection)
                
                if (!list.isNullOrEmpty() && isTypo) {
                    viewModel?.setSuggestions(list)
                } else {
                    // Check if it's a typo before showing the overlay
                    requestCheck(selection)
                }
            } else {
                selectedWord = ""
            }
        } else {
            selectedWord = ""
            viewModel?.setSuggestions(emptyList())
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    private fun updateCapsMode() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return

        if (!LayoutPreferences.isAutoCapitalizeEnabled(this)) {
            viewModel?.setCapsMode(false)
            return
        }

        // might be set if the TextField is set to capitalize sentence starts, for example
        val caps = ic.getCursorCapsMode(ei.inputType)
        viewModel?.setCapsMode(caps != 0)
    }

    override fun onKeyPressed(code: Int) {
        undoFrom = ""
        viewModel?.setSuggestions(emptyList())
        viewModel?.hideClipboard()
    }

    override fun onSubmitWord(word: CharSequence) {
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        composingWord = ""
        val selection = ic.getSelectedText(0)
        if (!selection.isNullOrEmpty()) {
            ic.commitText(word, 1)
        } else {
            ic.commitText("$word ", 1)
        }
        undoFrom = ""
        viewModel?.setSuggestions(emptyList())
    }

    override fun onPasteText(text: CharSequence) {
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        composingWord = ""
        ic.commitText(text, 1)
        undoFrom = ""
        viewModel?.hideClipboard()
    }

    override fun onSpecialKeyPressed(key: SpecialKey) {
        if (key != SpecialKey.Backspace) undoFrom = ""
        viewModel?.setSuggestions(emptyList())
        viewModel?.hideClipboard()
        when (key) {
            SpecialKey.Space -> {
                currentInputConnection?.finishComposingText()
                composingWord = ""
                if (attemptAutoPeriod()) return
                attemptAutocorrect(" ")
            }

            else -> {}
        }
    }

    private fun attemptAutoPeriod(): Boolean {
        if (!LayoutPreferences.isAutoPeriodEnabled(this)) return false
        val now = System.currentTimeMillis()
        val ic = currentInputConnection ?: return false
        
        // Double tap within 300ms
        if (now - lastSpaceTime < 300) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before?.length == 1 && before[0] == ' ') {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceTime = 0L
                updateCapsMode()
                return true
            }
        }
        
        lastSpaceTime = now
        return false
    }

    override fun onKeyReleased(code: Int) {
        val ic = currentInputConnection ?: return
        val text = buildString { appendCodePoint(code) }
        val terminators = ".,!?;:)"
        if (terminators.contains(text)) {
            ic.finishComposingText()
            composingWord = ""
            attemptAutocorrect(text)
        } else {
            composingWord += text
            ic.setComposingText(composingWord, 1)
            requestCheck(composingWord)
        }
        updateCapsMode()
    }

    override fun onSpecialKeyReleased(key: SpecialKey) {
        when (key) {
            SpecialKey.Backspace -> {
                val ic = currentInputConnection ?: return
                if (undoFrom.isNotEmpty()) {
                    val actual = ic.getTextBeforeCursor(undoFrom.length, 0)
                    if (actual == undoFrom) {
                        ic.deleteSurroundingText(undoFrom.length, 0)
                        ic.commitText(undoTo, 1)
                        undoFrom = ""
                        return
                    }
                }
                undoFrom = ""
                
                if (composingWord.isNotEmpty()) {
                    composingWord = composingWord.dropLast(1)
                    // deleteSurroundingText is defined to leave composing-region
                    // text alone, so it can't be relied on to remove the
                    // character while it's still marked as composing. Committing
                    // it as plain text first (a no-op on the actual characters,
                    // just drops the composing/underline styling) makes the
                    // delete land regardless of how well the target field tracks
                    // composing spans - some don't, at all.
                    ic.finishComposingText()
                    ic.deleteSurroundingText(1, 0)
                    if (composingWord.isNotEmpty()) {
                        ic.setComposingText(composingWord, 1)
                    }
                } else {
                    val selection = ic.getSelectedText(0)
                    if (!selection.isNullOrEmpty()) {
                        ic.commitText("", 1)
                    } else {
                        // deleteSurroundingText, not a raw KEYCODE_DEL key event -
                        // many apps' text fields only implement InputConnection's
                        // delete methods and never handle a sent DEL key event.
                        val before = ic.getTextBeforeCursor(2, 0)
                        val deleteCount = if (before != null && before.length == 2 &&
                            Character.isSurrogatePair(before[0], before[1])
                        ) 2 else 1
                        ic.deleteSurroundingText(deleteCount, 0)
                    }
                }
                updateCapsMode()
            }

            SpecialKey.Return -> {
                performReturnAction()
            }

            SpecialKey.Close -> {
                requestHideSelf(0)
            }

            SpecialKey.Voice -> {
                onMic()
            }

            else -> {}
        }
    }

    private fun onMic() {
        if (!LayoutPreferences.isVoiceEnabled(this)) return

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this, MicPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            return
        }

        if (!VoiceModel.isInstalled(this)) {
            viewModel?.setDictationState(DictationState.Error("Voice not downloaded"))
            return
        }

        startVoice()
    }

    private fun startVoice() {
        val vm = viewModel ?: return
        val v = voice ?: return

        vm.setDictationState(DictationState.Loading)
        v.listen(
            onPartial = { partial ->
                vm.setDictationState(DictationState.Listening(partial))
            },
            onSegment = { text ->
                currentInputConnection?.commitText("$text ", 1)
                vm.setDictationState(DictationState.Listening(""))
            },
            onError = { err ->
                vm.setDictationState(DictationState.Error(err))
            }
        )
    }

    private fun stopVoice() {
        voice?.stop()
    }

    override fun onKeyLongPressed(code: Int) {
    }

    private fun deletePrecedingWord() {
        val ic = currentInputConnection ?: return
        val selection = ic.getSelectedText(0)
        if (!selection.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            // Get text before cursor to find the word boundary (max 100 chars long)
            val before = ic.getTextBeforeCursor(100, 0) ?: return
            val trimmed = before.trimEnd()
            val lastSpace = trimmed.indexOfLast { it.isWhitespace() }
            // Delete from cursor back to start of word (including trailing spaces)
            val charsToDelete = before.length - (if (lastSpace >= 0) lastSpace + 1 else 0)
            ic.deleteSurroundingText(charsToDelete, 0)
        }
        updateCapsMode()
    }

    override fun onSpecialKeyLongPressed(key: SpecialKey) {
        when (key) {
            SpecialKey.Backspace -> {
                deletePrecedingWord()
            }

            SpecialKey.Space -> {
                if (LayoutPreferences.isClipboardEnabled(this)) {
                    refreshClipboardHistory()
                    viewModel?.showClipboard()
                }
            }

            else -> {}
        }
    }

    override fun onKeyRepeated(code: Int) {
        onKeyReleased(code)
    }

    override fun onSpecialKeyRepeated(specialKey: SpecialKey) {
        when (specialKey) {
            SpecialKey.Space -> {
                // Long-pressing space opens the clipboard overlay instead of
                // repeating; don't also spam space characters into the field.
                if (clipboardOverlayVisible) return
                currentInputConnection?.commitText(" ", 1)
                updateCapsMode()
            }

            SpecialKey.Backspace -> {
                deletePrecedingWord()
            }

            else -> {}
        }
    }

    override fun onCursorMove(delta: Int) {
        val ic = currentInputConnection ?: return
        if (composingWord.isNotEmpty()) {
            ic.finishComposingText()
            composingWord = ""
        }
        val key = if (delta > 0) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT
        val count = Math.abs(delta)
        for (i in 0 until count) {
            ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
            ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
        }
    }
}
