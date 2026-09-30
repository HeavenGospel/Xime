package com.kingzcheung.xime.bitwarden

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bitwarden 会话：token + 用户密钥经 EncryptedSharedPreferences 落盘，
 * 应用更新 / 进程重启后可自动恢复解锁（点「锁定/清除会话」才会清掉）。
 */
class BitwardenVaultRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var userKey: BitwardenCrypto.SymmetricKey? = null
    private var cachedItems: List<VaultLoginItem> = emptyList()
    /** 离开列表（进详情/编辑）时保留，返回时恢复。 */
    private var cachedQuery: String = ""
    private var cachedListIndex: Int = 0
    private var cachedListOffset: Int = 0
    /** 从详情点「编辑」进入；取消/保存后应回到详情而非搜索。 */
    private var editingFromDetail: Boolean = false
    private var cachedViewingItem: VaultLoginItem? = null
    private var pendingEmail: String? = null
    private var pendingPassword: String? = null

    private val _state = MutableStateFlow<BitwardenUiState>(resolveInitialState())
    val state: StateFlow<BitwardenUiState> = _state.asStateFlow()

    val listScrollIndex: Int get() = cachedListIndex
    val listScrollOffset: Int get() = cachedListOffset
    /** 列表/详情/编辑共用：离开 Unlocked 后仍保留搜索词供顶栏展示。 */
    val currentQuery: String get() = cachedQuery

    init {
        scope.launch { restorePersistedSession() }
    }

    fun setListScroll(index: Int, offset: Int) {
        cachedListIndex = index.coerceAtLeast(0)
        cachedListOffset = offset.coerceAtLeast(0)
    }

    private fun pinnedIds(): Set<String> = BitwardenPrefs.getPinnedCipherIds(app)

    /** 切换本地置顶；置顶条目始终排在匹配分之前。 */
    fun togglePinned(itemId: String) {
        BitwardenPrefs.togglePinnedCipher(app, itemId)
        val s = _state.value as? BitwardenUiState.Unlocked ?: return
        _state.value = s.copy(pinnedIds = pinnedIds())
    }

    /** 从加密偏好恢复密钥并同步；用于冷启动 / 覆盖安装后。 */
    private suspend fun restorePersistedSession() = mutex.withLock {
        if (userKey != null) return@withLock
        val key = BitwardenPrefs.loadUserKey(app) ?: return@withLock
        if (BitwardenPrefs.getRefreshToken(app).isNullOrBlank()) return@withLock
        userKey = key
        _state.value = BitwardenUiState.Unlocked(
            items = emptyList(),
            host = currentHostOrEmpty(),
            filterCurrentApp = true,
            query = cachedQuery,
            loading = true,
            pinnedIds = pinnedIds(),
        )
        try {
            val access = ensureAccessToken()
            syncLocked(api(), access)
        } catch (_: Exception) {
            // refresh 失效：清会话，回到锁定
            userKey = null
            cachedItems = emptyList()
            BitwardenPrefs.clearSession(app)
            _state.value = resolveInitialState()
        }
    }

    fun refreshHost(packageName: String?) {
        val host = resolveHost(packageName)
        when (val s = _state.value) {
            is BitwardenUiState.Unlocked -> {
                _state.value = s.copy(host = host)
            }
            is BitwardenUiState.Editing -> {
                // 仅「新建」才用当前宿主 App 填补空关联；编辑已有条目不改写用户关联
                val appPackage = if (s.existingId == null) {
                    s.appPackage.ifBlank { host.packageName }
                } else {
                    s.appPackage
                }
                _state.value = s.copy(host = host, appPackage = appPackage)
            }
            is BitwardenUiState.Viewing -> {
                _state.value = s.copy(host = host)
            }
            else -> Unit
        }
    }

    fun setQuery(query: String) {
        cachedQuery = query
        val s = _state.value as? BitwardenUiState.Unlocked ?: return
        if (s.query == query) return
        _state.value = s.copy(query = query)
    }

    fun setFilterCurrentApp(enabled: Boolean) {
        val s = _state.value as? BitwardenUiState.Unlocked ?: return
        _state.value = s.copy(filterCurrentApp = enabled)
    }

    fun beginCreate(packageName: String?) {
        val host = resolveHost(packageName)
        val unlocked = _state.value as? BitwardenUiState.Unlocked
        editingFromDetail = false
        cachedViewingItem = null
        if (unlocked != null) {
            cachedItems = unlocked.items
            cachedQuery = unlocked.query
        }
        val opts = BitwardenPrefs.getPasswordGeneratorOptions(app)
        _state.value = BitwardenUiState.Editing(
            existingId = null,
            name = host.appLabel.ifBlank { host.packageName },
            username = "",
            password = BitwardenCrypto.generatePassword(opts),
            webUri = "",
            appPackage = host.packageName,
            totp = "",
            notes = "",
            fields = emptyList(),
            host = host,
        )
    }

    fun beginView(item: VaultLoginItem, packageName: String?) {
        val host = resolveHost(packageName)
        val unlocked = _state.value as? BitwardenUiState.Unlocked
        if (unlocked != null) {
            cachedItems = unlocked.items
            cachedQuery = unlocked.query
        }
        cachedViewingItem = item
        _state.value = BitwardenUiState.Viewing(item = item, host = host)
    }

    fun beginEdit(item: VaultLoginItem, packageName: String?) {
        val host = resolveHost(packageName)
        when (val s = _state.value) {
            is BitwardenUiState.Unlocked -> {
                editingFromDetail = false
                cachedViewingItem = null
                cachedItems = s.items
                cachedQuery = s.query
            }
            is BitwardenUiState.Viewing -> {
                editingFromDetail = true
                cachedViewingItem = s.item
            }
            else -> {
                editingFromDetail = false
            }
        }
        _state.value = BitwardenUiState.Editing(
            existingId = item.id,
            name = item.name,
            username = item.username,
            password = item.password,
            webUri = InstalledApps.firstWebUri(item.uris),
            // 编辑：只用条目已有 androidapp:// 关联，空则保持未关联（不自动绑当前 App）
            appPackage = InstalledApps.firstAppPackage(item.uris),
            totp = item.totp.orEmpty(),
            notes = item.notes.orEmpty(),
            fields = item.fields,
            host = host,
        )
    }

    fun beginEditFromViewing() {
        val viewing = _state.value as? BitwardenUiState.Viewing ?: return
        editingFromDetail = true
        cachedViewingItem = viewing.item
        beginEdit(viewing.item, viewing.host.packageName)
    }

    fun cancelViewing() {
        val viewing = _state.value as? BitwardenUiState.Viewing ?: return
        restoreUnlocked(viewing.host)
    }

    fun updateEditing(
        name: String? = null,
        username: String? = null,
        password: String? = null,
        webUri: String? = null,
        appPackage: String? = null,
        totp: String? = null,
        notes: String? = null,
        fields: List<VaultCustomField>? = null,
    ) {
        val s = _state.value as? BitwardenUiState.Editing ?: return
        _state.value = s.copy(
            name = name ?: s.name,
            username = username ?: s.username,
            password = password ?: s.password,
            webUri = webUri ?: s.webUri,
            appPackage = appPackage ?: s.appPackage,
            totp = totp ?: s.totp,
            notes = notes ?: s.notes,
            fields = fields ?: s.fields,
            error = null,
        )
    }

    fun clearEditingAppPackage() {
        val s = _state.value as? BitwardenUiState.Editing ?: return
        _state.value = s.copy(appPackage = "", error = null)
    }

    fun addCustomField(type: Int = 0) {
        val s = _state.value as? BitwardenUiState.Editing ?: return
        _state.value = s.copy(
            fields = s.fields + VaultCustomField(type = type, name = "", value = ""),
            error = null,
        )
    }

    fun updateCustomField(index: Int, name: String? = null, value: String? = null, type: Int? = null) {
        val s = _state.value as? BitwardenUiState.Editing ?: return
        if (index !in s.fields.indices) return
        val next = s.fields.toMutableList()
        val cur = next[index]
        next[index] = cur.copy(
            name = name ?: cur.name,
            value = value ?: cur.value,
            type = type ?: cur.type,
        )
        _state.value = s.copy(fields = next, error = null)
    }

    fun removeCustomField(index: Int) {
        val s = _state.value as? BitwardenUiState.Editing ?: return
        if (index !in s.fields.indices) return
        _state.value = s.copy(fields = s.fields.filterIndexed { i, _ -> i != index }, error = null)
    }

    fun regeneratePassword() {
        val s = _state.value as? BitwardenUiState.Editing ?: return
        val opts = BitwardenPrefs.getPasswordGeneratorOptions(app)
        _state.value = s.copy(password = BitwardenCrypto.generatePassword(opts), error = null)
    }

    fun cancelEditing() {
        val editing = _state.value as? BitwardenUiState.Editing ?: return
        val host = editing.host
        val backToDetail = editingFromDetail
        val viewingItem = cachedViewingItem
        editingFromDetail = false
        if (backToDetail && viewingItem != null && userKey != null) {
            _state.value = BitwardenUiState.Viewing(item = viewingItem, host = host)
        } else {
            restoreUnlocked(host)
        }
    }

    /** 保存成功后：从详情进的回到详情（用同步后的新数据），否则留在列表。 */
    private fun finishEditingAfterSave(host: VaultHostContext, savedId: String?) {
        val backToDetail = editingFromDetail
        editingFromDetail = false
        if (backToDetail && !savedId.isNullOrBlank()) {
            val item = cachedItems.find { it.id == savedId }
            if (item != null) {
                cachedViewingItem = item
                _state.value = BitwardenUiState.Viewing(item = item, host = host)
                return
            }
        }
        cachedViewingItem = null
        restoreUnlocked(host)
    }

    private fun restoreUnlocked(host: VaultHostContext) {
        if (userKey != null) {
            _state.value = BitwardenUiState.Unlocked(
                items = cachedItems,
                host = host,
                filterCurrentApp = true,
                query = cachedQuery,
                pinnedIds = pinnedIds(),
            )
        } else {
            _state.value = resolveInitialState()
        }
    }

    fun isVaultUnlocked(): Boolean = userKey != null

    suspend fun unlock(password: String, twoFactorToken: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!BitwardenPrefs.isConfigured(app)) {
                    _state.value = BitwardenUiState.NotConfigured
                    return@withLock Result.failure(BitwardenApiException("请先保存服务器与邮箱"))
                }
                    val email = BitwardenCrypto.normalizeEmail(BitwardenPrefs.getEmail(app))
                    try {
                    _state.value = BitwardenUiState.Unlocked(
                        items = emptyList(),
                        host = currentHostOrEmpty(),
                        filterCurrentApp = true,
                        query = "",
                        loading = true,
                    )
                    val endpoints = resolveEndpointsForUnlock()
                    BitwardenPrefs.saveResolvedEndpoints(app, endpoints)
                    val api = BitwardenApi(endpoints, BitwardenPrefs.getDeviceId(app))
                    val pre = api.prelogin(email)
                    val kdf = pre.resolvedKdf()
                    val iterations = pre.resolvedIterations()
                    val memory = pre.resolvedMemory()
                    val parallelism = pre.resolvedParallelism()
                    // 主密码不做 trim：Bitwarden 官方也不裁剪首尾空白
                    val masterKey = BitwardenCrypto.makeMasterKey(
                        password = password,
                        email = email,
                        kdf = kdf,
                        iterations = iterations,
                        memoryMb = memory,
                        parallelism = parallelism,
                    )
                    val hash = BitwardenCrypto.hashMasterPassword(masterKey, password)
                    val kdfHint = "KDF=${if (kdf == BitwardenCrypto.KDF_ARGON2ID) "argon2id" else "pbkdf2"} " +
                        "iter=$iterations mem=$memory p=$parallelism hashLen=${hash.length}"
                    val token = try {
                        api.loginPassword(
                            email,
                            hash,
                            twoFactorToken,
                            twoFactorProvider = if (twoFactorToken != null) 0 else null,
                        )
                    } catch (e: BitwardenTwoFactorRequiredException) {
                        pendingEmail = email
                        pendingPassword = password
                        _state.value = BitwardenUiState.NeedsTwoFactor(email)
                        return@withLock Result.failure(e)
                    } catch (e: BitwardenApiException) {
                        throw BitwardenApiException("${e.message}\n$kdfHint")
                    }
                    val access = token.accessToken
                        ?: throw BitwardenApiException("未返回 access_token")
                    BitwardenPrefs.saveTokens(app, access, token.refreshToken, token.expiresIn)
                    val stretched = BitwardenCrypto.stretchMasterKey(masterKey)
                    var keyCipher = token.resolvedKey()
                    if (keyCipher.isNullOrBlank()) {
                        val sync = api.sync(access)
                        keyCipher = sync.profile?.resolvedKey()
                    }
                    if (keyCipher.isNullOrBlank()) {
                        throw BitwardenApiException("未返回用户密钥（Key），请检查服务端版本")
                    }
                    userKey = BitwardenCrypto.decryptToSymmetricKey(keyCipher, stretched)
                    BitwardenPrefs.saveUserKey(app, userKey!!)
                    pendingEmail = null
                    pendingPassword = null
                    syncLocked(api, access)
                    Result.success(Unit)
                } catch (e: BitwardenTwoFactorRequiredException) {
                    Result.failure(e)
                } catch (e: Exception) {
                    userKey = null
                    val msg = e.message ?: "解锁失败"
                    _state.value = BitwardenUiState.Error(msg, locked = true)
                    Result.failure(BitwardenApiException(msg))
                }
            }
        }

    private fun resolveEndpointsForUnlock(): BitwardenEndpoints {
        return when (BitwardenPrefs.getPreset(app)) {
            BitwardenServerPreset.SELF_HOSTED ->
                BitwardenApi.resolveSelfHostedEndpoints(BitwardenPrefs.getSelfHostedBase(app))
            else -> BitwardenPrefs.getEndpoints(app)
        }
    }

    suspend fun submitTwoFactor(token: String): Result<Unit> {
        val password = pendingPassword
        if (password.isNullOrBlank()) {
            _state.value = BitwardenUiState.Locked
            return Result.failure(BitwardenApiException("请重新输入主密码"))
        }
        return unlock(password, token)
    }

    suspend fun syncNow(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = userKey ?: run {
                _state.value = BitwardenUiState.Locked
                return@withLock Result.failure(BitwardenApiException("保险库已锁定"))
            }
            val current = _state.value as? BitwardenUiState.Unlocked
            if (current != null) {
                _state.value = current.copy(loading = true, error = null)
            }
            try {
                val access = ensureAccessToken()
                syncLocked(api(), access)
                Result.success(Unit)
            } catch (e: Exception) {
                userKey = key
                _state.value = (current ?: BitwardenUiState.Unlocked(
                    items = cachedItems,
                    host = currentHostOrEmpty(),
                    filterCurrentApp = true,
                    query = cachedQuery,
                )).let {
                    if (it is BitwardenUiState.Unlocked) it.copy(loading = false, error = e.message)
                    else BitwardenUiState.Error(e.message ?: "同步失败", locked = false)
                }
                Result.failure(e)
            }
        }
    }

    suspend fun saveEditing(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val editing = _state.value as? BitwardenUiState.Editing
                ?: return@withLock Result.failure(BitwardenApiException("无编辑中的条目"))
            val key = userKey ?: run {
                _state.value = BitwardenUiState.Locked
                return@withLock Result.failure(BitwardenApiException("保险库已锁定"))
            }
            _state.value = editing.copy(saving = true, error = null)
            try {
                val access = ensureAccessToken()
                val uriList = buildList {
                    val web = normalizeWebUri(editing.webUri)
                    if (web.isNotBlank()) add(web)
                    val app = editing.androidAppUri
                    if (app.isNotBlank()) add(app)
                }
                val encFields = editing.fields
                    .filter { it.name.isNotBlank() || it.value.isNotBlank() }
                    .map { f ->
                        CipherFieldRequest(
                            type = f.type.coerceIn(0, 2),
                            name = f.name.takeIf { it.isNotBlank() }?.let {
                                BitwardenCrypto.encryptUtf8(it, key)
                            },
                            value = when (f.type) {
                                2 -> BitwardenCrypto.encryptUtf8(
                                    if (f.value.equals("true", true) || f.value == "1") "true" else "false",
                                    key,
                                )
                                else -> f.value.takeIf { it.isNotBlank() }?.let {
                                    BitwardenCrypto.encryptUtf8(it, key)
                                }
                            },
                        )
                    }
                val req = CipherRequest(
                    type = 1,
                    name = BitwardenCrypto.encryptUtf8(editing.name.ifBlank { editing.host.appLabel }, key),
                    notes = editing.notes.takeIf { it.isNotBlank() }?.let { BitwardenCrypto.encryptUtf8(it, key) },
                    login = CipherLoginRequest(
                        username = editing.username.takeIf { it.isNotBlank() }?.let {
                            BitwardenCrypto.encryptUtf8(it, key)
                        },
                        password = editing.password.takeIf { it.isNotBlank() }?.let {
                            BitwardenCrypto.encryptUtf8(it, key)
                        },
                        totp = editing.totp.takeIf { it.isNotBlank() }?.let {
                            BitwardenCrypto.encryptUtf8(it, key)
                        },
                        uris = uriList.map { raw ->
                            CipherUriRequest(uri = BitwardenCrypto.encryptUtf8(raw, key))
                        },
                    ),
                    fields = encFields.takeIf { it.isNotEmpty() },
                )
                val savedId = if (editing.existingId.isNullOrBlank()) {
                    api().createCipher(access, req).id ?: editing.existingId
                } else {
                    api().updateCipher(access, editing.existingId, req)
                    editing.existingId
                }
                syncLocked(api(), access)
                finishEditingAfterSave(editing.host, savedId)
                Result.success(Unit)
            } catch (e: Exception) {
                _state.value = editing.copy(saving = false, error = e.message ?: "保存失败")
                Result.failure(e)
            }
        }
    }

    fun lock() {
        userKey = null
        cachedItems = emptyList()
        pendingPassword = null
        BitwardenPrefs.clearSession(app)
        _state.value = if (BitwardenPrefs.isConfigured(app)) {
            BitwardenUiState.Locked
        } else {
            BitwardenUiState.NotConfigured
        }
    }

    fun notifyConfigChanged() {
        if (userKey == null) {
            _state.value = resolveInitialState()
        }
    }

    private fun syncLocked(api: BitwardenApi, access: String) {
        val key = userKey ?: throw IllegalStateException("未解锁")
        val sync = api.sync(access)
        // 部分服务端把 Key 只放在 profile
        if (sync.profile?.key != null && tokenKeyMissing()) {
            // already have userKey from login
        }
        val items = sync.ciphers.mapNotNull { cipher ->
            if (cipher.type != 1 || cipher.id.isNullOrBlank() || !cipher.deletedDate.isNullOrBlank()) {
                return@mapNotNull null
            }
            try {
                VaultLoginItem(
                    id = cipher.id,
                    name = BitwardenCrypto.decryptUtf8(cipher.name, key).ifBlank { "(未命名)" },
                    username = BitwardenCrypto.decryptUtf8(cipher.login?.username, key),
                    password = BitwardenCrypto.decryptUtf8(cipher.login?.password, key),
                    totp = cipher.login?.totp?.let { BitwardenCrypto.decryptUtf8(it, key) }?.ifBlank { null },
                    uris = cipher.login?.uris.orEmpty().mapNotNull { u ->
                        u.uri?.let { BitwardenCrypto.decryptUtf8(it, key) }?.takeIf { it.isNotBlank() }
                    },
                    notes = cipher.notes?.let { BitwardenCrypto.decryptUtf8(it, key) }?.ifBlank { null },
                    revisionDate = cipher.revisionDate,
                    fields = cipher.fields.orEmpty().mapNotNull { f ->
                        try {
                            VaultCustomField(
                                type = f.type,
                                name = f.name?.let { BitwardenCrypto.decryptUtf8(it, key) }.orEmpty(),
                                value = f.value?.let { BitwardenCrypto.decryptUtf8(it, key) }.orEmpty(),
                            )
                        } catch (_: Exception) {
                            null
                        }
                    },
                    favorite = cipher.favorite,
                )
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.name.lowercase() }
        cachedItems = items
        val host = when (val s = _state.value) {
            is BitwardenUiState.Unlocked -> {
                cachedQuery = s.query
                s.host
            }
            is BitwardenUiState.Editing -> s.host
            is BitwardenUiState.Viewing -> s.host
            else -> currentHostOrEmpty()
        }
        // 保存/同步中途不要把 Editing/Viewing 冲成 Unlocked，否则表单会闪成「无编辑中的条目」
        when (val s = _state.value) {
            is BitwardenUiState.Editing -> {
                // 仅刷新缓存；保持 Editing（含 saving）直到 finishEditingAfterSave
            }
            is BitwardenUiState.Viewing -> {
                val refreshed = items.find { it.id == s.item.id } ?: s.item
                _state.value = s.copy(item = refreshed)
            }
            else -> {
                _state.value = BitwardenUiState.Unlocked(
                    items = items,
                    host = host,
                    filterCurrentApp = true,
                    query = cachedQuery,
                    loading = false,
                    pinnedIds = pinnedIds(),
                )
            }
        }
    }

    private fun tokenKeyMissing(): Boolean = false

    private suspend fun ensureAccessToken(): String {
        val existing = BitwardenPrefs.getAccessToken(app)
        val expiry = BitwardenPrefs.getAccessExpiry(app)
        if (!existing.isNullOrBlank() && System.currentTimeMillis() < expiry) {
            return existing
        }
        val refresh = BitwardenPrefs.getRefreshToken(app)
            ?: throw BitwardenApiException("会话已过期，请重新解锁")
        val token = api().refresh(refresh)
        val access = token.accessToken ?: throw BitwardenApiException("刷新令牌失败")
        BitwardenPrefs.saveTokens(app, access, token.refreshToken ?: refresh, token.expiresIn)
        return access
    }

    private fun normalizeWebUri(raw: String): String {
        val w = raw.trim()
        if (w.isEmpty()) return ""
        if (w.startsWith("http://", ignoreCase = true) ||
            w.startsWith("https://", ignoreCase = true)
        ) {
            return w
        }
        return "https://$w"
    }

    private fun api(): BitwardenApi =
        BitwardenApi(BitwardenPrefs.getEndpoints(app), BitwardenPrefs.getDeviceId(app))

    private fun resolveInitialState(): BitwardenUiState =
        if (BitwardenPrefs.isConfigured(app)) BitwardenUiState.Locked
        else BitwardenUiState.NotConfigured

    private var lastPackageName: String? = null

    private fun resolveHost(packageName: String?): VaultHostContext {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: lastPackageName.orEmpty()
        if (pkg.isNotBlank()) lastPackageName = pkg
        val label = if (pkg.isBlank()) {
            ""
        } else {
            try {
                val pm = app.packageManager
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }
        }
        return VaultHostContext(packageName = pkg, appLabel = label)
    }

    private fun currentHostOrEmpty(): VaultHostContext = resolveHost(lastPackageName)

    companion object {
        @Volatile
        private var instance: BitwardenVaultRepository? = null

        fun getInstance(context: Context): BitwardenVaultRepository {
            return instance ?: synchronized(this) {
                instance ?: BitwardenVaultRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
