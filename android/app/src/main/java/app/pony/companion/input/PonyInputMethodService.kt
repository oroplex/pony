package app.pony.companion.input

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.pony.companion.a11y.TextEntry

/**
 * A real input method. Text is committed with [InputConnection.commitText],
 * which is the path Samsung Notes accepts. Pony is not the default keyboard.
 * The owner enables it and switches to it; after a prompted type, Pony switches back.
 */
class PonyInputMethodService : InputMethodService() {
    private var passwordField = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        passwordField = TextEntry.isPasswordField(false, attribute?.inputType ?: 0)
        ImeBridge.onStart(passwordField, attribute?.packageName)
        // Show the keys only when a type command asked for them. Asking on every
        // focus fights the accessibility service and the keyboard flaps.
        if (!passwordField && ImeBridge.wantsKeyboard && !isInputViewShown) requestShowSelf(0)
    }

    override fun onEvaluateInputViewShown(): Boolean = !passwordField

    override fun onFinishInput() {
        passwordField = false
        ImeBridge.onStop()
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF6C4DF6.toInt())
        }
        column.addView(TextView(this).apply {
            text = if (passwordField) {
                "Password field. Pony will not type here."
            } else {
                "Pony keyboard. Your assistant types into this field. This does not replace your keyboard."
            }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        })
        column.addView(Button(this).apply {
            text = "Use my keyboard"
            setOnClickListener { switchToPreviousInputMethod() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (12 * density).toInt(); gravity = Gravity.START }
        })
        return column
    }

    private fun commitOnServiceThread(text: String): Boolean {
        if (passwordField) return false
        if (text.isEmpty()) return true
        if (!isInputViewShown) requestShowSelf(0)
        val connection: InputConnection = currentInputConnection ?: return false
        connection.beginBatchEdit()
        return try {
            connection.finishComposingText()
            if (connection.commitText(text, 1)) {
                true
            } else {
                // A few Samsung editors ignore commitText and accept a composing span.
                connection.setComposingText(text, 1) && connection.finishComposingText()
            }
        } finally {
            connection.endBatchEdit()
        }
    }

    companion object {
        fun commitFromMain(text: String): Boolean {
            val service = instance ?: return false
            return service.commitOnServiceThread(text)
        }

        fun switchBackFromMain() {
            instance?.switchToPreviousInputMethod()
        }

        private val instance: PonyInputMethodService?
            get() = if (ImeBridge.active) current else null

        @Volatile
        private var current: PonyInputMethodService? = null
    }

    override fun onCreate() {
        super.onCreate()
        current = this
    }

    override fun onDestroy() {
        if (current === this) current = null
        ImeBridge.onStop()
        super.onDestroy()
    }
}
