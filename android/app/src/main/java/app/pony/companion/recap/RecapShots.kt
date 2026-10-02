package app.pony.companion.recap

/** Whether a captured screen may be shown as a recap before/after thumbnail. */
object RecapShotPolicy {
    /**
     * Never Pony's own screen. The work happens in other apps, and that's what a
     * "here's what I did" recap shows — so a shot is kept only while Pony itself
     * is in the background.
     */
    fun keep(ponyForeground: Boolean): Boolean = !ponyForeground
}

/**
 * The before/after thumbnails for the task Pony is running right now. Held in
 * memory and never written to disk: they show another app's screen, which the
 * on-disk history deliberately never keeps. Replaced when a new task starts.
 */
object RecapShots {
    data class Shots(val before: ByteArray?, val after: ByteArray?) {
        val any: Boolean get() = before != null || after != null
    }

    private val lock = Any()
    private var taskId: String? = null
    private var before: ByteArray? = null
    private var after: ByteArray? = null

    fun start(id: String) = synchronized(lock) {
        taskId = id
        before = null
        after = null
    }

    /** The first work screen becomes "before"; each later one updates "after". */
    fun capture(id: String?, thumbnail: ByteArray?) = synchronized(lock) {
        if (id == null || id != taskId || thumbnail == null || thumbnail.isEmpty()) return
        if (before == null) before = thumbnail else after = thumbnail
    }

    fun of(id: String?): Shots = synchronized(lock) {
        if (id != null && id == taskId) Shots(before, after) else Shots(null, null)
    }

    fun clear() = synchronized(lock) {
        taskId = null
        before = null
        after = null
    }
}
