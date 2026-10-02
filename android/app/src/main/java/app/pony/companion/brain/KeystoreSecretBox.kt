package app.pony.companion.brain

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM key that never leaves the Android Keystore.
 * The phone build uses this box. JVM tests use [JceSecretBox] with the same file format.
 */
class KeystoreSecretBox(private val alias: String = ALIAS) : SecretBox {
    private val key: SecretKey

    init {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE)
        store.load(null)
        key = if (store.containsAlias(alias)) {
            store.getKey(alias, null) as SecretKey
        } else {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generator.generateKey()
        }
    }

    override fun encrypt(plain: ByteArray): CipherBlob {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return CipherBlob(b64(iv), b64(ct))
    }

    override fun decrypt(blob: CipherBlob): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b64d(blob.iv)))
        return cipher.doFinal(b64d(blob.ct))
    }

    private fun b64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun b64d(text: String): ByteArray {
        val mod = text.length % 4
        val padded = if (mod == 0) text else text + "=".repeat(4 - mod)
        return java.util.Base64.getDecoder().decode(padded)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "pony_brain_keys"
        const val SESSION_ALIAS = "pony_session_keys"
        const val MEMORY_ALIAS = "pony_memory_keys"
    }
}
