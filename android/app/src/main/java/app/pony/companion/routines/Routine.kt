package app.pony.companion.routines

/**
 * A saved routine: a finished task the owner gave a name so they can run it
 * again just by saying the name. [text] is the very words Pony first ran — the
 * same thing the owner asked — so replaying a routine goes back through the
 * brain and every Send this?/Pay? gate still happens. Nothing here is a secret,
 * so like a scheduled task it's kept as plain JSON, not encrypted.
 */
data class Routine(
    val id: String,
    val name: String,
    val text: String,
    val createdAt: Long,
    val lastRunAt: Long? = null,
    val runCount: Int = 0,
)
