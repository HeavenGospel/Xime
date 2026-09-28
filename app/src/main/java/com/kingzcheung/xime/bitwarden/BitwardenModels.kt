package com.kingzcheung.xime.bitwarden

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class BitwardenServerPreset(val id: String, val label: String) {
    CLOUD_US("cloud_us", "Bitwarden 云（美国）"),
    CLOUD_EU("cloud_eu", "Bitwarden 云（欧洲）"),
    SELF_HOSTED("self_hosted", "自建 / Vaultwarden"),
}

/** 工具栏打开密码库时的 PIN 保护策略。 */
enum class BitwardenPinMode(val id: String, val label: String) {
    OFF("off", "不使用 PIN"),
    EVERY_OPEN("every_open", "每次打开都要输入"),
    TIMEOUT("timeout", "一段时间后需要输入"),
    ;

    companion object {
        fun fromId(id: String?): BitwardenPinMode =
            entries.find { it.id == id } ?: OFF
    }
}

data class BitwardenEndpoints(
    val identityBase: String,
    val apiBase: String,
) {
    companion object {
        /** 去掉用户误填的 /api、/identity、/# 后缀。 */
        fun normalizeSelfHostedBase(raw: String): String {
            var base = raw.trim().trimEnd('/')
            val suffixes = listOf("/identity", "/api", "/#", "#")
            var changed = true
            while (changed) {
                changed = false
                for (suffix in suffixes) {
                    if (base.endsWith(suffix, ignoreCase = true)) {
                        base = base.dropLast(suffix.length).trimEnd('/')
                        changed = true
                    }
                }
            }
            return base
        }

        fun fromPreset(preset: BitwardenServerPreset, selfHostedBase: String): BitwardenEndpoints {
            return when (preset) {
                BitwardenServerPreset.CLOUD_US -> BitwardenEndpoints(
                    identityBase = "https://identity.bitwarden.com",
                    apiBase = "https://api.bitwarden.com",
                )
                BitwardenServerPreset.CLOUD_EU -> BitwardenEndpoints(
                    identityBase = "https://identity.bitwarden.eu",
                    apiBase = "https://api.bitwarden.eu",
                )
                BitwardenServerPreset.SELF_HOSTED -> {
                    val base = normalizeSelfHostedBase(selfHostedBase)
                    BitwardenEndpoints(
                        identityBase = "$base/identity",
                        apiBase = "$base/api",
                    )
                }
            }
        }
    }
}

