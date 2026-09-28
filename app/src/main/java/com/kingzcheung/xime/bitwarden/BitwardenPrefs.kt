package com.kingzcheung.xime.bitwarden

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom

object BitwardenPrefs {
    private const val TAG = "BitwardenPrefs"
    private const val PREFS = "xime_bitwarden_secure"
    private const val PLAIN = "xime_bitwarden"

    private const val KEY_PRESET = "server_preset"
    private const val KEY_SELF_HOSTED = "self_hosted_base"
    private const val KEY_RESOLVED_IDENTITY = "resolved_identity"
    private const val KEY_RESOLVED_API = "resolved_api"
    private const val KEY_EMAIL = "email"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_ACCESS_EXPIRY = "access_expiry_ms"
    /** 解锁后的用户对称密钥（enc||mac，64 字节 Base64），经 EncryptedSharedPreferences 落盘。 */
    private const val KEY_USER_SYM = "user_sym_key_b64"
    private const val KEY_USER_KEY_ENC = "user_key_cipher" // legacy
    private const val KEY_PIN_MODE = "pin_mode"
    private const val KEY_PIN_TIMEOUT_MIN = "pin_timeout_min"
    private const val KEY_PIN_SALT = "pin_salt_b64"
    private const val KEY_PIN_HASH = "pin_hash_b64"
    private const val KEY_PIN_LAST_OK = "pin_last_ok_ms"
    private const val KEY_GEN_LENGTH = "pw_gen_length"
    private const val KEY_GEN_LOWER = "pw_gen_lower"
    private const val KEY_GEN_UPPER = "pw_gen_upper"
    private const val KEY_GEN_NUMBER = "pw_gen_number"
    private const val KEY_GEN_SPECIAL = "pw_gen_special"
    private const val KEY_GEN_AVOID_AMBIG = "pw_gen_avoid_ambig"
    private const val KEY_PINNED_CIPHERS = "pinned_cipher_ids"

    /** 超时模式可选分钟数。 */
    val PIN_TIMEOUT_OPTIONS_MIN = listOf(1, 5, 15, 30, 60, 240)

    @Volatile
    private var securePrefs: SharedPreferences? = null

    fun getPasswordGeneratorOptions(context: Context): PasswordGeneratorOptions {
        val p = plain(context)
        return PasswordGeneratorOptions(
            length = p.getInt(KEY_GEN_LENGTH, 20),
            lowercase = p.getBoolean(KEY_GEN_LOWER, true),
            uppercase = p.getBoolean(KEY_GEN_UPPER, true),
            numbers = p.getBoolean(KEY_GEN_NUMBER, true),
            special = p.getBoolean(KEY_GEN_SPECIAL, true),
            avoidAmbiguous = p.getBoolean(KEY_GEN_AVOID_AMBIG, true),
        ).sanitized()
    }

    fun setPasswordGeneratorOptions(context: Context, options: PasswordGeneratorOptions) {
        val o = options.sanitized()
        plain(context).edit()
            .putInt(KEY_GEN_LENGTH, o.length)
            .putBoolean(KEY_GEN_LOWER, o.lowercase)
            .putBoolean(KEY_GEN_UPPER, o.uppercase)
            .putBoolean(KEY_GEN_NUMBER, o.numbers)
            .putBoolean(KEY_GEN_SPECIAL, o.special)
            .putBoolean(KEY_GEN_AVOID_AMBIG, o.avoidAmbiguous)
            .apply()
    }

    fun getPinnedCipherIds(context: Context): Set<String> =
        plain(context).getStringSet(KEY_PINNED_CIPHERS, emptySet())?.toSet().orEmpty()

    /** @return 切换后是否置顶 */
    fun togglePinnedCipher(context: Context, id: String): Boolean {
        if (id.isBlank()) return false
        val next = getPinnedCipherIds(context).toMutableSet()
        val pinned = if (id in next) {
            next.remove(id)
            false
        } else {
            next.add(id)
            true
        }
        plain(context).edit().putStringSet(KEY_PINNED_CIPHERS, next).apply()
        return pinned
    }

    fun isPinnedCipher(context: Context, id: String): Boolean =
        id.isNotBlank() && id in getPinnedCipherIds(context)

    fun isConfigured(context: Context): Boolean {
        val email = getEmail(context)
        return email.isNotBlank() && (
            getPreset(context) != BitwardenServerPreset.SELF_HOSTED ||
                getSelfHostedBase(context).isNotBlank()
        )
    }

