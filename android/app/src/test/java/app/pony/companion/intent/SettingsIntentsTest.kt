package app.pony.companion.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsIntentsTest {
    @Test
    fun namedScreensResolveToTheirAction() {
        assertEquals("android.settings.INPUT_METHOD_SETTINGS", SettingsIntents.resolve("input_method")?.action)
        assertEquals("android.settings.INPUT_METHOD_SETTINGS", SettingsIntents.resolve("keyboard")?.action)
        assertEquals("android.settings.LOCALE_SETTINGS", SettingsIntents.resolve("languages")?.action)
        assertEquals("android.settings.VOICE_INPUT_SETTINGS", SettingsIntents.resolve("voice_input")?.action)
        assertEquals("android.settings.ACCESSIBILITY_SETTINGS", SettingsIntents.resolve("accessibility")?.action)
    }

    @Test
    fun namesAreNormalizedForCaseSpacesAndHyphens() {
        assertEquals("android.settings.VOICE_INPUT_SETTINGS", SettingsIntents.resolve("Voice Input")?.action)
        assertEquals("android.settings.LOCALE_SETTINGS", SettingsIntents.resolve("  Languages ")?.action)
        assertEquals("android.settings.INPUT_METHOD_SETTINGS", SettingsIntents.resolve("input-method")?.action)
    }

    @Test
    fun appDetailsNeedsAPackageAndBuildsItsUri() {
        val target = SettingsIntents.resolve("app_details", "com.example.app")
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", target?.action)
        assertEquals("package:com.example.app", target?.data)
        // Same screen, built directly.
        assertEquals("package:com.whatsapp", SettingsIntents.appDetails("com.whatsapp").data)
        // Without a package there is nothing to show.
        assertNull(SettingsIntents.resolve("app_details"))
        assertNull(SettingsIntents.resolve("app_details", " "))
    }

    @Test
    fun rawActionsAreAllowedOnlyUnderTheSettingsNamespace() {
        assertEquals("android.settings.NFC_SETTINGS", SettingsIntents.resolve("android.settings.NFC_SETTINGS")?.action)
        assertNull(SettingsIntents.resolve("android.intent.action.CALL"))
        assertNull(SettingsIntents.resolve("com.evil.OPEN"))
        assertNull(SettingsIntents.resolve("android.settings."))
        assertNull(SettingsIntents.resolve("android.settings.BAD;rm -rf"))
    }

    @Test
    fun unknownAndEmptyNamesResolveToNothing() {
        assertNull(SettingsIntents.resolve(""))
        assertNull(SettingsIntents.resolve("   "))
        assertNull(SettingsIntents.resolve("teleport"))
    }

    @Test
    fun aRawAppDetailsActionStillNeedsAPackage() {
        assertNull(SettingsIntents.resolve("android.settings.APPLICATION_DETAILS_SETTINGS"))
        assertEquals(
            "package:com.example",
            SettingsIntents.resolve("android.settings.APPLICATION_DETAILS_SETTINGS", "com.example")?.data,
        )
    }
}
