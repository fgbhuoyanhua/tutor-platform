# -*- coding: utf-8 -*-
"""
批量修复：删除 .page 选择器里的 flex: 1

原因：UTS/UVUE 不支持 flex:1（project-rules 明确记录），
     会导致 <scroll-view class="page" style="height:1000px"> 高度异常、页面无法滚动。
     2026-10-26 实测：老师工作台内容被截断且滑不动。

只删除 .page 块内的 flex: 1 行，其他选择器的 flex: 1 保持不动。
"""
import re
from pathlib import Path

BASE = Path(r"E:\毕业设计\project\mobile-app\pages")

TARGETS = [
    "auth/login.uvue",
    "favorite/list.uvue",
    "message/list.uvue",
    "order/create.uvue",
    "order/list.uvue",
    "order/reschedule.uvue",
    "tutor/detail.uvue",
]

NOTE = ("  /* 不要用 flex:1 —— UTS/UVUE 不支持，会让 scroll-view 高度异常导致无法滚动 */\n")


def fix(path: Path):
    text = path.read_text(encoding="utf-8")
    # 精确匹配 .page { ... flex: 1; ... } 块
    pat = re.compile(r"(?s)(\.page\s*\{)([^}]*)(\})")

    def repl(m):
        head, body, tail = m.group(1), m.group(2), m.group(3)
        if not re.search(r"flex\s*:\s*1\s*;", body):
            return m.group(0)
        # 删掉 flex: 1; 那一行（含其所在行的缩进与换行）
        new_body = re.sub(r"[ \t]*flex\s*:\s*1\s*;\s*\n?", "", body)
        # 块内注释只加一次
        if "UTS/UVUE 不支持" not in new_body:
            new_body = "\n" + NOTE + new_body.lstrip("\n")
        return head + new_body.rstrip("\n") + "\n" + tail

    new_text, n = pat.subn(repl, text)
    if n == 0:
        return None
    # 校验：改完后 .page 块里不应再有 flex: 1
    m = re.search(r"(?s)\.page\s*\{([^}]*)\}", new_text)
    if m and re.search(r"flex\s*:\s*1\s*;", m.group(1)):
        return "STILL_HAS_FLEX"
    path.write_text(new_text, encoding="utf-8", newline="\n")
    return "OK"


def main():
    ok, skip, bad = [], [], []
    for rel in TARGETS:
        p = BASE / rel
        if not p.exists():
            skip.append(f"{rel}(不存在)")
            continue
        r = fix(p)
        if r is None:
            skip.append(f"{rel}(无 .page flex:1)")
        elif r == "STILL_HAS_FLEX":
            bad.append(rel)
        else:
            ok.append(rel)
    print(f"已修复 {len(ok)}:")
    for x in ok:
        print("  ", x)
    if skip:
        print("跳过:", skip)
    if bad:
        print("失败:", bad)
    return 0 if not bad else 1


if __name__ == "__main__":
    raise SystemExit(main())
