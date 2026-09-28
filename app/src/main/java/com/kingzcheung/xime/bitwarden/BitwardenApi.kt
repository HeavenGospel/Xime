package com.kingzcheung.xime.bitwarden

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

class BitwardenApi(
    private val endpoints: BitwardenEndpoints,
    private val deviceId: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        // Vaultwarden 缺 Bitwarden-Client-Version 会直接拒登，并伪装成账密错误
        .addInterceptor { chain ->
            val original = chain.request()
            val req = original.newBuilder()
                .header("Bitwarden-Client-Name", CLIENT_NAME)
                .header("Bitwarden-Client-Version", CLIENT_VERSION)
                .header("Device-Type", "0")
                .header(
                    "User-Agent",
                    "Bitwarden_Mobile/$CLIENT_VERSION (Android; SDK; Model Xime)",
                )
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun prelogin(email: String): PreloginResponse {
        val body = json.encodeToString(PreloginRequest.serializer(), PreloginRequest(email))
            .toRequestBody(jsonMedia)
        val urls = listOf(
            "${endpoints.identityBase}/accounts/prelogin",
            "${endpoints.apiBase}/accounts/prelogin",
        )
        var lastError: Exception? = null
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .build()
                return execute(request) { json.decodeFromString(PreloginResponse.serializer(), it) }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw BitwardenApiException(
            "预登录失败（已试 identity/api）。${lastError?.message ?: ""}".trim()
        )
    }

    fun loginPassword(
        email: String,
        masterPasswordHash: String,
        twoFactorToken: String? = null,
        twoFactorProvider: Int? = null,
    ): TokenResponse {
        val form = FormBody.Builder()
            .add("grant_type", "password")
            .add("username", email)
            .add("password", masterPasswordHash)
            .add("scope", "api offline_access")
            .add("client_id", "mobile")
            .add("deviceType", "0") // DeviceType.Android
            .add("deviceIdentifier", deviceId)
            .add("deviceName", "Xime")
        if (!twoFactorToken.isNullOrBlank() && twoFactorProvider != null) {
            form.add("twoFactorToken", twoFactorToken)
            form.add("twoFactorProvider", twoFactorProvider.toString())
            form.add("twoFactorRemember", "0")
        }
        val request = Request.Builder()
            .url("${endpoints.identityBase}/connect/token")
            .post(form.build())
            .header(
                "Auth-Email",
                android.util.Base64.encodeToString(
                    email.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP,
                ),
            )
            .build()
        return executeRaw(request) { body, code ->
            val parsed = runCatching {
                json.decodeFromString(TokenResponse.serializer(), body)
            }.getOrElse {
                throw BitwardenApiException("登录响应无法解析 ($code): ${body.take(160)}")
            }
            if (code !in 200..299 && parsed.accessToken.isNullOrBlank()) {
                val desc = extractLoginError(parsed, body, code)
                if (needsTwoFactor(parsed) || body.contains("TwoFactor", ignoreCase = true)) {
                    throw BitwardenTwoFactorRequiredException(desc)
                }
                throw BitwardenApiException("$desc\n端点: ${endpoints.identityBase}/connect/token")
            }
            parsed
        }
    }

    fun refresh(refreshToken: String): TokenResponse {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", "mobile")
            .add("refresh_token", refreshToken)
            .build()
        val request = Request.Builder()
            .url("${endpoints.identityBase}/connect/token")
            .post(form)
            .build()
        return execute(request) { json.decodeFromString(TokenResponse.serializer(), it) }
    }

    fun sync(accessToken: String): SyncResponse {
        val request = Request.Builder()
            .url("${endpoints.apiBase}/sync?excludeDomains=true")
            .get()
            .header("Authorization", "Bearer $accessToken")
            .build()
        return execute(request) { json.decodeFromString(SyncResponse.serializer(), it) }
    }

    fun createCipher(accessToken: String, cipher: CipherRequest): SyncCipher {
        val body = json.encodeToString(CipherRequest.serializer(), cipher).toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("${endpoints.apiBase}/ciphers")
            .post(body)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .build()
        return execute(request) { json.decodeFromString(SyncCipher.serializer(), it) }
    }

    fun updateCipher(accessToken: String, id: String, cipher: CipherRequest): SyncCipher {
        val body = json.encodeToString(CipherRequest.serializer(), cipher).toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("${endpoints.apiBase}/ciphers/$id")
            .put(body)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .build()
        return execute(request) { json.decodeFromString(SyncCipher.serializer(), it) }
    }

    private fun needsTwoFactor(token: TokenResponse): Boolean {
        return token.twoFactorProviders != null || token.twoFactorProvidersLegacy != null
    }

    private fun extractLoginError(parsed: TokenResponse, body: String, code: Int): String {
        parsed.errorDescription?.takeIf { it.isNotBlank() }?.let { return it }
        parsed.error?.takeIf { it.isNotBlank() }?.let { return it }
        try {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["ErrorModel"]?.jsonObject?.get("Message")?.jsonPrimitive?.contentOrNull
                ?.let { return it }
            obj["message"]?.jsonPrimitive?.contentOrNull?.let { return it }
        } catch (_: Exception) {
        }
        return "登录失败 ($code)"
    }

    private fun <T> execute(request: Request, parse: (String) -> T): T {
        return executeRaw(request) { body, code ->
            if (code !in 200..299) {
                throw BitwardenApiException(extractError(body, code) + "\nURL: ${request.url}")
            }
            parse(body)
        }
    }

    private fun <T> executeRaw(request: Request, parse: (String, Int) -> T): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return parse(body, response.code)
        }
    }

    private fun extractError(body: String, code: Int): String {
        return try {
            val obj = json.decodeFromString(JsonObject.serializer(), body)
            obj["ErrorModel"]?.jsonObject?.get("Message")?.jsonPrimitive?.contentOrNull
                ?: obj["message"]?.jsonPrimitive?.contentOrNull
                ?: obj["error_description"]?.jsonPrimitive?.contentOrNull
                ?: obj["error"]?.jsonPrimitive?.contentOrNull
                ?: "请求失败 ($code)"
        } catch (_: Exception) {
            body.ifBlank { "请求失败 ($code)" }.take(200)
        }
    }

    companion object {
        /** 与官方 Android 客户端同级的版本头；Vaultwarden 强制校验。 */
        const val CLIENT_VERSION = "2024.12.0"
        const val CLIENT_NAME = "mobile"

        fun newDeviceId(): String = UUID.randomUUID().toString()

        /**
         * 自建库可读 `/api/config` 的 environment.api / identity。
         * Vaultwarden 若未配置 DOMAIN，常返回 http://localhost —— 必须改写为用户填写的公网根地址，
         * 否则会连到手机本机 127.0.0.1:80。
         */
        fun resolveSelfHostedEndpoints(baseRaw: String): BitwardenEndpoints {
            val base = BitwardenEndpoints.normalizeSelfHostedBase(baseRaw)
            if (base.isBlank()) {
                throw BitwardenApiException("自建地址为空")
            }
            if (isLoopbackUrl(base)) {
                throw BitwardenApiException(
                    "自建地址不能是 localhost/127.0.0.1。请填写手机能访问的公网或局域网地址，例如 https://vault.example.com"
                )
            }
            val fallback = BitwardenEndpoints(
                identityBase = "$base/identity",
                apiBase = "$base/api",
            )
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("Bitwarden-Client-Name", CLIENT_NAME)
                        .header("Bitwarden-Client-Version", CLIENT_VERSION)
                        .header("Device-Type", "0")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(req)
                }
                .build()
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val configUrls = listOf("$base/api/config", "$base/config")
            for (url in configUrls) {
                try {
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string().orEmpty()
                        val cfg = json.decodeFromString(ServerConfigResponse.serializer(), body)
                        val apiRaw = cfg.environment?.api?.trim()?.trimEnd('/')
                        val identityRaw = cfg.environment?.identity?.trim()?.trimEnd('/')
                        if (!apiRaw.isNullOrBlank() && !identityRaw.isNullOrBlank()) {
                            val api = rewriteLoopbackToPublicBase(apiRaw, base)
                            val identity = rewriteLoopbackToPublicBase(identityRaw, base)
                            // 改写后仍指向环回则丢弃 config，改用根地址拼接
                            if (!isLoopbackUrl(api) && !isLoopbackUrl(identity)) {
                                return BitwardenEndpoints(identityBase = identity, apiBase = api)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // 尝试下一地址 / 回退默认拼接
                }
            }
            return fallback
        }

        private fun isLoopbackHost(host: String): Boolean {
            val h = host.trim().lowercase()
            return h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "0.0.0.0" || h == "[::1]"
        }

        private fun isLoopbackUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            return isLoopbackHost(parsed.host)
        }

        /** 把 config 里的 localhost 主机名替换成用户填写的公网 host/scheme/port，保留 path。 */
        private fun rewriteLoopbackToPublicBase(url: String, publicBase: String): String {
            val parsed = url.toHttpUrlOrNull() ?: return url.trimEnd('/')
            val baseUrl = publicBase.toHttpUrlOrNull() ?: return url.trimEnd('/')
            if (!isLoopbackHost(parsed.host)) {
                return url.trimEnd('/')
            }
            return parsed.newBuilder()
                .scheme(baseUrl.scheme)
                .host(baseUrl.host)
                .port(baseUrl.port)
                .build()
                .toString()
                .trimEnd('/')
        }
    }
}

class BitwardenApiException(message: String) : Exception(message)
class BitwardenTwoFactorRequiredException(message: String) : Exception(message)
