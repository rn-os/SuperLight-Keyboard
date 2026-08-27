package com.thelightphone.lp3Keyboard.ui.viewmodel

sealed class DictationState {
    object Idle : DictationState()
    object Loading : DictationState()
    data class Listening(val partialText: String = "") : DictationState()
    data class Error(val message: String) : DictationState()
}
