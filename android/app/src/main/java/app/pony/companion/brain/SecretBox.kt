package app.pony.companion.brain

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CipherBlob(val iv: String, val ct: String)

interface SecretBox {
    fun encrypt(plain: ByteArray): CipherBlob
    fun decrypt(blob: CipherBlob): ByteArray
}

/** AES-GCM box whose key lives only in memory. Tests and the file format use this contract. */
class JceSecretBox(private val key: SecretKey) : SecretBox {
    override fun encrypt(plain: ByteArray): CipherBlob {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return CipherBlob(b64(iv), b64(cipher.doFinal(plain)))
    }

    override fun decrypt(blob: CipherBlob): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b64d(blob.iv)))
        return cipher.doFinal(b64d(blob.ct))
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun b64d(text: String): ByteArray = Base64.getDecoder().decode(pad(text))

    companion object {
        fun random(): JceSecretBox {
            val generator = KeyGenerator.getInstance("AES")
            generator.init(256)
            return JceSecretBox(generator.generateKey())
        }
    }
}

/**
 * Writes ciphertext only. The plaintext key is never a JSON field.
 * [box] is Android Keystore on the phone and an in-memory AES key in tests.
 */
class FileKeyVault(private val file: java.io.File, private val box: SecretBox) {
    @Synchronized
    fun put(id: String, secret: String) {
        val all = read().toMutableMap()
        all[id] = box.encrypt(secret.toByteArray(Charsets.UTF_8))
        write(all)
    }

    @Synchronized
    fun get(id: String): String? {
        val blob = read()[id] ?: return null
        return box.decrypt(blob).toString(Charsets.UTF_8)
    }

    @Synchronized
    fun delete(id: String) {
        val all = read().toMutableMap()
        if (all.remove(id) != null) write(all)
    }

    /** Wipes every sealed key. The ciphertext file is removed, not just emptied. */
    @Synchronized
    fun clear() {
        file.delete()
    }

    @Synchronized
    fun raw(): String = if (file.exists()) file.readText() else ""

    private fun read(): Map<String, CipherBlob> {
        if (!file.exists() || file.length() == 0L) return emptyMap()
        val root = JsonValue.parse(file.readText()) as? JsonValue.Obj ?: return emptyMap()
        val items = root.get("secrets") as? JsonValue.Arr ?: return emptyMap()
        return buildMap {
            items.items.forEach { item ->
                val obj = item as? JsonValue.Obj ?: return@forEach
                val id = (obj.get("id") as? JsonValue.Str)?.value ?: return@forEach
                val iv = (obj.get("iv") as? JsonValue.Str)?.value ?: return@forEach
                val ct = (obj.get("ct") as? JsonValue.Str)?.value ?: return@forEach
                put(id, CipherBlob(iv, ct))
            }
        }
    }

    private fun write(all: Map<String, CipherBlob>) {
        file.parentFile?.mkdirs()
        val body = JsonValue.obj(
            "secrets" to JsonValue.arr(all.map { (id, blob) ->
                JsonValue.obj(
                    "id" to JsonValue.str(id),
                    "iv" to JsonValue.str(blob.iv),
                    "ct" to JsonValue.str(blob.ct),
                )
            }),
        )
        file.writeText(body.encode())
    }
}

private fun pad(text: String): String {
    val mod = text.length % 4
    return if (mod == 0) text else text + "=".repeat(4 - mod)
}
