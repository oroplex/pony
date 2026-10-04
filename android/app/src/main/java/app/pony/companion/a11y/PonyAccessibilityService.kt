package app.pony.companion.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import app.pony.companion.display.CallGuard
import app.pony.companion.display.WinType
import app.pony.companion.display.WindowShot
import app.pony.companion.display.WindowSignal
import app.pony.companion.display.WindowStack
import app.pony.companion.input.ClipboardPreference
import app.pony.companion.input.ImeBridge
import app.pony.companion.input.ImeStatus
import app.pony.companion.overlay.AppVisibility
import app.pony.companion.overlay.OverlayHost
import app.pony.companion.session.Readiness
import app.pony.companion.session.SessionRepository
import app.pony.companion.voice.TapTarget
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PonyAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
        SessionRepository.setAccessibilityEnabled(true)
        Readiness.refresh(this)
        OverlayHost.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (onDefaultDisplay(e)) CallGuard.noteForeground(e.packageName?.toString(), e.className?.toString())
                WindowSignal.changed()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> WindowSignal.changed()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> WindowSignal.changed()
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val notice = e.parcelableData as? Notification ?: return
                val pkg = e.packageName?.toString() ?: return
                if (pkg != packageName && isCallNotice(notice)) CallGuard.noteCallNotification(pkg)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        keyboardHandler.removeCallbacks(keyboardRelease)
        runCatching { setTypingKeyboardHidden(false) }
        OverlayHost.detach(this)
        if (instance === this) instance = null
        SessionRepository.setAccessibilityEnabled(false)
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        OverlayHost.detach(this)
        if (instance === this) instance = null
        SessionRepository.setAccessibilityEnabled(false)
        return super.onUnbind(intent)
    }

    /** The UI tree as text, one block per window, topmost first. */
    fun uiTree(displayId: Int = Display.DEFAULT_DISPLAY, hidden: Set<String> = emptySet()): String {
        val windows = orderedWindows(displayId)
        if (windows.isEmpty()) {
            val root = rootInActiveWindow ?: return "(no active window)"
            return try {
                if (root.packageName?.toString() in hidden) "(call screen hidden)" else UiTreeDumper.dump(root)
            } finally {
                runCatching { root.recycle() }
            }
        }
        val out = StringBuilder()
        for (window in windows) {
            val root = runCatching { window.root }.getOrNull()
            val pkg = root?.packageName?.toString()
            try {
                if (pkg != null && pkg in hidden) {
                    out.append("window pkg=").append(pkg).append(" (call screen hidden)\n")
                    continue
                }
                val bounds = Rect().also { window.getBoundsInScreen(it) }
                out.append("window pkg=").append(pkg ?: "?")
                    .append(" layer=").append(window.layer)
                    .append(" bounds=").append(bounds.left).append(',').append(bounds.top)
                    .append(',').append(bounds.right).append(',').append(bounds.bottom)
                    .append('\n')
                val body = if (root == null) "" else UiTreeDumper.dump(root)
                if (body.isBlank() || body == "(no active window)") {
                    out.append("  (no readable nodes here — read the screenshot for this window)\n")
                } else {
                    out.append(body).append('\n')
                }
            } finally {
                runCatching { root?.recycle() }
            }
        }
        return out.toString().trimEnd().ifEmpty { "(no active window)" }
    }

    fun tap(x: Float, y: Float, displayId: Int = Display.DEFAULT_DISPLAY): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 40)
        return passingOverlay(displayId) { dispatch(gesture(displayId, stroke)) }
    }

    fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val dur = durationMs.coerceIn(50, 2000)
        val stroke = GestureDescription.StrokeDescription(path, 0, dur)
        return passingOverlay(displayId) { dispatch(gesture(displayId, stroke)) }
    }

    fun longPress(x: Float, y: Float, durationMs: Long = GestureBuilder.LONG_PRESS_MS, displayId: Int = Display.DEFAULT_DISPLAY): Boolean =
        dispatchSpecs(GestureBuilder.longPress(x, y, durationMs), displayId)

    fun drag(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long = GestureBuilder.DRAG_MS,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ): Boolean = dispatchSpecs(GestureBuilder.drag(x1, y1, x2, y2, durationMs), displayId)

    fun pinch(
        x: Float,
        y: Float,
        fromDistance: Float,
        toDistance: Float,
        durationMs: Long = GestureBuilder.PINCH_MS,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ): Boolean = dispatchSpecs(GestureBuilder.pinch(x, y, fromDistance, toDistance, durationMs), displayId)

    /** Turns pure stroke specs into a multi-stroke gesture and dispatches it with the pill held off. */
    private fun dispatchSpecs(specs: List<GestureBuilder.Stroke>, displayId: Int): Boolean {
        if (specs.isEmpty()) return false
        val strokes = specs.map { spec ->
            val path = Path()
            val first = spec.points.first()
            path.moveTo(first.x, first.y)
            spec.points.drop(1).forEach { path.lineTo(it.x, it.y) }
            GestureDescription.StrokeDescription(path, spec.startAt, spec.durationMs)
        }
        val waitMs = GestureBuilder.spanMs(specs) + 1_500L
        return passingOverlay(displayId) { dispatch(gesture(displayId, strokes), waitMs) }
    }

    fun press(key: String): Boolean {
        val action = when (key) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "enter", "search", "go", "send", "next", "done" -> return imeAction(key)
            else -> return false
        }
        return performGlobalAction(action)
    }

    /** Submit the focused field with its IME action (Search/Go/Send/Done/Next), falling back to an Enter or Search key. */
    private fun imeAction(key: String): Boolean {
        val node = UiTreeDumper.focusedEditable(rootInActiveWindow)
        if (node != null && Build.VERSION.SDK_INT >= 30) {
            val done = runCatching {
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }.getOrDefault(false)
            if (done) return true
        }
        val code = if (key == "search") KeyEvent.KEYCODE_SEARCH else KeyEvent.KEYCODE_ENTER
        return injectKey(code)
    }

    private fun injectKey(keyCode: Int): Boolean {
        return try {
            val inputManager = getSystemService(InputManager::class.java) ?: return false
            val inject = inputManager.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType,
            )
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
            val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
            (inject.invoke(inputManager, down, 0) as Boolean) && (inject.invoke(inputManager, up, 0) as Boolean)
        } catch (_: Throwable) {
            false
        }
    }

    fun setTypingKeyboardHidden(hidden: Boolean) {
        softKeyboardController.showMode = if (hidden) SHOW_MODE_HIDDEN else SHOW_MODE_AUTO
    }

    private val keyboardHandler = Handler(Looper.getMainLooper())
    private val keyboardRelease = Runnable { runCatching { setTypingKeyboardHidden(false) } }

    /**
     * Keeps the soft keyboard from jumping onto the owner's screen while the
     * assistant taps and types. It comes back [forMs] after the last command,
     * and at once whenever Pony's own screen is in front.
     */
    fun holdKeyboard(forMs: Long = KEYBOARD_HOLD_MS) {
        keyboardHandler.post {
            keyboardHandler.removeCallbacks(keyboardRelease)
            if (AppVisibility.visible.value) {
                keyboardRelease.run()
                return@post
            }
            runCatching { setTypingKeyboardHidden(true) }
            keyboardHandler.postDelayed(keyboardRelease, forMs)
        }
    }

    fun releaseKeyboard() {
        keyboardHandler.post {
            keyboardHandler.removeCallbacks(keyboardRelease)
            keyboardRelease.run()
        }
    }

    /**
     * Writes into the focused field. Default [TypeMode.INSERT] honors the caret.
     * When the Pony keyboard is not the active IME, ACTION_SET_TEXT composes
     * around the selection instead of wiping the field.
     */
    fun typeText(text: String, mode: TypeMode = TypeMode.INSERT, displayId: Int = Display.DEFAULT_DISPLAY): Result<TypedText> {
        if (displayId != Display.DEFAULT_DISPLAY) return typeOnDisplay(text, mode, displayId)
        val replace = mode == TypeMode.REPLACE
        val focused = focusedEditableIn(Display.DEFAULT_DISPLAY)
        var askedToSwitch = false
        ImeBridge.prepareToType()
        try {
            val nodePassword = focused != null &&
                TextEntry.isPasswordField(focused.isPassword, focused.inputType)
            val imeEnabled = ImeStatus.enabled(this)
            val clipboard = ClipboardPreference.isEnabled(this)
            val order = TypeRoute.order(
                password = nodePassword || ImeBridge.password,
                imeActive = ImeBridge.active && !ImeBridge.password,
                imeEnabled = imeEnabled,
                clipboardOptIn = clipboard,
            )
            for (step in order) {
                when (step) {
                    TypeRoute.PASSWORD -> return Result.failure(SecurityException("password_field"))
                    TypeRoute.IME -> {
                        setTypingKeyboardHidden(false)
                        if (replace) focused?.let { selectAll(it) }
                        if (mode == TypeMode.APPEND) focused?.let { selectEnd(it) }
                        if (finishImeCommit(text, switchBack = false)) {
                            return Result.success(TypedText(text.length, TextEntry.METHOD_IME))
                        }
                    }
                    TypeRoute.SET_TEXT -> {
                        val value = fieldTarget(focused, mode, text)
                        if (focused != null && setText(focused, value)) {
                            return Result.success(TypedText(text.length, TextEntry.METHOD_SET_TEXT))
                        }
                    }
                    TypeRoute.ASK_IME -> {
                        setTypingKeyboardHidden(false)
                        // Click only a node we already believe is the editor. Samsung Notes
                        // hides the body from isFocused; clicking some other EditText (the
                        // title) would move the caret. If nothing is exposed, the framework
                        // focus — the body the owner tapped — still receives the new keyboard.
                        focused?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        focused?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (!ImeBridge.active && ImeStatus.selected(this)) {
                            ImeBridge.awaitActive(1_500)
                        }
                        if (!ImeBridge.active) {
                            askedToSwitch = true
                            showImePicker()
                            if (!ImeBridge.awaitActive(30_000)) continue
                        }
                        if (ImeBridge.password) return Result.failure(SecurityException("password_field"))
                        if (replace) focused?.let { selectAll(it) }
                        if (mode == TypeMode.APPEND) focused?.let { selectEnd(it) }
                        if (finishImeCommit(text, switchBack = askedToSwitch)) {
                            return Result.success(TypedText(text.length, TextEntry.METHOD_IME))
                        }
                    }
                    TypeRoute.PASTE -> {
                        if (focused != null && pasteReplace(focused, text, replace)) {
                            return Result.success(TypedText(text.length, TextEntry.METHOD_PASTE))
                        }
                    }
                    TypeRoute.KEY_EVENTS -> {
                        if (replace) focused?.let { selectAll(it) }
                        if (mode == TypeMode.APPEND) focused?.let { selectEnd(it) }
                        if (injectKeyEvents(text)) {
                            return Result.success(TypedText(text.length, TextEntry.METHOD_KEY_EVENTS))
                        }
                    }
                }
            }
            val code = if (focused == null && !imeEnabled) {
                "ime_disabled"
            } else if (focused == null && !ImeBridge.active) {
                TypeRoute.failure(imeEnabled, askedToSwitch).let { if (it == "type_unsupported") "no_focused_field" else it }
            } else {
                TypeRoute.failure(imeEnabled, askedToSwitch)
            }
            return Result.failure(IllegalStateException(code))
        } finally {
            focused?.recycle()
            ImeBridge.typingFinished()
            holdKeyboard()
        }
    }

    /**
     * Commits through the live input connection, then waits so the editor can
     * apply it before Pony switches keyboards or hides the soft keyboard.
     * Switching back happens only when this command opened the picker.
     */
    private fun finishImeCommit(text: String, switchBack: Boolean): Boolean {
        if (!ImeBridge.commit(text)) return false
        pauseForEditor()
        if (switchBack) {
            ImeBridge.switchBack()
            pauseForEditor()
        }
        return true
    }

    private fun pauseForEditor() {
        if (Looper.myLooper() == Looper.getMainLooper()) return
        try {
            Thread.sleep(250)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Select the field's whole contents so the next commit, paste, or key replaces it. */
    private fun selectAll(node: AccessibilityNodeInfo) {
        val len = node.text?.length ?: return
        if (len <= 0) return
        setSelection(node, 0, len)
    }

    private fun selectEnd(node: AccessibilityNodeInfo) {
        val len = node.text?.length ?: return
        setSelection(node, len, len)
    }

    private fun setSelection(node: AccessibilityNodeInfo, start: Int, end: Int) {
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        }
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args) }
    }

    private fun fieldTarget(node: AccessibilityNodeInfo?, mode: TypeMode, insert: String): String {
        if (node == null) return insert
        val hint = node.hintText?.toString()
        val showing = if (Build.VERSION.SDK_INT >= 26) node.isShowingHintText else false
        return TextEntry.targetValue(
            mode,
            node.text?.toString(),
            hint,
            showing,
            node.textSelectionStart,
            node.textSelectionEnd,
            insert,
        )
    }

    private fun showImePicker() {
        val show = Runnable {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            show.run()
            return
        }
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            show.run()
            latch.countDown()
        }
        latch.await(2, TimeUnit.SECONDS)
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun pasteReplace(node: AccessibilityNodeInfo, insert: String, replace: Boolean): Boolean {
        if (!ClipboardPreference.isEnabled(this)) return false
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        val previous = try {
            clipboard.primaryClip
        } catch (_: Throwable) {
            null
        }
        val pasted = try {
            if (replace) selectAll(node)
            clipboard.setPrimaryClip(ClipData.newPlainText("pony", insert))
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (ok) Thread.sleep(250)
            ok
        } catch (_: Throwable) {
            false
        }
        restoreClipboard(clipboard, previous)
        return pasted
    }

    private fun restoreClipboard(clipboard: ClipboardManager, previous: android.content.ClipData?) {
        try {
            if (previous != null) {
                clipboard.setPrimaryClip(previous)
            } else if (Build.VERSION.SDK_INT >= 28) {
                clipboard.clearPrimaryClip()
            }
        } catch (_: Throwable) {
            // The field already consumed the paste. Leaving a restore failure
            // is logged by the caller only when typing itself fails.
        }
    }

    /** Last resort. Stock phones reject this without INJECT_EVENTS; the result is marked when it works. */
    private fun injectKeyEvents(text: String): Boolean {
        if (text.isEmpty()) return true
        return try {
            val inputManager = getSystemService(InputManager::class.java) ?: return false
            val inject = inputManager.javaClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType,
            )
            val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .getEvents(text.toCharArray()) ?: return false
            for (event in events) {
                val accepted = inject.invoke(inputManager, event, 0) as Boolean
                if (!accepted) return false
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun screenshotJpeg(displayId: Int = Display.DEFAULT_DISPLAY): Result<ByteArray> {
        if (Build.VERSION.SDK_INT < 30) {
            return Result.failure(UnsupportedOperationException("need_projection"))
        }
        // Never hand back an "ok" with no image: capture is retried, and an empty
        // frame is a failure, so the caller reports an error instead of success.
        var last: Throwable = IllegalStateException("screenshot_empty")
        repeat(3) {
            captureJpeg(displayId).fold(
                onSuccess = { bytes -> if (bytes.isNotEmpty()) return Result.success(bytes) },
                onFailure = { last = it },
            )
            try {
                Thread.sleep(120)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return Result.failure(last)
            }
        }
        return Result.failure(last)
    }

    private fun captureJpeg(displayId: Int): Result<ByteArray> {
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        var error: String? = null
        takeScreenshot(
            displayId,
            screenshotExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val wrapped = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        wrapped?.recycle()
                    } finally {
                        screenshot.hardwareBuffer.close()
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    error = "screenshot_failed:$errorCode"
                    latch.countDown()
                }
            },
        )
        if (!latch.await(4, TimeUnit.SECONDS)) {
            return Result.failure(IllegalStateException("screenshot_timeout"))
        }
        val bmp = bitmap ?: return Result.failure(IllegalStateException(error ?: "screenshot_empty"))
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bmp.recycle()
        val bytes = out.toByteArray()
        return if (bytes.isEmpty()) Result.failure(IllegalStateException("screenshot_empty")) else Result.success(bytes)
    }

    /**
     * Waits for the screen to stop changing — a loaded page, a finished
     * animation — instead of sleeping a fixed guess. It watches the
     * accessibility stream for quiet and compares down-sampled screenshots, and
     * returns as soon as both hold still or at [timeoutMs].
     */
    fun waitUntilIdle(displayId: Int = Display.DEFAULT_DISPLAY, timeoutMs: Long = SettleWait.DEFAULT_TIMEOUT_MS): IdleResult {
        val start = System.currentTimeMillis()
        var previous = captureLuma(displayId)
        var lastChangeAt = start
        while (true) {
            val moved = WindowSignal.awaitChange(SETTLE_POLL_MS)
            val now = System.currentTimeMillis()
            if (moved) lastChangeAt = now
            val frame = captureLuma(displayId)
            val diff = if (previous != null && frame != null) SettleWait.diffRatio(previous!!, frame) else 1.0
            if (frame != null) previous = frame
            val sample = SettleWait.Sample(elapsedMs = now - start, quietForMs = now - lastChangeAt, diffRatio = diff)
            when (SettleWait.decide(sample, timeoutMs)) {
                SettleWait.Verdict.SETTLED -> return IdleResult(true, now - start, "settled")
                SettleWait.Verdict.TIMED_OUT -> return IdleResult(false, now - start, "timeout")
                SettleWait.Verdict.WAIT -> Unit
            }
        }
    }

    /** A tiny grid of luma values for the current frame, for the cheap settle diff. Null when capture isn't available. */
    private fun captureLuma(displayId: Int, cols: Int = 24, rows: Int = 48): IntArray? {
        if (Build.VERSION.SDK_INT < 30) return null
        val latch = CountDownLatch(1)
        var luma: IntArray? = null
        takeScreenshot(
            displayId,
            screenshotExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val wrapped = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        val soft = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        wrapped?.recycle()
                        val small = soft?.let { Bitmap.createScaledBitmap(it, cols, rows, true) }
                        soft?.recycle()
                        if (small != null) {
                            val pixels = IntArray(cols * rows)
                            small.getPixels(pixels, 0, cols, 0, 0, cols, rows)
                            small.recycle()
                            luma = IntArray(pixels.size) { i ->
                                val c = pixels[i]
                                val r = (c shr 16) and 0xFF
                                val g = (c shr 8) and 0xFF
                                val b = c and 0xFF
                                (r * 299 + g * 587 + b * 114) / 1000
                            }
                        }
                    } finally {
                        screenshot.hardwareBuffer.close()
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    latch.countDown()
                }
            },
        )
        if (!latch.await(2, TimeUnit.SECONDS)) return null
        return luma
    }

    fun labelAt(x: Float, y: Float, displayId: Int = Display.DEFAULT_DISPLAY): String = tapTarget(x, y, displayId).label

    /** What a tap at (x, y) would press: its label, whether it's a password field, and whose app it is. */
    fun tapTarget(x: Float, y: Float, displayId: Int = Display.DEFAULT_DISPLAY): TapTarget {
        val nodes = roots(displayId)
        val activity = CallGuard.foregroundClass
        val title = focusedWindowTitle(displayId)
        if (nodes.isEmpty()) {
            return TapTarget("", packageName = CallGuard.foregroundPackage, activity = activity, windowTitle = title)
        }
        return try {
            var best: TapTarget? = null
            var bestArea = Int.MAX_VALUE
            var password = false
            fun walk(node: AccessibilityNodeInfo) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.contains(x.toInt(), y.toInt())) return
                val area = bounds.width() * bounds.height()
                if (TextEntry.isPasswordField(node.isPassword, node.inputType) && area in 1 until Int.MAX_VALUE) password = true
                val visible = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val hint = node.hintText?.toString()?.trim().orEmpty()
                val text = sequenceOf(visible, desc, hint).firstOrNull { it.isNotEmpty() }
                if ((text != null || node.isClickable) && (node.isClickable || node.isEditable || node.isFocusable) && area in 1 until bestArea) {
                    val pkg = node.packageName?.toString()
                    val password = TextEntry.isPasswordField(node.isPassword, node.inputType)
                    best = TapTarget(
                        label = if (password) "" else visible.ifBlank { desc.ifBlank { hint } },
                        isPassword = password,
                        packageName = pkg,
                        appLabel = pkg?.let { appLabel(it) },
                        viewId = node.viewIdResourceName,
                        contentDescription = desc.takeIf { it.isNotEmpty() && it != visible },
                        className = node.className?.toString(),
                    )
                    bestArea = area
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    walk(child)
                    child.recycle()
                }
            }
            nodes.forEach { walk(it) }
            val screen = visibleScreenText(nodes)
            val found = best ?: TapTarget("", packageName = nodes.firstOrNull()?.packageName?.toString())
            val withPassword = if (password && !found.isPassword) found.copy(isPassword = true) else found
            withPassword.copy(
                activity = activity,
                windowTitle = title,
                screenText = screen,
                packageName = withPassword.packageName ?: CallGuard.foregroundPackage,
            )
        } finally {
            nodes.forEach { runCatching { it.recycle() } }
        }
    }

    private fun focusedWindowTitle(displayId: Int): String? {
        val windows = orderedWindows(displayId)
        val focused = windows.firstOrNull { it.isFocused } ?: windows.firstOrNull()
        return focused?.title?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun visibleScreenText(nodes: List<AccessibilityNodeInfo>, limit: Int = 2_000): String {
        val out = StringBuilder()
        fun walk(node: AccessibilityNodeInfo) {
            if (out.length >= limit) return
            if (!TextEntry.isPasswordField(node.isPassword, node.inputType)) {
                val raw = sequenceOf(node.text, node.contentDescription)
                    .mapNotNull { it?.toString()?.trim() }
                    .firstOrNull { it.isNotEmpty() }
                if (raw != null) {
                    if (out.isNotEmpty()) out.append('\n')
                    out.append(raw.take(80))
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        nodes.forEach { walk(it) }
        return out.toString().take(limit)
    }

    fun focusedIsPassword(displayId: Int = Display.DEFAULT_DISPLAY): Boolean {
        val nodes = roots(displayId)
        val node = nodes.firstNotNullOfOrNull { UiTreeDumper.focusedEditable(it) }
        nodes.forEach { runCatching { it.recycle() } }
        if (node == null) return false
        return try {
            TextEntry.isPasswordField(node.isPassword, node.inputType)
        } finally {
            node.recycle()
        }
    }

    /** Every window on [displayId], as plain data for the cover check. */
    fun windowShots(displayId: Int = Display.DEFAULT_DISPLAY): List<WindowShot> {
        val list: List<AccessibilityWindowInfo> = try {
            if (Build.VERSION.SDK_INT >= 30 && displayId != Display.DEFAULT_DISPLAY) {
                windowsOnAllDisplays?.get(displayId).orEmpty()
            } else {
                windows.orEmpty()
            }
        } catch (_: Throwable) {
            emptyList()
        }
        return list.map { window ->
            val bounds = Rect()
            window.getBoundsInScreen(bounds)
            val root = runCatching { window.root }.getOrNull()
            val pkg = root?.packageName?.toString()
            runCatching { root?.recycle() }
            WindowShot(
                type = when (window.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> WinType.APPLICATION
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WinType.INPUT_METHOD
                    AccessibilityWindowInfo.TYPE_SYSTEM -> WinType.SYSTEM
                    AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WinType.ACCESSIBILITY_OVERLAY
                    AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> WinType.DIVIDER
                    AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> WinType.MAGNIFICATION
                    else -> WinType.OTHER
                },
                layer = window.layer,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
                packageName = pkg,
                focused = window.isFocused,
            )
        }
    }

    /** True when accessibility has this display in [windowsOnAllDisplays] (API 30+). */
    fun seesDisplay(displayId: Int): Boolean {
        if (displayId == Display.DEFAULT_DISPLAY) return true
        if (Build.VERSION.SDK_INT < 30) return false
        return try {
            val displays = windowsOnAllDisplays ?: return false
            displays.indexOfKey(displayId) >= 0
        } catch (_: Throwable) {
            false
        }
    }

    fun foregroundPackage(displayId: Int = Display.DEFAULT_DISPLAY): String? {
        WindowStack.leadWindow(windowShots(displayId))?.packageName?.let { return it }
        if (displayId == Display.DEFAULT_DISPLAY) {
            val root = rootInActiveWindow ?: return CallGuard.foregroundPackage
            return try {
                root.packageName?.toString()
            } finally {
                runCatching { root.recycle() }
            }
        }
        return null
    }

    /** The window a tree read leads with (focused, else topmost app), for reporting its rect. */
    fun leadWindow(displayId: Int = Display.DEFAULT_DISPLAY): WindowShot? =
        WindowStack.leadWindow(windowShots(displayId))

    /** The topmost window under (x, y), for reporting which window a touch lands in. */
    fun windowAt(x: Float, y: Float, displayId: Int = Display.DEFAULT_DISPLAY): WindowShot? =
        WindowStack.topmostAt(x.toInt(), y.toInt(), windowShots(displayId))

    fun statusBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val metrics = getSystemService(WindowManager::class.java).currentWindowMetrics
                return metrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top
            }
        }
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (24 * resources.displayMetrics.density).toInt()
    }

    fun navBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val metrics = getSystemService(WindowManager::class.java).currentWindowMetrics
                return metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
            }
        }
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (16 * resources.displayMetrics.density).toInt()
    }

    /**
     * Pony Basics: find a key in the app in front by its label or view id and
     * press it. Candidates are ranked so a digit matches the key, not a display
     * that happens to show the same digit.
     */
    fun pressControl(labels: List<String>, idHints: List<String>): String? {
        val root = rootInActiveWindow ?: return null
        val found = mutableListOf<Pair<Int, AccessibilityNodeInfo>>()
        return try {
            collectControls(root, labels.map { it.lowercase() }, idHints.map { it.lowercase() }, found)
            val node = found.maxByOrNull { it.first }?.second ?: return null
            val label = sequenceOf(node.text, node.contentDescription).mapNotNull { it?.toString() }.firstOrNull { it.isNotBlank() }
                ?: labels.first()
            val clicked = clickable(node)?.let { target ->
                try {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } finally {
                    if (target !== node) runCatching { target.recycle() }
                }
            } ?: false
            if (clicked) return label
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty && tap(bounds.exactCenterX(), bounds.exactCenterY())) label else null
        } finally {
            found.forEach { runCatching { it.second.recycle() } }
            runCatching { root.recycle() }
        }
    }

    private fun collectControls(
        node: AccessibilityNodeInfo,
        labels: List<String>,
        ids: List<String>,
        out: MutableList<Pair<Int, AccessibilityNodeInfo>>,
    ) {
        val text = node.text?.toString()?.trim()?.lowercase()
        val desc = node.contentDescription?.toString()?.trim()?.lowercase()
        val id = node.viewIdResourceName?.substringAfter(":id/")?.lowercase()
        val byLabel = (text != null && text in labels) || (desc != null && desc in labels)
        val byId = id != null && ids.any { hint -> id == hint || id.endsWith("_$hint") }
        if (node.isVisibleToUser && (byLabel || byId)) {
            val cls = node.className?.toString().orEmpty()
            var score = 0
            if (byId) score += 4
            if (byLabel) score += 3
            if (node.isClickable) score += 2
            if (cls.contains("Button")) score += 1
            if (!node.isClickable && (cls.contains("EditText") || cls.contains("TextView"))) score -= 4
            out += score to AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectControls(child, labels, ids, out)
            child.recycle()
        }
    }

    /** Every visible text in the app in front, with its view id, for reading results back. */
    fun visibleTexts(): List<Pair<String, String?>> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<Pair<String, String?>>()
        fun walk(node: AccessibilityNodeInfo) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && !TextEntry.isPasswordField(node.isPassword, node.inputType)) {
                out += text to node.viewIdResourceName
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        return try {
            walk(root)
            out
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isClickable) return parent
            val next = parent.parent
            parent.recycle()
            parent = next
            depth += 1
        }
        parent?.recycle()
        return null
    }

    /** The smallest node under (x, y), handed to [block] and recycled after. Null when nothing is there. */
    private fun <T> withNodeAt(x: Float, y: Float, displayId: Int, block: (AccessibilityNodeInfo) -> T): T? {
        val roots = roots(displayId)
        if (roots.isEmpty()) return null
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        fun walk(node: AccessibilityNodeInfo) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.contains(x.toInt(), y.toInt())) {
                val area = bounds.width() * bounds.height()
                if (area in 1 until bestArea) {
                    best?.let { runCatching { it.recycle() } }
                    best = AccessibilityNodeInfo.obtain(node)
                    bestArea = area
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        return try {
            roots.forEach { walk(it) }
            best?.let { node -> try { block(node) } finally { runCatching { node.recycle() } } }
        } finally {
            roots.forEach { runCatching { it.recycle() } }
        }
    }

    /** True when a clickable control sits under (x, y): a tap there would drive something. */
    fun clickableAt(x: Float, y: Float, displayId: Int = Display.DEFAULT_DISPLAY): Boolean =
        withNodeAt(x, y, displayId) { node ->
            val c = clickable(node)
            if (c != null && c !== node) runCatching { c.recycle() }
            c != null
        } ?: false

    /** Ask the control under (x, y) to click itself. True only when a real click was performed. */
    fun clickAt(x: Float, y: Float, displayId: Int = Display.DEFAULT_DISPLAY): Boolean =
        withNodeAt(x, y, displayId) { node ->
            val target = clickable(node) ?: return@withNodeAt false
            try {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } finally {
                if (target !== node) runCatching { target.recycle() }
            }
        } ?: false

    /** Scroll the list or page under (x, y) forward to reveal more of it. True when a scroll happened. */
    fun scrollToward(x: Float, y: Float, displayId: Int = Display.DEFAULT_DISPLAY): Boolean {
        val roots = roots(displayId)
        if (roots.isEmpty()) return false
        var best: AccessibilityNodeInfo? = null
        var bestContains = false
        var bestArea = 0
        fun walk(node: AccessibilityNodeInfo) {
            if (node.isScrollable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val area = bounds.width() * bounds.height()
                val contains = bounds.contains(x.toInt(), y.toInt())
                if ((contains && !bestContains) || (contains == bestContains && area > bestArea)) {
                    best?.let { runCatching { it.recycle() } }
                    best = AccessibilityNodeInfo.obtain(node)
                    bestContains = contains
                    bestArea = area
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                child.recycle()
            }
        }
        return try {
            roots.forEach { walk(it) }
            val node = best ?: return false
            try {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            } finally {
                runCatching { node.recycle() }
            }
        } finally {
            roots.forEach { runCatching { it.recycle() } }
        }
    }

    /** A cheap fingerprint of what's on screen, to tell whether a tap changed anything. */
    fun screenSignature(displayId: Int = Display.DEFAULT_DISPLAY): String {
        val pkg = foregroundPackage(displayId) ?: ""
        val texts = visibleTexts().asSequence().take(60).joinToString("\u0001") { it.first }
        return pkg + "\u0002" + texts.hashCode()
    }


    fun openApp(packageName: String): Boolean {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(launch, null)
    }

    fun launch(intent: Intent, options: android.os.Bundle?): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent, options)
        return true
    }

    /** "freeform" when the app's window does not fill the screen, otherwise "main". */
    fun placementOf(packageName: String): String {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val windows = windows ?: return "missing"
        var best = 0
        for (window in windows) {
            val root = window.root ?: continue
            val matches = root.packageName == packageName
            root.recycle()
            if (!matches) continue
            val bounds = android.graphics.Rect()
            window.getBoundsInScreen(bounds)
            best = maxOf(best, bounds.width() * bounds.height())
        }
        if (best <= 0) return "missing"
        val screen = screenW * screenH
        return if (screen > 0 && best < screen * 0.85) "freeform" else "main"
    }

    fun resolveX(value: Double): Float = resolve(value, resources.displayMetrics.widthPixels)
    fun resolveY(value: Double): Float = resolve(value, resources.displayMetrics.heightPixels)

    private fun resolve(value: Double, screen: Int): Float {
        return if (value in 0.0..1.0) (value * screen).toFloat() else value.toFloat()
    }

    private fun appLabel(pkg: String): String? = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private fun <T> passingOverlay(displayId: Int, block: () -> T): T {
        // Some protected switches (Developer options "Stay awake", secure toggles)
        // set filterTouchesWhenObscured and drop any touch while a touchable overlay
        // from another uid sits anywhere over their window — not only over the tapped
        // point. So the pill goes non-touchable for every gesture on the main screen,
        // then back, rather than only when it covers the point.
        if (displayId != Display.DEFAULT_DISPLAY) return block()
        return OverlayHost.passThrough(block)
    }

    private fun gesture(displayId: Int, stroke: GestureDescription.StrokeDescription): GestureDescription =
        gesture(displayId, listOf(stroke))

    private fun gesture(displayId: Int, strokes: List<GestureDescription.StrokeDescription>): GestureDescription {
        val builder = GestureDescription.Builder()
        strokes.forEach { builder.addStroke(it) }
        if (Build.VERSION.SDK_INT >= 30 && displayId != Display.DEFAULT_DISPLAY) {
            builder.setDisplayId(displayId)
        }
        return builder.build()
    }

    /** Window handles for [displayId], topmost (highest layer) first, minus Pony's own overlays. */
    private fun orderedWindows(displayId: Int): List<AccessibilityWindowInfo> {
        val raw: List<AccessibilityWindowInfo> = try {
            if (Build.VERSION.SDK_INT >= 30 && displayId != Display.DEFAULT_DISPLAY) {
                windowsOnAllDisplays?.get(displayId).orEmpty()
            } else {
                windows.orEmpty()
            }
        } catch (_: Throwable) {
            emptyList()
        }
        return raw
            .filter {
                it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                    it.type != AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY
            }
            .sortedByDescending { it.layer }
    }

    /**
     * Roots for [displayId], topmost window first. Freeform pop-ups (Settings,
     * the Google app, Gboard) are not the active window, so reading only
     * rootInActiveWindow returns the app underneath; every window is included,
     * led by the one on top.
     */
    private fun roots(displayId: Int): List<AccessibilityNodeInfo> {
        val ordered = orderedWindows(displayId).mapNotNull { window ->
            // The handle from a freeform pop-up can be a frame behind: Postmates
            // keeps serving the old feed, with bounds off the screen edge. Refresh
            // each root so a hit-test or tree read matches what's on screen now.
            window.root?.also { runCatching { it.refresh() } }
        }
        if (ordered.isNotEmpty()) return ordered
        return listOfNotNull(rootInActiveWindow?.also { runCatching { it.refresh() } })
    }

    /** The topmost editable field across every window on [displayId] (the pop-up's, not the app's underneath). */
    private fun focusedEditableIn(displayId: Int): AccessibilityNodeInfo? {
        val roots = roots(displayId)
        return try {
            roots.firstNotNullOfOrNull { UiTreeDumper.focusedEditable(it) }
        } finally {
            roots.forEach { runCatching { it.recycle() } }
        }
    }

    private fun typeOnDisplay(text: String, mode: TypeMode, displayId: Int): Result<TypedText> {
        val focused = roots(displayId).let { nodes ->
            val found = nodes.firstNotNullOfOrNull { UiTreeDumper.focusedEditable(it) }
            nodes.forEach { runCatching { it.recycle() } }
            found
        }
        return try {
            if (focused != null && TextEntry.isPasswordField(focused.isPassword, focused.inputType)) {
                return Result.failure(SecurityException("password_field"))
            }
            val value = fieldTarget(focused, mode, text)
            if (focused != null && setText(focused, value)) {
                Result.success(TypedText(text.length, TextEntry.METHOD_SET_TEXT))
            } else {
                Result.failure(IllegalStateException(app.pony.companion.display.DisplayPolicy.TYPE_FAILED))
            }
        } finally {
            focused?.recycle()
        }
    }

    private fun dispatch(gesture: GestureDescription, timeoutMs: Long = 2_000L): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    ok = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    latch.countDown()
                }
            },
            null,
        )
        if (!accepted) return false
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return ok
    }

    private fun onDefaultDisplay(event: AccessibilityEvent): Boolean =
        Build.VERSION.SDK_INT < 30 || event.displayId == Display.DEFAULT_DISPLAY

    private fun isCallNotice(notice: Notification): Boolean {
        if (notice.category == Notification.CATEGORY_CALL) return true
        val template = notice.extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return template.contains("CallStyle")
    }

    companion object {
        @Volatile
        var instance: PonyAccessibilityService? = null
            private set

        private val screenshotExecutor = Executors.newSingleThreadExecutor()

        const val KEYBOARD_HOLD_MS = 3_000L

        /** How often the settle wait re-checks the screen while the a11y stream is quiet. */
        const val SETTLE_POLL_MS = 200L

        fun isEnabled(): Boolean = instance != null
    }
}

data class TypedText(val length: Int, val method: String)

/** Outcome of a settle wait: whether the screen went still, how long it took, and why it stopped. */
data class IdleResult(val settled: Boolean, val waitedMs: Long, val reason: String)
