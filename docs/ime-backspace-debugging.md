# Debugging IME backspace: what took hours, and how to skip it next time

This documents the debugging process behind the v1.2.2/v1.2.3 backspace fixes
(see the README's "Recent fixes" section for the user-facing summary). The
bug kept resurfacing in a different shape after each fix because backspace on
Android touches three separate, independently-flaky subsystems -
`InputConnection` deletion methods, composing regions, and raw key events -
and different apps support different subsets of each. Read this before
touching backspace/composing/autocorrect code again.

## The core fact that explains almost everything

**There is no single `InputConnection` method for "delete one character" that
every app implements correctly.** Concretely, across the apps tested on this
device:

| Mechanism | Native `EditText` | Compose (`OutlinedTextField`, this app's own settings) | Compose (search-as-you-type widgets, e.g. Aurora Store) | Custom fields (e.g. LightChat's `Name` field) |
|---|---|---|---|---|
| `deleteSurroundingText()` | Works | Usually works | Unreliable - sometimes a clean no-op, sometimes applies a frame late (race, see below) | Confirmed no-op, repeatedly, across multiple test builds |
| `sendKeyEvent(KEYCODE_DEL)` via `InputMethodService.sendDownUpKeyEvents()` | Works | **Silently does nothing**, even though a real hardware/`adb shell input keyevent 67` DOES work on the identical field | Unreliable, same as above | Also a no-op |
| `setComposingText()` (shrinking an active composing region) | Works | Works | Works | Untested (field never showed composing underline at all, so no active region to shrink) |

The middle two columns are the trap: `sendDownUpKeyEvents()` is *not*
equivalent to a real key press. It's tagged `KeyEvent.FLAG_SOFT_KEYBOARD` /
`KeyCharacterMap.VIRTUAL_KEYBOARD`, delivered through
`InputConnection.sendKeyEvent()`, and several Compose-based fields just don't
route it to their edit logic - while the *exact same field*, hit with
`adb shell input keyevent 67` (real system-level dispatch), deletes correctly.
This was verified directly on-device (see "How this was actually diagnosed"
below); it is not a guess from documentation.

**Conclusion:** there is no clever universal trick. Pick the mechanism that's
correct for the situation (see "Decision guide" below) and accept that some
third-party fields (LightChat's own `Name` field, as of this writing) may
simply not be fixable from the IME side.

## Timeline of what was tried and why each attempt failed

Working from `git log`, the bug came back four times in a row because each
fix addressed the *symptom* that had just been reported, not the mechanism
underneath. In order:

1. **Original code (pre-session):** `sendDownUpKeyEvents(KEYCODE_DEL)` for
   plain deletion. Symptom: does nothing in some third-party apps (the ones
   that don't route soft-keyboard key events to their edit logic).
2. **First fix attempt:** switched to `deleteSurroundingText()`. This
   *reintroduced* the exact bug it fixed for a different set of apps -
   `deleteSurroundingText` is a no-op in fields that don't implement it
   (LightChat's `Name` field, confirmed by testing before and after this
   change - zero visual difference either way).
3. **Second attempt:** `deleteSurroundingText()` first, then read the text
   back immediately to check if it changed, falling back to a key event if
   not. This looked correct in isolated testing but **introduced a race
   condition**: some fields apply `deleteSurroundingText` on a later frame,
   not synchronously. Reading the text back right away sometimes caught
   stale state, wrongly concluded the delete failed, and fired the key-event
   fallback *on top of* a delete that actually landed a moment later - a
   double delete per keystroke, compounding on fast repeated taps into what
   looked like "the cursor jumps back several characters."
4. **Third attempt (digits):** noticed the numpad specifically was affected
   worse than letters, traced it to digits being routed through the same
   composing-region (`setComposingText`) machinery as autocorrect-tracked
   letters. Numeric fields (phone numbers, dates) commonly reformat text and
   move the cursor as you type, which drifts a *tracked* composing position
   away from the field's *actual* cursor. Fix: stop tracking digits through
   composing entirely, commit them as plain text. This part held up and did
   not need revisiting.
5. **Fourth attempt (letters):** applied the same "stop using composing
   regions" logic to letters, committing each character as plain text
   instead of via `setComposingText`. This fixed backspace but **silently
   dropped the underline** that appears under a word while it's still being
   typed (a plain visual side effect of an active composing region - not
   itself a bug, but a regression a user will notice and ask about). Tried
   committing styled (`SpannableString` + `UnderlineSpan`) plain text as a
   composing-region-free way to get the underline back - **most editors
   silently drop style spans on `commitText`**, confirmed via direct
   on-device inspection. No underline appeared.
6. **Final design:** letters go back to a *real* composing region
   (`setComposingText`), which is the one thing virtually every editor
   reliably underlines - because that's the fundamental IME contract, far
   more universally honored than either deletion method above. The actual
   defect was never "composing regions are bad" - it was the specific
   **three-step sequence** used for backspace: `finishComposingText()` (turn
   the region into plain text), delete a character, then
   `setComposingText()` again to re-establish a *new* region. Some fields
   mishandle exactly that sequence (canceling the entire span when a raw
   delete arrives while composing is technically still active, or applying
   the delete against stale state). The fix: shrink the *existing* composing
   region in place with a single `setComposingText(shorterWord, 1)` call.
   No finish, no delete, no re-establish - just one call, which is also how
   every mainstream software keyboard implements this exact interaction.

## Where this landed, in the code

- [`onKeyReleased`](../app/src/main/java/com/thelightphone/lp3keyboard/IMEService.kt) -
  digits commit as plain text (no composing region, ever); letters go through
  `setComposingText` to keep the live underline.
- `onSpecialKeyReleased`'s `SpecialKey.Backspace` case - mid-word backspace
  shrinks the composing region in place (`composingWord.dropLast(1)` then one
  `setComposingText` call); backspace with no active word falls through to
  `deleteCharsBeforeCursor`.
- `deleteCharsBeforeCursor` - a single, deterministic `deleteSurroundingText`
  call. No verify-then-fallback. If you're tempted to add a fallback here
  again, read "the race condition" above first, and see "Decision guide"
  below for how to do it without reintroducing that bug.
- `deletePrecedingWord` (long-press word-delete) - finishes the composing
  region first, since `deleteSurroundingText` isn't guaranteed to reach text
  still marked as composing (this is a *different* code path from
  single-character backspace and needed its own fix).
- `onUpdateSelection` - resets `composingWord` when the field stops reporting
  the composing region as active (`candidatesStart == -1`). This check is
  only meaningful because composing regions are back in use; it was a no-op
  bug during the brief window where letters used plain `commitText` (every
  keystroke would have wrongly reset tracking, though it happened not to
  matter in practice because nothing downstream depended on it during that
  window).

## Decision guide for the next backspace/composing bug

1. **Reproduce on-device, not in an emulator or by reading code.** Every bug
   in this saga was App-specific and timing-sensitive; none of it was
   visible from static analysis alone.
2. **Identify which category of field you're dealing with** before touching
   any code:
   - Does `deleteSurroundingText` visibly work? Test by triggering backspace
     and watching the field.
   - Does the underline appear while typing? If not, either autocorrect is
     off, or the field doesn't render composing regions (rare) - check
     before assuming a code bug.
   - Is it a search-as-you-type field with its own async suggestion/history
     logic (like Aurora Store)? Those can appear to "fight" the IME by
     resetting their displayed text independent of what was actually typed -
     rule this out before assuming the keyboard is at fault.
3. **Never add a "verify then fall back" step that reads `InputConnection`
   state immediately after writing to it.** If you need this kind of
   robustness, either accept the field is simply unsupported, or defer the
   check to a later main-thread message (e.g. `Handler.post`, not
   `postDelayed`) so any pending recomposition on the app's side has a
   chance to run first - `onSpecialKeyReleased` is currently synchronous and
   assumes no such deferral, so this would be a real refactor, not a
   one-line change.
4. **Never call `finishComposingText()` immediately before touching the
   composing region again in the same handler.** If a composing region needs
   to shrink or grow, call `setComposingText()` with the new full string
   directly - don't finish-and-recreate.
5. **Digits, punctuation, and anything that shouldn't be
   autocorrected/spell-checked should stay out of the composing region
   entirely.** That's not just a backspace-safety measure; it's also a
   real category of Android IME bugs unto itself (reformatting fields
   dragging a tracked cursor position out of sync).

## How this was actually diagnosed (methodology, for next time)

The device has wireless `adb` enabled (see it with `adb devices -l`; it
reconnects on its own most of the time, but occasionally needs
`adb kill-server && adb start-server` after an idle period, or a `sleep`
retry loop). The loop that actually found each root cause:

1. `adb shell input tap <x> <y>` to drive the on-screen keyboard directly -
   coordinates were derived once from a screenshot and reused.
2. `adb exec-out screencap -p > /tmp/x.png` + read the PNG after every single
   keystroke, not just at the end - several bugs (e.g. the composing region
   getting canceled) were only visible by checking after *each* backspace
   press, not after a batch of them.
3. To isolate "is this the IME's fault or the app's own state management,"
   compare against `adb shell input keyevent <code>` (real system-level key
   dispatch, bypassing the IME). If that succeeds where the IME's
   `sendDownUpKeyEvents` fails on the identical field, that's a hard
   confirmation that soft-keyboard-tagged key events are the specific thing
   being ignored - not a general field bug.
4. Test across multiple app categories on purpose, not just one: this app's
   own Settings "Try the keyboard" preview field (clean, no personal data,
   good for screenshots), a search-as-you-type field (Aurora Store, exposed
   the async-state-fighting-the-IME issue), and a plain-looking form field in
   a first-party app (LightChat's "New Agent" screen, exposed the hard
   `deleteSurroundingText` no-op). One field is never enough to trust a fix.
5. `sips -c <h> <w>` + `sips -z <h> <w>` (both built into macOS) to crop and
   upscale a screenshot region when you need to confirm something as fine as
   "is there actually an underline under this word" - a full-resolution
   screenshot at normal viewing size hides this kind of detail.
