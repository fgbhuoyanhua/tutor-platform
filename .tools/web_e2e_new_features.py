# -*- coding: utf-8 -*-
"""
新增三项功能（阶段二）Web 端 E2E：
  1. 老师课时账单（Web 老师端 /tutor/bill）：月份选择、汇总卡片、明细、CSV 导出、快照口径
  2. 投诉举报（Web 学生端 /student/complaints + 管理端 /admin/complaints）：提交 → 待处理 → 成立扣分
  3. 推荐排序显式化（/tutors「智能推荐」）：权重说明 + 推荐分 + 推荐理由；等级标签
     + 老师详情抽屉里的信用分进度条

测试数据自己造（tutor01 在 2026-12 有 1 单已完成的预约 + 学生提交 1 条投诉），
结束按 SQL 复原：删预约、删投诉、删违规、删消息、tutor 信用分回到原值。
"""
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
MYSQL = r"D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe"

RESULTS, JS_ERRORS, HTTP_ERRORS = [], [], []


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


# ---------------- UI helpers ----------------
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


def main():
    snap = {
        "appt_max": max_id("appointment"),
        "complaint_max": max_id("complaint"),
        "violation_max": max_id("tutor_violation"),
        "msg_max": max_id("message"),
        "log_max": max_id("admin_log"),
    }
    credit_before = sql("SELECT credit_score FROM tutor WHERE user_id=2")
    print(f"[前置] 快照 {snap} tutor01 credit={credit_before}", flush=True)

    if snap['complaint_max'] > 1:
        sql(f"DELETE FROM complaint WHERE id>{snap['complaint_max']}")
        snap['complaint_max'] = max_id('complaint')
    t1 = login("tutor01")
    st = login("student01")
    ad = login("admin")

    # ---------- 造数据：tutor01 在 2026-12-01 08:00-10:00 一笔已完成订单 ----------
    r = call("POST", "/appointments", st["token"], {
        "tutorId": 1, "subjectId": 2, "appointDate": "2026-12-01", "timeSlot": "08:00-10:00"})
    appt_id = r.get("data")
    if appt_id:
        # 走真实状态机：老师接单 → 开始授课 → 学生完成
        call("PUT", f"/appointments/{appt_id}/confirm", t1["token"])
        call("PUT", f"/appointments/{appt_id}/start", t1["token"])
        call("PUT", f"/appointments/{appt_id}/finish", st["token"])
    print(f"[前置] 造订单 appt_id={appt_id}（已完成，2026-12-01 08:00-10:00）", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": 1560, "height": 980}, accept_downloads=True)
        page = ctx.new_page()
        page.on("console", lambda m: JS_ERRORS.append(m.text) if m.type == "error" else None)
        page.on("response",
                lambda r: HTTP_ERRORS.append(f"{r.status} {r.request.method} {r.url}")
                if "/api/" in r.url and r.status >= 400 else None)
        try:
            # ==================== 1. 老师课时账单 ====================
            login_ui(page, "tutor01")
            goto(page, "/tutor/bill")
            page.wait_for_timeout(1200)
            # 默认当前月（2026-11），先看空态，再切到 2026-12
            body0 = page.locator("body").inner_text()
            rec("1. 账单页渲染（账期/汇总/明细表）",
                all(k in body0 for k in ["账期", "已完成节数", "已完成金额", "课时明细"]),
                f"页面文本长度={len(body0)}")

            # 切到 2026-12：日期选择器输入
            month_input = page.locator(".el-date-editor input").first
            month_input.click()
            month_input.fill("2026-12")
            month_input.press("Enter")
            page.wait_for_timeout(1500)
            body1 = page.locator("body").inner_text()
            has_amount = "¥120.00" in body1
            rows = page.locator(".el-table__body tr").count()
            rec("2. 2026-12 账单：明细 1 行且金额 120.00", rows >= 1 and has_amount,
                f"行数={rows} 含¥120.00={has_amount}")
            page.screenshot(path=str(SHOT / "new-01-bill.png"), full_page=True)

            # CSV 导出
            with page.expect_download(timeout=20000) as d:
                page.get_by_role("button", name=re.compile("导出 CSV")).first.click()
            dl = d.value
            csv_path = Path(r"E:\毕业设计\project\.tmp_compile") / dl.suggested_filename
            dl.save_as(str(csv_path))
            raw = csv_path.read_bytes()
            txt = raw.decode("utf-8-sig")
            ok_csv = raw[:3] == b"\xef\xbb\xbf" and "已完成金额(元),120" in txt and "2026-12-01" in txt
            rec("3. 导出 CSV：UTF-8 BOM + 汇总 + 明细行", ok_csv,
                f"文件名={dl.suggested_filename} 字节={len(raw)} BOM={raw[:3] == b'\xef\xbb\xbf'}")

            # 快照口径：老师改价后历史账单金额不变
            call("PUT", "/tutors/1", t1["token"], {
                "id": 1, "subjectId": 2, "grade": "初中/高中", "price": 999.00,
                "introduce": "数学专业研究生，三年家教经验，擅长提分。"})
            call("PUT", "/admin/tutors/1/audit?pass=true", ad["token"])
            bill_after = call("GET", "/tutor/bill?month=2026-12", t1["token"])["data"]
            rec("4. 改价后历史账单金额仍为快照 120.00（不被污染）",
                str(bill_after["totalAmount"]).startswith("120"),
                f"改价 999 后 totalAmount={bill_after['totalAmount']}")
            call("PUT", "/tutors/1", t1["token"], {
                "id": 1, "subjectId": 2, "grade": "初中/高中", "price": 120.00,
                "introduce": "数学专业研究生，三年家教经验，擅长提分。"})
            call("PUT", "/admin/tutors/1/audit?pass=true", ad["token"])

            # ==================== 2. 投诉举报（学生 + 管理端）====================
            page.evaluate("localStorage.clear()")
            login_ui(page, "student01")
            goto(page, "/student/complaints")
            page.wait_for_timeout(1000)
            body = page.locator("body").inner_text()
            rec("5. 学生端投诉页两个 tab 渲染", "我的投诉" in body and "提交投诉" in body,
                f"长度={len(body)}")
            page.get_by_role("tab", name="提交投诉").first.click()
            page.wait_for_timeout(600)
            # 选老师（下拉第一项）
            page.locator(".el-form-item").filter(has_text="被投诉老师").locator(".el-select").first.click()
            page.wait_for_timeout(500)
            page.locator(".el-select-dropdown__item").first.click()
            # 投诉类型：私改价格
            page.get_by_text("私改价格", exact=True).first.click()
            content = "约定 120 元/时，课后要求加价到 150 元，涉及私改价格（E2E）"
            page.locator("textarea").first.fill(content)
            page.get_by_role("button", name=re.compile("提交投诉")).first.click()
            wait_msg(page, "投诉已提交")
            page.wait_for_timeout(1200)
            mine = call("GET", "/complaints/my", st["token"])["data"]
            new_one = next(c for c in mine if c["id"] > snap["complaint_max"])
            rec("6. 投诉提交成功并出现在「我的投诉」",
                "私改价格" in new_one["content"] and new_one["status"] == 0,
                f"我的投诉数={len(mine)} 新投诉 id={new_one['id']} type={new_one['typeLabel']}")
            claim_tutor = new_one["tutorId"]
            # 处理前的信用分，扣分断言要用（投诉成立后值会变）
            claim_credit_before = int(sql(f"SELECT credit_score FROM tutor WHERE user_id={claim_tutor}") or 100)
            page.screenshot(path=str(SHOT / "new-02-complaint-submit.png"), full_page=True)

            # 再提交一次重复投诉（同老师、同为待处理）→ 409
            dup = call("POST", "/complaints", st["token"], {
                "tutorId": claim_tutor, "type": 1, "content": "重复投诉"})
            rec("7. 重复投诉被拦（409）", dup.get("code") == 409, f"返回={dup.get('message')}")

            # 管理端处理
            page.evaluate("localStorage.clear()")
            login_ui(page, "admin")
            goto(page, "/admin/complaints")
            page.wait_for_timeout(1200)
            row = page.locator(".el-table__body tr").filter(has_text="私改价格").first
            row.wait_for(state="visible", timeout=10000)
            rec("8. 管理端待处理列表出现该投诉", row.count() > 0, "行可见")
            row.get_by_role("button", name=re.compile("^成\\s*立$")).first.click()
            dlg = page.locator(".el-dialog").filter(has_text="判定投诉成立").first
            dlg.wait_for(state="visible", timeout=15000)
            page.wait_for_timeout(800)
            page.screenshot(path=str(SHOT / "new-03-complaint-handle.png"), full_page=True)
            # 扣分改成 15：点 input 后用键盘全选覆盖，再 Tab 提交（Element Plus 的 input-number 需要 blur 才回写）
            # Element Plus 的 input-number：直接 fill 会触发控件自身的 change（回写成 10），
            # 真实用户是点两次 "+" 得到 12，这里点 5 次 "+" 得到 15，等价于真实点击。
            plus = dlg.locator(".el-input-number__increase").first
            for _ in range(5):
                plus.click()
                page.wait_for_timeout(120)
            print("    扣分输入框当前值=" + dlg.locator(".el-input-number input").first.input_value(), flush=True)
            dlg.locator("textarea").first.fill("核实聊天记录属实")
            dlg.get_by_role("button", name="确定").first.click()
            box = page.locator(".el-message-box")
            box.wait_for(state="visible", timeout=15000)
            box_txt = box.inner_text()
            print("    二次确认框=" + box_txt.replace("\n", " ")[:80], flush=True)
            # 注意：`.el-message-box` 在 el-dialog 之上；用页面级 locator 点「确定」，且取 last()，
            # 避免匹配到 dialog footer 里被遮挡的那个「确定」（Element Plus 弹层按 z-index 叠加）
            cands = page.locator(".el-message-box__btns button")
            print(f"    确认框按钮数={cands.count()} 文本={cands.all_inner_texts()}", flush=True)
            # 用 nth(1)（最后一个是确定），不用 filter+last 组合，避免匹配到被遮挡的 dialog footer 按钮
            btn = cands.nth(cands.count() - 1)
            btn.wait_for(state="visible", timeout=10000)
            page.wait_for_timeout(400)
            btn.click(force=False)
            box.wait_for(state="hidden", timeout=10000)
            wait_msg(page, "处理成功")
            page.wait_for_timeout(1500)

            credit_after = sql(f"SELECT credit_score FROM tutor WHERE user_id={claim_tutor}")
            # 处理前的信用分在投诉页提交之前就记下来（此刻已扣过分，不能现查）
            credit_before_t = str(claim_credit_before)
            viol = sql(f"SELECT COUNT(*) FROM tutor_violation WHERE tutor_id={claim_tutor} AND id>{snap['violation_max']}")
            # 精确取本次提交的那条（库里有历史投诉，不能取 data[0]）
            handled = next(c for c in call("GET", "/complaints/my", st["token"])["data"]
                           if c["id"] > snap["complaint_max"])
            # 期望值 = 该老师处理前的信用分 - 15（各老师初始分不同，不能写死 90）
            expect_credit = str(int(credit_before_t) - 15)
            rec("9. 投诉成立：信用分 -15、违规记录 +1、投诉状态=成立",
                credit_after == expect_credit and viol == "1" and handled["status"] == 1
                and handled["deductScore"] == 15,
                f"老师 userId={claim_tutor} 信用分 {credit_before_t}->{credit_after}（期望 {expect_credit}） 违规记录={viol} "
                f"状态={handled['statusLabel']} 扣分={handled['deductScore']}")
            page.screenshot(path=str(SHOT / "new-04-complaint-done.png"), full_page=True)

            # ==================== 3. 推荐排序显式化 ====================
            page.evaluate("localStorage.clear()")
            login_ui(page, "student01")
            goto(page, "/tutors")
            page.wait_for_timeout(1200)
            # 等级标签
            tags = page.locator(".tutor-card .el-tag").all_inner_texts()
            has_level = any("金牌老师" in t for t in tags)
            rec("10. 老师卡片显示信用等级标签", has_level, f"卡片标签={tags[:8]}")
            page.get_by_role("button", name=re.compile("智能推荐")).first.click()
            dlg2 = page.locator(".el-dialog").filter(has_text="加权排序").first
            dlg2.wait_for(state="visible", timeout=10000)
            page.wait_for_timeout(1500)
            txt = dlg2.inner_text()
            ok_formula = all(k in txt for k in ["认证 35%", "评分 25%", "完课 20%", "信用 20%"])
            ok_reason = "推荐理由" in txt and "推荐分" in txt
            rec("11. 推荐弹窗展示权重公式 + 推荐分 + 推荐理由", ok_formula and ok_reason,
                "公式命中=" + str(ok_formula) + " 分值/理由命中=" + str(ok_reason))
            page.screenshot(path=str(SHOT / "new-05-recommend.png"), full_page=True)
            dlg2.locator(".el-dialog__close").first.click()
            page.wait_for_timeout(500)

            # 详情抽屉信用分
            page.locator(".tutor-card .tutor-name").first.click()
            drawer = page.locator(".el-drawer").first
            drawer.wait_for(state="visible", timeout=8000)
            page.wait_for_timeout(800)
            dtxt = drawer.inner_text()
            rec("12. 老师详情抽屉显示信用分与等级", "信用分" in dtxt and any(k in dtxt for k in ["金牌老师", "优质老师", "普通老师", "受限老师"]),
                dtxt.replace("\n", "|")[:110])
            page.screenshot(path=str(SHOT / "new-06-credit-drawer.png"), full_page=True)
        finally:
            browser.close()

    # ---------------- 复原 ----------------
    print("\n[复原] 开始...", flush=True)
    if appt_id:
        sql(f"DELETE FROM appointment WHERE id={appt_id}")
    if snap["complaint_max"] is not None:
        sql(f"DELETE FROM complaint WHERE id>{snap['complaint_max']}")
    sql(f"DELETE FROM tutor_violation WHERE id>{snap['violation_max']}")
    sql(f"DELETE FROM message WHERE id>{snap['msg_max']}")
    sql(f"DELETE FROM admin_log WHERE id>{snap['log_max']}")
    if credit_before:
        sql(f"UPDATE tutor SET credit_score={credit_before}, violation_count=0 WHERE user_id=2")
    sql("UPDATE tutor SET price=120.00, status=1 WHERE id=1")
    print("[复原] 完成", flush=True)

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
    out = Path(r"E:\毕业设计\project\docs\new-features-e2e-result.json")
    out.write_text(json.dumps({"summary": {"passed": passed, "total": len(RESULTS)},
                               "cases": RESULTS, "js_errors": js, "http_errors": http_err},
                              ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"结果: {out}", flush=True)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