data class VaultLoginItem(
    val id: String,
    val name: String,
    val username: String,
    val password: String,
    val totp: String?,
    val uris: List<String>,
    val notes: String?,
    val revisionDate: String?,
    val fields: List<VaultCustomField> = emptyList(),
    /** 服务端 favorite；本地置顶见 BitwardenPrefs.pinnedIds。 */
    val favorite: Boolean = false,
) {
    /** 精确：URI 含 androidapp://包名 或裸包名。 */
    fun matchesPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val target = "androidapp://$packageName"
        return uris.any { uri ->
            uri.equals(target, ignoreCase = true) ||
                uri.equals(packageName, ignoreCase = true) ||
                // 仅当整段路径/authority 等于包名时算精确，避免误伤
                stripUriScheme(uri).equals(packageName, ignoreCase = true)
        }
    }

    /**
     * 是否关联当前宿主 App：精确包名优先，否则用应用名 / 域名关键词模糊匹配。
     * 模糊时会先剥掉 androidapp://、https://、http://、www. 等固定前缀再比。
     */
    fun matchesHost(host: VaultHostContext): Boolean = hostMatchScore(host) > 0

    /**
     * 匹配强度：精确包名 100；应用名命中 60；域名/包名片段模糊 40。越大越靠前。
     */
    fun hostMatchScore(host: VaultHostContext): Int {
        if (host.packageName.isBlank() && host.appLabel.isBlank()) return 0
        if (matchesPackage(host.packageName)) return 100

        val hostTokens = hostFuzzyTokens(host)
        if (hostTokens.isEmpty()) return 0
        val itemTokens = itemFuzzyTokens()
        if (itemTokens.isEmpty()) return 0

        var best = 0
        for (h in hostTokens) {
            for (i in itemTokens) {
                if (!fuzzyTokenHit(h, i)) continue
                val score = when {
                    // 应用名（通常是中文「哔哩哔哩」）与条目名互含
                    h.source == TokenSource.APP_LABEL || i.source == TokenSource.ITEM_NAME -> 60
                    else -> 40
                }
                if (score > best) best = score
            }
        }
        return best
    }

    fun matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return name.contains(q, ignoreCase = true) ||
            username.contains(q, ignoreCase = true) ||
            uris.any { it.contains(q, ignoreCase = true) } ||
            (notes?.contains(q, ignoreCase = true) == true)
    }

    private fun itemFuzzyTokens(): List<FuzzyToken> {
        val out = ArrayList<FuzzyToken>()
        name.trim().takeIf { it.isNotEmpty() }?.let {
            out += FuzzyToken(normalizeToken(it), TokenSource.ITEM_NAME)
        }
        notes?.trim()?.takeIf { it.isNotEmpty() }?.let {
            out += FuzzyToken(normalizeToken(it), TokenSource.OTHER)
        }
        for (uri in uris) {
            val stripped = stripUriScheme(uri)
            if (stripped.isBlank()) continue
            // androidapp 包名整段不参与模糊（精确路径已单独处理）
            if (uri.startsWith("androidapp://", ignoreCase = true)) continue
            val hostPart = stripped.substringBefore('/').substringBefore('?').substringBefore('#')
            if (hostPart.isNotBlank()) {
                out += FuzzyToken(normalizeToken(hostPart), TokenSource.DOMAIN)
                // bilibili.com → bilibili
                hostPart.split('.').forEach { part ->
                    if (isUsefulDomainLabel(part)) {
                        out += FuzzyToken(normalizeToken(part), TokenSource.DOMAIN)
                    }
                }
            }
        }
        return out.filter { it.value.length >= minTokenLen(it.value) }
    }

    private enum class TokenSource { APP_LABEL, PKG_SEGMENT, ITEM_NAME, DOMAIN, OTHER }

    private data class FuzzyToken(val value: String, val source: TokenSource)

    companion object {
        private val SCHEME_PREFIXES = listOf(
            "androidapp://",
            "https://",
            "http://",
            "android://",
        )

        fun stripUriScheme(raw: String): String {
            var s = raw.trim()
            for (p in SCHEME_PREFIXES) {
                if (s.startsWith(p, ignoreCase = true)) {
                    s = s.substring(p.length)
                    break
                }
            }
            if (s.startsWith("www.", ignoreCase = true)) {
                s = s.substring(4)
            }
            return s.trim().trimEnd('/')
        }

        private fun hostFuzzyTokens(host: VaultHostContext): List<FuzzyToken> {
            val out = ArrayList<FuzzyToken>()
            host.appLabel.trim().takeIf { it.isNotEmpty() }?.let {
                out += FuzzyToken(normalizeToken(it), TokenSource.APP_LABEL)
            }
            val pkg = host.packageName.trim()
            if (pkg.isNotEmpty()) {
                pkg.split('.').forEach { part ->
                    if (isUsefulPackageSegment(part)) {
                        out += FuzzyToken(normalizeToken(part), TokenSource.PKG_SEGMENT)
                    }
                }
            }
            return out.filter { it.value.length >= minTokenLen(it.value) }
        }

        private fun normalizeToken(s: String): String = s.trim().lowercase()

        private fun minTokenLen(s: String): Int =
            if (s.any { it.code > 0x7f }) 2 else 3

        private fun isUsefulPackageSegment(part: String): Boolean {
            val p = part.lowercase()
            if (p.length < 3) return false
            return p !in IGNORE_PKG_SEGMENTS
        }

        private fun isUsefulDomainLabel(part: String): Boolean {
            val p = part.lowercase()
            if (p.length < 3) return false
            return p !in IGNORE_DOMAIN_LABELS
        }

        private fun fuzzyTokenHit(a: FuzzyToken, b: FuzzyToken): Boolean {
            val x = a.value
            val y = b.value
            if (x == y) return true
            // 双向包含：哔哩哔哩↔哔哩；bilibili↔bili
            if (x.contains(y) || y.contains(x)) return true
            return false
        }

        private val IGNORE_PKG_SEGMENTS = setOf(
            "com", "org", "net", "android", "google", "cn", "www", "app", "apps",
            "mobile", "client", "main", "ui", "lib", "sdk", "tv", "demo", "test",
        )

        private val IGNORE_DOMAIN_LABELS = setOf(
            "com", "org", "net", "cn", "www", "co", "io", "app", "android",
            "hk", "tw", "jp", "kr", "edu", "gov", "mil",
        )
    }
}

data class VaultHostContext(
    val packageName: String,
    val appLabel: String,
) {
    val androidAppUri: String
        get() = if (packageName.isBlank()) "" else "androidapp://$packageName"
}

sealed class BitwardenUiState {
    data object NotConfigured : BitwardenUiState()
    data object Locked : BitwardenUiState()
    data class NeedsTwoFactor(val email: String) : BitwardenUiState()
    data class Unlocked(
        val items: List<VaultLoginItem>,
        val host: VaultHostContext,
        val filterCurrentApp: Boolean,
        val query: String,
        val loading: Boolean = false,
        val error: String? = null,
        /** 本地置顶条目 id；排序优先级高于精确/模糊匹配。 */
        val pinnedIds: Set<String> = emptySet(),
    ) : BitwardenUiState() {
        val visibleItems: List<VaultLoginItem>
            get() {
                val base = if (filterCurrentApp &&
                    (host.packageName.isNotBlank() || host.appLabel.isNotBlank())
                ) {
                    val matched = items.filter { it.matchesHost(host) }
                    if (matched.isNotEmpty()) matched else items
                } else {
                    items
                }
                return base
                    .filter { it.matchesQuery(query) }
                    .sortedWith(
                        compareByDescending<VaultLoginItem> {
                            if (it.id in pinnedIds || it.favorite) 1 else 0
                        }.thenByDescending { it.hostMatchScore(host) },
                    )
            }

        val showingFallbackAll: Boolean
            get() = filterCurrentApp &&
                (host.packageName.isNotBlank() || host.appLabel.isNotBlank()) &&
                items.none { it.matchesHost(host) }
    }

