# -*- coding: utf-8 -*-
"""边界/极限场景实测（接口层，真调后端 + 真查库）

覆盖：
  1. 信用分 100 时完成订单 → 封顶不加（不出现 101）
  2. 信用分 0 / 59 下单 → 403；60 → 允许（边界值）
  3. 同一订单并发两次「完成」→ 只成功一次、只加一分
  4. 投诉说明 500 字可提交、501 字被拒
  5. 证据上传：6MB 超大被拒、假 .exe 被拒、读取接口路径穿越被拒
  6. 违规记录 limit=0 / 1000 / -1 不 500
  7. 同一投诉并发两条 → 一条成功一条 409

结束按 SQL 复原（删本轮订单/投诉/违规，信用分还原）。
"""
import json
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

API = "http://127.0.0.1:8080/api"
MYSQL = r"D:\phpstudy_pro\Extensions\MySQL5.7.26\bin\mysql.exe"
OUT = Path(r"E:\毕业设计\project\docs\api-boundary-result.json")
MARK = f"BD{int(time.time())}"
RESULTS = []


def rec(case, ok, detail=""):
    RESULTS.append({"case": case, "ok": ok, "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {case}" + (f" :: {detail}" if detail else ""), flush=True)


def call(method, path, token=None, body=None, raw=None, ctype="application/json"):
    if raw is not None:
        data = raw
    elif body is not None:
        data = json.dumps(body).encode()
    else:
        data = None
    h = {"Content-Type": ctype}
    if token:
        h["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            txt = r.read().decode("utf-8", "replace")
            return json.loads(txt) if txt.strip().startswith("{") else {"_raw": txt, "_http": r.status}
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8", "replace"))
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


def credit_of(uid):
    return int(sql(f"SELECT credit_score FROM tutor WHERE user_id={uid}") or 100)


def set_credit(uid, v):
    sql(f"UPDATE tutor SET credit_score={v} WHERE user_id={uid}")


def make_order(st, tutor_id, date, slot):
    return call("POST", "/appointments", st["token"],
                {"tutorId": tutor_id, "subjectId": 2, "appointDate": date, "timeSlot": slot})


def multipart(field, filename, content, ctype="image/png"):
    boundary = "----bd" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
        f"Content-Type: {ctype}\r\n\r\n"
    ).encode() + content + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def main():
    credit0 = credit_of(2)
    appt_max = int(sql("SELECT IFNULL(MAX(id),0) FROM appointment"))
    complaint_max = int(sql("SELECT IFNULL(MAX(id),0) FROM complaint"))
    violation_max = int(sql("SELECT IFNULL(MAX(id),0) FROM tutor_violation"))
    print(f"[前置] credit={credit0} appt_max={appt_max} complaint_max={complaint_max}", flush=True)

    t1 = login("tutor01")
    st = login("student01")
    ad = login("admin")
    created_appts = []

    # 1. 信用分 100 封顶
    set_credit(2, 100)
    r = make_order(st, 1, "2027-01-05", "08:00-10:00")
    a1 = r.get("data")
    if a1:
        created_appts.append(a1)
        call("PUT", f"/appointments/{a1}/confirm", t1["token"])
        call("PUT", f"/appointments/{a1}/start", t1["token"])
        fin = call("PUT", f"/appointments/{a1}/finish", st["token"])
        c = credit_of(2)
        rec("1. 信用分已 100 时完成订单：封顶保持 100，不出现 101",
            fin.get("code") == 200 and c == 100, f"finish={fin.get('code')} credit={c}")
    else:
        rec("1. 信用分已 100 时完成订单：封顶保持 100", False, f"造单失败 {r}")

    # 2. 信用分边界：0 / 59 / 60
    for val, expect_ok in ((0, False), (59, False), (60, True)):
        set_credit(2, val)
        r = make_order(st, 1, f"2027-01-1{val % 10}", "14:00-16:00")
        ok = (r.get("code") == 200) if expect_ok else (r.get("code") == 403)
        if r.get("data"):
            created_appts.append(r.get("data"))
            call("PUT", f"/appointments/{r['data']}/cancel", st["token"])
        rec(f"2.{val}. 信用分 {val} 下单：{'允许' if expect_ok else '拒绝 403'}",
            ok, f"code={r.get('code')} msg={r.get('message')}")

    # 3. 同一订单并发完成两次
    set_credit(2, credit0)
    r = make_order(st, 1, "2027-01-20", "19:00-21:00")
    a3 = r.get("data")
    if a3:
        created_appts.append(a3)
        call("PUT", f"/appointments/{a3}/confirm", t1["token"])
        call("PUT", f"/appointments/{a3}/start", t1["token"])
        before = credit_of(2)
        res = {}

        def fire(i):
            res[i] = call("PUT", f"/appointments/{a3}/finish", st["token"]).get("code")

        th = [threading.Thread(target=fire, args=(i,)) for i in range(2)]
        [t.start() for t in th]
        [t.join() for t in th]
        after = credit_of(2)
        codes = sorted(res.values())
        rec("3. 同一订单并发两次「完成」：只成功一次、信用分只加一分",
            codes == [200, 400] and after == before + 1,
            f"返回码={codes} 信用分 {before}->{after}")
    else:
        rec("3. 同一订单并发两次「完成」", False, f"造单失败 {r}")

    # 4. 投诉说明长度边界 500 / 501
    c500 = call("POST", "/complaints", st["token"],
                {"tutorId": 2, "type": 1, "content": f"{MARK}" + "约" * (500 - len(MARK))})
    if c500.get("data"):
        sql(f"DELETE FROM complaint WHERE id={c500['data']}")
    c501 = call("POST", "/complaints", st["token"],
                {"tutorId": 2, "type": 1, "content": f"{MARK}" + "约" * (501 - len(MARK))})
    rec("4. 投诉说明 500 字可提交、501 字被拒",
        c500.get("code") == 200 and c501.get("code") == 400,
        f"500字={c500.get('code')} 501字={c501.get('code')}/{c501.get('message')}")

    # 5. 证据上传边界
    small = b"\x89PNG\r\n\x1a\n" + b"\x00" * 2000
    body, ct = multipart("file", "ok.png", small)
    up1 = call("POST", "/complaints/evidence", st["token"], raw=body, ctype=ct)
    big = b"\x89PNG\r\n\x1a\n" + b"\x00" * (6 * 1024 * 1024)
    body, ct = multipart("file", "big.png", big)
    up2 = call("POST", "/complaints/evidence", st["token"], raw=body, ctype=ct)
    body, ct = multipart("file", "evil.exe", b"MZ" + b"\x00" * 100, "application/octet-stream")
    up3 = call("POST", "/complaints/evidence", st["token"], raw=body, ctype=ct)
    rec("5.1 正常 PNG 上传成功", up1.get("code") == 200, f"code={up1.get('code')} url={str(up1.get('data'))[:60]}")
    rec("5.2 6MB 超大文件被拒", up2.get("code") in (400, 413), f"code={up2.get('code')}/{up2.get('message')}")
    rec("5.3 假 .exe 文件被拒", up3.get("code") == 400, f"code={up3.get('code')}/{up3.get('message')}")
    trav = call("GET", "/complaints/evidence/2/..%2F..%2Fpom.xml", ad["token"])
    # Tomcat 在进到应用前就以 400 拒绝（响应体是 HTML，不是 JSON 信封）
    trav_code = trav.get("code") if trav.get("code") is not None else trav.get("_http")
    rec("5.4 证据读取路径穿越被拒", trav_code == 400, f"code={trav_code}")
    noauth = call("GET", "/complaints/evidence/2/x.png", None)
    rec("5.5 证据读取未登录被拒 401", noauth.get("code") == 401, f"code={noauth.get('code')}")
    other = call("GET", "/complaints/evidence/2/x.png", login("student01")["token"])
    rec("5.6 学生读他人证据目录被拒（403/404）", other.get("code") in (403, 404), f"code={other.get('code')}")

    # 6. 违规记录 limit 边界
    for lim in (0, 1000, -1):
        r = call("GET", f"/admin/violations?limit={lim}", ad["token"])
        rec(f"6.limit={lim}. 违规记录 limit={lim} 不 500",
            r.get("code") in (200, 400), f"code={r.get('code')} size={len(r.get('data') or [])}")

    # 7. 同一投诉并发两条
    body_ok, ct2 = multipart("file", "x.png", small)
    payload = {"tutorId": 2, "type": 2, "content": f"并发投诉-{MARK}"}
    threads_res = {}

    def fire_complaint(i):
        threads_res[i] = call("POST", "/complaints", st["token"], payload).get("code")

    th = [threading.Thread(target=fire_complaint, args=(i,)) for i in range(2)]
    [t.start() for t in th]
    [t.join() for t in th]
    codes = sorted(threads_res.values())
    rec("7. 同一投诉并发两条：一条成功一条 409（唯一索引兜底）",
        codes == [200, 409], f"返回码={codes}")

    # 复原
    for a in created_appts:
        sql(f"DELETE FROM appointment WHERE id={a}")
    sql(f"DELETE FROM complaint WHERE id>{complaint_max}")
    sql(f"DELETE FROM tutor_violation WHERE id>{violation_max}")
    sql(f"DELETE FROM message WHERE id>(SELECT IFNULL(MAX(id),0) FROM message)-0 AND content LIKE '%{MARK}%'")
    set_credit(2, credit0)
    sql("UPDATE tutor SET violation_count=(SELECT COUNT(*) FROM tutor_violation WHERE tutor_id=1) WHERE id=1")
    print(f"[复原] 删除订单 {created_appts}、投诉 id>{complaint_max}、违规 id>{violation_max}；信用分回 {credit0}", flush=True)

    passed = sum(1 for r in RESULTS if r["ok"])
    print(f"\n[结果] {passed}/{len(RESULTS)} 通过", flush=True)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"passed": passed, "total": len(RESULTS), "results": RESULTS},
                              ensure_ascii=False, indent=2), encoding="utf-8")
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
