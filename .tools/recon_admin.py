# -*- coding: utf-8 -*-
"""
管理端 DOM 侦察（按 webapp-testing 规范：先侦察再写选择器）
覆盖：/admin/stats、/admin/tutor-audit、/admin/users、/admin/logs
"""
import json
import re
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
OUT = Path(r"E:\毕业设计\project\docs\web-recon")
OUT.mkdir(parents=True, exist_ok=True)

ROUTES = ["/admin/stats", "/admin/tutor-audit", "/admin/users", "/admin/logs"]


def recon(page, route):
    page.goto(f"{BASE}{route}", wait_until="networkidle")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(400)

    slug = route.strip("/").replace("/", "_")
    page.screenshot(path=str(OUT / f"{slug}.png"), full_page=True)

    d = {"route": route, "url": page.url, "title": page.title(),
         "tables": page.locator(".el-table").count(),
         "rows": page.locator(".el-table__body tr").count(),
         "empty": page.locator(".el-empty").count(),
         "pager": page.locator(".el-pagination").count(),
         "buttons": [], "tags": [], "row_sample": []}

    for i in range(page.get_by_role("button").count()):
        b = page.get_by_role("button").nth(i)
        try:
            if b.is_visible():
                t = b.inner_text().strip()
                if t:
                    d["buttons"].append(t)
        except Exception:
            pass
    d["buttons"] = list(dict.fromkeys(d["buttons"]))

    for i in range(min(page.locator(".el-tag").count(), 15)):
        try:
            d["tags"].append(page.locator(".el-tag").nth(i).inner_text().strip())
        except Exception:
            pass

    # 每行前 120 字，用来看清列结构
    for i in range(min(d["rows"], 5)):
        try:
            d["row_sample"].append(
                page.locator(".el-table__body tr").nth(i).inner_text().replace("\n", " | ")[:120])
        except Exception:
            pass

    (OUT / f"{slug}.json").write_text(
        json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n=== {route}  title={d['title']}")
    print(f"    table={d['tables']} rows={d['rows']} empty={d['empty']} pager={d['pager']}")
    print(f"    buttons={d['buttons'][:10]}")
    print(f"    tags={d['tags'][:10]}")
    for r in d["row_sample"]:
        print(f"    ROW: {r}")
    return d


def main():
    with sync_playwright() as p:
        b = p.chromium.launch(headless=True)
        pg = b.new_context(viewport={"width": 1500, "height": 950}).new_page()

        # 用 admin 登录
        pg.goto(f"{BASE}/login", wait_until="networkidle")
        pg.get_by_placeholder(re.compile("用户名|账号|手机")).first.fill("admin")
        pg.get_by_placeholder(re.compile("密码")).first.fill("123456")
        pg.get_by_role("button", name=re.compile(r"登\s*录")).first.click()
        pg.wait_for_url(re.compile(r"/(home|dashboard)"), timeout=15000)
        pg.wait_for_load_state("networkidle")

        for r in ROUTES:
            recon(pg, r)

        b.close()
    print(f"\n侦察结果: {OUT}")


if __name__ == "__main__":
    main()
