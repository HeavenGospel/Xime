<p align="center">
  <img src="docs/logo.jpg" alt="Xime Logo" width="600">
</p>

<h1 align="center">Xime（曦码）自用改进版</h1>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-TW.md">繁體中文</a>
</p>

> **关于本仓库**  
> 本仓库是 [ximeiorg/Xime](https://github.com/ximeiorg/Xime)（曦码官方 Android 输入法）的 **fork**，在上游基础上按个人使用习惯做了体验增强与功能补全。  
> - 上游项目：[https://github.com/ximeiorg/Xime](https://github.com/ximeiorg/Xime)  
> - 本 fork：[https://github.com/HeavenGospel/Xime](https://github.com/HeavenGospel/Xime)  
> - 当前版本：**2.8.3**  
> - 改动明细见：[docs/changelog-2026-09-session.md](docs/changelog-2026-09-session.md)

官方文档与生态仍以原项目为准：[使用文档](https://ime.ximei.me) · [Windows](https://github.com/ximeiorg/winxime) · [Linux](https://github.com/ximeiorg/xime-wayland)

基于 [Rime](https://rime.im/) 引擎的 Android 五笔/拼音输入法，保留上游完整能力，并叠加本 fork 的改进。

---

## 本 fork 相对上游的改进

以下为 fork 内陆续落地的改动（不完全随上游发版同步，以本仓库 Release / changelog 为准）。

### 键盘与输入

| 功能 | 说明 |
|------|------|
| **滑动移光标** | 步进震动可调；灵敏度可配；激活后清气泡、抬手不再误点按；左右折返不再跳变 |
| **拼音分音** | 全拼组合态空格插入音节分隔符 `'`（如 `xi'an`），不误触引号标点 |
| **中英标点** | 长按/上滑按中英文模式区分；配对引号走 Rime pair |
| **长按选符** | 选中符号后不再误进拼音候选；滑动切换有震动 |
| **方案菜单开关** | 显示当前状态，非默认高亮；切换后菜单不立刻关闭 |

### 按键自定义

- 设置 → 外观与交互 → **按键自定义**
- 中文 / 英文全键分开编辑：键面字根（仅显示）、上滑 / 下滑提示、长按选项
- 预设：仓颉、五笔 86、注音、英文字母大写 / 小写等
- **JSON 导入 / 导出 / 分享**（覆盖或合并）

### 主题与图标

| 功能 | 说明 |
|------|------|
| **默认动态配色** | 新安装默认 Material You（`dynamic`，Android 12+） |
| **启动图标跟随主题** | 动态主题用系统强调色画图标；固定主题按色相切换色板 |
| **夜间图标** | 深色模式下深底 + 浅色字根 |

### 工具栏

- 工具栏可左对齐 / 居中
- 编辑模式：**长按拖拽排序**（可跨多格）、进入排序有震动
- 非编辑模式记住横向滚动位置

### 表情与收藏

| 功能 | 说明 |
|------|------|
| **收藏表情** | 表情面板独立「收藏」Tab；相册导入、裁剪、动图原样保留 |
| **设置内管理** | 添加 / 删除 / 拖动排序；顶栏 **导入/导出**（zip 包，可覆盖或合并） |
| **面板快捷入口** | 收藏 / 插件 Tab 右下角「设置」→ 跳转收藏管理或对应插件配置 |
| **发图兼容** | 插入失败时可分享到当前应用；可选复制到剪贴板 |

### 词库与方案

- 方案包与个人词库**分开导入**
- 个人词库：导入 + **导出当前方案 userdb 为 zip**
- 附带工具：薄荷方案打包、薄荷 userdb → 简拼转换、Fcitx 手势 → `xime.custom.yaml`

### 手写

- 候选栏「清除」：清笔画的同时撤销本轮手写已上屏文本

---

## 上游原有能力（摘要）

继承自 [ximeiorg/Xime](https://github.com/ximeiorg/Xime)：

- 五笔 86/98、拼音、混输等方案；方案市场 / 无线导入
- QWERTY、T9、笔画、手写、数字（含计算器）、悬浮键盘
- 本地 / 在线语音识别、联想预测、插件市场
- 剪贴板历史与同步、WebDAV 云备份、实体键盘候选栏
- Material Design 3 多主题

默认拼音若不够强，可到扩展商店安装如「凇雾拼音」等方案。

<table align="center">
  <tr>
    <td><img src="docs/Screenshot/full_keyboard_light.jpg" width="180"><br><p align="center">全键盘（亮色）</p></td>
    <td><img src="docs/Screenshot/full_keyboard_dark.jpg" width="180"><br><p align="center">全键盘（暗色）</p></td>
    <td><img src="docs/Screenshot/全键盘_下滑_light.jpg" width="180"><br><p align="center">字根下滑</p></td>
    <td><img src="docs/Screenshot/shotcut_light.jpg" width="180"><br><p align="center">快捷操作</p></td>
  </tr>
  <tr>
    <td><img src="docs/Screenshot/floating.jpg" width="180"><br><p align="center">悬浮键盘</p></td>
    <td><img src="docs/Screenshot/t9_pinyin.jpg" width="180"><br><p align="center">T9 九宫格拼音</p></td>
    <td><img src="docs/Screenshot/hw.png" width="180"><br><p align="center">手写输入</p></td>
    <td><img src="docs/Screenshot/emoji.jpg" width="180"><br><p align="center">Emoji 键盘</p></td>
  </tr>
</table>

## 系统要求

- Android 9.0 (API 28) 及以上

## 安装

### 本 fork Release

1. 在 [HeavenGospel/Xime Releases](https://github.com/HeavenGospel/Xime/releases) 下载 APK  
   - 真机优先 **arm64-v8a**；也可用 **universal**
2. 安装后在系统设置中启用曦码，并设为当前输入法
3. 若换签名安装，需先卸载旧包

上游官方包见：[ximeiorg/Xime Releases](https://github.com/ximeiorg/Xime/releases) · [F-Droid](https://f-droid.org/packages/com.kingzcheung.xime)

### 从源码构建

```bash
git clone --recursive https://github.com/HeavenGospel/Xime.git
cd Xime
# 本地签名：配置 app/keystore.properties（勿提交）
./gradlew assembleRelease
```

产物示例：`app/build/outputs/apk/release/Xime-2.8.3-arm64-v8a.apk`

## 文档

- 本 fork 改动记录：[docs/changelog-2026-09-session.md](docs/changelog-2026-09-session.md)
- 上游使用说明：[https://ime.ximei.me](https://ime.ximei.me)

## 技术栈

- Kotlin · Jetpack Compose · Material Design 3
- Rime (librime) · JNI

## 致谢

- **[ximeiorg/Xime](https://github.com/ximeiorg/Xime)** — 原项目作者 [Kingz Cheung](https://github.com/kingzcheung) 及上游贡献者
- [Rime](https://rime.im/) · [Trime](https://github.com/osfans/trime) · [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)
- [onnxruntime](https://github.com/microsoft/onnxruntime)

## 许可证

与上游一致：**GPLv3**

Copyright © 2026 Kingz Cheung（原项目）  
本 fork 的修改同样按 GPLv3 开源。

「Xime」名称、Logo 等品牌资产不在 GPLv3 范围内，详见上游 [TRADEMARKS.md](TRADEMARKS.md)。
