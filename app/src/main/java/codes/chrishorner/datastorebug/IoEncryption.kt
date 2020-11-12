package codes.chrishorner.datastorebug

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import okio.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

internal object IoEncryption {
    private const val KEY_ALIAS = "IoEncryption"
    private const val KEY_TYPE = "AndroidKeyStore"
    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEY_TYPE)
        keyStore.load(null)

        // Check if we have an existing key and use that if possible.
        val existingEntry = keyStore.getEntry(KEY_ALIAS, null)
        val existingKey = (existingEntry as? KeyStore.SecretKeyEntry)?.secretKey
        if (existingKey != null) return existingKey

        val keyParams = KeyGenParameterSpec
            .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true) // Ensure that encryption ciphers generate a random initialisation vector.
            .build()

        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, KEY_TYPE)
        keyGenerator.init(keyParams)

        return keyGenerator.generateKey()
    }

    /**
     * Wrap an [OutputStream] into a [Sink] that encrypts all data written to it.
     */
    fun getEncryptedSink(output: OutputStream): Sink {
        // When creating the encryption Cipher, we deliberately don't provide an initialisation
        // vector. (The IvParameterSpec argument). Because we specify that randomised
        // encryption is _required_ when generating our key, we take the randomly generated IV from
        // the newly created Cipher. That IV along with its size are stored unencrypted at the
        // beginning of the output.
        //
        // This is OK, as IVs are designed to be publicly readable. It's their randomness that matters.
        // https://en.wikipedia.org/wiki/Block_cipher_mode_of_operation
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        return output.sink().buffer()
            .writeInt(cipher.iv.size) // First write the size of the cipher's IV.
            .write(cipher.iv) // Then write the IV itself.
            .apply { flush() }
            .cipherSink(cipher) // Finally return a Sink that a consumer can write to that'll be encrypted.
    }

    /**
     * Wrap an [InputStream] into a [Source] that decrypts all dead read from it, assuming
     * that the data was originally written with [getEncryptedSink].
     */
    fun getDecryptedSource(input: InputStream): Source {
        // First we start by reading the data raw without a Cipher, as we need to first
        // retrieve the unencrypted initialisation vector to create the Cipher.
        val source = input.source().buffer()

        val cipher = try {
            val ivSize = source.readInt() // Assume that the first item written to the source was the IV size.
            val iv = source.readByteArray(ivSize.toLong()) // Assume that the second item written was the IV itself.

            // Create a decryption Cipher once we've read the IV.
            Cipher.getInstance(TRANSFORMATION).apply {
                val ivParameterSpec = IvParameterSpec(iv)
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), ivParameterSpec)
            }
        } catch (e: IOException) {
            try {
                source.close()
            } catch (inner: IOException) {
                e.addSuppressed(inner)
            }
            throw e
        }

        // Finally return a Source that a consumer can read decrypted data from.
        return source.cipherSource(cipher)
    }
}
