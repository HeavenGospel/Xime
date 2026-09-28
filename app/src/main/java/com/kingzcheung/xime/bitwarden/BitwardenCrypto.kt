package com.kingzcheung.xime.bitwarden

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor
import kotlin.math.ceil

/**
 * Bitwarden 客户端侧加解密（与官方 jslib 算法对齐）。
 *
 * 注意：主密码哈希二次 PBKDF2 的 password 是 **masterKey 原始字节**。
 * Android `PBEKeySpec(char[])` 可能把 char 再按 UTF-8 编码，导致哈希错误；
 * 因此这里用 RFC2898 的字节版 PBKDF2-HMAC-SHA256。
 */
object BitwardenCrypto {
    const val KDF_PBKDF2 = 0
    const val KDF_ARGON2ID = 1
    private const val TYPE_AES_CBC_256_HMAC = 2

    private val secureRandom = SecureRandom()
    private val argon2 by lazy { Argon2Kt() }

    data class SymmetricKey(val encKey: ByteArray, val macKey: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SymmetricKey) return false
            return encKey.contentEquals(other.encKey) && macKey.contentEquals(other.macKey)
        }

        override fun hashCode(): Int = 31 * encKey.contentHashCode() + macKey.contentHashCode()
    }

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun makeMasterKey(
        password: String,
        email: String,
        kdf: Int,
        iterations: Int,
        memoryMb: Int?,
        parallelism: Int?,
    ): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val salt = normalizeEmail(email).toByteArray(Charsets.UTF_8)
        return when (kdf) {
            KDF_ARGON2ID -> {
                val saltHash = sha256(salt)
                val mem = (memoryMb ?: 64).coerceAtLeast(16)
                val para = (parallelism ?: 4).coerceAtLeast(1)
                argon2.hash(
                    mode = Argon2Mode.ARGON2_ID,
                    password = passwordBytes,
                    salt = saltHash,
                    tCostInIterations = iterations.coerceAtLeast(1),
                    mCostInKibibyte = mem * 1024,
                    parallelism = para,
                    hashLengthInBytes = 32,
                ).rawHashAsByteArray()
            }
            else -> pbkdf2HmacSha256(
                password = passwordBytes,
                salt = salt,
                iterations = iterations.coerceAtLeast(1),
                dkLen = 32,
            )
        }
    }

    /** masterPasswordHash = PBKDF2(masterKeyBytes, passwordUtf8, 1) → Base64 */
    fun hashMasterPassword(masterKey: ByteArray, password: String): String {
        val hash = pbkdf2HmacSha256(
            password = masterKey,
            salt = password.toByteArray(Charsets.UTF_8),
            iterations = 1,
            dkLen = 32,
        )
        return Base64.getEncoder().encodeToString(hash)
    }

    fun stretchMasterKey(masterKey: ByteArray): SymmetricKey {
        val enc = hkdfExpand(masterKey, "enc".toByteArray(Charsets.UTF_8), 32)
        val mac = hkdfExpand(masterKey, "mac".toByteArray(Charsets.UTF_8), 32)
        return SymmetricKey(enc, mac)
    }

    fun decryptToSymmetricKey(cipherString: String, key: SymmetricKey): SymmetricKey {
        val raw = decrypt(cipherString, key)
        return when (raw.size) {
            64 -> SymmetricKey(raw.copyOfRange(0, 32), raw.copyOfRange(32, 64))
            32 -> stretchMasterKey(raw)
            else -> throw IllegalStateException("无法解析用户密钥（长度 ${raw.size}）")
        }
    }

    fun decrypt(cipherString: String?, key: SymmetricKey): ByteArray {
        if (cipherString.isNullOrBlank()) return ByteArray(0)
        val parsed = parseCipherString(cipherString)
        if (parsed.type != TYPE_AES_CBC_256_HMAC) {
            throw IllegalStateException("暂不支持的密文类型: ${parsed.type}")
        }
        val computed = hmacSha256(key.macKey, parsed.iv + parsed.data)
        if (!constantTimeEquals(computed, parsed.mac)) {
            throw IllegalStateException("MAC 校验失败（主密码或密钥不正确）")
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.encKey, "AES"), IvParameterSpec(parsed.iv))
        return cipher.doFinal(parsed.data)
    }

    fun decryptUtf8(cipherString: String?, key: SymmetricKey): String {
        val bytes = decrypt(cipherString, key)
        if (bytes.isEmpty()) return ""
        return bytes.toString(Charsets.UTF_8)
    }

    fun encryptUtf8(plain: String, key: SymmetricKey): String {
        val iv = ByteArray(16).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.encKey, "AES"), IvParameterSpec(iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val mac = hmacSha256(key.macKey, iv + data)
        return "$TYPE_AES_CBC_256_HMAC.${b64(iv)}|${b64(data)}|${b64(mac)}"
    }

    fun generatePassword(options: PasswordGeneratorOptions = PasswordGeneratorOptions()): String {
        val opts = options.sanitized()
        val lower = if (opts.avoidAmbiguous) "abcdefghijkmnopqrstuvwxyz" else "abcdefghijklmnopqrstuvwxyz"
        val upper = if (opts.avoidAmbiguous) "ABCDEFGHJKLMNPQRSTUVWXYZ" else "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val nums = if (opts.avoidAmbiguous) "23456789" else "0123456789"
        val specials = "!@#$%^&*"
        val pools = buildList {
            if (opts.lowercase) add(lower)
            if (opts.uppercase) add(upper)
            if (opts.numbers) add(nums)
            if (opts.special) add(specials)
        }
        val alphabet = pools.joinToString("")
        // 尽量保证每类至少各取 1 个
        val required = pools.map { pool ->
            pool[(secureRandom.nextInt(pool.length))]
        }.toMutableList()
        while (required.size < opts.length) {
            required += alphabet[secureRandom.nextInt(alphabet.length)]
        }
        required.shuffle(secureRandom)
        return required.take(opts.length).joinToString("")
    }

    private fun <T> MutableList<T>.shuffle(random: SecureRandom) {
        for (i in size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = this[i]
            this[i] = this[j]
            this[j] = tmp
        }
    }

    /**
     * RFC 2898 PBKDF2-HMAC-SHA256，password/salt 均为原始字节（与 WebCrypto / Node 一致）。
     */
    internal fun pbkdf2HmacSha256(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        dkLen: Int,
    ): ByteArray {
        require(iterations >= 1)
        require(dkLen > 0)
        val hLen = 32
        val l = ceil(dkLen.toDouble() / hLen).toInt()
        val result = ByteArray(l * hLen)
        var offset = 0
        for (block in 1..l) {
            val u = pbkdf2Block(password, salt, iterations, block)
            System.arraycopy(u, 0, result, offset, hLen)
            offset += hLen
        }
        return result.copyOf(dkLen)
    }

    private fun pbkdf2Block(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        blockIndex: Int,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val blockIndexBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(blockIndex).array()
        var u = mac.doFinal(salt + blockIndexBytes)
        val result = u.copyOf()
        for (i in 2..iterations) {
            mac.reset()
            mac.init(SecretKeySpec(password, "HmacSHA256"))
            u = mac.doFinal(u)
            for (j in result.indices) {
                result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        return result
    }

    private data class ParsedCipher(
        val type: Int,
        val iv: ByteArray,
        val data: ByteArray,
        val mac: ByteArray,
    )

    private fun parseCipherString(value: String): ParsedCipher {
        val dot = value.indexOf('.')
        require(dot > 0) { "非法 CipherString" }
        val type = value.substring(0, dot).toInt()
        val parts = value.substring(dot + 1).split('|')
        require(parts.size >= 3) { "CipherString 缺少 mac" }
        return ParsedCipher(type, b64d(parts[0]), b64d(parts[1]), b64d(parts[2]))
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        var previous = ByteArray(0)
        val result = ByteArray(length)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val copy = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, result, offset, copy)
            offset += copy
            counter++
        }
        return result
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun b64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

    private fun b64d(data: String): ByteArray = Base64.getDecoder().decode(data)

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i] xor b[i]).toInt()
        return r == 0
    }
}
