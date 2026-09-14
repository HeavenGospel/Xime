# 会话工作整理（2026-09-13 ～ 2026-09-14）

从「键盘滑动移光标」优化开始，到个人词库导出为止的改动汇总。  
首批光标相关改动已提交：`6cb89e3c feat(keyboard): 完善滑动移光标体验`；其后大量改动仍在工作区，尚未整体提交。

---

## 一、键盘滑动移光标

| 项 | 说明 |
|----|------|
| 震动反馈 | 每移动一步短震动；设置 → **按键效果** → 可调时长/强度 |
| 灵敏度 | 设置 → **布局与显示** → 步进距离、激活距离可配 |
| 气泡 | 滑动激活后立即清除起始键按下态与气泡 |
| 误触 | 光标滑动激活后，抬手不再触发点按上屏 |
| 跳变修复 | 累计位移改按「步长取整」，避免左右折返时光标闪跳 |

**相关文件**：`KeyboardView.kt`、`KeyButton.kt`、`SwipeBubble.kt`、`FeedbackManager.kt`、`KeyEffectSettingsScreen.kt`、`LayoutDisplaySettingsScreen.kt`、`SettingsPreferences.kt`

---

## 二、手写

- 候选栏「清除」：清笔画的同时撤销本轮手写已上屏文本（不再只清笔画留字）。
- 手写键盘高度调节：曾讨论过，按用户要求**未改**。

---

## 三、工具栏

| 项 | 说明 |
|----|------|
| 对齐 | 设置里可选工具栏左对齐 / 居中 |
| 编辑排序 | 编辑模式下长按拖拽排序（非相邻交换）；进入排序有震动；拖拽跟随手指 |
| 滚动记忆 | 非编辑模式下记住横向滚动位置，重开不闪回再跳 |

**相关文件**：`ToolbarCustomizeView.kt`、`ToolbarScrollMemory.kt`、`MenuBar.kt`、布局/显示设置

---

## 四、表情包 / 收藏表情

### 4.1 插件与发图

- 表情插件启用后，面板出现对应 Tab（如恶搞兔）。
- 发图兼容：失败时可「分享到当前应用」、可选「复制到剪贴板」（设置开关，默认开）。
- GIF：尽量原样保留；微信等无法直接插入动图时走分享路径。

### 4.2 收藏表情（仿微信）

- 表情面板增加 **收藏** Tab；相册多选导入（系统选图器）。
- 静图：裁剪/旋转（含常用比例）；动图跳过裁剪原样入库。
- 长按：移到前面 / 删除 / 取消（无「收藏表情」标题）。
- 导入结束后回到收藏页，并记忆上次选中的分类 Tab。
- **App 设置**内可管理：添加、删除、长按拖动排序。  
  入口：设置主页「收藏表情」/「布局与显示 → 管理收藏表情」。

**相关文件**：`FavoriteStickersStore.kt`、`EmojiKeyboardLayout.kt`、`ui/emoji/*`、`FavoriteStickersSettingsScreen.kt`、`ClipboardManager.kt`、布局显示设置中的发图开关

---

## 五、键位与标点

| 项 | 说明 |
|----|------|
| 长按选符 | 选中符号后不再误进拼音候选；滑动切换选项有震动 |
| 中英标点 | 中文模式长按/上滑偏中文标点；英文模式偏 ASCII；逗号键中英分别对应 |
| 英文键帽 | 英文布局显示大写字母（与中文键面一致） |
| 配对引号 | 中文模式走 ASCII `"`/`'` 进 Rime pair，避免一直出左引号 |
| 配置生成 | `tools/gen-xime-custom-from-fcitx.py` 从 Fcitx 手势生成 `xime.custom.yaml` |

**相关文件**：`xime.yaml` / `xime.custom.yaml`、`ImeKeyRouter.kt`、`ImeTextCommit.kt`、`KeysConfigHelper.kt`、`CommonSymbolKeyboardLayout.kt`、`KeyboardLayout.kt`

### 方案菜单开关

- 保留「中文🔁西文 / 半角🔁全角 / 。，🔁．，」。
- 展示**当前状态**，非默认时高亮；点击切换不立刻关菜单。

---

## 六、拼音分音（空格）

