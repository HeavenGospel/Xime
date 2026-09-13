package com.kingzcheung.xime.service

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.kingzcheung.xime.R
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

enum class HapticMode(val value: String) {
    FollowingSystem("following_system"),
    Enabled("enabled"),
    Disabled("disabled");

    companion object {
        fun fromValue(value: String): HapticMode =
            entries.find { it.value == value } ?: FollowingSystem
    }
}

class FeedbackManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<String, Int>()

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private var hasAmplitudeControl = false

    private val feedbackScope = CoroutineScope(Dispatchers.Default)

    private var soundEnabled = true
    private var soundVolume = 50
    private var hapticMode = HapticMode.FollowingSystem
    private var hapticOnKeyUp = false
    private var pressDuration = 0L
    private var longPressDuration = 0L
    private var pressAmplitude = 0
    private var longPressAmplitude = 0
    private var cursorMoveDuration = 0L
    private var cursorMoveAmplitude = 0

    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun initialize() {
        hasAmplitudeControl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator.hasAmplitudeControl()
        initSoundPool()
        loadSettings()
        registerPrefsListener()
    }

    fun release() {
        prefsListener?.let {
            SettingsPreferences.getPrefsPublic(context).unregisterOnSharedPreferenceChangeListener(it)
        }
        soundPool?.release()
        soundPool = null
        feedbackScope.cancel()
    }

    private fun initSoundPool() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        soundPool?.let { pool ->
            soundIds["standard"] = pool.load(context, R.raw.kb_key_standard, 1)
            soundIds["delete"] = pool.load(context, R.raw.kb_key_delete, 1)
            soundIds["space"] = pool.load(context, R.raw.kb_key_space, 1)
            soundIds["enter"] = pool.load(context, R.raw.kb_key_enter, 1)
        }
    }

    private fun loadSettings() {
        soundEnabled = SettingsPreferences.isSoundEnabled(context)
        soundVolume = SettingsPreferences.getSoundVolume(context)
        hapticMode = HapticMode.fromValue(SettingsPreferences.getHapticMode(context))
        hapticOnKeyUp = SettingsPreferences.isHapticOnKeyUp(context)
        pressDuration = SettingsPreferences.getVibrationPressDuration(context).toLong()
        longPressDuration = SettingsPreferences.getVibrationLongPressDuration(context).toLong()
        pressAmplitude = SettingsPreferences.getVibrationPressAmplitude(context)
        longPressAmplitude = SettingsPreferences.getVibrationLongPressAmplitude(context)
        cursorMoveDuration = SettingsPreferences.getVibrationCursorMoveDuration(context).toLong()
        cursorMoveAmplitude = SettingsPreferences.getVibrationCursorMoveAmplitude(context)
    }

    private fun registerPrefsListener() {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "sound_enabled" -> soundEnabled = SettingsPreferences.isSoundEnabled(context)
                "sound_volume" -> soundVolume = SettingsPreferences.getSoundVolume(context)
                "haptic_mode" -> hapticMode = HapticMode.fromValue(SettingsPreferences.getHapticMode(context))
                "haptic_on_keyup" -> hapticOnKeyUp = SettingsPreferences.isHapticOnKeyUp(context)
                "vibration_press_duration" -> pressDuration = SettingsPreferences.getVibrationPressDuration(context).toLong()
                "vibration_long_press_duration" -> longPressDuration = SettingsPreferences.getVibrationLongPressDuration(context).toLong()
                "vibration_press_amplitude" -> pressAmplitude = SettingsPreferences.getVibrationPressAmplitude(context)
                "vibration_long_press_amplitude" -> longPressAmplitude = SettingsPreferences.getVibrationLongPressAmplitude(context)
                "vibration_cursor_move_duration" -> cursorMoveDuration = SettingsPreferences.getVibrationCursorMoveDuration(context).toLong()
                "vibration_cursor_move_amplitude" -> cursorMoveAmplitude = SettingsPreferences.getVibrationCursorMoveAmplitude(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun playKeySound(keyType: String = "standard") {
        if (!soundEnabled) return

        val pool = soundPool ?: return
        val soundId = soundIds[keyType] ?: soundIds["standard"] ?: return
        val volume = soundVolume / 100f

        pool.play(soundId, volume, volume, 1, 0, 1.0f)
    }

    fun hapticFeedback(view: View, longPress: Boolean = false, keyUp: Boolean = false) {
        when (hapticMode) {
            HapticMode.Enabled -> {}
            HapticMode.Disabled -> return
            HapticMode.FollowingSystem -> {
                val systemEnabled = try {
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.HAPTIC_FEEDBACK_ENABLED,
                        1
                    ) == 1
                } catch (_: Exception) {
                    true
                }
                if (!systemEnabled) return
            }
        }
        if (keyUp && !hapticOnKeyUp) return

        val duration = if (longPress) longPressDuration else pressDuration
        val amplitude = if (longPress) longPressAmplitude else pressAmplitude
        val hfc: Int = if (longPress) {
            HapticFeedbackConstants.LONG_PRESS
        } else if (keyUp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.KEYBOARD_RELEASE
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }

        if (duration != 0L) {
            if (hasAmplitudeControl && amplitude != 0) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } else {
            var flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            if (hapticMode == HapticMode.Enabled) {
                @Suppress("DEPRECATION")
                flags = flags or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            }
            view.performHapticFeedback(hfc, flags)
        }
    }

    /**
     * 滑动键盘移动光标时的短振动。
     * 自定义时长/振幅均为 0 时使用系统 [HapticFeedbackConstants.CLOCK_TICK] / TEXT_HANDLE_MOVE。
     */
    fun cursorMoveHaptic(view: View) {
        when (hapticMode) {
            HapticMode.Enabled -> {}
            HapticMode.Disabled -> return
            HapticMode.FollowingSystem -> {
                val systemEnabled = try {
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.HAPTIC_FEEDBACK_ENABLED,
                        1
                    ) == 1
                } catch (_: Exception) {
                    true
                }
                if (!systemEnabled) return
            }
        }

        if (cursorMoveDuration != 0L) {
            if (hasAmplitudeControl && cursorMoveAmplitude != 0) {
                vibrator.vibrate(VibrationEffect.createOneShot(cursorMoveDuration, cursorMoveAmplitude))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(cursorMoveDuration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(cursorMoveDuration)
            }
            return
        }

        // 仅设置振幅时：用短脉冲体现强度
        if (hasAmplitudeControl && cursorMoveAmplitude != 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(12L, cursorMoveAmplitude))
            }
            return
        }

        val hfc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        var flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        if (hapticMode == HapticMode.Enabled) {
            @Suppress("DEPRECATION")
            flags = flags or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        }
        view.performHapticFeedback(hfc, flags)
    }

    fun performKeyPressEffect(keyType: String = "standard", view: View) {
        playKeySound(keyType)
        hapticFeedback(view)
    }

    fun performKeyPressDownEffect(key: String, view: View) {
        val keyType = when (key) {
            "delete", "clear_composition" -> "delete"
            "enter" -> "enter"
            "space" -> "space"
            else -> "standard"
        }
        playKeySound(keyType)
        hapticFeedback(view)
    }

    fun performVibration() {
        if (hapticMode == HapticMode.Disabled) return
        if (!vibrator.hasVibrator()) return
        val duration = if (pressDuration != 0L) pressDuration else 20L
        val amplitude = if (hasAmplitudeControl && pressAmplitude != 0) pressAmplitude else VibrationEffect.DEFAULT_AMPLITUDE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
