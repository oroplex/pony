package app.pony.companion.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrainLibraryTest {
    private val key = "sk-pony-PLAINTEXT-secret-9999"

    @Test
    fun keyIsNotPlaintextOnDiskAndOnlyLast4IsVisible() {
        val root = tempRoot()
        val library = BrainLibrary(root, JceSecretBox.random())
        val saved = library.save(draft(name = "Nightstand", newKey = key))
        assertEquals("9999", saved.keyLast4)
        assertEquals(key, library.secret(saved.id))
        val disk = library.providerText() + library.secretText()
        assertFalse(disk.contains(key))
        assertFalse(disk.contains("PLAINTEXT"))
        assertFalse(disk.contains("sk-pony"))
        assertTrue(disk.contains("9999"))
        assertFalse(saved.toString().contains(key))
    }

    @Test
    fun switchingActiveProviderAndDeleteKeepASingleActiveBrain() {
        val library = BrainLibrary(tempRoot(), JceSecretBox.random())
        val grok = library.save(draft(name = "Grok", preset = ProviderPreset.XAI, newKey = key))
        val claude = library.save(
            draft(name = "Claude", preset = ProviderPreset.ANTHROPIC, newKey = "sk-ant-PLAINTEXT-secret-2222"),
        )
        assertTrue(library.get(grok.id)!!.active)
        assertFalse(library.get(claude.id)!!.active)

        library.activate(claude.id)
        assertFalse(library.get(grok.id)!!.active)
        assertEquals(claude.id, library.active()!!.id)

        library.delete(claude.id)
        assertNull(library.get(claude.id))
        assertNull(library.secret(claude.id))
        assertEquals(grok.id, library.active()!!.id)
        assertFalse(library.providerText().contains("Claude"))
        assertFalse(library.providerText().contains("sk-ant"))
    }

    @Test
    fun editKeepsTheSavedKeyWhenNoNewKeyIsPasted() {
        val library = BrainLibrary(tempRoot(), JceSecretBox.random())
        val saved = library.save(draft(name = "Grok", newKey = key))
        val edited = library.save(
            ProviderDraft(
                id = saved.id,
                name = "Grok home",
                preset = ProviderPreset.XAI,
                model = "grok-4",
                baseUrl = ProviderPreset.XAI.defaultBaseUrl,
                newKey = null,
            ),
        )
        assertEquals("grok-4", edited.model)
        assertEquals("9999", edited.keyLast4)
        assertEquals(key, library.secret(saved.id))
        assertFalse(library.providerText().contains(key))
    }

    @Test
    fun clearAllWipesEveryKeyAndRecordFromDisk() {
        val root = tempRoot()
        val library = BrainLibrary(root, JceSecretBox.random())
        val grok = library.save(draft(name = "Grok", preset = ProviderPreset.XAI, newKey = key))
        val claude = library.save(
            draft(name = "Claude", preset = ProviderPreset.ANTHROPIC, newKey = "sk-ant-PLAINTEXT-secret-2222"),
        )

        library.clearAll()

        assertTrue(library.list().isEmpty())
        assertNull(library.active())
        assertNull(library.get(grok.id))
        assertNull(library.get(claude.id))
        assertNull(library.secret(grok.id))
        assertNull(library.secret(claude.id))
        val disk = library.providerText() + library.secretText()
        assertFalse(disk.contains("Claude"))
        assertFalse(disk.contains("9999"))
        assertFalse(disk.contains("2222"))
        assertFalse(disk.contains("sk-ant"))

        // A reopened library sees nothing left behind and can still add a fresh brain.
        val reopened = BrainLibrary(root, JceSecretBox.random())
        assertTrue(reopened.list().isEmpty())
        val fresh = reopened.save(draft(name = "New", newKey = key))
        assertTrue(reopened.get(fresh.id)!!.active)
    }

    @Test
    fun oldAnthropicDefaultMigratesToOpusWhileHandPickedModelsStay() {
        val root = tempRoot()
        val box = JceSecretBox.random()
        val library = BrainLibrary(root, box)
        val onOldDefault = library.save(
            ProviderDraft(
                id = null,
                name = "Claude",
                preset = ProviderPreset.ANTHROPIC,
                model = "claude-sonnet-4-5",
                baseUrl = ProviderPreset.ANTHROPIC.defaultBaseUrl,
                newKey = "sk-ant-PLAINTEXT-secret-2222",
            ),
        )
        val handPicked = library.save(
            ProviderDraft(
                id = null,
                name = "Claude Haiku",
                preset = ProviderPreset.ANTHROPIC,
                model = "claude-haiku-4-5",
                baseUrl = ProviderPreset.ANTHROPIC.defaultBaseUrl,
                newKey = "sk-ant-PLAINTEXT-secret-3333",
            ),
        )

        val reopened = BrainLibrary(root, box)
        assertEquals("claude-opus-5-5", reopened.get(onOldDefault.id)!!.model)
        assertEquals("claude-haiku-4-5", reopened.get(handPicked.id)!!.model)
        assertFalse(reopened.providerText().contains("claude-sonnet-4-5"))
        assertTrue(reopened.providerText().contains("claude-opus-5-5"))
    }

    @Test
    fun disconnectingOneBrainLeavesTheOthersConnectedAndFallsBack() {
        val library = BrainLibrary(tempRoot(), JceSecretBox.random())
        val claude = library.save(draft(name = "Claude", preset = ProviderPreset.ANTHROPIC, newKey = "sk-ant-PLAINTEXT-secret-1111"))
        val gemini = library.save(draft(name = "Gemini", preset = ProviderPreset.GEMINI, newKey = "AIza-PLAINTEXT-secret-2222"))
        val openai = library.save(draft(name = "OpenAI", preset = ProviderPreset.OPENAI, newKey = "sk-proj-PLAINTEXT-secret-3333"))

        // The first brain saved is the active one; the others are connected but idle.
        assertEquals(claude.id, library.active()!!.id)

        // Disconnecting an idle brain clears only its key and leaves the rest connected.
        library.delete(gemini.id)
        assertNull(library.get(gemini.id))
        assertNull(library.secret(gemini.id))
        assertEquals(2, library.list().size)
        assertEquals(claude.id, library.active()!!.id)
        assertEquals("sk-ant-PLAINTEXT-secret-1111", library.secret(claude.id))
        assertEquals("sk-proj-PLAINTEXT-secret-3333", library.secret(openai.id))
        assertFalse(library.providerText().contains("Gemini"))

        // Disconnecting the active brain promotes a remaining one, never leaving zero active.
        library.delete(claude.id)
        assertNull(library.get(claude.id))
        assertNull(library.secret(claude.id))
        assertEquals(openai.id, library.active()!!.id)
        assertEquals(1, library.list().size)

        // Disconnecting the last brain falls back to the "no brain yet" state.
        library.delete(openai.id)
        assertTrue(library.list().isEmpty())
        assertNull(library.active())
        assertNull(library.secret(openai.id))
    }

    private fun draft(
        name: String,
        preset: ProviderPreset = ProviderPreset.XAI,
        newKey: String?,
    ) = ProviderDraft(
        id = null,
        name = name,
        preset = preset,
        model = preset.defaultModel.ifBlank { "custom-model" },
        baseUrl = preset.defaultBaseUrl.ifBlank { "https://example.test/v1" },
        newKey = newKey,
    )

    private fun tempRoot(): File = File.createTempFile("pony-brains", "").apply {
        delete()
        mkdirs()
    }
}
