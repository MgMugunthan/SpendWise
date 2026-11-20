package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.domain.model.BankAccountModel
import com.example.domain.model.CategoryModel
import com.example.domain.model.MoneyFormatter
import com.example.domain.model.TransactionModel
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Local-first cryptographic & export utility for SpendWise:
 * - Android Keystore AES-256-GCM key provisioning (hardware-backed when supported by device).
 * - Salted PBKDF2-HMAC-SHA256 PIN hashing (never stores plaintext PINs).
 * - Passphrase-derived AES-256-GCM encrypted backup files for safe cross-device migration.
 * - SHA-256 notification fingerprinting for duplicate suppression without retaining raw SMS text.
 * - RFC-4180 compliant CSV export.
 */
object SpendWiseCryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "spendwise_master_aes_key"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val AES_KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val SALT_BYTES = 16
    private const val BACKUP_HEADER = "SPENDWISE_ENC_V1"

    private val secureRandom = SecureRandom()

    fun ensureKeystoreMasterKey(): Boolean {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_BITS)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            true
        }.getOrDefault(false)
    }

    fun isKeystoreKeyReady(): Boolean {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.containsAlias(KEYSTORE_ALIAS)
        }.getOrDefault(false)
    }

    fun hashPin(pin: String): Pair<String, String> {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val derived = derivePbkdf2(pin.toCharArray(), salt, AES_KEY_BITS)
        return Base64.encodeToString(derived, Base64.NO_WRAP) to Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, storedHashB64: String, storedSaltB64: String): Boolean {
        return runCatching {
            val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
            val expected = Base64.decode(storedHashB64, Base64.NO_WRAP)
            val actual = derivePbkdf2(pin.toCharArray(), salt, AES_KEY_BITS)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Encrypts backup JSON using AES-256-GCM with a key derived from the user's backup passphrase
     * via PBKDF2-HMAC-SHA256 (100,000 iterations) + random salt + random 12-byte IV.
     */
    fun encryptBackupPayload(plainJson: String, passphrase: String): String {
        require(passphrase.length >= 6) { "Passphrase must be at least 6 characters" }
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val keyBytes = derivePbkdf2(passphrase.toCharArray(), salt, AES_KEY_BITS)
        val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainJson.toByteArray(StandardCharsets.UTF_8))

        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "$BACKUP_HEADER:$saltB64:$ivB64:$cipherB64"
    }

    fun decryptBackupPayload(encryptedContent: String, passphrase: String): Result<String> {
        return runCatching {
            val parts = encryptedContent.trim().split(":")
            require(parts.size == 4 && parts[0] == BACKUP_HEADER) {
                "Invalid SpendWise encrypted backup file format."
            }
            val salt = Base64.decode(parts[1], Base64.NO_WRAP)
            val iv = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[3], Base64.NO_WRAP)
            val keyBytes = derivePbkdf2(passphrase.toCharArray(), salt, AES_KEY_BITS)
            val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        }
    }

    fun buildTransactionsCsv(
        transactions: List<TransactionModel>,
        accounts: List<BankAccountModel>,
        categories: List<CategoryModel>
    ): String {
        val accountMap = accounts.associateBy { it.accountId }
        val categoryMap = categories.associateBy { it.categoryId }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("Date,Type,Merchant/Counterparty,Category,Account,Currency,Amount,PaymentMethod,Reference,Source,Status,Notes")
        for (tx in transactions) {
            val accLabel = accountMap[tx.accountId]?.let { "${it.bankName} (XX${it.accountNumberSuffix})" } ?: "Unassigned"
            val catLabel = categoryMap[tx.categoryId]?.categoryName ?: "Uncategorized"
            val amountStr = MoneyFormatter.minorToEditableString(tx.amountMinorUnits, tx.currencyCode)
            val row = listOf(
                dateFormat.format(Date(tx.transactionDateMillis)),
                tx.transactionType.name,
                tx.displayCounterparty,
                catLabel,
                accLabel,
                tx.currencyCode,
                amountStr,
                tx.paymentMethod.displayName,
                tx.referenceNumber.orEmpty(),
                tx.sourceType.displayName,
                tx.transactionStatus.displayName,
                tx.description
            ).joinToString(",") { csvEscape(it) }
            sb.appendLine(row)
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun derivePbkdf2(password: CharArray, salt: ByteArray, keyLengthBits: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, keyLengthBits)
        return factory.generateSecret(spec).encoded
    }
}
