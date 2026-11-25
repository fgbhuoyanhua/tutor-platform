# -*- coding: utf-8 -*-
"""
第三轮 E2E（Web 真实点击）：P1 接线 + P2 新页面
  1. P1-1 订单完成 → 老师信用分 +1（学生端订单页点「确认完成」触发，重复完成不再加分）
  2. P1-2 信用分 < 60 → 老师卡片按钮禁用「暂不可预约」、详情按钮禁用、下单接口 403
  3. P2-3 管理端「违规记录」页：列表渲染、按老师筛选、汇总统计
  4. P2-4 投诉证据上传：学生上传 PNG → 我的投诉「查看」→ 管理端「查看证据」（blob 预览）

测试数据自己造，结束按 SQL 复原（删预约/投诉/违规/消息、信用分还原）。
"""
import base64
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
API = "http://localhost:8080/api"
SHOT = Path(r"E:\毕业设计\project\docs\web-e2e-shots")
SHOT.mkdir(parents=True, exist_ok=True)
TMP = Path(r"E:\毕业设计\project\.tmp_upload")
TMP.mkdir(parents=True, exist_ok=True)
MYSQL = r"D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe"

RESULTS, JS_ERRORS, HTTP_ERRORS = [], [], []
MARK = f"E2E{int(time.time())}"
TUTOR_UID = 2  # 演示老师 tutor01（王老师）的 user.id；tutor 表主键是 1，两者别混

# 1x1 红点 PNG，够小且合法，Pillow / 浏览器都能识别
PNG_1PX = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")


def rec(case, ok, detail=""):
    RESULTS.append({"case": case, "ok": ok, "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {case}" + (f" :: {detail}" if detail else ""), flush=True)


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"_http": e.code}


def login(u, p="123456"):
    r = call("POST", "/auth/login", None, {"username": u, "password": p})
    if "data" not in r:
        raise RuntimeError(f"登录失败 {u}: {r}")
    return r["data"]


def sql(q):
    p = subprocess.run([MYSQL, "-h", "127.0.0.1", "-P", "3306", "-u", "root", "-proot",
                        "--ssl-mode=DISABLED", "-D", "tutor_platform",
                        "--default-character-set=utf8mb4", "-N", "-B", "-e", q],
                       capture_output=True, timeout=30)
    return p.stdout.decode("utf-8", "replace").strip()


def max_id(table):
    return int(sql(f"SELECT IFNULL(MAX(id),0) FROM {table}") or 0)


def credit_of(user_id):
    return int(sql(f"SELECT credit_score FROM tutor WHERE user_id={user_id}") or 100)


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


def wait_msg(page, expect, timeout=10000):
    el = page.locator(".el-message").filter(has_text=expect).first
    el.wait_for(state="visible", timeout=timeout)
    return el.inner_text()


def confirm_box(page):
    """Element Plus MessageBox 二次确认：点最后一个按钮（确定）"""
    box = page.locator(".el-message-box")
    box.wait_for(state="visible", timeout=15000)
    btns = page.locator(".el-message-box__btns button")
    btn = btns.nth(btns.count() - 1)
    btn.wait_for(state="visible", timeout=10000)
    page.wait_for_timeout(300)
    btn.click()
    box.wait_for(state="hidden", timeout=10000)


