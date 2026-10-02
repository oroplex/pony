package app.pony.companion.brain

/**
 * Grok Bot is SpaceXAI's agent app (@bot). It has no API key.
 * It drives the phone through Pony's connector, which is Connected mode.
 * Replace [PONY_GROK_BOT_TEMPLATE_URL] with the published template link.
 */
const val PONY_GROK_BOT_TEMPLATE_URL = "https://example.com/pony-grok-bot-template"

object GrokBot {
    const val NAME = "Grok Bot"

    fun isGrok(name: String?): Boolean = name?.trim() == NAME

    /** Deep link `client=grokbot`, intent extra, or the display name. */
    fun isTemplateToken(value: String?): Boolean {
        val token = value?.trim()?.lowercase() ?: return false
        return token == "grokbot" || token == "grok-bot" || token == "grok_bot" || token == "grok bot"
    }
}