- 全拼组合态：空格 = 插入音节分隔符 `'`（如 `xi` + 空格 + `an` → `xi'an`）。
- 实现：`setInput` 追加 `'`，**不** `processKey(')`，避免被引号 punctuator 清码。
- 上屏：点候选或数字键；九键/英文空格仍按原逻辑。
- 解除「单引号 = 选第 3 候选」绑定，避免与分音冲突。

**相关文件**：`RimeEngine.appendSyllableDelimiter()`、`ImeKeyRouter.kt`、`default.yaml` / `default.custom.yaml`

---

## 七、词库与方案（Fcitx / 薄荷）

### 7.1 概念区分

| 名称 | 是什么 | 是否改官方大词库 |
|------|--------|------------------|
| 方案词库 | 方案自带大词典（浏览器约 5 万上限截断） | 否 |
| 个人词库 | `*.userdb.txt` 打字习惯 / 导入习惯 | 旁挂，不改官方表 |
| 自定义短语 | 用户手写高优先级短句 | 独立 table_translator |
| 智能联想 | 上屏后的下一词预测 | 与词库时机不同，不冲突 |

个人词库**按方案绑定**（`rime_mint.userdb` ≠ `pinyin_simp.userdb`）。

### 7.2 已做能力

- 方案包与个人词库**分开**导入（方案 zip 不再自动吞 userdb）。
- 词库管理 → 个人词库：**导入**、**导出当前方案 userdb 为 zip**（Downloads）。
- 从 Fcitx 薄荷 userdb **转换**为默认简拼可用：  
  `tools/convert-mint-userdb-to-pinyin-simp.py`  
  产物：`build/scheme-release/pinyin-simp-from-mint-userdb.zip`  
  （去声调、`ü→v`，生成 `pinyin_simp.userdb.txt` + `user_simp.dict.yaml`）
- 薄荷完整方案打包脚本：`tools/pack-rime-mint.ps1`（方案 zip + 可选 userdb zip）。

**相关文件**：`UserDictImporter.kt`、`UserDictManager.kt`、`PersonalDictViewModel.kt`、`DictionarySettingsScreen.kt`、`ImportManager.kt`、`SchemaManager.kt`

### 7.3 刻意不做的

- 个人词库不做逐条编辑（与「自定义短语」分工，避免重复）。
- 清除整库：曾做过草案，按最终需求**只保留导出**。

---

## 八、构建与安装注意

- Release 若无 `keystore.properties`，需用 debug 密钥签名后再装（`-signed.apk`）。
- 真机优先：`Xime-2.8.1-arm64-v8a-signed.apk`。
- 换签名需先卸载旧包。

---

## 九、工具脚本一览

| 脚本 | 作用 |
|------|------|
| `tools/gen-xime-custom-from-fcitx.py` | Fcitx 上滑/长按 → `xime.custom.yaml` |
| `tools/pack-rime-mint.ps1` | 打包薄荷方案 / userdb zip |
| `tools/convert-mint-userdb-to-pinyin-simp.py` | 薄荷 userdb → 简拼个人词库 |

---

## 十、建议后续提交拆分（未提交工作区）

便于 review，可按主题拆 commit，例如：

1. 工具栏对齐 / 拖拽排序 / 滚动记忆  
2. 收藏表情 + App 设置管理 + 发图兜底开关  
3. 中英标点 / 长按符号 / 配对引号 / xime.custom 生成  
4. 空格分音（`appendSyllableDelimiter`）  
5. 个人词库导入导出 + 薄荷转换工具  
6. 方案菜单状态展示  

（插件资源重命名、无关 diff 可另开或丢弃。）

---

## 十一、时间线（简）

```
09-13  滑动移光标震动/灵敏度/误触 → 提交 6cb89e3c
       手写清除撤销上屏
       表情插件可见性、发图分享/剪贴板开关
       工具栏居中选项、编辑拖拽排序、滚动记忆
       收藏表情（导入/裁剪/GIF/记忆 Tab）
09-14  光标折返跳变修复
       收藏裁剪与返回路径、动图微信分享特例
       薄荷方案/词库拆分导入
       长按符号误输入修复、中英标点与键帽
       空格分音（setInput）；配对引号
       App 内收藏表情管理
       薄荷 userdb → 简拼转换；个人词库导出
```

---

*文档生成自本会话实现与讨论，便于回顾与后续提交拆分。*
