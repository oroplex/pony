package app.pony.companion.brain

enum class ProviderKind { OPENAI_COMPAT, ANTHROPIC, GEMINI }

/**
 * A brain Pony can run on the phone with the owner's own API key.
 *
 * [title] is the recognizable name shown in the picker; [provider] is the
 * company whose key page [keyUrl] opens ("Get your <provider> API key");
 * [blurb] is the one line the setup sheet shows. The key page URLs are the
 * real, current consoles so the "Get your key" button lands on the right page.
 */
enum class ProviderPreset(
    val title: String,
    val provider: String,
    val kind: ProviderKind,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val keyUrl: String,
    val blurb: String,
) {
    XAI(
        "xAI Grok",
        "xAI",
        ProviderKind.OPENAI_COMPAT,
        "grok-4.7",
        "https://api.x.ai/v1",
        "https://console.x.ai/team/default/api-keys",
        "Grok models from xAI, running on this phone with your xAI key.",
    ),
    OPENAI(
        "OpenAI",
        "OpenAI",
        ProviderKind.OPENAI_COMPAT,
        "gpt-4.1",
        "https://api.openai.com/v1",
        "https://platform.openai.com/api-keys",
        "GPT models from OpenAI, running on this phone with your OpenAI key.",
    ),
    ANTHROPIC(
        "Anthropic Claude",
        "Anthropic",
        ProviderKind.ANTHROPIC,
        "claude-opus-5-5",
        "https://api.anthropic.com",
        "https://console.anthropic.com/settings/keys",
        "Claude models from Anthropic, running on this phone with your Anthropic key.",
    ),
    GEMINI(
        "Google Gemini",
        "Google",
        ProviderKind.GEMINI,
        "gemini-2.5-flash",
        "https://generativelanguage.googleapis.com/v1beta",
        "https://aistudio.google.com/api-keys",
        "Gemini models from Google, running on this phone with your Google AI key.",
    ),
    OPENROUTER(
        "OpenRouter",
        "OpenRouter",
        ProviderKind.OPENAI_COMPAT,
        "openai/gpt-4.1",
        "https://openrouter.ai/api/v1",
        "https://openrouter.ai/keys",
        "Any model through OpenRouter, with one OpenRouter key.",
    ),
    CUSTOM(
        "Custom OpenAI-compatible",
        "your provider",
        ProviderKind.OPENAI_COMPAT,
        "",
        "",
        "",
        "Any OpenAI-compatible endpoint. Set the base URL and model yourself.",
    ),
    ;

    companion object {
        /**
         * The brains the Connect picker offers by name, in order. Each has a
         * real key page and runs on the phone; OpenRouter and Custom stay under
         * Advanced for people who want them.
         */
        val pickable: List<ProviderPreset> = listOf(ANTHROPIC, GEMINI, OPENAI, XAI)

        /**
         * Built-in defaults we've since replaced. A saved brain still on one of
         * these never had its model hand-picked, so it rides the new default; a
         * model the owner chose by hand is never in this list and stays untouched.
         */
        val supersededModels: Map<ProviderPreset, Set<String>> = mapOf(
            ANTHROPIC to setOf("claude-sonnet-4-5"),
        )
    }
}

data class ProviderRecord(
    val id: String,
    val name: String,
    val preset: ProviderPreset,
    val model: String,
    val baseUrl: String,
    val keyLast4: String,
    val active: Boolean,
)

data class ProviderDraft(
    val id: String?,
    val name: String,
    val preset: ProviderPreset,
    val model: String,
    val baseUrl: String,
    val newKey: String?,
)
