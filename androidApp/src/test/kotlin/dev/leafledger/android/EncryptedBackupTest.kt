package dev.leafledger.android

import org.junit.Assert.*
import org.junit.Test

class EncryptedBackupTest {
    @Test fun roundTripAndRandomization() {
        val data = "账本数据 ¥28.60".toByteArray()
        val one = EncryptedBackup.encrypt(data, "test-password".toCharArray())
        val two = EncryptedBackup.encrypt(data, "test-password".toCharArray())
        assertFalse(one.contentEquals(two))
        assertArrayEquals(data, EncryptedBackup.decrypt(one, "test-password".toCharArray()))
    }
    @Test fun wrongPasswordAndTamperingAreRejected() {
        val data = EncryptedBackup.encrypt("ledger".toByteArray(), "test-password".toCharArray())
        for (position in listOf(8, 24, data.lastIndex)) {
            val corrupt = data.copyOf(); corrupt[position] = (corrupt[position].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { EncryptedBackup.decrypt(corrupt, "test-password".toCharArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackup.decrypt(data, "wrong-password".toCharArray()) }
    }
    @Test fun emptyAndUnsupportedFilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackup.decrypt(byteArrayOf(), "test-password".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { EncryptedBackup.encrypt(byteArrayOf(), "short".toCharArray()) }
    }
}
