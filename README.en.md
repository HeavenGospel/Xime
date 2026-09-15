<p align="center">
  <img src="docs/logo.jpg" alt="Xime Logo" width="600">
</p>

<h1 align="center">Xime (Fork) — Personal Improvements</h1>

<p align="center">
  <a href="README.md">简体中文</a> · <a href="README.zh-TW.md">繁體中文</a>
</p>

> **About this repository**  
> This is a **fork** of the official Android IME [ximeiorg/Xime](https://github.com/ximeiorg/Xime), with UX and feature enhancements for personal use.  
> - Upstream: [https://github.com/ximeiorg/Xime](https://github.com/ximeiorg/Xime)  
> - This fork: [https://github.com/HeavenGospel/Xime](https://github.com/HeavenGospel/Xime)  
> - Version: **2.8.3**  
> - Change log (Chinese): [docs/changelog-2026-09-session.md](docs/changelog-2026-09-session.md)

Official docs & ecosystem still follow upstream: [docs](https://ime.ximei.me) · [Windows](https://github.com/ximeiorg/winxime) · [Linux](https://github.com/ximeiorg/xime-wayland)

An Android Wubi / Pinyin IME built on [Rime](https://rime.im/), keeping upstream capabilities plus the fork changes below.

---

## Fork improvements (vs upstream)

### Keyboard & input

| Feature | Notes |
|---------|--------|
| **Swipe cursor** | Tunable haptic; sensitivity; no mis-tap after swipe; no jump when reversing |
| **Syllable break** | Space inserts `'` in composing Pinyin (e.g. `xi'an`) |
| **CN/EN punctuation** | Swipe / long-press follow mode; paired quotes via Rime |
| **Long-press symbols** | No accidental Pinyin candidates; haptic when sliding options |
| **Schema menu toggles** | Show current state; highlight non-default; menu stays open |

### Key customization

- Settings → Appearance → **Key customize**
- Separate ZH / EN QWERTY: face labels (display only), swipe hints, long-press lists
- Presets: Cangjie, Wubi 86, Zhuyin, A–Z upper/lower, etc.
- **JSON import / export / share** (replace or merge)

### Theme & launcher icon

- Default **Material You** (`dynamic`) for new installs (Android 12+)
- Launcher icon follows keyboard theme (system accent or hue palette); night variants

### Toolbar

- Alignment options; long-press drag reorder; scroll position remembered

### Stickers & emoji

- Favorites tab; album import / crop; GIF kept as-is
- Manage in app; top-bar **import/export** (zip)
- Favorites / plugin tabs: bottom-right **Settings** opens manage / plugin page
- Image send fallbacks (share / clipboard)

### Dictionaries

- Schema packages vs personal `userdb` import separated
- Personal dict export as zip; helper scripts for Mint / Fcitx conversion

### Handwriting

- Clear undoes committed handwriting text for the current stroke session

---

## Upstream features (summary)

Inherited from [ximeiorg/Xime](https://github.com/ximeiorg/Xime): Wubi / Pinyin schemas, T9, handwriting, voice, plugins, clipboard sync, WebDAV backup, MD3 themes, floating keyboard, and more.

## Requirements

- Android 9.0 (API 28)+

## Install

### This fork

1. Download from [HeavenGospel/Xime Releases](https://github.com/HeavenGospel/Xime/releases) (prefer **arm64-v8a**)
2. Enable Xime in system IME settings
3. Re-signing requires uninstalling the old build first

Upstream packages: [ximeiorg/Xime Releases](https://github.com/ximeiorg/Xime/releases) · [F-Droid](https://f-droid.org/packages/com.kingzcheung.xime)

### Build

```bash
git clone --recursive https://github.com/HeavenGospel/Xime.git
cd Xime
# Optional: app/keystore.properties for release signing (do not commit)
./gradlew assembleRelease
```

## Docs

- Fork changelog: [docs/changelog-2026-09-session.md](docs/changelog-2026-09-session.md)
- Upstream: [https://ime.ximei.me](https://ime.ximei.me)

## Acknowledgments

- **[ximeiorg/Xime](https://github.com/ximeiorg/Xime)** — [Kingz Cheung](https://github.com/kingzcheung) and upstream contributors
- [Rime](https://rime.im/) · [Trime](https://github.com/osfans/trime) · [fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)

## License

**GPLv3**, same as upstream.

Copyright © 2026 Kingz Cheung (upstream). Fork modifications are also under GPLv3.

Brand assets: see upstream [TRADEMARKS.md](TRADEMARKS.md).
