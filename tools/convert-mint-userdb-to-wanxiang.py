#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 Fcitx 薄荷拼音 userdb 转成万象拼音（标准版）可用的个人词库。

输入：rime_mint.userdb.txt（编码已带声调，如 biàn chéng）
输出：
  1) wanxiang.userdb.txt  — 标准版个人词库（保留声调；db_name=wanxiang）
  2) zc.userdb.txt        — 增强版 PRO 个人词库同内容（db_name=zc；无辅码）
     （PRO 若启用辅码筛选，建议再用万象官方工具补 ;辅码）

注意：
  - Fcitx 原生 pinyin/user.dict（libime 二进制）不是本脚本输入；本脚本吃 Rime sync 的 *.userdb.txt
  - 标准版导入后走 wanxiang.userdb；PRO 走 zc.userdb

Usage:
  python tools/convert-mint-userdb-to-wanxiang.py
  python tools/convert-mint-userdb-to-wanxiang.py --src PATH/rime_mint.userdb.txt
"""

from __future__ import annotations

import argparse
import re
import zipfile
from pathlib import Path

META_RE = re.compile(r"c=(\d+)")
ENTRY_RE = re.compile(r"^(.+?)\t(.+?)(?:\t(.*))?$")

# 带调音节：字母 + 可选声调符；允许 ü / ń 等
SYL_RE = re.compile(
    r"^[a-zA-ZüÜǖǘǚǜńňǹāáǎàēéěèīíǐìōóǒòūúǔùĀÁǍÀĒÉĚÈĪÍǏÌŌÓǑÒŪÚǓÙǕǗǙǛ]+$"
)


def normalize_code(code: str) -> str:
    """保留声调，仅规范化空白与大小写（音节小写）。"""
    parts = [p.strip().lower() for p in code.split() if p.strip()]
    return " ".join(parts)


def is_pinyin_code(code: str) -> bool:
    if not code:
        return False
    return all(SYL_RE.fullmatch(p) for p in code.split())


def parse_entries(text: str) -> dict[tuple[str, str], dict]:
    """(code, word) -> {c, meta}；同码同词合并 c。"""
    merged: dict[tuple[str, str], dict] = {}
    skipped = 0
    for raw in text.splitlines():
        line = raw.rstrip("\n\r")
        if not line or line.lstrip().startswith("#"):
            continue
        m = ENTRY_RE.match(line)
        if not m:
            continue
        code = normalize_code(m.group(1))
        word = m.group(2).strip()
        meta = (m.group(3) or "").strip()
        if not code or not word:
            continue
        if not is_pinyin_code(code):
            skipped += 1
            continue
        c_m = META_RE.search(meta)
        c = int(c_m.group(1)) if c_m else 1
        key = (code, word)
        if key in merged:
            merged[key]["c"] += c
        else:
            merged[key] = {"c": c, "meta": meta}
    return merged


def write_userdb(path: Path, db_name: str, entries: dict[tuple[str, str], dict]) -> int:
    lines = [
        "# Rime user dictionary",
        f"#@/db_name\t{db_name}",
        "#@/db_type\tuserdb",
        "#@/rime_version\t1.16.1",
        "#@/tick\t1",
        "#@/user_id\tconverted-from-rime-mint",
        "# converted from rime_mint.userdb.txt for wanxiang (tones kept)",
    ]
    items = sorted(entries.items(), key=lambda kv: (-kv[1]["c"], kv[0][0], kv[0][1]))
    for (code, word), info in items:
        c = max(1, info["c"])
        lines.append(f"{code}\t{word}\tc={c} d=0 t=1")
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

    base = out_dir / "wanxiang.userdb.txt"
    pro = out_dir / "zc.userdb.txt"
    n = write_userdb(base, "wanxiang", entries)
    write_userdb(pro, "zc", entries)
    files = [base, pro]

    zip_path = out_dir / "wanxiang-from-mint-userdb.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for f in files:
            zf.write(f, arcname=f.name)

    print(f"src     {src}")
    print(f"entries {n}")
    for f in files:
        print(f"out     {f} ({f.stat().st_size} bytes)")
    print(f"zip     {zip_path} ({zip_path.stat().st_size} bytes)")
    print("导入：")
    print("  · Xime：词库管理 → 导入个人词库 → 选 zip，部署一次")
    print("  · 万象标准版：把 wanxiang.userdb.txt 放到 sync/<installation_id>/ 后同步")
    print("  · 万象 PRO：用 zc.userdb.txt（辅码需另用官方工具补）")


if __name__ == "__main__":
    main()
