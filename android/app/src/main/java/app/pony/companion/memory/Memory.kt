package app.pony.companion.memory

import android.content.Context
import app.pony.companion.brain.KeystoreSecretBox
import java.io.File

/** Builds the phone's memory store from the app's private files, encrypted under its own Keystore alias. */
object Memory {
    fun store(context: Context): MemoryStore =
        MemoryStore(File(context.filesDir, "memory/memory.json"), KeystoreSecretBox(KeystoreSecretBox.MEMORY_ALIAS))

    /** The preamble handed to the on-phone brain at task start. Empty when nothing is remembered. */
    fun summary(context: Context): String =
        runCatching { MemorySummary.of(store(context).all()) }.getOrDefault("")
}
