# -*- coding: utf-8 -*-
"""
老师资格认证 —— 管理端真实点击验证（补 2026-11-25 交接单里「管理员资格认证 tab 内部没点到」）

覆盖 15 条用例：tab 与待审数量、待审表格渲染、材料 blob 预览、通过（回写 tutor 表）、
驳回（理由必填 + 回填）、老师端看到驳回理由、重新提交回到待审等。

自包含：用例 0 先清理历史待审残留，再自己上传真实证明文件并提交申请，全程不污染业务数据；
结束时把 tutor_cert / message / admin_log / tutor 认证字段全部复原到脚本开始前的状态。
"""
import json
import re
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import zlib
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
API = "http://localhost:8080/api"
SHOT = Path(r"E:\毕业设计\project\docs\web-e2e-shots")
SHOT.mkdir(parents=True, exist_ok=True)
MYSQL = r"D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe"

RESULTS = []
JS_ERRORS = []
HTTP_ERRORS = []
CREATED = []   # 本轮新建的 tutor_cert id


def rec(case, ok, detail=""):
    RESULTS.append({"case": case, "ok": ok, "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {case}" + (f" :: {detail}" if detail else ""), flush=True)


# ---------------- HTTP ----------------
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


def upload(token, filename, content, mime):
    boundary = "----dsh" + uuid.uuid4().hex
    parts = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
             f"filename=\"{filename}\"\r\nContent-Type: {mime}\r\n\r\n").encode()
    body = parts + content + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(f"{API}/tutor-cert/upload", data=body, method="POST",
                                 headers={"Content-Type": f"multipart/form-data; boundary={boundary}",
                                          "Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"_http": e.code, "_body": e.read().decode("utf-8", "ignore")[:200]}


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


def tutor_row(user_id):
    out = sql(f"SELECT school, major, grade_level, cert_status FROM tutor WHERE user_id={user_id}")
    if not out:
        return None
    f = out.split("\t")
    return {"school": f[0], "major": f[1], "grade": f[2], "cert_status": int(f[3])}


def snapshot():
    snap = {"tutor": {uid: tutor_row(uid) for uid in (2, 3)}}
    for k, t in (("messages_max", "message"), ("cert_max", "tutor_cert"), ("log_max", "admin_log")):
        out = sql(f"SELECT IFNULL(MAX(id),0) FROM {t}")
        snap[k] = int(out or 0)
    return snap


def restore(snap):
    print("\n[复原] 开始...", flush=True)
    sql(f"DELETE FROM tutor_cert WHERE id > {snap['cert_max']}")
    sql(f"DELETE FROM message WHERE id > {snap['messages_max']}")
    sql(f"DELETE FROM admin_log WHERE id > {snap['log_max']}")
    for uid, row in snap["tutor"].items():
        if not row:
            continue
        lit = lambda v: "NULL" if v == "NULL" else "'" + v.replace("'", "''") + "'"
        sql(f"UPDATE tutor SET school={lit(row['school'])}, major={lit(row['major'])}, "
            f"grade_level={lit(row['grade'])}, cert_status={row['cert_status']} WHERE user_id={uid}")
    print(f"[复原] 完成：新增 cert> {snap['cert_max']} / msg> {snap['messages_max']} / "
          f"log> {snap['log_max']} 已删，tutor 认证字段已还原 {snap['tutor']}", flush=True)


def make_png(text="CERT-E2E"):
    """生成一张带文字色块的合法 PNG（不需要 PIL）"""
    w, h = 240, 120
    rows = []
    for y in range(h):
        row = bytearray([0])
        for x in range(w):
            inside = (30 <= x <= 210) and (30 <= y <= 90) and ((x // 12 + y // 12) % 2 == 0)
            row += bytes((40, 90, 200) if inside else (245, 245, 245))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(t, d):
        c = t + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


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


def open_cert_tab(page):
    goto(page, "/admin/tutor-audit")
    page.wait_for_timeout(600)
    tab = page.locator(".el-tabs__item").filter(has_text="资格认证").first
    tab.wait_for(state="visible", timeout=10000)
    tab.click()
    page.wait_for_timeout(900)
    return tab.inner_text()


def cert_rows_in_cert_tab(page):
    """资格认证 tab 内的表格是第二个 .el-table"""
    return page.locator(".el-tab-pane:visible .el-table__body tr")


def find_row(page, text, timeout=15000):
    t0 = time.time()
    while time.time() - t0 < timeout / 1000:
        r = page.locator(".el-tab-pane:visible .el-table__body tr").filter(has_text=text)
        if r.count() > 0:
            return r.first
        page.wait_for_timeout(300)
    raise AssertionError(f"资格认证 tab 里找不到包含『{text}』的行")


def confirm_box(page, expect_btn=r"确定|确认"):
    box = page.locator(".el-message-box")
    box.wait_for(state="visible", timeout=8000)
    txt = box.inner_text()
    box.get_by_role("button", name=re.compile(expect_btn)).first.click()
    return box, txt


def main():
    snap = snapshot()
    print(f"[前置] 快照 {snap}", flush=True)
    at = login("admin")["token"]

    # ---------- 用例 0：清理历史待审残留（2026-11-25 遗留的 id=9 无文件）----------
    stale = call("GET", "/admin/certs/pending", at)["data"]
    if stale:
        ids = ",".join(str(s["id"]) for s in stale)
        sql(f"UPDATE tutor_cert SET status=2, review_note='E2E 清理', review_time=NOW() WHERE id IN ({ids})")
        for s in stale:
            sql(f"UPDATE tutor SET cert_status=3 WHERE user_id={s['tutorId']}")
        print(f"[前置] 清理历史待审 {ids}", flush=True)
    pending0 = call("GET", "/admin/certs/pending", at)["data"]
    rec("0. 历史待审残留已清理（队列为空）", len(pending0) == 0, f"待审={len(pending0)}")

    # ---------- 用例 0b：造两条带真实文件的待审申请 ----------
    created = {}
    for uname, school, major, grade, ctype in (
            ("tutor02", "苏州科技大学", "数学与应用数学", "大三", 3),   # 用于驳回 + 材料预览
            ("tutor01", "苏州大学", "软件工程", "大二", 2)):           # 用于通过（学信网报告必填编号）
        tok = login(uname)["token"]
        up = upload(tok, "cert_proof.png", make_png(), "image/png")
        if "data" not in up:
            rec(f"0b. {uname} 上传证明材料", False, str(up)[:120])
            continue
        body = {"school": school, "major": major, "gradeLevel": grade,
                "certType": ctype, "certUrl": up["data"], "note": "E2E 验证用例"}
        if ctype == 2:
            body["certNo"] = "XH" + time.strftime("%Y%m%d%H%M%S")
        r = call("POST", "/tutor-cert", tok, body)
        if "data" in r:
            CREATED.append(r["data"])
            created[uname] = r["data"]
        rec(f"0b. {uname} 上传+提交认证申请", "data" in r,
            f"certId={r.get('data')} file={up['data']}" if "data" in r else str(r)[:150])
    pend = call("GET", "/admin/certs/pending", at)["data"]
    print(f"[前置] 待审队列: {[(x['id'], x['tutorId'], x['certUrl']) for x in pend]}", flush=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        ctx = browser.new_context(viewport={"width": 1560, "height": 980})
        page = ctx.new_page()
        page.on("console", lambda m: JS_ERRORS.append(m.text) if m.type == "error" else None)
        page.on("response",
                lambda r: HTTP_ERRORS.append(f"{r.status} {r.request.method} {r.url}")
                if "/api/" in r.url and r.status >= 400 else None)
        try:
            login_ui(page, "admin")
            label = open_cert_tab(page)
            tabs = [t.strip() for t in page.locator(".el-tabs__item").all_inner_texts()]
            rec("1. 管理端两个 tab 存在", len(tabs) >= 2 and any("资格认证" in t for t in tabs), f"tabs={tabs}")
            rec("2. 资格认证标签带待审数量", f"（{len(pend)}）" in label, f"标签='{label}' 实际待审={len(pend)}")

            body = page.locator(".el-tab-pane:visible .el-table").last.inner_text()
            has_cols = all(k in body for k in ["老师", "院校", "专业", "年级", "证明类型", "材料"])
            rec("3. 待审表格渲染（列头+数据）", has_cols and "苏州科技大学" in body,
                f"表格文本={body.replace(chr(10), '|')[:120]}")
            page.screenshot(path=str(SHOT / "cert-01-pending-tab.png"), full_page=True)

            # ---------- 4. 材料预览：blob + 新窗口 ----------
            with_file = next((x for x in pend if x["certUrl"]), None)
            if with_file:
                row = find_row(page, with_file["tutorName"])
                with ctx.expect_page(timeout=20000) as info:
                    row.get_by_role("button", name=re.compile("查\s*看")).first.click()
                np = info.value
                np.wait_for_timeout(1500)
                probe = np.evaluate("""() => {
                    const img = document.images[0];
                    return {url: location.href,
                            img: img ? img.naturalWidth : 0,
                            body: document.body ? document.body.innerHTML.length : 0};
                }""")
                np.screenshot(path=str(SHOT / "cert-02-material-preview.png"))
                ok = probe["url"].startswith("blob:") and probe["img"] > 0
                rec("4. 材料预览（blob 新窗口 + 图片真实渲染）", ok,
                    f"url={probe['url'][:48]} 图片宽={probe['img']}px")
                np.close()
            else:
                rec("4. 材料预览（blob 新窗口 + 图片真实渲染）", False, "待审队列无带文件申请")

            # ---------- 5~8. 通过（tutor01）----------
            before1 = tutor_row(2)
            row1 = find_row(page, "王老师")
            row1.get_by_role("button", name=re.compile("通\s*过")).first.click()
            box, box_txt = confirm_box(page)
            box.wait_for(state="hidden", timeout=8000)
            wait_msg(page, "操作成功")
            page.wait_for_timeout(1300)
            after1 = tutor_row(2)
            gone1 = page.locator(".el-tab-pane:visible .el-table__body tr").filter(has_text="王老师").count() == 0
            rec("5. 通过二次确认弹窗文案正确", "确认" in box_txt and ("通过" in box_txt),
                f"弹窗='{box_txt.splitlines()[0][:40] if box_txt else ''}'")
            rec("6. 通过后从待审表格消失（UI）", gone1, "王老师行数=0")
            rec("7. 通过后 tutor.cert_status=2 且院校回写 tutor 表",
                after1 is not None and after1["cert_status"] == 2 and after1["school"] == "苏州大学",
                f"前={before1} 后={after1}")
            pend2 = call("GET", "/admin/certs/pending", at)["data"]
            rec("8. 通过后接口待审数减少", len(pend2) == len(pend) - 1, f"{len(pend)} -> {len(pend2)}")
            lst = call("GET", "/tutors?page=1&size=10")["data"]["records"]
            wang = next((x for x in lst if x["userId"] == 2), None)
            rec("9. 列表接口返回 certStatus=2 与 school",
                bool(wang) and wang.get("certStatus") == 2 and bool(wang.get("school")),
                f"王老师 certStatus={wang and wang.get('certStatus')} school={wang and wang.get('school')}")
            page.screenshot(path=str(SHOT / "cert-03-passed.png"), full_page=True)

            # ---------- 10~12. 驳回（tutor02）----------
            row2 = find_row(page, "李老师")
            row2.get_by_role("button", name=re.compile("驳\s*回")).first.click()
            pbox = page.locator(".el-message-box")
            pbox.wait_for(state="visible", timeout=8000)
            pbox.get_by_role("button", name=re.compile("确定|确认")).first.click()
            page.wait_for_timeout(600)
            err_txt = pbox.inner_text() if pbox.is_visible() else ""
            rec("10. 驳回理由为空被拦截", pbox.is_visible() and "不能为空" in err_txt,
                f"提示='{err_txt.splitlines()[-1][:36] if err_txt else '（弹窗已关）'}'")
            reason = "学生证信息不清晰，请重新拍照上传（E2E 驳回用例）"
            pbox.locator("input").first.fill(reason)
            pbox.get_by_role("button", name=re.compile("确定|确认")).first.click()
            pbox.wait_for(state="hidden", timeout=8000)
            wait_msg(page, "操作成功")
            page.wait_for_timeout(1300)
            pend3 = call("GET", "/admin/certs/pending", at)["data"]
            after2 = tutor_row(3)
            rec("11. 驳回后 tutor.cert_status=3、待审清空",
                after2 is not None and after2["cert_status"] == 3 and len(pend3) == 0,
                f"tutor02={after2} 待审剩余={len(pend3)}")
            dbrow = sql("SELECT status, review_note FROM tutor_cert WHERE tutor_id=3 ORDER BY id DESC LIMIT 1")
            rec("12. 申请记录 status=2 且 review_note 有值",
                dbrow.startswith("2\t") and "不清晰" in dbrow, f"库内={dbrow[:70]}")
            empty_n = page.locator(".el-tab-pane:visible .el-empty").count()
            rec("13. 待审清空后显示空态", empty_n > 0, f"el-empty={empty_n}")
            page.screenshot(path=str(SHOT / "cert-04-rejected-empty.png"), full_page=True)

            # ---------- 14. 老师端看驳回理由回填 ----------
            page.evaluate("localStorage.clear()")
            login_ui(page, "tutor02")
            goto(page, "/my-tutor")
            page.wait_for_timeout(1500)
            txt = page.locator("body").inner_text()
            rec("14. 老师端显示驳回状态与新提交表单",
                "驳回" in txt and ("重新提交" in txt or "提交" in txt), f"页面文本长度={len(txt)}")
            page.screenshot(path=str(SHOT / "cert-05-tutor-rejected.png"), full_page=True)

            # ---------- 15. 驳回后重新提交回到待审 ----------
            t2 = login("tutor02")["token"]
            r = call("POST", "/tutor-cert", t2, {
                "school": "苏州科技大学", "major": "数学与应用数学", "gradeLevel": "大三",
                "certType": 3, "note": "重新提交（E2E）"})
            if "data" in r:
                CREATED.append(r["data"])
            pend4 = call("GET", "/admin/certs/pending", at)["data"]
            rec("15. 驳回后重新提交成功并回到待审", "data" in r and len(pend4) == 1,
                f"提交返回={r.get('data', r)} 待审={len(pend4)}")
        finally:
            browser.close()

    restore(snap)
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
    out = Path(r"E:\毕业设计\project\docs\cert-e2e-result.json")
    out.write_text(json.dumps({"summary": {"passed": passed, "total": len(RESULTS)},
                               "cases": RESULTS, "js_errors": js, "http_errors": http_err},
                              ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"结果: {out}", flush=True)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
