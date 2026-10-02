package app.pony.companion.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {
    @Test
    fun localAndEnglishComeFirst() {
        val names = listOf(
            "ja-jp-local",
            "en-us-x-sfg-local",
            "fr-fr-local",
            "en_us-network-off",
            "de-de-local",
            "en-gb-local",
        )
        val ordered = VoiceCatalog.ordered(names, "en")
        assertEquals("en-gb-local", ordered[0])
        assertEquals("en-us-x-sfg-local", ordered[1])
        assertEquals("en_us-network-off", ordered[2])
    }

    @Test
    fun deviceLanguageOutranksEnglish() {
        val names = listOf("en-us-local", "fr-fr-local", "de-de-local", "es-es-local")
        val ordered = VoiceCatalog.ordered(names, "fr")
        assertEquals("fr-fr-local", ordered[0])
        assertEquals("en-us-local", ordered[1])
    }

    @Test
    fun capsTheListAtTwelve() {
        val names = (1..20).map { "voice-$it" }
        assertEquals(12, VoiceCatalog.ordered(names, "en").size)
    }

    @Test
    fun naturalVoicesRankAboveLegacyWithinALanguage() {
        val options = listOf(
            VoiceCatalog.Option("en-us-legacy", "en-US", VoiceCatalog.QUALITY_LOW),
            VoiceCatalog.Option("en-us-neural", "en-US", VoiceCatalog.QUALITY_VERY_HIGH),
            VoiceCatalog.Option("en-us-standard", "en-US", VoiceCatalog.QUALITY_NORMAL),
        )
        val ordered = VoiceCatalog.ordered(options, "en", allowCloud = false).map { it.name }
        assertEquals(listOf("en-us-neural", "en-us-standard", "en-us-legacy"), ordered)
    }

    @Test
    fun cloudVoicesAppearOnlyWhenAllowed() {
        val options = listOf(
            VoiceCatalog.Option("en-us-local", "en-US", VoiceCatalog.QUALITY_NORMAL, networkRequired = false),
            VoiceCatalog.Option("en-us-cloud", "en-US", VoiceCatalog.QUALITY_VERY_HIGH, networkRequired = true),
        )
        assertEquals(listOf("en-us-local"), VoiceCatalog.ordered(options, "en", allowCloud = false).map { it.name })
        // When cloud is allowed the higher-quality network voice is offered, and ranks first.
        assertEquals(listOf("en-us-cloud", "en-us-local"), VoiceCatalog.ordered(options, "en", allowCloud = true).map { it.name })
    }

    @Test
    fun deviceLanguageStillOutranksAHigherQualityEnglishVoice() {
        val options = listOf(
            VoiceCatalog.Option("en-us-neural", "en-US", VoiceCatalog.QUALITY_VERY_HIGH),
            VoiceCatalog.Option("he-il-standard", "he-IL", VoiceCatalog.QUALITY_NORMAL),
        )
        assertEquals("he-il-standard", VoiceCatalog.ordered(options, "he", allowCloud = true).first().name)
    }

    @Test
    fun tiersAndLabelsAreReadable() {
        assertEquals("Neural", VoiceCatalog.tier(VoiceCatalog.QUALITY_VERY_HIGH))
        assertEquals("Enhanced", VoiceCatalog.tier(VoiceCatalog.QUALITY_HIGH))
        assertEquals("Basic", VoiceCatalog.tier(VoiceCatalog.QUALITY_LOW))
        val label = VoiceCatalog.label(VoiceCatalog.Option("en-us-x-iol-local", "en-US", VoiceCatalog.QUALITY_HIGH))
        assertTrue(label.contains("English"))
        assertTrue(label.endsWith("Enhanced"))
        // A nameless locale still produces a non-empty place rather than raw engine ids.
        assertFalse(VoiceCatalog.place(VoiceCatalog.Option("en-us-x-sfg-local", "en-US")).contains("x-sfg"))
    }
}