def main():
    snap = {
        "appt_max": max_id("appointment"),
        "complaint_max": max_id("complaint"),
        "violation_max": max_id("tutor_violation"),
        "msg_max": max_id("message"),
    }
    credit0 = credit_of(2)
    print(f"[前置] 快照 {snap} tutor01(user_id=2) credit={credit0}", flush=True)

    t1 = login("tutor01")
    st = login("student01")
    ad = login("admin")

    # 造一笔「授课中」订单，供学生在页面上点「确认完成」
    r = call("POST", "/appointments", st["token"], {
        "tutorId": 1, "subjectId": 2, "appointDate": "2026-12-20", "timeSlot": "08:00-10:00"})
    appt_id = r.get("data")
    if not appt_id:
        raise RuntimeError(f"造订单失败: {r}")
    call("PUT", f"/appointments/{appt_id}/confirm", t1["token"])
    call("PUT", f"/appointments/{appt_id}/start", t1["token"])
    print(f"[前置] 订单 {appt_id} 已到「授课中」，信用分={credit0}", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": 1560, "height": 980}, accept_downloads=True)
        page = ctx.new_page()
        page.on("console", lambda m: JS_ERRORS.append(m.text) if m.type == "error" else None)
        page.on("response",
                lambda resp: HTTP_ERRORS.append(f"{resp.status} {resp.request.method} {resp.url}")
                if "/api/" in resp.url and resp.status >= 400 else None)
        try:
            # ============ 1. 订单完成 → 信用分 +1（学生端真点） ============
            login_ui(page, "student01")
            goto(page, "/orders")
            page.wait_for_timeout(1200)
            row = page.locator(".el-table__body tr").filter(has_text="授课中").first
            row.wait_for(state="visible", timeout=10000)
            row.get_by_role("button", name=re.compile("确认完成")).first.click()
            confirm_box(page)
            wait_msg(page, "操作成功")
            page.wait_for_timeout(1200)
            credit1 = credit_of(2)
            status_row = sql(f"SELECT status FROM appointment WHERE id={appt_id}")
            rec("1. 点「确认完成」后订单完成且老师信用分 +1",
                status_row == "3" and credit1 == credit0 + 1,
                f"订单状态={status_row} 信用分 {credit0}->{credit1}")
            page.screenshot(path=str(SHOT / "r3-01-finish-order.png"), full_page=True)

            # 重复完成：直接调接口，状态机拦截，不能二次加分
            dup = call("PUT", f"/appointments/{appt_id}/finish", st["token"])
            page.wait_for_timeout(300)
            credit2 = credit_of(2)
            rec("2. 重复完成被状态机拦截，信用分不二次累加",
                dup.get("code") == 400 and credit2 == credit1,
                f"返回={dup.get('code')}/{dup.get('message')} 信用分仍={credit2}")

            # ============ 2. 信用分 < 60 限制接单 ============
            sql("UPDATE tutor SET credit_score=50 WHERE user_id=2")
            page.evaluate("localStorage.clear()")
            login_ui(page, "student01")
            goto(page, "/tutors")
            page.wait_for_timeout(1500)
            card = page.locator(".tutor-card").filter(has_text="王老师").first
            card.wait_for(state="visible", timeout=10000)
            btn = card.get_by_role("button", name=re.compile("暂不可预约|预约 TA")).first
            txt = btn.inner_text().strip()
            disabled = btn.is_disabled()
            rec("3. 信用分 50 时老师卡片按钮禁用并显示「暂不可预约」",
                txt == "暂不可预约" and disabled, f"按钮文本={txt} disabled={disabled}")
            page.screenshot(path=str(SHOT / "r3-02-tutor-restricted.png"), full_page=True)

            card.locator(".tutor-name").first.click()
            drawer = page.locator(".el-drawer").first
            drawer.wait_for(state="visible", timeout=8000)
            page.wait_for_timeout(800)
            dbtn = drawer.get_by_role("button", name=re.compile("暂不可预约")).first
            rec("4. 详情抽屉预约按钮禁用（信用受限）",
                dbtn.count() > 0 and dbtn.is_disabled(), f"按钮文本={dbtn.inner_text().strip()}")
            page.screenshot(path=str(SHOT / "r3-03-drawer-restricted.png"), full_page=True)
            drawer.locator(".el-drawer__close-btn").first.click()

            blocked = call("POST", "/appointments", st["token"], {
                "tutorId": 1, "subjectId": 2, "appointDate": "2026-12-22", "timeSlot": "19:00-21:00"})
            rec("5. 后端下单接口拒绝受限老师（403 信用分受限）",
                blocked.get("code") == 403 and "信用分受限" in (blocked.get("message") or ""),
                f"返回={blocked.get('code')}/{blocked.get('message')}")
            sql(f"UPDATE tutor SET credit_score={credit1} WHERE user_id=2")

            # ============ 3. 管理端「违规记录」页 ============
            # 先造一条投诉，再由管理员在页面上判「成立」，产生违规记录
            # 注意：投诉接口的 tutorId 是老师的 user.id（王老师=2），不是 tutor 表主键
            c1 = call("POST", "/complaints", st["token"], {
                "tutorId": TUTOR_UID, "type": 1, "content": f"违规记录页验证-{MARK}"})
            complaint_id = c1.get("data")
            if not complaint_id:
                raise RuntimeError(f"造投诉失败: {c1}")
            page.evaluate("localStorage.clear()")
            login_ui(page, "admin")
            goto(page, "/admin/complaints")
            page.wait_for_timeout(1200)
            crow = page.locator(".el-table__body tr").filter(has_text=f"违规记录页验证-{MARK}").first
            crow.wait_for(state="visible", timeout=10000)
            crow.get_by_role("button", name=re.compile("^成\\s*立$")).first.click()
            dlg = page.locator(".el-dialog").filter(has_text="判定投诉成立").first
            dlg.wait_for(state="visible", timeout=15000)
            page.wait_for_timeout(600)
            dlg.locator("textarea").first.fill("核实属实（违规记录页 E2E）")
            dlg.get_by_role("button", name="确定").first.click()
            confirm_box(page)
            wait_msg(page, "处理成功")
            page.wait_for_timeout(1200)

            goto(page, "/admin/violations")
            page.wait_for_timeout(1200)
            body = page.locator("body").inner_text()
            rec("6. 违规记录页能打开且表格有数据", "违规记录" in body and "累计扣分" in body,
                f"页面文本长度={len(body)}")
            vrow = page.locator(".el-table__body tr").filter(has_text="王老师").first
            vrow.wait_for(state="visible", timeout=10000)
            vtxt = vrow.inner_text().replace("\n", "|")
            rec("7. 新产生的违规记录出现在列表（老师/类型/扣分/操作人）",
                "投诉成立" in vtxt and "王老师" in vtxt and "管理员" in vtxt,
                vtxt[:120])
            page.screenshot(path=str(SHOT / "r3-04-violations.png"), full_page=True)

            # 按老师筛选（下拉选王老师，列表仍含该老师且其他老师被过滤）
            page.locator(".filter-card .el-select").first.click()
            page.wait_for_timeout(500)
            page.locator(".el-select-dropdown__item").filter(has_text="王老师").first.click()
            page.wait_for_timeout(1200)
            rows_after = page.locator(".el-table__body tr").all_inner_texts()
            only_tutor = len(rows_after) > 0 and all("王老师" in t for t in rows_after)
            summary_txt = page.locator(".summary").first.inner_text()
            rec("8. 按老师筛选生效 + 汇总统计（条数/累计扣分/涉及老师数）",
                only_tutor and "共" in summary_txt and "累计扣分" in summary_txt,
                f"行数={len(rows_after)} 汇总={summary_txt}")
            page.screenshot(path=str(SHOT / "r3-05-violations-filter.png"), full_page=True)

            # ============ 4. 投诉证据上传（学生 + 管理端预览） ============
            shot_png = TMP / f"evidence-{MARK}.png"
            shot_png.write_bytes(PNG_1PX)
            page.evaluate("localStorage.clear()")
            login_ui(page, "student01")
            goto(page, "/student/complaints")
            page.wait_for_timeout(1000)
            page.get_by_role("tab", name="提交投诉").first.click()
            page.wait_for_timeout(600)
            page.locator(".el-form-item").filter(has_text="被投诉老师").locator(".el-select").first.click()
            page.wait_for_timeout(500)
            page.locator(".el-select-dropdown__item").first.click()
            page.get_by_text("其他", exact=True).first.click()
            page.locator("textarea").first.fill(f"证据上传验证-{MARK}")
            page.locator("input[type=file]").first.set_input_files(str(shot_png))
            wait_msg(page, "证据上传成功")
            page.wait_for_timeout(800)
            uploaded_hint = page.locator(".evidence-row .uploaded").first
            rec("9. 证据上传成功（页面显示已上传 + 预览入口）",
                uploaded_hint.count() > 0 and "已上传" in uploaded_hint.inner_text(),
                uploaded_hint.inner_text().replace("\n", " ") if uploaded_hint.count() else "未显示")
            page.screenshot(path=str(SHOT / "r3-06-evidence-uploaded.png"), full_page=True)

            page.get_by_role("button", name=re.compile("提交投诉")).first.click()
            wait_msg(page, "投诉已提交")
            page.wait_for_timeout(1200)
            mine = call("GET", "/complaints/my", st["token"])["data"]
            new_one = next(c for c in mine if c["content"].endswith(MARK))
            rec("10. 提交后证据字段落库（evidenceUrl 指向本系统上传接口）",
                (new_one.get("evidenceUrl") or "").startswith("/api/complaints/evidence/"),
                f"evidenceUrl={new_one.get('evidenceUrl')}")
            page.screenshot(path=str(SHOT / "r3-07-evidence-submitted.png"), full_page=True)

            # 我的投诉列表点「查看」，应新开 blob 预览页
            mrow = page.locator(".el-table__body tr").filter(has_text=MARK).first
            mrow.wait_for(state="visible", timeout=10000)
            with page.expect_popup(timeout=15000) as pop:
                mrow.get_by_role("button", name=re.compile("查看")).first.click()
            popup = pop.value
            popup.wait_for_load_state("load")
            purl = popup.url
            rec("11. 学生端「查看」证据：新窗口 blob 预览可打开",
                purl.startswith("blob:"), f"新窗口 url={purl[:60]}")
            popup.close()

            # 管理端点「查看证据」
            page.evaluate("localStorage.clear()")
            login_ui(page, "admin")
            goto(page, "/admin/complaints")
            page.wait_for_timeout(1200)
            arow = page.locator(".el-table__body tr").filter(has_text=MARK).first
            arow.wait_for(state="visible", timeout=10000)
            rec("12. 管理端投诉列表出现证据「查看」按钮",
                arow.get_by_role("button", name=re.compile("查看")).count() > 0, "按钮存在")
            with page.expect_popup(timeout=15000) as pop2:
                arow.get_by_role("button", name=re.compile("查看")).first.click()
            popup2 = pop2.value
            popup2.wait_for_load_state("load")
            rec("13. 管理端查看证据：blob 预览可打开",
                popup2.url.startswith("blob:"), f"新窗口 url={popup2.url[:60]}")
            popup2.close()
            page.screenshot(path=str(SHOT / "r3-08-admin-evidence.png"), full_page=True)
        finally:
            browser.close()

    # ---------------- 复原 ----------------
    print("\n[复原] 开始...", flush=True)
    sql(f"DELETE FROM appointment WHERE id={appt_id}")
    sql(f"DELETE FROM complaint WHERE id>{snap['complaint_max']}")
    sql(f"DELETE FROM tutor_violation WHERE id>{snap['violation_max']}")
    sql(f"DELETE FROM message WHERE id>{snap['msg_max']}")
    sql(f"UPDATE tutor SET credit_score={credit0} WHERE user_id=2")
    print("[复原] 完成 信用分=" + sql("SELECT credit_score FROM tutor WHERE user_id=2"), flush=True)

    return summary()


def summary():
    print("\n" + "=" * 72, flush=True)
    passed = sum(1 for r in RESULTS if r["ok"])
    print(f"结果: {passed}/{len(RESULTS)} 通过", flush=True)
    js = [e for e in JS_ERRORS if "favicon" not in e.lower()]
    http_err = list(dict.fromkeys(HTTP_ERRORS))
    print(f"页面 JS 错误: {len(js)}   4xx/5xx API: {len(http_err)}", flush=True)
    for e in js[:6]:
        print(f"  ! JS {e[:170]}", flush=True)
    for e in http_err[:6]:
        print(f"  ! API {e}", flush=True)
    out = Path(r"E:\毕业设计\project\docs\round3-e2e-result.json")
    out.write_text(json.dumps({"summary": {"passed": passed, "total": len(RESULTS)},
                               "cases": RESULTS, "js_errors": js, "http_errors": http_err},
                              ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"结果文件: {out}", flush=True)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
