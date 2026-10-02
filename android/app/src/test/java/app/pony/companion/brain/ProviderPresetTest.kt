package app.pony.companion.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPresetTest {
    @Test
    fun therePickerOffersClaudeGeminiOpenAiAndGrokInOrder() {
        assertEquals(
            listOf(ProviderPreset.ANTHROPIC, ProviderPreset.GEMINI, ProviderPreset.OPENAI, ProviderPreset.XAI),
            ProviderPreset.pickable,
        )
        // OpenRouter and Custom stay out of the by-name picker; they live under Advanced.
        assertFalse(ProviderPreset.OPENROUTER in ProviderPreset.pickable)
        assertFalse(ProviderPreset.CUSTOM in ProviderPreset.pickable)
    }

    @Test
    fun everyPickableBrainPointsAtItsRealKeyPage() {
        assertEquals("https://console.anthropic.com/settings/keys", ProviderPreset.ANTHROPIC.keyUrl)
        assertEquals("https://platform.openai.com/api-keys", ProviderPreset.OPENAI.keyUrl)
        assertEquals("https://aistudio.google.com/api-keys", ProviderPreset.GEMINI.keyUrl)
        assertEquals("https://console.x.ai/team/default/api-keys", ProviderPreset.XAI.keyUrl)
        // Each is an https console the "Get your key" button can open, and has a blurb.
        ProviderPreset.pickable.forEach { preset ->
            assertTrue("${preset.name} key page must be https", preset.keyUrl.startsWith("https://"))
            assertTrue("${preset.name} needs a provider name", preset.provider.isNotBlank())
            assertTrue("${preset.name} needs a blurb", preset.blurb.isNotBlank())
        }
    }

    @Test
    fun theProviderNamesDriveTheGetYourKeyButton() {
        assertEquals("Anthropic", ProviderPreset.ANTHROPIC.provider)
        assertEquals("OpenAI", ProviderPreset.OPENAI.provider)
        assertEquals("Google", ProviderPreset.GEMINI.provider)
        assertEquals("xAI", ProviderPreset.XAI.provider)
    }

    @Test
    fun grokKeepsItsCleanTitleAndOpenAiCompatibleKind() {
        assertEquals("xAI Grok", ProviderPreset.XAI.title)
        assertEquals(ProviderKind.OPENAI_COMPAT, ProviderPreset.XAI.kind)
        assertEquals(ProviderKind.OPENAI_COMPAT, ProviderPreset.OPENAI.kind)
        assertEquals(ProviderKind.ANTHROPIC, ProviderPreset.ANTHROPIC.kind)
        assertEquals(ProviderKind.GEMINI, ProviderPreset.GEMINI.kind)
    }
}
