package com.kingzcheung.xime.service

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.association.AssociationManager
import com.kingzcheung.xime.association.AssociationService
import com.kingzcheung.xime.association.PrefixAssociationEngine
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PredictionManager(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val onPredictionResult: (List<String>) -> Unit,
) {
    companion object {
        private const val TAG = "PredictionManager"
        private const val MAX_CONTEXT_LENGTH = 25
        const val MAX_ASSOCIATION_COUNT = 20
    }
    
    private var _lastCommittedText = ""
    val lastCommittedText: String get() = _lastCommittedText

    /**
     * 预测请求代际号：退格/清空等改变上下文的操作调用 [invalidatePendingPredictions]，
     * 使所有在途的异步预测结果失效。否则长按退格删除时，旧的联想结果会迟到并反复
     * 回填 associationCandidates，候选栏在"显示联想词 ↔ 空"之间闪动（一闪一闪）。
     */
    private var requestEpoch = 0L

    fun invalidatePendingPredictions() {
        requestEpoch++
    }

    /**
     * 单次联想抑制标志：联想候选上屏（点击/空格）前置位，使 commitText 触发的下一轮
     * 自动推理被跳过并清空联想候选（候选栏干净），等待用户下一次真实输入。
     * 连续联想模式不置位——commitText 的自动推理即为"一直上屏一直推理"。
     */
    @Volatile
    private var suppressNextPrediction = false

    fun suppressNextPredictionOnce() {
        suppressNextPrediction = true
    }

    /**
     * 最近一次 Prefix 联想：display → commitSuffix。
     * 候选栏展示整词；上屏须走 [resolveAssociationCommit]，否则会把前缀再打一遍。
     * ONNX / 英文路径不写此表，resolve 时原样返回。
     */
    @Volatile
    private var prefixCommitMap: Map<String, String> = emptyMap()
    
    fun appendCommittedText(text: String) {
        _lastCommittedText = (_lastCommittedText + text).takeLast(MAX_CONTEXT_LENGTH)
        if (BuildConfig.DEBUG) {
            FileLogger.d(TAG, "Context updated: '$text' -> '$lastCommittedText' (len=${lastCommittedText.length})")
        }
    }
    
    fun clearCommittedText() {
        _lastCommittedText = ""
        prefixCommitMap = emptyMap()
    }
    
    fun deleteLastChar() {
        if (_lastCommittedText.isNotEmpty()) {
            _lastCommittedText = _lastCommittedText.dropLast(1)
        }
    }

    /** 联想点选/空格上屏：Prefix 模式只补后缀，ONNX 模式原样上屏。 */
    fun resolveAssociationCommit(displayed: String): String {
        return prefixCommitMap[displayed] ?: displayed
    }
    
    fun initialize() {
        FileLogger.i(TAG, "Initializing association system")
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                val trieInit = AssociationService.initialize(context)
                if (trieInit) {
                    FileLogger.i(TAG, "Trie association service initialized")
                } else {
                    FileLogger.w(TAG, "Trie association service initialization failed")
                }

                val prefixOk = PrefixAssociationEngine.ensureLoaded(context)
                FileLogger.i(TAG, "Prefix association index ready=$prefixOk")
                
                if (!ExtensionManager.isInitialized()) {
                    FileLogger.d(TAG, "ExtensionManager not initialized, initializing...")
                    ExtensionManager.initialize(context)
                }
                
                if (SettingsPreferences.isSmartPredictionEnabled(context)) {
                    try {
                        val initialized = AssociationManager.initialize(context)
                        if (initialized) {
                            FileLogger.i(TAG, "Smart prediction initialized")
                        } else {
                            FileLogger.w(TAG, "Smart prediction initialization failed")
                        }
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "Failed to initialize smart prediction: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "Failed to initialize association system: ${e.message}")
            }
        }
    }
    
    fun getPrediction(contextText: String) {
        // 单次联想：消费抑制标志——联想上屏引发的本轮推理不执行，回调空结果清空候选栏
        if (suppressNextPrediction) {
            suppressNextPrediction = false
            prefixCommitMap = emptyMap()
            onPredictionResult(emptyList())
            return
        }

        if (contextText.isEmpty()) {
            prefixCommitMap = emptyMap()
            onPredictionResult(emptyList())
            return
        }

        val useOnnx = SettingsPreferences.isSmartPredictionEnabled(context)
        val epoch = requestEpoch
        serviceScope.launch {
            try {
                val displayList: List<String> = if (useOnnx) {
                    prefixCommitMap = emptyMap()
                    predictOnnx(contextText)
                } else {
                    predictPrefix(contextText)
                }
                FileLogger.d(
                    TAG,
                    "Prediction(${if (useOnnx) "onnx" else "prefix"}) " +
                        "returned ${displayList.size} for '$contextText' (epoch ok: ${epoch == requestEpoch})"
                )

                withContext(Dispatchers.Main) {
                    if (epoch == requestEpoch) {
                        onPredictionResult(displayList)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Prediction failed", e)
                withContext(Dispatchers.Main) {
                    if (epoch == requestEpoch) {
                        prefixCommitMap = emptyMap()
                        onPredictionResult(emptyList())
                    }
                }
            }
        }
    }

    private suspend fun predictOnnx(contextText: String): List<String> {
        if (!AssociationManager.isInitialized()) {
            val initSuccess = withContext(Dispatchers.IO) {
                AssociationManager.initialize(context)
            }
            if (!initSuccess) {
                Log.e(TAG, "Failed to initialize AssociationManager")
                return emptyList()
            }
        }
        return AssociationManager.predict(contextText, MAX_ASSOCIATION_COUNT).map { it.text }
    }

    private suspend fun predictPrefix(contextText: String): List<String> {
        withContext(Dispatchers.IO) {
            PrefixAssociationEngine.ensureLoaded(context)
        }
        val hits = PrefixAssociationEngine.lookup(contextText, MAX_ASSOCIATION_COUNT)
        prefixCommitMap = hits.associate { it.display to it.commitSuffix }
        return hits.map { it.display }
    }
    
    fun recordInput(text: String) {
        if (SettingsPreferences.isSmartPredictionEnabled(context) && AssociationManager.isInitialized()) {
            AssociationManager.recordInput(text)
        }
    }
    
    fun recordInputPair(lastChar: String, candidate: String) {
        if (SettingsPreferences.isSmartPredictionEnabled(context) && AssociationManager.isInitialized()) {
            AssociationManager.recordInput(lastChar + candidate)
        }
    }
    
    suspend fun getEnglishAssociations(text: String, limit: Int = MAX_ASSOCIATION_COUNT): List<String> {
        return try {
            AssociationService.getAssociations(context, text, true, limit)
        } catch (e: Exception) {
            Log.e(TAG, "English association failed", e)
            emptyList()
        }
    }
    
    suspend fun getChineseAssociations(text: String, limit: Int = MAX_ASSOCIATION_COUNT): List<String> {
        return try {
            if (SettingsPreferences.isSmartPredictionEnabled(context)) {
                if (!AssociationManager.isInitialized()) {
                    AssociationManager.initialize(context)
                }
                AssociationManager.predict(text, limit).map { it.text }
            } else {
                PrefixAssociationEngine.ensureLoaded(context)
                val hits = PrefixAssociationEngine.lookup(text, limit)
                prefixCommitMap = hits.associate { it.display to it.commitSuffix }
                hits.map { it.display }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chinese association failed", e)
            emptyList()
        }
    }
}