    fun getPreset(context: Context): BitwardenServerPreset {
        val id = plain(context).getString(KEY_PRESET, BitwardenServerPreset.CLOUD_US.id)
        return BitwardenServerPreset.entries.find { it.id == id } ?: BitwardenServerPreset.CLOUD_US
    }

    fun setPreset(context: Context, preset: BitwardenServerPreset) {
        plain(context).edit().putString(KEY_PRESET, preset.id).apply()
    }

    fun getSelfHostedBase(context: Context): String =
        plain(context).getString(KEY_SELF_HOSTED, "") ?: ""

    fun setSelfHostedBase(context: Context, base: String) {
        plain(context).edit()
            .putString(KEY_SELF_HOSTED, BitwardenEndpoints.normalizeSelfHostedBase(base))
            .remove(KEY_RESOLVED_IDENTITY)
            .remove(KEY_RESOLVED_API)
            .apply()
    }

    fun saveResolvedEndpoints(context: Context, endpoints: BitwardenEndpoints) {
        plain(context).edit()
            .putString(KEY_RESOLVED_IDENTITY, endpoints.identityBase)
            .putString(KEY_RESOLVED_API, endpoints.apiBase)
            .apply()
    }

    fun getEmail(context: Context): String =
        plain(context).getString(KEY_EMAIL, "") ?: ""

    fun setEmail(context: Context, email: String) {
        plain(context).edit().putString(KEY_EMAIL, BitwardenCrypto.normalizeEmail(email)).apply()
    }

    fun getDeviceId(context: Context): String {
        val existing = plain(context).getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val id = BitwardenApi.newDeviceId()
        plain(context).edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun getEndpoints(context: Context): BitwardenEndpoints {
        val resolvedIdentity = plain(context).getString(KEY_RESOLVED_IDENTITY, null)
        val resolvedApi = plain(context).getString(KEY_RESOLVED_API, null)
        if (!resolvedIdentity.isNullOrBlank() && !resolvedApi.isNullOrBlank()) {
            return BitwardenEndpoints(resolvedIdentity, resolvedApi)
        }
        return BitwardenEndpoints.fromPreset(getPreset(context), getSelfHostedBase(context))
    }

    fun saveTokens(context: Context, access: String?, refresh: String?, expiresInSec: Long?) {
        val edit = secure(context).edit()
        if (!access.isNullOrBlank()) edit.putString(KEY_ACCESS, access)
        if (!refresh.isNullOrBlank()) edit.putString(KEY_REFRESH, refresh)
        if (expiresInSec != null) {
            edit.putLong(KEY_ACCESS_EXPIRY, System.currentTimeMillis() + expiresInSec * 1000L - 60_000L)
        }
        edit.apply()
    }

    fun getAccessToken(context: Context): String? =
        secure(context).getString(KEY_ACCESS, null)

    fun getRefreshToken(context: Context): String? =
        secure(context).getString(KEY_REFRESH, null)

    fun getAccessExpiry(context: Context): Long =
        secure(context).getLong(KEY_ACCESS_EXPIRY, 0L)

    fun hasPersistedSession(context: Context): Boolean =
        !getRefreshToken(context).isNullOrBlank() && loadUserKey(context) != null

    fun getPinMode(context: Context): BitwardenPinMode =
        BitwardenPinMode.fromId(plain(context).getString(KEY_PIN_MODE, BitwardenPinMode.OFF.id))

    fun setPinMode(context: Context, mode: BitwardenPinMode) {
        plain(context).edit().putString(KEY_PIN_MODE, mode.id).apply()
        if (mode == BitwardenPinMode.OFF) {
            clearPinVerified(context)
        }
    }

    fun getPinTimeoutMinutes(context: Context): Int {
        val v = plain(context).getInt(KEY_PIN_TIMEOUT_MIN, 15)
        return if (v in PIN_TIMEOUT_OPTIONS_MIN) v else 15
    }

    fun setPinTimeoutMinutes(context: Context, minutes: Int) {
        val m = if (minutes in PIN_TIMEOUT_OPTIONS_MIN) minutes else 15
        plain(context).edit().putInt(KEY_PIN_TIMEOUT_MIN, m).apply()
    }

    fun hasPin(context: Context): Boolean =
        !secure(context).getString(KEY_PIN_HASH, null).isNullOrBlank() &&
            !secure(context).getString(KEY_PIN_SALT, null).isNullOrBlank()

    fun setPin(context: Context, pin: String): Boolean {
        val digits = pin.filter { it.isDigit() }
        if (digits.length !in 4..8) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(digits, salt)
        secure(context).edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, hash)
            .apply()
        markPinVerified(context)
        return true
    }

