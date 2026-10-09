package com.spendwise.app.data.backup.snapshot

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidFormat(message: String) : CryptoException(message)
    class WrongPasswordOrKey(message: String, cause: Throwable? = null) : CryptoException(message, cause)
    class CorruptedData(message: String, cause: Throwable? = null) : CryptoException(message, cause)
}

@Singleton
class BackupCryptoManager @Inject constructor() {

    companion object {
        private val MAGIC_HEADER = "EXPBK01".toByteArray(StandardCharsets.US_ASCII) // 7 bytes
        private const val ENVELOPE_VERSION: Byte = 1
        private const val GCM_IV_LENGTH = 12 // 96 bits
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val SALT_LENGTH = 16

        // PBKDF2 parameters
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ACCOUNT_BOUND_ITERATIONS = 10000
        private const val PASSWORD_ITERATIONS = 100000
        private const val KEY_LENGTH_BITS = 256

        private const val FIXED_APP_SALT = "SpendWise_GoogleDrive_EncryptedVault_v5"
    }

    private val secureRandom = SecureRandom()

    /**
     * Encrypts compressed payload bytes in Account-bound mode.
     */
    fun encryptAccountBound(payloadBytes: ByteArray, accountEmail: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val secretKey = deriveKeyFromEmail(accountEmail, salt)
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertext = cipher.doFinal(payloadBytes)
        return packEnvelope(SnapshotHeader.ENCRYPTION_MODE_ACCOUNT_BOUND.toByte(), salt, iv, ciphertext)
    }

    /**
     * Encrypts compressed payload bytes in Password-protected mode.
     */
    fun encryptWithPassword(payloadBytes: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val secretKey = deriveKeyFromPassword(password, salt)
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertext = cipher.doFinal(payloadBytes)
        return packEnvelope(SnapshotHeader.ENCRYPTION_MODE_PASSWORD.toByte(), salt, iv, ciphertext)
    }

    /**
     * Encrypts compressed payload bytes with a 64-character hex Recovery Key.
     */
    fun encryptWithRecoveryKey(payloadBytes: ByteArray, recoveryKeyHex: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val keyBytes = hexToBytes(recoveryKeyHex)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertext = cipher.doFinal(payloadBytes)
        return packEnvelope(SnapshotHeader.ENCRYPTION_MODE_RECOVERY_KEY.toByte(), salt, iv, ciphertext)
    }

    /**
     * Inspects envelope without decrypting to determine encryption mode.
     */
    fun inspectEnvelopeMode(encryptedFileBytes: ByteArray): Int {
        if (encryptedFileBytes.size < MAGIC_HEADER.size + 2) {
            throw CryptoException.InvalidFormat("File is too small to be a valid Expenso backup")
        }
        for (i in MAGIC_HEADER.indices) {
            if (encryptedFileBytes[i] != MAGIC_HEADER[i]) {
                throw CryptoException.InvalidFormat("Invalid magic header: not an Expenso backup")
            }
        }
        val modeByte = encryptedFileBytes[MAGIC_HEADER.size + 1]
        return modeByte.toInt()
    }

    /**
     * Decrypts envelope using the appropriate key based on envelope mode.
     */
    fun decrypt(
        encryptedFileBytes: ByteArray,
        accountEmail: String? = null,
        password: CharArray? = null,
        recoveryKeyHex: String? = null
    ): ByteArray {
        if (encryptedFileBytes.size < MAGIC_HEADER.size + 2 + SALT_LENGTH + GCM_IV_LENGTH + 16) {
            throw CryptoException.InvalidFormat("Backup file is too small or truncated")
        }

        // Verify magic header
        for (i in MAGIC_HEADER.indices) {
            if (encryptedFileBytes[i] != MAGIC_HEADER[i]) {
                throw CryptoException.InvalidFormat("Invalid magic header: not an Expenso backup")
            }
        }

        val buffer = ByteBuffer.wrap(encryptedFileBytes)
        buffer.position(MAGIC_HEADER.size)

        val version = buffer.get()
        if (version != ENVELOPE_VERSION) {
            throw CryptoException.InvalidFormat("Unsupported backup envelope version: $version")
        }

        val mode = buffer.get().toInt()

        val salt = ByteArray(SALT_LENGTH)
        buffer.get(salt)

        val iv = ByteArray(GCM_IV_LENGTH)
        buffer.get(iv)

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val secretKey: SecretKey = when (mode) {
            SnapshotHeader.ENCRYPTION_MODE_ACCOUNT_BOUND -> {
                if (accountEmail.isNullOrBlank()) {
                    throw CryptoException.WrongPasswordOrKey("Account email required for account-bound backup")
                }
                deriveKeyFromEmail(accountEmail, salt)
            }
            SnapshotHeader.ENCRYPTION_MODE_PASSWORD -> {
                if (password == null || password.isEmpty()) {
                    if (!recoveryKeyHex.isNullOrBlank()) {
                        SecretKeySpec(hexToBytes(recoveryKeyHex), "AES")
                    } else {
                        throw CryptoException.WrongPasswordOrKey("Password or Recovery Key required for this backup")
                    }
                } else {
                    deriveKeyFromPassword(password, salt)
                }
            }
            SnapshotHeader.ENCRYPTION_MODE_RECOVERY_KEY -> {
                if (recoveryKeyHex.isNullOrBlank()) {
                    throw CryptoException.WrongPasswordOrKey("64-character Recovery Key required for this backup")
                }
                SecretKeySpec(hexToBytes(recoveryKeyHex), "AES")
            }
            else -> throw CryptoException.InvalidFormat("Unknown encryption mode in backup: $mode")
        }

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw CryptoException.WrongPasswordOrKey("Incorrect password or corrupted authentication tag", e)
        } catch (e: Exception) {
            throw CryptoException.CorruptedData("Decryption failed: ${e.message}", e)
        }
    }

    /**
     * Generates a secure random 64-character (256-bit) hexadecimal recovery key.
     */
    fun generateRecoveryKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytesToHex(bytes)
    }

    private fun packEnvelope(mode: Byte, salt: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val totalSize = MAGIC_HEADER.size + 1 + 1 + salt.size + iv.size + ciphertext.size
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.put(MAGIC_HEADER)
        buffer.put(ENVELOPE_VERSION)
        buffer.put(mode)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    private fun deriveKeyFromEmail(email: String, salt: ByteArray): SecretKey {
        val combined = "$email:$FIXED_APP_SALT".toCharArray()
        val spec = PBEKeySpec(combined, salt, ACCOUNT_BOUND_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }

    private fun deriveKeyFromPassword(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PASSWORD_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().replace(" ", "").replace("-", "")
        if (clean.length != 64) {
            throw IllegalArgumentException("Recovery key must be exactly 64 hex characters (32 bytes)")
        }
        val bytes = ByteArray(32)
        for (i in 0 until 32) {
            val byteStr = clean.substring(i * 2, i * 2 + 2)
            bytes[i] = byteStr.toInt(16).toByte()
        }
        return bytes
    }
}
