# -*- coding: utf-8 -*-
"""
统一给所有页面底部加留白，避免内容被 bottom tab 栏 / 系统导航栏遮挡。

依据：
  - 实测 tab 栏占据 y=1770~1920，约 150px（= 300rpx，按 1080 宽 / 480dpi）
  - 非 tab 页面底部有系统导航栏，约 48~72px，留 120rpx 足够

规则（project-rules 第 7 条不擅自改功能）：只加 padding-bottom，
不动其他样式、不加/删任何功能元素。
"""
import re
from pathlib import Path

BASE = Path(r"E:\毕业设计\project\mobile-app\pages")

# 4 个 tab 页 —— 需要避开 tab 栏
TAB_PAGES = {
    "home/index.uvue",
    "message/list.uvue",
    "order/list.uvue",
    "me/index.uvue",
}

TAB_PB = "300rpx"      # tab 栏 ~150px
NORMAL_PB = "120rpx"   # 系统导航栏 + 余量


def set_pb(text: str, pb: str):
    """只操作 .page 块，把 padding-bottom 设为 pb（保留原有左右/上内边距）"""
    m = re.search(r"(?s)(\.page\s*\{)([^}]*)(\})", text)
    if not m:
        return None, "no-page-block"

    head, body, tail = m.group(1), m.group(2), m.group(3)

    # 1) 有 padding-bottom 的，直接替换
    if re.search(r"padding-bottom\s*:\s*[^;]+;", body):
        new_body = re.sub(r"padding-bottom\s*:\s*[^;]+;",
                          f"padding-bottom: {pb};", body)
        new_text = text[:m.start()] + head + new_body + tail + text[m.end():]
        return new_text, "replaced-pb"

    # 2) 有 padding 简写的，拆成 padding-top/right/left + padding-bottom
    pm = re.search(r"padding\s*:\s*([^;]+);", body)
    if pm:
        parts = pm.group(1).strip().split()
        if len(parts) == 1:
            top = right = left = parts[0]
        elif len(parts) == 2:
            top, right = parts
            left = right
        elif len(parts) == 3:
            top, right, left = parts
        else:
            top, right, _bottom, left = parts
        repl = (f"padding-top: {top}; padding-right: {right}; "
                f"padding-left: {left}; padding-bottom: {pb};")
        new_body = body.replace(pm.group(0), repl)
        new_text = text[:m.start()] + head + new_body + tail + text[m.end():]
        return new_text, "split-padding"

    # 3) 完全没有 padding，直接追加
    new_body = body.rstrip()
    if not new_body.endswith("\n"):
        new_body += "\n"
    new_body += f"  padding-bottom: {pb};\n"
    new_text = text[:m.start()] + head + new_body + tail + text[m.end():]
    return new_text, "added-pb"


def main():
    dry = False
    ok, skip, bad = [], [], []
    for p in sorted(BASE.rglob("*.uvue")):
        rel = str(p.relative_to(BASE)).replace("\\", "/")
        pb = TAB_PB if rel in TAB_PAGES else NORMAL_PB
        text = p.read_text(encoding="utf-8")
        new_text, how = set_pb(text, pb)
        if new_text is None:
            skip.append(f"{rel}({how})")
            continue
        if new_text == text:
            skip.append(f"{rel}(无变化)")
            continue
        # 校验：改完后 .page 块里必须有 padding-bottom
        m = re.search(r"(?s)\.page\s*\{([^}]*)\}", new_text)
        if not m or "padding-bottom" not in m.group(1):
            bad.append(f"{rel}(校验失败)")
            continue
        p.write_text(new_text, encoding="utf-8", newline="\n")
        ok.append(f"{rel:<30} {pb:<8} ({how})")

    print(f"=== 已修改 {len(ok)} 个页面 ===")
    for x in ok:
        print("  " + x)
    if skip:
        print(f"\n跳过 {len(skip)}:")
        for x in skip:
            print("  " + x)
    if bad:
        print(f"\n失败 {len(bad)}:")
        for x in bad:
            print("  " + x)
    return 0 if not bad else 1


if __name__ == "__main__":
    raise SystemExit(main())
