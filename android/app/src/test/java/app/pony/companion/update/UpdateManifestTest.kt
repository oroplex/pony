package app.pony.companion.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateManifestTest {
    private val sha = "a".repeat(64)

    @Test
    fun parsesTheLatestManifest() {
        val manifest = UpdateManifests.parse(
            """{"versionCode":6,"versionName":"0.5.1","apk":"pony-0.5.1.apk","sha256":"$sha","size":1234,
               "publishedAt":"2026-10-02","minSdk":29,"changelog":["Faster reconnects","Calmer bubble"]}""",
        )
        assertEquals(6, manifest.versionCode)
        assertEquals("0.5.1", manifest.versionName)
        assertEquals("https://download.pony.karlmagendavid.com/pony-0.5.1.apk", manifest.apkUrl)
        assertEquals(listOf("Faster reconnects", "Calmer bubble"), manifest.changelog)
        assertTrue(UpdateManifests.isNewer(manifest, 5))
        assertFalse(UpdateManifests.isNewer(manifest, 6))
        assertTrue(UpdateManifests.supports(manifest, 36))
        assertFalse(UpdateManifests.supports(manifest, 28))
    }

    @Test
    fun aChangelogStringBecomesLines() {
        val manifest = UpdateManifests.parse(
            """{"versionCode":7,"versionName":"0.6.0","url":"https://download.pony.karlmagendavid.com/a/b.apk","sha256":"$sha",
               "changelog":"- One\n- Two\n\n• Three"}""",
        )
        assertEquals(listOf("One", "Two", "Three"), manifest.changelog)
        assertEquals("https://download.pony.karlmagendavid.com/a/b.apk", manifest.apkUrl)
    }

    @Test
    fun downloadsMustBeHttpsFromThePonyHost() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifests.parse("""{"versionCode":6,"versionName":"x","apk":"http://download.pony.karlmagendavid.com/p.apk","sha256":"$sha"}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateManifests.parse("""{"versionCode":6,"versionName":"x","apk":"https://evil.example/p.apk","sha256":"$sha"}""")
        }
        assertThrows(IllegalStateException::class.java) {
            UpdateManifests.parse("""{"versionCode":6,"versionName":"x","apk":"p.apk","sha256":"nope"}""")
        }
    }

    @Test
    fun checksumsAreVerified() {
        val file = File.createTempFile("pony", ".apk").apply { writeText("pony") }
        val digest = file.inputStream().use { UpdateManifests.sha256(it) }
        assertEquals(64, digest.length)
        assertTrue(UpdateManifests.matches(file, digest.uppercase()))
        assertFalse(UpdateManifests.matches(file, sha))
    }
}
