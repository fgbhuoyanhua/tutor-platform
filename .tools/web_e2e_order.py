# -*- coding: utf-8 -*-
"""
Web 端订单全流程回归测试（P0）—— 按 webapp-testing 技能规范重写

规范遵循点：
  1. 服务已在运行 → 直接写 Playwright 脚本（Decision Tree 分支）
  2. sync_playwright() 同步 API
  3. 一律 headless 启动 chromium
  4. 每个页面先 wait_until='networkidle' 再检视/操作（Common Pitfall）
  5. 选择器优先 role / text，避免脆弱 CSS 链
  6. 结束时必定 close browser
  7. 侦察结果来自 recon_web.py 的真实渲染 DOM（Reconnaissance-Then-Action）

覆盖：docs/交接单.md 第五节 P0「Web 端订单操作」
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
SHOT_DIR = Path(r"E:\毕业设计\project\docs\web-e2e-shots")
SHOT_DIR.mkdir(parents=True, exist_ok=True)

RESULTS = []
CONSOLE_ERRORS = []
NET_ERRORS = []
HTTP_4XX_5XX = []   # 记录所有非 2xx 的 API 响应，便于定位真实缺陷


def rec(case, ok, detail=""):
    RESULTS.append({"case": case, "ok": ok, "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {case}" + (f" :: {detail}" if detail else ""),
          flush=True)


# ---------------- API helpers ----------------
def api_login(username, password="123456"):
    body = json.dumps({"username": username, "password": password}).encode()
    req = urllib.request.Request(f"{API}/auth/login", data=body, method="POST",
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read().decode())["data"]


def api_get(path, token):
    req = urllib.request.Request(f"{API}{path}",
                                 headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read().decode())


def api_call(method, path, token, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        f"{API}{path}", data=data, method=method,
        headers={"Authorization": f"Bearer {token}",
                 "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode())


# ---------------- UI helpers（role/text 优先）----------------
def goto(page, route):
    """技能规范：动态应用必须先等 networkidle"""
    page.goto(f"{BASE}{route}", wait_until="networkidle")


def login_ui(page, username, password="123456"):
    goto(page, "/login")
    page.get_by_placeholder(re.compile("用户名|账号|手机")).first.fill(username)
    page.get_by_placeholder(re.compile("密码")).first.fill(password)
    page.get_by_role("button", name=re.compile(r"登\s*录")).first.click()
    page.wait_for_url(re.compile(r"/(home|dashboard)"), timeout=15000)
    page.wait_for_load_state("networkidle")


def switch_role(page, username):
    page.evaluate("localStorage.clear()")
    login_ui(page, username)


def confirm_box(page, expect_text):
    """ElMessageBox 二次确认 —— 用 role 定位按钮"""
    box = page.locator(".el-message-box")
    box.wait_for(state="visible", timeout=8000)
    txt = box.inner_text()
    if expect_text not in txt:
        raise AssertionError(f"确认框文案不符，期望含『{expect_text}』，实际：{txt[:120]}")
    box.get_by_role("button", name=re.compile("确定|确认")).first.click()
    box.wait_for(state="hidden", timeout=8000)


def wait_msg(page, expect_text, timeout=10000):
    el = page.locator(".el-message").filter(has_text=expect_text).first
    el.wait_for(state="visible", timeout=timeout)
    return el.inner_text()


def find_row(page, order_no):
    row = page.locator(".el-table__body tr").filter(has_text=order_no)
    row.first.wait_for(state="visible", timeout=15000)
    return row.first


def find_row_any_page(page, order_no, max_pages=8):
    """订单列表分页浏览：用 role 定位分页『下一页』"""
    for _ in range(max_pages):
        rows = page.locator(".el-table__body tr").filter(has_text=order_no)
        if rows.count() > 0:
            rows.first.wait_for(state="visible", timeout=5000)
            return rows.first
        nxt = page.get_by_role("button", name=re.compile("下一页|next")).first
        if nxt.count() == 0 or not nxt.is_enabled():
            break
        nxt.click()
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(400)
    raise AssertionError(f"所有分页都没找到订单 {order_no}")


def row_status(page, order_no):
    return find_row(page, order_no).locator(".el-tag").first.inner_text().strip()


def wait_status(page, order_no, expect_substr, timeout=15):
    deadline = time.time() + timeout
    last = ""
    while time.time() < deadline:
        try:
            last = row_status(page, order_no)
            if expect_substr in last:
                return last
        except Exception:
            pass
        page.wait_for_timeout(500)
    raise AssertionError(f"状态未变为含『{expect_substr}』，最后读到『{last}』")


def row_action(page, order_no, btn_name):
    find_row(page, order_no).get_by_role(
        "button", name=re.compile(btn_name)).first.click()


def pick_free_date(page, occupied_iso):
    """在家教预约弹窗里选一个 tutor01 未占用的日期（规避 DEFECT-2 唯一键冲突）"""
    page.locator("input[placeholder='选择日期']").click()
    page.wait_for_timeout(700)
    header = page.locator(".el-date-picker__header-label:visible")
    if header.count() < 2:
        raise AssertionError("读不到日期面板年月")
    y = int(re.sub(r"\D", "", header.nth(0).inner_text()))
    mo = int(re.sub(r"\D", "", header.nth(1).inner_text()))
    cells = page.locator(".el-date-table td.available:not(.prev-month):not(.next-month)")
    for i in range(cells.count()):
        d = int(re.sub(r"\D", "", cells.nth(i).inner_text()) or 0)
        if d <= 0:
            continue
        iso = f"{y:04d}-{mo:02d}-{d:02d}"
        if iso not in occupied_iso:
            cells.nth(i).click()
            page.wait_for_timeout(500)
            return iso
    # 本月占满则翻月
    page.get_by_role("button", name=re.compile("下个月|next month")).first.click()
    page.wait_for_timeout(700)
    cells = page.locator(".el-date-table td.available:not(.prev-month):not(.next-month)")
    cells.first.click()
    page.wait_for_timeout(500)
    return "<翻月>"


def main():
    student = api_login("student01")
    tutor = api_login("tutor01")
    print(f"student01 uid={student['userId']}  tutor01 uid={tutor['userId']}", flush=True)

    with sync_playwright() as p:
        # 规范：一律 headless
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": 1440, "height": 900})
        page = ctx.new_page()

        page.on("console",
                lambda m: CONSOLE_ERRORS.append(m.text) if m.type == "error" else None)
        page.on("requestfailed",
                lambda r: NET_ERRORS.append(f"{r.method} {r.url} :: {r.failure}")
                if "/api/" in r.url else None)
        page.on("response",
                lambda r: HTTP_4XX_5XX.append(
                    f"{r.status} {r.request.method} {r.url}")
                if "/api/" in r.url and r.status >= 400 else None)

        order_no = None
        try:
            # ===== 1. 学生登录 =====
            login_ui(page, "student01")
            rec("1. 学生 UI 登录", True, page.url)

            # ===== 2. 学生下单（家教列表是 .tutor-card 卡片布局，非表格）=====
            goto(page, "/tutors")
            so = api_get("/appointments?page=1&size=200", student["token"])["data"]["records"]
            occupied = {o["appointDate"] for o in so
                        if o["tutorId"] == tutor["userId"] and o["status"] in (0, 1, 2, 3)}

            card = page.locator(".tutor-card").filter(has_text="王老师").first
            if card.count() == 0:
                # 翻页找（每页 8 条）
                for _ in range(5):
                    nxt = page.get_by_role("button", name=re.compile("下一页|next")).first
                    if nxt.count() == 0 or not nxt.is_enabled():
                        break
                    nxt.click()
                    page.wait_for_load_state("networkidle")
                    card = page.locator(".tutor-card").filter(has_text="王老师").first
                    if card.count() > 0:
                        break
            card.get_by_role("button", name=re.compile("预约\\s*TA")).first.click()

            dlg = page.locator(".el-dialog:visible").first
            dlg.wait_for(state="visible", timeout=10000)
            picked = pick_free_date(page, occupied)
            print(f"    下单日期: {picked}", flush=True)
            page.locator(".el-dialog__title").first.click()   # 收起日期面板
            page.wait_for_timeout(400)
            dlg.locator(".el-select").first.click()           # 时段
            page.wait_for_timeout(600)
            page.locator(".el-select-dropdown__item:visible").first.click()
            page.wait_for_timeout(400)
            dlg.get_by_role("button", name=re.compile("提交预约")).click()
            rec("2. 学生 Web 下单", True, wait_msg(page, "预约提交成功"))

            time.sleep(1)
            orders = api_get("/appointments?page=1&size=20", student["token"])["data"]["records"]
            newest = max(orders, key=lambda o: o["id"])
            order_no = newest["orderNo"]
            assert newest["tutorId"] == tutor["userId"], "订单未下给 tutor01"
            print(f"    新订单 {order_no} status={newest['status']}", flush=True)

            # ===== 3. 老师登录 =====
            switch_role(page, "tutor01")
            rec("3. 老师 UI 登录（切身份）", True, page.url)

            # ===== 4. 老师接单 =====
            goto(page, "/orders")
            before = row_status(page, order_no)
            row_action(page, order_no, "接单")
            confirm_box(page, "确认接单")
            wait_msg(page, "操作成功")
            after = wait_status(page, order_no, "已预约")
            rec("4. 老师接单", True, f"{before} → {after}")

            # ===== 5. 老师开始授课（DEFECT-1 已修复，应为 UI 可操作）=====
            goto(page, "/orders")
            start_btns = find_row(page, order_no).get_by_role(
                "button", name=re.compile("开始授课"))
            has_start = start_btns.count()
            rec("5a. Web端『开始授课』按钮存在性", has_start > 0,
                f"按钮数={has_start}（DEFECT-1 修复后应 ≥1）")
            if has_start > 0:
                # 走真实 UI 点击，验证完整闭环
                start_btns.first.click()
                confirm_box(page, "确认开始授课")
                wait_msg(page, "操作成功")
                after = wait_status(page, order_no, "授课中")
                rec("5b. 老师开始授课 UI 点击(1→2)", True, f"→ {after}")
            else:
                # 兜底：退回 API 验证，保证后续链路可继续
                api_call("PUT", f"/appointments/{newest['id']}/start", tutor["token"])
                time.sleep(1)
                d = api_get("/appointments?page=1&size=200", tutor["token"])["data"]["records"]
                st = next(o for o in d if o["orderNo"] == order_no)["status"]
                rec("5b. 老师开始授课 API(1→2)", st == 2, f"status={st}（UI 按钮缺失，走 API）")

            # ===== 6. 学生确认完成 =====
            switch_role(page, "student01")
            goto(page, "/orders")
            before = row_status(page, order_no)
            row_action(page, order_no, "确认完成")
            confirm_box(page, "确认已完成")
            wait_msg(page, "操作成功")
            after = wait_status(page, order_no, "已完成")
            rec("6. 学生确认完成(2→3)", True, f"{before} → {after}")

            # ===== 7. 学生评价 =====
            goto(page, "/orders")
            row_action(page, order_no, "评价老师")
            dlg = page.locator(".el-dialog:visible").first
            dlg.wait_for(state="visible", timeout=10000)
            dlg.locator(".el-rate__item").nth(3).click(position={"x": 18, "y": 8})
            page.wait_for_timeout(300)
            dlg.locator("textarea").fill("Web端回归测试：老师讲解细致，反馈及时。")
            dlg.get_by_role("button", name=re.compile("提交评价")).click()
            rec("7. 学生评价老师", True, wait_msg(page, "评价提交成功"))

            # ===== 8. 老师写学习报告 =====
            switch_role(page, "tutor01")
            goto(page, "/orders")
            row_action(page, order_no, "写报告")
            dlg = page.locator(".el-dialog:visible").first
            dlg.wait_for(state="visible", timeout=10000)
            dlg.locator("textarea").fill("Web端回归测试：本节讲解函数与导数，掌握良好。")
            dlg.get_by_role("button", name=re.compile("提交")).click()
            rec("8. 老师写学习报告", True, wait_msg(page, "报告已提交"))

            # ===== 9. 学生取消预约 =====
            switch_role(page, "student01")
            orders = api_get("/appointments?page=1&size=200", student["token"])["data"]["records"]
            pending = [o for o in orders if o["status"] == 0]
            if pending:
                t = pending[0]
                goto(page, "/orders")
                find_row_any_page(page, t["orderNo"])
                before = row_status(page, t["orderNo"])
                row_action(page, t["orderNo"], "取消")
                confirm_box(page, "确定取消")
                wait_msg(page, "操作成功")
                after = wait_status(page, t["orderNo"], "已取消")
                rec("9. 学生取消预约(0→4)", True, f"{before} → {after}")
            else:
                rec("9. 学生取消预约(0→4)", False, "无 status=0 订单")

            # ===== 10. 学生申请调课 =====
            orders = api_get("/appointments?page=1&size=200", student["token"])["data"]["records"]
            cand = [o for o in orders
                    if o["status"] == 1 and o["tutorId"] == tutor["userId"]]
            ok = False
            for t in cand:
                try:
                    goto(page, "/orders")
                    find_row_any_page(page, t["orderNo"])
                    row_action(page, t["orderNo"], "申请调课")
                    dlg = page.locator(".el-dialog:visible").first
                    dlg.wait_for(state="visible", timeout=10000)
                    dlg.locator("input").first.click()        # 该弹窗日期框 placeholder 为空
                    page.wait_for_timeout(700)
                    page.locator(".el-date-table td.available:not(.prev-month):not(.next-month)").first.click()
                    page.wait_for_timeout(500)
                    dlg.locator(".el-dialog__header").first.click()
                    page.wait_for_timeout(400)
                    dlg.locator(".el-select").first.click()
                    page.wait_for_timeout(600)
                    page.locator(".el-select-dropdown__item:visible").first.click()
                    page.wait_for_timeout(300)
                    dlg.get_by_role("button", name=re.compile("提交申请")).click()
                    hang = page.locator(".el-message:visible")
                    hang.first.wait_for(state="visible", timeout=8000)
                    txt = hang.first.inner_text()
                    if "调课申请已提交" in txt:
                        rec("10. 学生申请调课", True, txt)
                        ok = True
                        break
                    print(f"    订单 {t['orderNo']} 调课被拒：{txt}", flush=True)
                except Exception as e:
                    print(f"    订单 {t.get('orderNo')} 调课异常：{str(e)[:120]}", flush=True)
            if not ok:
                rec("10. 学生申请调课", False, "所有候选订单均未能提交（见上方明细）")

            # ===== 11. 老师拒绝预约 =====
            switch_role(page, "tutor01")
            tos = api_get("/appointments?page=1&size=200", tutor["token"])["data"]["records"]
            pend = [o for o in tos if o["status"] == 0]
            if pend:
                t = pend[0]
                goto(page, "/orders")
                find_row_any_page(page, t["orderNo"])
                before = row_status(page, t["orderNo"])
                row_action(page, t["orderNo"], "拒绝")
                confirm_box(page, "确定拒绝")
                wait_msg(page, "操作成功")
                after = wait_status(page, t["orderNo"], "已拒绝")
                rec("11. 老师拒绝预约(0→5)", True, f"{before} → {after}")
            else:
                rec("11. 老师拒绝预约(0→5)", False, "无 status=0 订单")

            page.screenshot(path=str(SHOT_DIR / "final-tutor-orders.png"), full_page=True)
        finally:
            browser.close()   # 规范：始终关闭浏览器

    return summary()


def summary():
    print("\n" + "=" * 72, flush=True)
    passed = sum(1 for r in RESULTS if r["ok"])
    print(f"结果: {passed}/{len(RESULTS)} 通过", flush=True)
    print("=" * 72, flush=True)

    real_js = [e for e in CONSOLE_ERRORS if "favicon" not in e.lower()]
    print(f"\n页面 JS 错误: {len(real_js)}", flush=True)
    for e in real_js[:8]:
        print(f"  ! {e[:180]}", flush=True)
    print(f"\n失败的请求: {len(NET_ERRORS)}", flush=True)
    for e in NET_ERRORS[:8]:
        print(f"  ! {e[:180]}", flush=True)
    print(f"\n4xx/5xx API 响应: {len(HTTP_4XX_5XX)}", flush=True)
    for e in dict.fromkeys(HTTP_4XX_5XX):
        print(f"  ! {e}", flush=True)

    out = Path(r"E:\毕业设计\project\docs\web-e2e-result.json")
    out.write_text(json.dumps({
        "summary": {"passed": passed, "total": len(RESULTS)},
        "cases": RESULTS,
        "console_errors": real_js,
        "net_errors": NET_ERRORS,
        "http_errors": list(dict.fromkeys(HTTP_4XX_5XX)),
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n结果: {out}", flush=True)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
