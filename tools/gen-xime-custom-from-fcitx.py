#!/usr/bin/env python3
"""Generate Xime keyboard gestures from Fcitx5 PopupPreset + TextKeyboardLayout."""
from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
FCITX = ROOT / "org.fcitx.fcitx5.android.fx" / "files" / "config"
OUT = ROOT / "Xime" / "app" / "src" / "main" / "assets" / "xime.custom.yaml"
PACK_COPY = ROOT / "Xime" / "build" / "scheme-release" / "xime.custom.mint.yaml"


def yaml_escape(s: str) -> str:
    # Always quote non-alnum symbols so YAML doesn't treat ? ( ) = etc. specially.
    if s.isalnum() and s.isascii():
        return s
    return json.dumps(s, ensure_ascii=False)


# Fcitx 键面 alt 多为半角；中文输入时改为全角标点
ZH_PUNCT_MAP = {
    "?": "？",
    "!": "！",
    "(": "（",
    ")": "）",
    ":": "：",
    "~": "～",
    ",": "，",
    ".": "。",
    ";": "；",
    "[": "【",
    "]": "】",
    "<": "《",
    ">": "》",
    "{": "『",
    "}": "』",
    "\\": "、",
    "/": "／",
    "*": "＊",
    "+": "＋",
    "=": "＝",
    "-": "－",
    "_": "——",
    "^": "……",
    "`": "·",
    "|": "｜",
    "&": "＆",
    "%": "％",
    "#": "＃",
    "@": "＠",
    "$": "￥",
}

# 引号：显示中文，value 用 ASCII 以便 Rime 成对切换
ZH_QUOTE_ASCII = {
    '"': '"',
    "“": '"',
    "”": '"',
    "'": "'",
    "‘": "'",
    "’": "'",
}
ZH_QUOTE_LABEL = {
    '"': "“",
    "“": "“",
    "”": "“",
    "'": "‘",
    "‘": "‘",
    "’": "‘",
}


def to_zh_punct(s: str) -> str:
    return "".join(ZH_PUNCT_MAP.get(ch, ch) for ch in s)


def quote_ascii(s: str) -> str | None:
    return ZH_QUOTE_ASCII.get(s)


def format_long_press_value(v: str, *, zh: bool) -> str:
    if zh and (qa := quote_ascii(v)) is not None:
        return (
            f"{{ label: {yaml_escape(ZH_QUOTE_LABEL[v])}, "
            f"value: {yaml_escape(qa)} }}"
        )
    if zh and len(v) == 1 and v in ZH_PUNCT_MAP:
        return yaml_escape(ZH_PUNCT_MAP[v])
    return yaml_escape(v)


def format_swipe(alt: str, *, zh: bool) -> str:
    if zh and (qa := quote_ascii(alt)) is not None:
        return (
            f"{{ label: {yaml_escape(ZH_QUOTE_LABEL[alt])}, "
            f"value: {yaml_escape(qa)} }}"
        )
    return yaml_escape(alt)


def alts(layout: dict, section: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for row in layout[section]:
        for k in row:
            if k.get("type") == "AlphabetKey":
                out[k["main"].lower()] = k["alt"]
    return out


def key_line(letter: str, alt: str, popup_vals: list, *, zh: bool = False) -> str:
    raw = [str(v) for v in (popup_vals or [letter, letter.upper()])]
    if zh:
        # 引号保留原样交给 format_*；其它半角标点转全角
        alt_disp = alt if quote_ascii(alt) is not None else to_zh_punct(alt)
        vals: list[str] = [alt_disp] if alt_disp else []
        seen: set[str] = set()
        if alt_disp:
            qa0 = quote_ascii(alt_disp)
            seen.add(qa0 if qa0 is not None else alt_disp)
        for v in raw:
            qa = quote_ascii(v)
            if qa is not None:
                canon, item = qa, v
            elif len(v) == 1 and v in ZH_PUNCT_MAP:
                canon = item = ZH_PUNCT_MAP[v]
            else:
                canon = item = v
            if canon in seen:
                continue
            seen.add(canon)
            vals.append(item)
        swipe = format_swipe(alt_disp or alt, zh=True)
    else:
        # 英文：仅 ASCII 字母长大写（希腊字母保持原样）
        vals = []
        for v in raw:
            if len(v) == 1 and v.isascii() and v.isalpha() and v.islower():
                v = v.upper()
            if v not in vals:
                vals.append(v)
        swipe = yaml_escape(alt)

    vs = ", ".join(format_long_press_value(v, zh=zh) for v in vals)
    return (
        f"      {letter}: {{ tap: {yaml_escape(letter)}, "
        f"swipe_up: {swipe}, "
        f'long_press: {{ display: "bubble", values: [{vs}] }} }}'
    )


def main() -> None:
    popup = json.loads((FCITX / "PopupPreset.json").read_text(encoding="utf-8"))
    layout = json.loads((FCITX / "TextKeyboardLayout.json").read_text(encoding="utf-8"))
    zh_alt = alts(layout, "rime")
    en_alt = alts(layout, "keyboard-us")

    lines: list[str] = [
        "metadata:",
        "  app_name: Xime",
        '  app_version: ">=2.5.0"',
        "  platform: android",
        "  config_version: 1",
        "  generator: fcitx-PopupPreset+TextKeyboardLayout",
        "",
        "# 由 Fcitx5 的 PopupPreset.json / TextKeyboardLayout.json 生成。",
        "# 上滑 = AlphabetKey.alt；中文长按气泡 = 全角标点 + PopupPreset。",
        "# 若用户目录已有 xime.custom.yaml，会优先生效并覆盖本文件。",
        "keyboard:",
        "  qwerty:",
        "    keys:",
    ]
    for ch in "qwertyuiopasdfghjklzxcvbnm":
        lines.append(key_line(ch, zh_alt[ch], popup.get(ch, [ch, ch.upper()]), zh=True))

    if "。" in popup:
        vs = ", ".join(yaml_escape(v) for v in popup["。"])
        lines.append(
            f'      ".": {{ tap: {{ label: "。", value: "。" }}, '
            f'long_press: {{ display: "bubble", values: [{vs}] }} }}'
        )

    lines += ["", "  qwerty_en:", "    keys:"]
    for ch in "qwertyuiopasdfghjklzxcvbnm":
        pop = popup.get(ch.upper(), popup.get(ch, [ch, ch.upper()]))
        lines.append(key_line(ch, en_alt[ch], pop, zh=False))

    text = "\n".join(lines) + "\n"
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    PACK_COPY.parent.mkdir(parents=True, exist_ok=True)
    PACK_COPY.write_text(text, encoding="utf-8")
    print(f"OK {OUT}")
    print(f"OK {PACK_COPY}")


if __name__ == "__main__":
    main()
