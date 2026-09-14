#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 Fcitx 薄荷拼音 userdb 转成 Xime 默认「简体拼音」(pinyin_simp) 可用的个人词库。

输入：rime_mint.userdb.txt（编码带声调，如 biàn chéng）
输出：
  1) pinyin_simp.userdb.txt  — 去声调后的 Rime 用户词典，可走「词库管理 → 导入个人词库」
  2) user_simp.dict.yaml     — pinyin_simp / t9_pinyin 的 translator.packs，利于简拼召回

Usage:
  python tools/convert-mint-userdb-to-pinyin-simp.py
  python tools/convert-mint-userdb-to-pinyin-simp.py --src PATH/rime_mint.userdb.txt
"""

from __future__ import annotations

import argparse
import re
import zipfile
from collections import defaultdict
from pathlib import Path

TONE_MAP = str.maketrans(
    {
        "ā": "a",
        "á": "a",
        "ǎ": "a",
        "à": "a",
        "ē": "e",
        "é": "e",
        "ě": "e",
        "è": "e",
        "ī": "i",
        "í": "i",
        "ǐ": "i",
        "ì": "i",
        "ō": "o",
        "ó": "o",
        "ǒ": "o",
        "ò": "o",
        "ū": "u",
        "ú": "u",
        "ǔ": "u",
        "ù": "u",
        "ǖ": "ü",
        "ǘ": "ü",
        "ǚ": "ü",
        "ǜ": "ü",
        "ń": "n",
        "ň": "n",
        "ǹ": "n",
        "Ā": "a",
        "Á": "a",
        "Ǎ": "a",
        "À": "a",
        "Ē": "e",
        "É": "e",
        "Ě": "e",
        "È": "e",
        "Ī": "i",
        "Í": "i",
        "Ǐ": "i",
        "Ì": "i",
        "Ō": "o",
        "Ó": "o",
        "Ǒ": "o",
        "Ò": "o",
        "Ū": "u",
        "Ú": "u",
        "Ǔ": "u",
        "Ù": "u",
        "Ǖ": "ü",
        "Ǘ": "ü",
        "Ǚ": "ü",
        "Ǜ": "ü",
    }
)

META_RE = re.compile(r"c=(\d+)")
ENTRY_RE = re.compile(r"^(.+?)\t(.+?)(?:\t(.*))?$")


def strip_syllable(syl: str) -> str:
    s = syl.strip().translate(TONE_MAP).lower()
    # pinyin_simp 词库用 v 表示 ü（女 nv、绿 lv）
    s = s.replace("ü", "v").replace("ǚ", "v")
    return s


def strip_code(code: str) -> str:
    parts = [strip_syllable(p) for p in code.split()]
    return " ".join(p for p in parts if p)


def parse_entries(text: str) -> dict[tuple[str, str], dict]:
    """(code, word) -> {c, d_raw, t_raw}；同码同词合并 c。"""
    merged: dict[tuple[str, str], dict] = {}
    for raw in text.splitlines():
        line = raw.rstrip("\n\r")
        if not line or line.lstrip().startswith("#"):
            continue
        m = ENTRY_RE.match(line)
        if not m:
            continue
        code = strip_code(m.group(1))
        word = m.group(2).strip()
        meta = (m.group(3) or "").strip()
        if not code or not word:
            continue
        # 跳过明显非拼音编码（反查/其它）
        if not re.fullmatch(r"[a-z]+(?: [a-z]+)*", code):
            continue
        c_m = META_RE.search(meta)
        c = int(c_m.group(1)) if c_m else 1
        key = (code, word)
        if key in merged:
            merged[key]["c"] += c
        else:
            merged[key] = {"c": c, "meta": meta}
    return merged


def write_userdb(path: Path, entries: dict[tuple[str, str], dict]) -> int:
    lines = [
        "# Rime user dictionary",
        "#@/db_name\tpinyin_simp",
        "#@/db_type\tuserdb",
        "#@/rime_version\t1.16.1",
        "#@/tick\t1",
        "#@/user_id\tconverted-from-rime-mint",
    ]
    # 按选词次数降序，便于预览
    items = sorted(entries.items(), key=lambda kv: (-kv[1]["c"], kv[0][0], kv[0][1]))
    for (code, word), info in items:
        c = max(1, info["c"])
        lines.append(f"{code}\t{word}\tc={c} d=0 t=1")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(items)


def write_user_simp_dict(path: Path, entries: dict[tuple[str, str], dict]) -> int:
    lines = [
        "# Rime dictionary",
        "# encoding: utf-8",
        "# converted from rime_mint.userdb.txt for pinyin_simp / t9_pinyin packs",
        "---",
        "name: user_simp",
        'version: "1.0"',
        "sort: by_weight",
        "...",
    ]
    items = sorted(entries.items(), key=lambda kv: (-kv[1]["c"], kv[0][1], kv[0][0]))
    for (code, word), info in items:
        weight = max(1, info["c"])
        lines.append(f"{word}\t{code}\t{weight}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(items)


def default_src(root: Path) -> Path:
    sync = root / "org.fcitx.fcitx5.android.fx" / "files" / "data" / "rime" / "sync"
    if sync.is_dir():
        for d in sync.iterdir():
            cand = d / "rime_mint.userdb.txt"
            if cand.is_file():
                return cand
    raise SystemExit(f"找不到 rime_mint.userdb.txt，请用 --src 指定。已搜: {sync}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", type=Path, help="rime_mint.userdb.txt 路径")
    ap.add_argument(
        "--out-dir",
        type=Path,
        help="输出目录（默认 Xime/build/scheme-release）",
    )
    args = ap.parse_args()

    tools = Path(__file__).resolve().parent
    xime = tools.parent
    root = xime.parent

    src = args.src or default_src(root)
    out_dir = args.out_dir or (xime / "build" / "scheme-release")
    out_dir.mkdir(parents=True, exist_ok=True)

    text = src.read_text(encoding="utf-8")
    entries = parse_entries(text)
    if not entries:
        raise SystemExit(f"未解析到词条: {src}")

    userdb = out_dir / "pinyin_simp.userdb.txt"
    pack = out_dir / "user_simp.dict.yaml"
    n1 = write_userdb(userdb, entries)
    n2 = write_user_simp_dict(pack, entries)

    zip_path = out_dir / "pinyin-simp-from-mint-userdb.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(userdb, arcname=userdb.name)
        zf.write(pack, arcname=pack.name)

    print(f"src     {src}")
    print(f"entries {n1}")
    print(f"userdb  {userdb} ({userdb.stat().st_size} bytes)")
    print(f"pack    {pack} ({pack.stat().st_size} bytes)")
    print(f"zip     {zip_path} ({zip_path.stat().st_size} bytes)")
    print("导入：Xime → 设置 → 词库管理 → 导入个人词库 → 选 zip，然后部署一次。")


if __name__ == "__main__":
    main()
