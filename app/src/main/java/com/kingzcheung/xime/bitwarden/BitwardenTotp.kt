package com.kingzcheung.xime.bitwarden

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Bitwarden 登录项 TOTP（RFC 6238 / otpauth）。
 * 支持原始 Base32 密钥或 otpauth://totp/... URI。
 */
object BitwardenTotp {
    fun code(secretOrUri: String, timeMillis: Long = System.currentTimeMillis()): String? {
        val parsed = parse(secretOrUri) ?: return null
        return try {
            generate(parsed.secret, parsed.digits, parsed.period, timeMillis)
        } catch (_: Exception) {
            null
        }
    }

    data class Parsed(
        val secret: ByteArray,
        val digits: Int = 6,
        val period: Int = 30,
    )

    fun parse(raw: String): Parsed? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        return if (s.startsWith("otpauth://", ignoreCase = true)) {
            parseOtpAuth(s)
        } else {
            val secret = decodeBase32(s.filter { !it.isWhitespace() && it != '-' }) ?: return null
            Parsed(secret)
        }
    }

    private fun parseOtpAuth(uri: String): Parsed? {
        val q = uri.substringAfter('?', "")
        val params = q.split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null
            else part.substring(0, i) to java.net.URLDecoder.decode(part.substring(i + 1), Charsets.UTF_8)
        }.toMap()
        val secretRaw = params["secret"] ?: return null
        val secret = decodeBase32(secretRaw) ?: return null
        val digits = params["digits"]?.toIntOrNull()?.coerceIn(6, 8) ?: 6
        val period = params["period"]?.toIntOrNull()?.coerceAtLeast(15) ?: 30
        return Parsed(secret, digits, period)
    }

    private fun generate(secret: ByteArray, digits: Int, period: Int, timeMillis: Long): String {
        val counter = timeMillis / 1000L / period
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(data)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val mod = 10.0.pow(digits).toInt()
        return (binary % mod).toString().padStart(digits, '0')
    }

    private fun decodeBase32(input: String): ByteArray? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.uppercase().replace("=", "")
        if (cleaned.isEmpty() || cleaned.any { it !in alphabet }) return null
        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>()
        for (c in cleaned) {
            val v = alphabet.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out += ((buffer shr bitsLeft) and 0xff).toByte()
            }
        }
        return out.toByteArray()
    }
}

data class PasswordGeneratorOptions(
    val length: Int = 20,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val numbers: Boolean = true,
    val special: Boolean = true,
    val avoidAmbiguous: Boolean = true,
) {
    fun sanitized(): PasswordGeneratorOptions {
        var opts = copy(length = length.coerceIn(5, 128))
        if (!opts.lowercase && !opts.uppercase && !opts.numbers && !opts.special) {
            opts = opts.copy(lowercase = true, uppercase = true, numbers = true)
        }
        return opts
    }
}

/** Bitwarden 自定义字段：0=文本 1=隐藏 2=布尔 */
data class VaultCustomField(
    val type: Int = 0,
    val name: String = "",
    val value: String = "",
)
