#!/usr/bin/env python3
"""对齐雾凇/清风短码策略：禁止会抢简拼首页的整码/音节。

清风主库用 rime-frost/ice；其对 n/m/ng/hng 等「非标准音节」是注释掉的
（见 cn_dicts/41448.dict.yaml）。Android 底库曾带：
  - 单字整码：嗯→n、呒→m
  - 词组首音节：嗯哼→n heng  （只删单字不够，点 n 仍会出嗯）

本脚本扫主库+扩展库：
  1) frost 明确禁用的 (字, 编码)
  2) 单字母整码中除 a/e/o 以外的条目
  3) 编码任意音节为 n / m / hng 的条目（词组也算）
  --fix 时：能安全改写的改写（n→en），否则删除

用法：
  python scripts/guard_frost_short_codes.py --check
  python scripts/guard_frost_short_codes.py --fix
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DICT_DIR = ROOT / "app" / "src" / "main" / "assets" / "rime"
TARGETS = [
    DICT_DIR / "pinyin_simp.dict.yaml",
    DICT_DIR / "pinyin_simp_ext.dict.yaml",
]

FROST_COMMENTED: set[tuple[str, str]] = {
    ("唔", "n"),
    ("嗯", "n"),
    ("㕶", "n"),
    ("𠮾", "n"),
    ("𧗈", "n"),
    ("呒", "m"),
    ("呣", "m"),
    ("嘸", "m"),
    ("嗯", "ng"),
    ("唔", "ng"),
    ("㕶", "ng"),
    ("哼", "hng"),
}

ALLOWED_SINGLE_LETTER = frozenset({"a", "e", "o"})
# 出现在任意音节位都会让「点 n/m」整码命中
FORBIDDEN_SYLLABLES = frozenset({"n", "m", "hng"})

ENTRY_RE = re.compile(r"^([^\t#][^\t]*)\t([a-z]+(?: [a-z]+)*)(?:\t(.*))?$")


def rewrite_code(word: str, code: str) -> str | None:
    """若可安全改写则返回新编码，否则 None（表示应删除）。"""
    syls = code.split(" ")
    if not any(s in FORBIDDEN_SYLLABLES for s in syls):
        return None
    # 嗯/唔 系：n → en（与 frost 词组一致）
    if all(ch in "嗯唔哼" or ord(ch) > 0x9FFF for ch in word) or word.startswith(("嗯", "唔")):
        new = [ ("en" if s == "n" else s) for s in syls ]
        new = [ s for s in new if s not in {"m", "hng"} ]
        # 仍含禁用音节则放弃
        if any(s in FORBIDDEN_SYLLABLES for s in new):
            return None
        return " ".join(new) if new else None
    return None


def classify(word: str, code: str) -> str | None:
    if (word, code) in FROST_COMMENTED:
        return "frost-commented"
    if re.fullmatch(r"[a-z]", code) and code not in ALLOWED_SINGLE_LETTER:
        return "illegal-single-letter"
    if any(s in FORBIDDEN_SYLLABLES for s in code.split(" ")):
        return "forbidden-syllable"
    return None


def scan_file(path: Path) -> list[tuple[int, str, str, str]]:
    hits: list[tuple[int, str, str, str]] = []
    if not path.exists():
        return hits
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        m = ENTRY_RE.match(line)
        if not m:
            continue
        word, code = m.group(1), m.group(2)
        reason = classify(word, code)
        if reason:
            hits.append((i, word, code, reason))
    return hits


def fix_file(path: Path) -> tuple[int, int]:
    """返回 (rewritten, removed)。"""
    if not path.exists():
        return 0, 0
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    out: list[str] = []
    rewritten = removed = 0
    for line in lines:
        raw = line.rstrip("\n").rstrip("\r")
        m = ENTRY_RE.match(raw)
        if not m:
            out.append(line if line.endswith("\n") else line + "\n")
            continue
        word, code, rest = m.group(1), m.group(2), m.group(3)
        reason = classify(word, code)
        if not reason:
            out.append(line if line.endswith("\n") else line + "\n")
            continue
        new_code = rewrite_code(word, code)
        if new_code and new_code != code:
            weight = f"\t{rest}" if rest is not None else ""
            out.append(f"{word}\t{new_code}{weight}\n")
            rewritten += 1
        else:
            removed += 1
    if rewritten or removed:
        path.write_text("".join(out), encoding="utf-8")
    return rewritten, removed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    g = parser.add_mutually_exclusive_group(required=True)
    g.add_argument("--check", action="store_true", help="仅检查，有违规则退出码 1")
    g.add_argument("--fix", action="store_true", help="改写/删除违规编码")
    args = parser.parse_args()

    total = 0
    for path in TARGETS:
        rel = path.relative_to(ROOT)
        if args.check:
            hits = scan_file(path)
            if hits:
                print(f"[FAIL] {rel}: {len(hits)} 条")
                for i, w, c, why in hits[:40]:
                    print(f"  L{i}\t{w}\t{c}\t({why})")
                if len(hits) > 40:
                    print(f"  ... +{len(hits) - 40}")
                total += len(hits)
            else:
                print(f"[OK] {rel}")
        else:
            rw, rm = fix_file(path)
            total += rw + rm
            print(f"[FIX] {rel}: 改写 {rw} / 删除 {rm}")

    if args.check and total:
        print(
            "\n一键清理: python scripts/guard_frost_short_codes.py --fix",
            file=sys.stderr,
        )
        return 1
    if args.check:
        print("短码策略已对齐 frost（禁 n/m/hng 音节；单字母仅 a/e/o）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
