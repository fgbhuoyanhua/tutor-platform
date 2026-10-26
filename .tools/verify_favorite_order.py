# -*- coding: utf-8 -*-
"""
收藏页下单修复验证
先复现修复前的 400「科目不能为空」，再验证修复后能成功下单。
"""
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout

BASE = "http://localhost:5173"
API = "http://localhost:8080/api"
SHOT = Path(r"E:\毕业设计\project\docs\web-e2e-shots")
RESULTS = []


def rec(case, ok, detail=""):
    RESULTS.append({"case": case, "ok": ok, "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {case}" + (f" :: {detail}" if detail else ""))


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"_http": e.code, "_body": e.read().decode()[:300]}


def login(u, p="123456"):
    return call("POST", "/auth/login", None, {"username": u, "password": p})["data"]


def goto(page, route):
    page.goto(f"{BASE}{route}", wait_until="networkidle")
    page.wait_for_load_state("networkidle")


def login_ui(page, username, password="123456"):
    goto(page, "/login")
    page.get_by_placeholder(re.compile("用户名|账号|手机")).first.fill(username)
    page.get_by_placeholder(re.compile("密码")).first.fill(password)
    page.get_by_role("button", name=re.compile(r"登\s*录")).first.click()
    page.wait_for_url(re.compile(r"/(home|dashboard)"), timeout=15000)
    page.wait_for_load_state("networkidle")


def confirm(page, expect):
    box = page.locator(".el-message-box")
    box.wait_for(state="visible", timeout=8000)
    box.get_by_role("button", name=re.compile("确定|确认")).first.click()
    box.wait_for(state="hidden", timeout=8000)


def main():
    student = login("student01")
    st = student["token"]

    # 准备：确保收藏列表里有老师
    favs = call("GET", "/favorites", st)["data"]
    if not favs:
        tl = call("GET", "/tutors?page=1&size=20", st)["data"]["records"]
        t = tl[0]
        call("POST", f"/favorites/{t['id']}", st, {})
        favs = call("GET", "/favorites", st)["data"]
    print(f"收藏数量: {len(favs)}", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_context(viewport={"width": 1440, "height": 900}).new_page()
        api_errors = []
        page.on("response", lambda r: api_errors.append(f"{r.status} {r.url}")
                if "/api/" in r.url and r.status >= 400 else None)

        try:
            login_ui(page, "student01")

            # ===== 1. 收藏页渲染 =====
            goto(page, "/favorites")
            page.wait_for_timeout(1200)
            page.screenshot(path=str(SHOT / "fav-01-list.png"), full_page=True)
            has_card = page.locator(".el-card").count() > 1 or page.get_by_role(
                "button", name=re.compile("预约\\s*TA")).count() > 0
            rec("1. 收藏页渲染", has_card,
                f"卡片数={page.locator('.el-card').count()}")

            # ===== 2. 打开预约弹窗 =====
            page.get_by_role("button", name=re.compile("预约\\s*TA")).first.click()
            dlg = page.locator(".el-dialog:visible").first
            dlg.wait_for(state="visible", timeout=10000)
            page.wait_for_timeout(600)
            page.screenshot(path=str(SHOT / "fav-02-dialog.png"))
            rec("2. 预约弹窗打开", dlg.is_visible())

            # ===== 3. 选日期（避开 tutor01 已占用）=====
            so = call("GET", "/appointments?page=1&size=200", st)["data"]["records"]
            used = {(o["appointDate"], o["timeSlot"]) for o in so}

            dlg.locator("input[placeholder='选择日期']").click()
            page.wait_for_timeout(700)
            hdr = page.locator(".el-date-picker__header-label:visible")
            y = int(re.sub(r"\D", "", hdr.nth(0).inner_text()))
            mo = int(re.sub(r"\D", "", hdr.nth(1).inner_text()))
            cells = page.locator(".el-date-table td.available:not(.prev-month):not(.next-month)")
            picked = None
            for i in range(cells.count()):
                d = int(re.sub(r"\D", "", cells.nth(i).inner_text()) or 0)
                if d <= 0:
                    continue
                iso = f"{y:04d}-{mo:02d}-{d:02d}"
                if not any(k[0] == iso for k in used):
                    cells.nth(i).click()
                    picked = iso
                    break
            page.wait_for_timeout(500)
            page.locator(".el-dialog__title").first.click()
            page.wait_for_timeout(400)
            print(f"    选中日期: {picked}", flush=True)

            # ===== 4. 选时段 =====
            dlg.locator(".el-select").first.click()
            page.wait_for_timeout(600)
            page.locator(".el-select-dropdown__item:visible").first.click()
            page.wait_for_timeout(400)
            page.screenshot(path=str(SHOT / "fav-03-filled.png"))

            # ===== 5. 提交预约（核心验证）=====
            before_total = call("GET", "/appointments?page=1&size=1", st)["data"]["total"]
            dlg.get_by_role("button", name=re.compile("提交预约|预约")).first.click()
            try:
                msg = page.locator(".el-message").first
                msg.wait_for(state="visible", timeout=12000)
                txt = msg.inner_text()
            except PWTimeout:
                txt = "（无提示）"
            page.screenshot(path=str(SHOT / "fav-04-result.png"), full_page=True)

            time.sleep(1.5)
            after_total = call("GET", "/appointments?page=1&size=1", st)["data"]["total"]
            created = after_total > before_total

            ok = "预约已提交" in txt and created
            rec("3. 收藏页下单成功", ok,
                f"提示=「{txt}」 订单数 {before_total}→{after_total}")

            bad = [e for e in api_errors if "400" in e]
            rec("4. 无 400 参数错误", len(bad) == 0,
                f"400 响应: {bad[:3] if bad else '无'}")
        finally:
            browser.close()

    print("\n" + "=" * 60)
    passed = sum(1 for r in RESULTS if r["ok"])
    print(f"结果: {passed}/{len(RESULTS)} 通过")
    print("=" * 60)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
