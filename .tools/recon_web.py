# -*- coding: utf-8 -*-
"""
按 webapp-testing 技能的『Reconnaissance-Then-Action』做正式侦察：
1. 导航并等 networkidle
2. 截图 + 导出 DOM 结构
3. 从渲染结果里提取稳定的可选元素（role / text / 组件类）
4. 为每个页面输出建议选择器

用法: python recon_web.py [路由...]
"""
import json
import re
import sys
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
OUT = Path(r"E:\毕业设计\project\docs\web-recon")
OUT.mkdir(parents=True, exist_ok=True)

ROUTES = sys.argv[1:] or ["/login", "/home", "/tutors", "/orders"]


def collect(page, route, tag):
    """侦察一个页面：等 networkidle → 截图 → 抽取可交互元素"""
    page.goto(f"{BASE}{route}", wait_until="networkidle")   # CRITICAL: 等 JS 执行完
    page.wait_for_load_state("networkidle")

    slug = route.strip("/").replace("/", "_") or "root"
    shot = OUT / f"{tag}-{slug}.png"
    page.screenshot(path=str(shot), full_page=True)

    data = {
        "route": route,
        "url": page.url,
        "title": page.title(),
        "screenshot": str(shot),
        "buttons": [],
        "inputs": [],
        "tables": 0,
        "cards": [],
        "selects": 0,
        "empty": page.locator(".el-empty").count(),
    }

    # 按钮：优先取 role=button 的可见文本（最稳的定位方式）
    for i in range(page.get_by_role("button").count()):
        b = page.get_by_role("button").nth(i)
        try:
            if b.is_visible():
                data["buttons"].append(b.inner_text().strip())
        except Exception:
            pass

    # 输入框：记录 placeholder（并标注空 placeholder 的坑）
    for i in range(page.locator("input, textarea").count()):
        el = page.locator("input, textarea").nth(i)
        try:
            if el.is_visible():
                data["inputs"].append({
                    "tag": el.evaluate("e => e.tagName.toLowerCase()"),
                    "placeholder": el.get_attribute("placeholder"),
                    "class": el.get_attribute("class"),
                })
        except Exception:
            pass

    data["tables"] = page.locator(".el-table").count()
    data["selects"] = page.locator(".el-select").count()
    # 业务卡片：家教列表用 .tutor-card
    for cls in [".tutor-card", ".el-card"]:
        n = page.locator(cls).count()
        if n:
            data["cards"].append({"selector": cls, "count": n})

    (OUT / f"{tag}-{slug}.json").write_text(
        json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return data


def summarize(d):
    print(f"\n=== {d['route']} -> {d['url']}")
    print(f"    title={d['title']!r}  table={d['tables']} select={d['selects']} empty={d['empty']}")
    if d["cards"]:
        print(f"    cards: {d['cards']}")
    if d["buttons"]:
        uniq = list(dict.fromkeys(d["buttons"]))
        print(f"    buttons({len(uniq)}): {uniq[:14]}")
    for inp in d["inputs"][:6]:
        ph = inp["placeholder"]
        flag = "  <-- 空 placeholder，不能按 placeholder 定位" if not ph else ""
        print(f"    input[{inp['tag']}] ph={ph!r}{flag}")


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": 1440, "height": 900})
        page = ctx.new_page()

        # 登录页单独侦察（无需登录态）
        summarize(collect(page, "/login", "anon"))

        # 登录后侦察
        page.goto(f"{BASE}/login", wait_until="networkidle")
        page.get_by_placeholder(re.compile("用户名|账号|手机")).first.fill("student01")
        page.get_by_placeholder(re.compile("密码")).first.fill("123456")
        page.get_by_role("button", name=re.compile(r"登\s*录")).first.click()
        page.wait_for_url(re.compile(r"/(home|dashboard)"), timeout=15000)

        for r in ROUTES:
            if r == "/login":
                continue
            summarize(collect(page, r, "student"))

        browser.close()
    print(f"\n侦察结果已保存到: {OUT}")


if __name__ == "__main__":
    main()