    data class Editing(
        val existingId: String?,
        val name: String,
        val username: String,
        val password: String,
        /** 网站域名 / https URL。 */
        val webUri: String = "",
        /** 关联 App 包名（不含 androidapp:// 前缀）。 */
        val appPackage: String = "",
        val totp: String = "",
        val notes: String,
        val fields: List<VaultCustomField> = emptyList(),
        val host: VaultHostContext,
        val saving: Boolean = false,
        val error: String? = null,
    ) : BitwardenUiState() {
        val androidAppUri: String
            get() = if (appPackage.isBlank()) "" else "androidapp://$appPackage"
    }

    /** 只读详情（由搜索列表点入）。 */
    data class Viewing(
        val item: VaultLoginItem,
        val host: VaultHostContext,
    ) : BitwardenUiState()

    data class Error(val message: String, val locked: Boolean = true) : BitwardenUiState()
}

@Serializable
data class PreloginRequest(val email: String)

@Serializable
data class PreloginResponse(
    val kdf: Int? = null,
    @SerialName("Kdf") val kdfPascal: Int? = null,
    val kdfIterations: Int? = null,
    @SerialName("KdfIterations") val kdfIterationsPascal: Int? = null,
    val kdfMemory: Int? = null,
    @SerialName("KdfMemory") val kdfMemoryPascal: Int? = null,
    val kdfParallelism: Int? = null,
    @SerialName("KdfParallelism") val kdfParallelismPascal: Int? = null,
) {
    fun resolvedKdf(): Int = kdf ?: kdfPascal ?: 0
    fun resolvedIterations(): Int = kdfIterations ?: kdfIterationsPascal ?: 600000
    fun resolvedMemory(): Int? = kdfMemory ?: kdfMemoryPascal
    fun resolvedParallelism(): Int? = kdfParallelism ?: kdfParallelismPascal
}

@Serializable
data class ServerConfigResponse(
    val environment: ServerConfigEnvironment? = null,
)

@Serializable
data class ServerConfigEnvironment(
    val api: String? = null,
    val identity: String? = null,
    val vault: String? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val Key: String? = null,
    @SerialName("key") val keyLower: String? = null,
    val PrivateKey: String? = null,
    @SerialName("TwoFactorProviders2") val twoFactorProviders: JsonElement? = null,
    @SerialName("TwoFactorProviders") val twoFactorProvidersLegacy: JsonElement? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("ErrorModel") val errorModel: JsonElement? = null,
) {
    fun resolvedKey(): String? = Key ?: keyLower
}

@Serializable
data class SyncResponse(
    val ciphers: List<SyncCipher> = emptyList(),
    val profile: SyncProfile? = null,
)

@Serializable
data class SyncProfile(
    val key: String? = null,
    @SerialName("Key") val keyPascal: String? = null,
    val privateKey: String? = null,
    val email: String? = null,
) {
    fun resolvedKey(): String? = key ?: keyPascal
}

@Serializable
data class SyncCipher(
    val id: String? = null,
    val type: Int = 0,
    val name: String? = null,
    val notes: String? = null,
    val login: SyncLogin? = null,
    val fields: List<SyncField>? = null,
    val favorite: Boolean = false,
    val revisionDate: String? = null,
    val deletedDate: String? = null,
)

@Serializable
data class SyncField(
    val type: Int = 0,
    val name: String? = null,
    val value: String? = null,
    val linkedId: Int? = null,
)

@Serializable
data class SyncLogin(
    val username: String? = null,
    val password: String? = null,
    val totp: String? = null,
    val uris: List<SyncLoginUri>? = null,
)

@Serializable
data class SyncLoginUri(
    val uri: String? = null,
    val match: Int? = null,
)

@Serializable
data class CipherRequest(
    val type: Int = 1,
    val name: String,
    val notes: String? = null,
    val favorite: Boolean = false,
    val login: CipherLoginRequest,
    val fields: List<CipherFieldRequest>? = null,
    val secureNote: JsonElement? = null,
    val card: JsonElement? = null,
    val identity: JsonElement? = null,
    val passwordHistory: List<JsonElement>? = null,
    val attachments: List<JsonElement>? = null,
    val organizationId: String? = null,
    val collectionIds: List<String>? = null,
    val folderId: String? = null,
    val reprompt: Int = 0,
)

@Serializable
data class CipherFieldRequest(
    val type: Int = 0,
    val name: String? = null,
    val value: String? = null,
    val linkedId: Int? = null,
)

@Serializable
data class CipherLoginRequest(
    val username: String? = null,
    val password: String? = null,
    val totp: String? = null,
    val uris: List<CipherUriRequest>? = null,
)

@Serializable
data class CipherUriRequest(
    val uri: String,
    val match: Int? = null,
)
