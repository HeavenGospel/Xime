package com.kingzcheung.xime.ui.emoji

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.kingzcheung.xime.data.EmojiPanelMemory
import com.kingzcheung.xime.data.FavoriteStickersStore
import kotlinx.coroutines.launch

/**
 * 相册多选 →（静图）裁剪/旋转编辑 → 入库 → 回到表情收藏页。
 * 动图跳过裁剪，原样导入。
 */
class FavoriteStickerImportActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_FROM_APP = "from_app"

        /** 从输入法表情面板启动：结束后拉回键盘并恢复收藏页。 */
        fun start(context: Context) {
            EmojiPanelMemory.markRestoreFavorites(context)
            val intent = Intent(context, FavoriteStickerImportActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        }

        /** 从 App 设置页启动：结束后仅关闭本页，回到设置。 */
        fun startFromApp(context: Context) {
            val intent = Intent(context, FavoriteStickerImportActivity::class.java).apply {
                putExtra(EXTRA_FROM_APP, true)
            }
            context.startActivity(intent)
        }
    }

    private val fromApp: Boolean
        get() = intent.getBooleanExtra(EXTRA_FROM_APP, false)

    private var editQueue by mutableStateOf<List<Uri>>(emptyList())
    private var editIndex by mutableIntStateOf(0)
    private var addedCount by mutableIntStateOf(0)
    private var showEditor by mutableStateOf(false)

    private val picker = registerForActivityResult(PickMultipleVisualMedia(40)) { uris ->
        if (uris.isNullOrEmpty()) {
            finishImport()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val animated = mutableListOf<Uri>()
            val still = mutableListOf<Uri>()
            uris.forEach { uri ->
                if (FavoriteStickersStore.isAnimatedUri(this@FavoriteStickerImportActivity, uri)) {
                    animated.add(uri)
                } else {
                    still.add(uri)
                }
            }
            if (animated.isNotEmpty()) {
                addedCount += FavoriteStickersStore.importUris(
                    this@FavoriteStickerImportActivity,
                    animated
                )
            }
            if (still.isEmpty()) {
                toastAddedAndFinish()
            } else {
                editQueue = still
                editIndex = 0
                showEditor = true
                renderEditor()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 编辑页用 Compose padding 避让系统栏；先开 edge-to-edge 再自己垫，避免顶栏顶进状态栏
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        FavoriteStickersStore.ensureLoaded(this)
        if (!fromApp) {
            EmojiPanelMemory.markRestoreFavorites(this)
        }
        if (savedInstanceState == null) {
            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
    }

    private fun renderEditor() {
        val queue = editQueue
        val index = editIndex
        if (index !in queue.indices) {
            toastAddedAndFinish()
            return
        }
        setContent {
            FavoriteStickerCropScreen(
                uri = queue[index],
                index = index,
                total = queue.size,
                onCancelAll = { finishImport() },
                onSkipOriginal = {
                    lifecycleScope.launch {
                        addedCount += FavoriteStickersStore.importUris(
                            this@FavoriteStickerImportActivity,
                            listOf(queue[index])
                        )
                        advanceEdit()
                    }
                },
                onConfirm = { bitmap ->
                    lifecycleScope.launch {
                        if (FavoriteStickersStore.importBitmap(
                                this@FavoriteStickerImportActivity,
                                bitmap
                            )
                        ) {
                            addedCount++
                        }
                        if (!bitmap.isRecycled) bitmap.recycle()
                        advanceEdit()
                    }
                }
            )
        }
    }

    private fun advanceEdit() {
        val next = editIndex + 1
        if (next >= editQueue.size) {
            toastAddedAndFinish()
        } else {
            editIndex = next
            renderEditor()
        }
    }

    private fun toastAddedAndFinish() {
        val n = addedCount
        Toast.makeText(
            this,
            if (n > 0) "已添加 $n 个表情" else "未添加表情",
            Toast.LENGTH_SHORT
        ).show()
        finishImport()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (showEditor && editIndex < editQueue.lastIndex) {
            // 编辑中返回：跳过当前用原图？更符合预期是取消全部
            finishImport()
        } else {
            finishImport()
        }
    }

    private fun finishImport() {
        if (fromApp) {
            finish()
            return
        }
        EmojiPanelMemory.markRestoreFavorites(this)
        runCatching {
            @Suppress("DEPRECATION")
            getSystemService(InputMethodManager::class.java)
                ?.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        }
        finishAndRemoveTask()
    }
}
