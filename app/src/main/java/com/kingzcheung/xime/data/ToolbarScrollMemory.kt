package com.kingzcheung.xime.data

/**
 * 候选栏 Idle 工具栏横向滚动位置（进程内记忆）。
 * 打字进出 Idle、收起再弹出键盘时仍回到上次滑到的位置。
 */
object ToolbarScrollMemory {
    @Volatile
    var offsetPx: Int = 0
}
