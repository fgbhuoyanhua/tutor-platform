# -*- coding: utf-8 -*-
"""
管理端全功能 E2E 测试（P1）
覆盖：家教审核通过/驳回、用户禁用/启用、操作日志、数据统计

规范：按 webapp-testing「Reconnaissance-Then-Action」
      + project-rules「做完必须测试」「测完复原」

前置数据由 .tools/seed_admin_data.py 生成（家教信息 id=3 待审核）
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
SHOT.mkdir(parents=True, exist_ok=True)

RESULTS = []
JS_ERRORS = []
HTTP_ERRORS = []
CLEANUP = []          # 记录需要复原的操作
APPROVED_TUTORS = []  # 测试中通过/驳回的家教信息 id，测完删除


def rec(case, ok, detail=""):
    RESULTS.append({"case": case, "ok": ok, "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {case}" + (f" :: {detail}" if detail else ""),
          flush=True)


# ---------------- API ----------------
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


def set_user_status(uid, status, token):
    """PUT /admin/users/{id}/status?status=N —— status 是 query 参数，不是 body"""
    return call("PUT", f"/admin/users/{uid}/status?status={status}", token)


def login(u, p="123456"):
    r = call("POST", "/auth/login", None, {"username": u, "password": p})
    if "data" not in r:
        raise RuntimeError(f"登录失败 {u}: HTTP {r.get('_http')} {r.get('_body','')[:120]}")
    return r["data"]


# ---------------- UI ----------------
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
    t = box.inner_text()
    if expect not in t:
        raise AssertionError(f"确认框文案不符，期望含『{expect}』，实际：{t[:100]}")
    box.get_by_role("button", name=re.compile("确定|确认")).first.click()
    box.wait_for(state="hidden", timeout=8000)


def wait_msg(page, expect, timeout=10000):
    el = page.locator(".el-message").filter(has_text=expect).first
    el.wait_for(state="visible", timeout=timeout)
    return el.inner_text()


def row_of(page, text):
    """按文本定位表格行（管理端列表行数少，不需要翻页逻辑之外的复杂处理）"""
    r = page.locator(".el-table__body tr").filter(has_text=text).first
    r.wait_for(state="visible", timeout=15000)
    return r


def seed_pending_tutor(subject=4, prefix="通过测试"):
    """用 tutor02 发布一条家教信息，使其进入待审核状态；返回 (tutorId, introduce)"""
    t2 = login("tutor02")
    intro = f"{prefix}-{time.strftime('%H%M%S')}"
    r = call("POST", "/tutors", t2["token"], {
        "subjectId": subject, "grade": "初中", "price": 88.00, "introduce": intro})
    if "_http" in r:
        raise RuntimeError(f"发布家教失败: {r['_http']} {r['_body'][:150]}")
    at = login("admin")["token"]
    pend = call("GET", "/admin/tutors/pending", at)["data"]
    t = next((x for x in pend if x["introduce"] == intro), None)
    return (t["id"] if t else None), intro


def main():
    admin = login("admin")
    at = admin["token"]
    today = time.strftime("%Y-%m-%d")

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": 1500, "height": 950})
        page = ctx.new_page()
        page.on("console",
                lambda m: JS_ERRORS.append(m.text) if m.type == "error" else None)
        page.on("response",
                lambda r: HTTP_ERRORS.append(f"{r.status} {r.request.method} {r.url}")
                if "/api/" in r.url and r.status >= 400 else None)

        try:
            # ===== 1. 管理员登录 =====
            login_ui(page, "admin")
            rec("1. 管理员 UI 登录", True, page.url)

            # ===== 2. 数据统计页渲染 =====
            goto(page, "/admin/stats")
            page.wait_for_timeout(800)
            page.screenshot(path=str(SHOT / "admin-01-stats.png"), full_page=True)
            body = page.locator("body").inner_text()
            has_data = any(k in body for k in ["用户", "订单", "家教", "统计"])
            rec("2. 数据统计页渲染", has_data, f"页面文本长度={len(body)}")
            stats = call("GET", "/admin/stats", at)
            print(f"    API stats: {json.dumps(stats.get('data'), ensure_ascii=False)[:220]}",
                  flush=True)

            # ===== 3. 家教审核：通过（测试自己造数据）=====
            try:
                tid, intro = seed_pending_tutor(4, "通过测试")
                if tid:
                    goto(page, "/admin/tutor-audit")
                    row = row_of(page, intro[:10])
                    row.get_by_role("button", name=re.compile("通过")).first.click()
                    confirm(page, "确认通过")
                    wait_msg(page, "操作成功")
                    page.wait_for_timeout(1000)
                    still = call("GET", "/admin/tutors/pending", at)["data"]
                    gone = all(t["id"] != tid for t in still)
                    rec("3. 家教审核-通过", gone,
                        f"家教id={tid} 通过后从待审核列表移除={gone}")
                    page.screenshot(path=str(SHOT / "admin-02-audit-pass.png"), full_page=True)
                    APPROVED_TUTORS.append(tid)
                else:
                    rec("3. 家教审核-通过", False, "发布后未进入待审核列表")
            except Exception as e:
                rec("3. 家教审核-通过", False, str(e)[:150])

            # ===== 4. 家教审核：驳回（自造数据）=====
            try:
                tid2, intro2 = seed_pending_tutor(5, "驳回测试")
                if tid2:
                    goto(page, "/admin/tutor-audit")
                    row = row_of(page, intro2[:8])
                    row.get_by_role("button", name=re.compile("驳回")).first.click()
                    confirm(page, "确认驳回")
                    wait_msg(page, "操作成功")
                    page.wait_for_timeout(1000)
                    still = call("GET", "/admin/tutors/pending", at)["data"]
                    gone = all(t["id"] != tid2 for t in still)
                    rec("4. 家教审核-驳回", gone,
                        f"家教id={tid2} 驳回后从待审核列表移除={gone}")
                    APPROVED_TUTORS.append(tid2)
                else:
                    rec("4. 家教审核-驳回", False, "发布后未进入待审核列表")
            except Exception as e:
                rec("4. 家教审核-驳回", False, str(e)[:150])

            # ===== 5. 空态验证：待审核列表为空时显示 el-empty =====
            goto(page, "/admin/tutor-audit")
            page.wait_for_timeout(700)
            empty_n = page.locator(".el-empty").count()
            rec("5. 待审核为空时显示空态", empty_n > 0, f"el-empty 数量={empty_n}")

            # ===== 6. 用户禁用/启用（用 tutor02，测完复原）=====
            users = call("GET", "/admin/users?page=1&size=50", at)["data"]["records"]
            victim = next((u for u in users if u["username"] == "tutor02"), None)
            if victim:
                uid, uname, orig = victim["id"], victim["username"], victim["status"]
                goto(page, "/admin/users")
                row = row_of(page, uname)
                row.get_by_role("button", name=re.compile("禁用")).first.click()
                confirm(page, "确认禁用")
                wait_msg(page, "操作成功")
                page.wait_for_timeout(1000)
                u1 = next(u for u in call("GET", "/admin/users?page=1&size=50", at)
                          ["data"]["records"] if u["id"] == uid)
                rec("6. 用户禁用", u1["status"] == 0, f"{uname} status={u1['status']}")
                page.screenshot(path=str(SHOT / "admin-03-user-disabled.png"), full_page=True)

                # ===== 7. 用户启用（复原）=====
                goto(page, "/admin/users")
                row = row_of(page, uname)
                row.get_by_role("button", name=re.compile("启用")).first.click()
                confirm(page, "确认启用")
                wait_msg(page, "操作成功")
                page.wait_for_timeout(1000)
                u2 = next(u for u in call("GET", "/admin/users?page=1&size=50", at)
                          ["data"]["records"] if u["id"] == uid)
                rec("7. 用户启用（已复原）", u2["status"] == orig,
                    f"{uname} status={u2['status']}（原值 {orig}）")

                # ===== 8. 禁用后能否登录（安全验证）=====
                set_user_status(uid, 0, at)
                try:
                    login(uname)
                    blocked = False
                    detail = "禁用后仍可登录（安全问题！）"
                except Exception as e:
                    blocked = True
                    detail = f"禁用后登录被拦截：{str(e)[:100]}"
                # 复原
                set_user_status(uid, orig, at)
                rec("8. 禁用账号无法登录", blocked, detail)
            else:
                rec("6. 用户禁用", False, "找不到 tutor02")

            # ===== 9. 管理员自己不可禁用 =====
            goto(page, "/admin/users")
            admin_row = row_of(page, "admin")
            noop = admin_row.locator(".noop").count()
            rec("9. 管理员自身显示—不可禁用", noop > 0, f"noop 标记数={noop}")

            # ===== 10. 操作日志有记录 =====
            goto(page, "/admin/logs")
            page.wait_for_timeout(1000)
            logs = call("GET", "/admin/logs?page=1&size=20", at)["data"]
            n = len(logs.get("records") or [])
            # 刚才做了审核/禁用/启用，应该产生日志
            rec("10. 操作日志有记录", n > 0, f"日志条数={n}")
            page.screenshot(path=str(SHOT / "admin-04-logs.png"), full_page=True)
            if n > 0:
                sample = logs["records"][0]
                print(f"    最新日志: {json.dumps(sample, ensure_ascii=False)[:200]}",
                      flush=True)

            # ===== 11. 非管理员访问管理端被拦截 =====
            page.evaluate("localStorage.clear()")
            login_ui(page, "student01")
            goto(page, "/admin/users")
            page.wait_for_timeout(800)
            blocked = "/admin" not in page.url or page.locator(".el-table").count() == 0
            rec("11. 学生访问管理端被拦截", blocked, f"跳转到 {page.url}")
        finally:
            browser.close()

    restore_guard()
    return summary()


def cleanup_tutors():
    """删除测试产生的家教信息（保留初始的 tutor01 id=1）"""
    try:
        at = login("admin")["token"]
        # 用 tutor02 身份下架/删除比较麻烦，直接看待审核与已审核状态
        users = call("GET", "/admin/users?page=1&size=50", at)["data"]["records"]
        return users
    except Exception:
        return None


def restore_guard():
    """测试无论成功失败，都把所有可能被改动的状态复原：
       1) 所有非 admin 用户 status 恢复为 1
       2) 删除测试产生的家教信息
    """
    print("\n[复原] 开始恢复测试前状态...", flush=True)
    try:
        at = login("admin")["token"]
    except Exception as e:
        print(f"[复原] 无法登录 admin，跳过：{e}", flush=True)
        return
    # 1) 用户状态
    try:
        users = call("GET", "/admin/users?page=1&size=100", at)["data"]["records"]
        for u in users:
            if u["role"] != 3 and u["status"] != 1:
                set_user_status(u["id"], 1, at)
                print(f"[复原] 用户 {u['username']} status 0→1", flush=True)
    except Exception as e:
        print(f"[复原] 用户状态恢复失败：{e}", flush=True)
    # 2) 测试家教信息（用 SQL，通过 tutor02 视角删不干净已驳回的）
    try:
        import subprocess
        mysql = r"D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe"
        sql = ("DELETE FROM tutor WHERE id >= 3 AND "
               "(introduce LIKE '%测试%' OR introduce LIKE '%管理端审核%');")
        subprocess.run([mysql, "-h", "127.0.0.1", "-P", "3306", "-u", "root",
                        "-proot", "--ssl-mode=DISABLED", "-D", "tutor_platform",
                        "-e", sql], capture_output=True, text=True, timeout=30)
        print("[复原] 已清理测试家教信息", flush=True)
    except Exception as e:
        print(f"[复原] 清理家教信息失败：{e}", flush=True)


def cleanup_test_tutors():
    """测试结束后清理测试产生的家教信息（只删 introduce 含测试标记的）"""
    try:
        at = login("admin")["token"]
        # 通过 tutor02 视角看不到已驳回的，直接查数据库由外部脚本处理
        # 这里只负责把状态打回待审核以便识别，实际删除走 SQL
        print(f"\n[清理] 测试产生的家教信息 id: {APPROVED_TUTORS}", flush=True)
    except Exception as e:
        print(f"[清理] 失败: {e}", flush=True)


def summary():
    print("\n" + "=" * 72, flush=True)
    passed = sum(1 for r in RESULTS if r["ok"])
    print(f"结果: {passed}/{len(RESULTS)} 通过", flush=True)
    print("=" * 72, flush=True)
    js = [e for e in JS_ERRORS if "favicon" not in e.lower()]
    print(f"\n页面 JS 错误: {len(js)}", flush=True)
    for e in js[:8]:
        print(f"  ! {e[:180]}", flush=True)
    print(f"\n4xx/5xx API: {len(HTTP_ERRORS)}", flush=True)
    for e in list(dict.fromkeys(HTTP_ERRORS))[:8]:
        print(f"  ! {e}", flush=True)

    out = Path(r"E:\毕业设计\project\docs\admin-e2e-result.json")
    out.write_text(json.dumps({"summary": {"passed": passed, "total": len(RESULTS)},
                               "cases": RESULTS, "js_errors": js,
                               "http_errors": list(dict.fromkeys(HTTP_ERRORS))},
                              ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n结果: {out}", flush=True)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
