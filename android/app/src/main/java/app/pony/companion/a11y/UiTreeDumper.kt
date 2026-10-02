package app.pony.companion.a11y

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object UiTreeDumper {
    fun dump(root: AccessibilityNodeInfo?): String {
        if (root == null) return "(no active window)"
        // Roots pulled from a freeform window handle can be stale, dropping rows
        // the owner can see (the Developer options switch, menu items). A refresh
        // repopulates the child count before the walk.
        runCatching { root.refresh() }
        val out = StringBuilder()
        walk(root, 0, out)
        return out.toString().trimEnd()
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, out: StringBuilder) {
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "View"
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val text = visibleText(node)
        val flags = nodeFlags(
            clickable = node.isClickable,
            editable = node.isEditable,
            focused = node.isFocused,
            password = TextEntry.isPasswordField(node.isPassword, node.inputType),
            checkable = node.isCheckable,
            checked = node.isChecked,
            visibleToUser = node.isVisibleToUser,
        )
        out.append(nodeLine(depth, cls, text, flags, bounds.left, bounds.top, bounds.right, bounds.bottom))
        out.append('\n')
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, depth + 1, out)
            child.recycle()
        }
    }

    /**
     * The one-word tags on a node's line. Pure so the set — and the switch state
     * the brain needs to tell a toggle on from off — is testable without a view.
     */
    fun nodeFlags(
        clickable: Boolean,
        editable: Boolean,
        focused: Boolean,
        password: Boolean,
        checkable: Boolean,
        checked: Boolean,
        visibleToUser: Boolean,
    ): List<String> = buildList {
        if (clickable) add("clickable")
        if (editable) add("editable")
        if (focused) add("focused")
        if (password) add("password")
        // A switch or checkbox reads as checked/unchecked, so the brain doesn't
        // have to guess a toggle's state from a screenshot.
        if (checkable) add(if (checked) "checked" else "unchecked")
        // Keep rows accessibility hides from sight: the brain still needs to know
        // they are there (and roughly where) rather than have them vanish.
        if (!visibleToUser) add("offscreen")
    }

    /** One node's line. Pure so the shape — indent, label, flags, bounds — is testable. */
    fun nodeLine(depth: Int, cls: String, text: String?, flags: List<String>, left: Int, top: Int, right: Int, bottom: Int): String {
        val out = StringBuilder()
        out.append("  ".repeat(depth)).append(cls)
        if (text != null) out.append(" ").append(text)
        if (flags.isNotEmpty()) out.append(" ").append(flags.joinToString(" "))
        out.append(" bounds=").append(left).append(',').append(top).append(',').append(right).append(',').append(bottom)
        return out.toString()
    }

    fun visibleText(node: AccessibilityNodeInfo): String? {
        if (TextEntry.isPasswordField(node.isPassword, node.inputType)) return "[password]"
        val raw = sequenceOf(node.text, node.contentDescription, node.hintText)
            .mapNotNull { it?.toString()?.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: return null
        return "\"${raw.replace("\n", " ").take(80)}\""
    }

    fun focusedEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        find(root) { it.isFocused && it.isEditable }?.let { return it }
        // Some editors (e.g. Samsung Notes) keep input focus on a container
        // while the caret sits in a child EditText that never reports isFocused.
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        return try {
            if (inputFocus.isEditable) {
                AccessibilityNodeInfo.obtain(inputFocus)
            } else {
                find(inputFocus) { it.isEditable }
            }
        } finally {
            inputFocus.recycle()
        }
    }

    private fun find(
        node: AccessibilityNodeInfo,
        pred: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (pred(node)) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = find(child, pred)
            child.recycle()
            if (match != null) return match
        }
        return null
    }
}
