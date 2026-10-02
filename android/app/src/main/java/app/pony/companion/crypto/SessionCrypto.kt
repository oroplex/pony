package app.pony.companion.crypto

import com.google.crypto.tink.subtle.ChaCha20Poly1305
import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import app.pony.companion.proto.AEAD_AAD
import app.pony.companion.proto.HKDF_INFO
import app.pony.companion.proto.SAFETY_INFO
import java.security.MessageDigest
import java.util.Base64

data class KeyPairBytes(val privateKey: ByteArray, val publicKey: ByteArray)

data class SessionKeys(
    val send: ByteArray,
    val recv: ByteArray,
    val shared: ByteArray,
    val safetyCode: String,
)

object SessionCrypto {
    fun generateKeyPair(): KeyPairBytes {
        val privateKey = X25519.generatePrivateKey()
        return KeyPairBytes(privateKey, X25519.publicFromPrivate(privateKey))
    }

    fun derive(
        privateKey: ByteArray,
        peerPublicKey: ByteArray,
        tokenHex: String,
        role: String,
    ): SessionKeys {
        val shared = X25519.computeSharedSecret(privateKey, peerPublicKey)
        val salt = hexToBytes(tokenHex)
        val okm = Hkdf.computeHkdf("HMACSHA256", shared, salt, HKDF_INFO.toByteArray(), 64)
        val phoneToBot = okm.copyOfRange(0, 32)
        val botToPhone = okm.copyOfRange(32, 64)
        val send = if (role == "phone") phoneToBot else botToPhone
        val recv = if (role == "phone") botToPhone else phoneToBot
        return SessionKeys(send, recv, shared, safetyCode(shared, salt))
    }

    fun safetyCode(shared: ByteArray, token: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(shared)
        md.update(token)
        md.update(SAFETY_INFO.toByteArray())
        val digest = md.digest()
        val n = ((digest[0].toInt() and 0xff shl 16) or
            (digest[1].toInt() and 0xff shl 8) or
            (digest[2].toInt() and 0xff)) % 1_000_000
        return n.toString().padStart(6, '0')
    }

    fun formatSafety(code: String): String = "${code.substring(0, 3)}-${code.substring(3)}"

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val aead = ChaCha20Poly1305(key)
        return aead.encrypt(plaintext, AEAD_AAD.toByteArray())
    }

    fun decrypt(key: ByteArray, frame: ByteArray): ByteArray {
        val aead = ChaCha20Poly1305(key)
        return aead.decrypt(frame, AEAD_AAD.toByteArray())
    }

    fun b64urlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun b64urlDecode(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value)

    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