    fun clearPin(context: Context) {
        secure(context).edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH)
            .apply()
        plain(context).edit()
            .putString(KEY_PIN_MODE, BitwardenPinMode.OFF.id)
            .remove(KEY_PIN_LAST_OK)
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val saltB64 = secure(context).getString(KEY_PIN_SALT, null) ?: return false
        val expect = secure(context).getString(KEY_PIN_HASH, null) ?: return false
        val salt = try {
            Base64.decode(saltB64, Base64.NO_WRAP)
        } catch (_: Exception) {
            return false
        }
        val digits = pin.filter { it.isDigit() }
        if (digits.length !in 4..8) return false
        val actual = hashPin(digits, salt)
        if (!constantTimeEquals(actual, expect)) return false
        markPinVerified(context)
        return true
    }

    /** 打开工具栏密码库前是否需要先输 PIN。 */
    fun isPinRequiredToOpen(context: Context): Boolean {
        val mode = getPinMode(context)
        if (mode == BitwardenPinMode.OFF || !hasPin(context)) return false
        return when (mode) {
            BitwardenPinMode.EVERY_OPEN -> true
            BitwardenPinMode.TIMEOUT -> {
                val last = plain(context).getLong(KEY_PIN_LAST_OK, 0L)
                val window = getPinTimeoutMinutes(context) * 60_000L
                System.currentTimeMillis() - last >= window
            }
            BitwardenPinMode.OFF -> false
        }
    }

    fun markPinVerified(context: Context) {
        plain(context).edit().putLong(KEY_PIN_LAST_OK, System.currentTimeMillis()).apply()
    }

    fun clearPinVerified(context: Context) {
        plain(context).edit().remove(KEY_PIN_LAST_OK).apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(md.digest(), Base64.NO_WRAP)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ab = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        if (ab.size != bb.size) return false
        var r = 0
        for (i in ab.indices) r = r or (ab[i].toInt() xor bb[i].toInt())
        return r == 0
    }

    fun saveUserKey(context: Context, key: BitwardenCrypto.SymmetricKey) {
        val combined = ByteArray(64)
        System.arraycopy(key.encKey, 0, combined, 0, 32)
        System.arraycopy(key.macKey, 0, combined, 32, 32)
        val b64 = Base64.encodeToString(combined, Base64.NO_WRAP)
        secure(context).edit().putString(KEY_USER_SYM, b64).apply()
    }

    fun loadUserKey(context: Context): BitwardenCrypto.SymmetricKey? {
        val b64 = secure(context).getString(KEY_USER_SYM, null) ?: return null
        return try {
            val raw = Base64.decode(b64, Base64.NO_WRAP)
            if (raw.size != 64) return null
            BitwardenCrypto.SymmetricKey(
                encKey = raw.copyOfRange(0, 32),
                macKey = raw.copyOfRange(32, 64),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearSession(context: Context) {
        secure(context).edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_ACCESS_EXPIRY)
            .remove(KEY_USER_SYM)
            .remove(KEY_USER_KEY_ENC)
            .apply()
        clearPinVerified(context)
    }

    fun clearAll(context: Context) {
        clearSession(context)
        plain(context).edit().clear().apply()
    }

    private fun plain(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PLAIN, Context.MODE_PRIVATE)

    private fun secure(context: Context): SharedPreferences {
        securePrefs?.let { return it }
        synchronized(this) {
            securePrefs?.let { return it }
            val app = context.applicationContext
            val created = createSecurePrefs(app, recreateOnFailure = true)
            securePrefs = created
            return created
        }
    }

    private fun createSecurePrefs(app: Context, recreateOnFailure: Boolean): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "EncryptedSharedPreferences broken, recreate=$recreateOnFailure", e)
            if (recreateOnFailure) {
                // 密钥损坏时只能重建；旧 token 无法解密，避免再落到「空 fallback」假装还有会话
                app.deleteSharedPreferences(PREFS)
                return createSecurePrefs(app, recreateOnFailure = false)
            }
            app.getSharedPreferences(PREFS + "_fallback", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences create failed", e)
            if (recreateOnFailure) {
                try {
                    app.deleteSharedPreferences(PREFS)
                    return createSecurePrefs(app, recreateOnFailure = false)
                } catch (_: Exception) {
                    // fall through
                }
            }
            app.getSharedPreferences(PREFS + "_fallback", Context.MODE_PRIVATE)
        }
    }
}
