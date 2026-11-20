package dev.leafledger.android

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** LEAFBK01 | 16-byte salt | 12-byte nonce | AES-256-GCM ciphertext (incl. tag).
 * The complete header is authenticated. The password is never persisted. */
object EncryptedBackup {
    private val magic = "LEAFBK01".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 210_000
    const val MAX_BYTES = 100 * 1024 * 1024
    private fun key(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun encrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 8) { "密码至少 8 个字符" }
        require(bytes.size <= MAX_BYTES) { "备份过大" }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes); val nonce = ByteArray(12).also(random::nextBytes)
        val header = magic + salt + nonce
        val derived = key(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header); header + cipher.doFinal(bytes)
        } finally { derived.fill(0); password.fill('\u0000') }
    }
    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(bytes.size in 52..MAX_BYTES + 52 && bytes.copyOfRange(0, 8).contentEquals(magic)) { "不支持的账本备份格式" }
        val derived = key(password, bytes.copyOfRange(8, 24))
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, bytes.copyOfRange(24, 36)))
            cipher.updateAAD(bytes, 0, 36); cipher.doFinal(bytes, 36, bytes.size - 36)
        } catch (e: javax.crypto.AEADBadTagException) { throw IllegalArgumentException("密码错误或备份已损坏", e) }
        finally { derived.fill(0); password.fill('\u0000') }
    }
}
