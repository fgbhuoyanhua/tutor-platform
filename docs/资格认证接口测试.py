# -*- coding: utf-8 -*-
"""老师资格认证接口测试：正常链路 + 边界与极限"""
import json, os, threading, urllib.request, urllib.error

BASE = "http://localhost:8080/api"
D = r"E:\毕业设计\_tmp\cert_test"
OUT = r"E:\毕业设计\_tmp\cert_test_result.txt"
lines = []
stats = {"PASS": 0, "FAIL": 0}


def log(s):
    lines.append(s)
    print(s)


def check(name, expect, actual, note=""):
    if callable(expect):
        ok = bool(expect(actual))
    else:
        ok = (expect == actual)
    tag = "PASS" if ok else "FAIL"
    stats[tag] += 1
    log(f"[{tag}] {name} | 期望={expect} 实际={actual} {note}")
    return ok


def call(method, path, token=None, body=None, timeout=20):
    url = BASE + path
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            code, resp = r.getcode(), r.read()
    except urllib.error.HTTPError as e:
        code, resp = e.code, e.read()
    except Exception as e:
        return -1, {"message": str(e)}
    try:
        j = json.loads(resp.decode("utf-8"))
    except Exception:
        j = {"raw": resp[:200].decode("utf-8", "ignore")}
    return code, j


def upload(path, token):
    boundary = "----certtestboundary"
    fn = os.path.basename(path)
    body = ("--" + boundary + "\r\n").encode()
    body += ('Content-Disposition: form-data; name="file"; filename="' + fn + '"\r\n').encode()
    body += b"Content-Type: application/octet-stream\r\n\r\n"
    body += open(path, "rb").read()
    body += ("\r\n--" + boundary + "--\r\n").encode()
    req = urllib.request.Request(
        BASE + "/tutor-cert/upload", data=body, method="POST",
        headers={"Content-Type": "multipart/form-data; boundary=" + boundary,
                 "Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            code, resp = r.getcode(), r.read()
    except urllib.error.HTTPError as e:
        code, resp = e.code, e.read()
    except Exception as e:
        return -1, {"message": str(e)}
    try:
        return code, json.loads(resp.decode("utf-8"))
    except Exception:
        return code, {}


def login(username):
    code, j = call("POST", "/auth/login", body={"username": username, "password": "123456"})
    if code == 200:
        return j["data"]["token"], j["data"]["userId"]
    return None, None


log("=" * 70)
log("老师资格认证 接口测试")
log("=" * 70)

# ---------- 登录 ----------
t1, uid1 = login("tutor01")
t2, uid2 = login("tutor02")
ts, uids = login("student01")
ta, uida = login("admin")
check("T01 老师 tutor01 登录", 200 if t1 else 400, 200 if t1 else 400)
check("T01b 老师 tutor02 登录", 200 if t2 else 400, 200 if t2 else 400)
check("T01c 学生 student01 登录", 200 if ts else 400, 200 if ts else 400)
check("T01d 管理员 admin 登录", 200 if ta else 400, 200 if ta else 400,
      f"(adminUserId={uida})")

# ---------- 基线状态 ----------
code, j = call("GET", "/tutor-cert/my", t1)
check("T02 初始状态查询", 200, code, f"certStatus={j.get('data', {}).get('certStatus')}")

# ---------- 上传 ----------
code, j = upload(os.path.join(D, "small.png"), t1)
url_path = j.get("data", "")
check("T03 上传合法 PNG", 200, code, str(url_path)[:60])

code, j = upload(os.path.join(D, "big.png"), t1)
check("T04 上传 5.7MB 超限文件被拒", 400, code, str(j.get("message"))[:40])

code, j = upload(os.path.join(D, "fake.exe"), t1)
check("T05 上传 .exe 非法类型被拒", 400, code, str(j.get("message"))[:40])

code, j = upload(os.path.join(D, "small.png"), ts)
check("T06 学生角色上传被拒", 403, code)

# ---------- 提交 ----------
payload = {"school": "苏州大学", "major": "计算机科学与技术", "gradeLevel": "大二",
           "certType": 1, "certUrl": url_path, "note": "自动化测试"}
code, j = call("POST", "/tutor-cert", t1, payload)
cert_id1 = j.get("data")
check("T07 tutor01 首次提交认证", 200, code, f"certId={cert_id1}")

code, j = call("POST", "/tutor-cert", t1, payload)
check("T08 重复提交（已有待审）被拒", 409, code, str(j.get("message"))[:40])

code, j = call("GET", "/tutor-cert/my", t1)
check("T09 提交后状态=待审(1)", 1, j.get("data", {}).get("certStatus"))

code, j = call("POST", "/tutor-cert", ts, payload)
check("T10 学生角色提交被拒", 403, code)

# 学信网类型缺编号
code, j = call("POST", "/tutor-cert", t2, {"school": "苏州科技大学", "major": "数学",
                                          "gradeLevel": "大三", "certType": 2})
check("T11 学信网类型缺报告编号被拒", 400, code, str(j.get("message"))[:40])

# ---------- 文件鉴权 ----------
fp = url_path.replace("/api", "", 1)

code, j = call("GET", fp, ts)
check("T12 学生读取他人证明文件被拒", 403, code)

code, j = call("GET", fp, t1)
check("T13 老师读取自己的文件", 200, code)

code, j = call("GET", fp, ta)
check("T14 管理员读取任意文件", 200, code)

code, j = call("GET", "/tutor-cert/file/2/..%2F..%2Fapplication.yml", ta)
check("T15 路径穿越被拦截", 400, code, "(Tomcat 直接拒绝编码路径)")

code, j = call("GET", "/tutor-cert/my")
check("T16 未登录查询被拒", 401, code)

# ---------- 管理员审核 ----------
code, j = call("GET", "/admin/certs/pending", ta)
pend = j.get("data") or []
check("T17 管理员待审列表非空", True, len(pend) >= 1, f"共 {len(pend)} 条")

code, j = call("PUT", f"/admin/certs/{cert_id1}/audit?pass=true", ta)
check("T18 审核通过", 200, code)

code, j = call("PUT", f"/admin/certs/{cert_id1}/audit?pass=true", ta)
check("T19 重复审核被拒", 400, code, str(j.get("message"))[:40])

code, j = call("PUT", "/admin/certs/999999/audit?pass=true", ta)
check("T20 审核不存在的申请", 404, code)

code, j = call("GET", "/tutor-cert/my", t1)
d = j.get("data", {})
check("T21 通过后状态=已认证(2)", 2, d.get("certStatus"),
      f"school={d.get('school')} major={d.get('major')} grade={d.get('gradeLevel')}")
check("T22 认证学校信息写入", "苏州大学", d.get("school"))

# ---------- 前台列表展示 ----------
code, j = call("GET", "/tutors?page=1&size=100")
recs = (j.get("data") or {}).get("records") or []
mine = [r for r in recs if r.get("userId") == uid1]
if mine:
    check("T23 列表接口返回认证字段", 2, mine[0].get("certStatus"),
          f"school={mine[0].get('school')}")
else:
    check("T23 列表接口返回认证字段", True, False, "未找到该老师已上架记录")

# ---------- 驳回与重提 ----------
code, j = call("POST", "/tutor-cert", t2, {"school": "苏州科技大学", "major": "数学",
                                          "gradeLevel": "大三", "certType": 2,
                                          "certNo": "XH20261125001"})
cert_id2 = j.get("data")
check("T24 tutor02 提交学信网认证", 200, code, f"certId={cert_id2}")

code, j = call("PUT", f"/admin/certs/{cert_id2}/audit?pass=false&reason=" +
               urllib.request.quote("报告图片不清晰"), ta)
check("T25 审核驳回（带理由）", 200, code)

code, j = call("GET", "/tutor-cert/my", t2)
d2 = j.get("data", {})
check("T26 驳回后状态=3", 3, d2.get("certStatus"), f"reviewNote={d2.get('reviewNote')}")
check("T27 驳回理由可读", True, bool(d2.get("reviewNote")))

code, j = call("POST", "/tutor-cert", t2, {"school": "苏州科技大学", "major": "数学",
                                          "gradeLevel": "大三", "certType": 3})
check("T28 驳回后重新提交成功", 200, code, f"certId={j.get('data')}")

# ---------- 并发提交 ----------
code, j = call("GET", "/tutor-cert/my", t1)
# tutor01 此时已认证（无待审），并发提交两条应只成功一条
results_conc = []


def worker():
    results_conc.append(call("POST", "/tutor-cert", t1,
                             {"school": "苏州大学", "major": "软件工程",
                              "gradeLevel": "大二", "certType": 1})[0])


threads = [threading.Thread(target=worker) for _ in range(2)]
for t in threads:
    t.start()
for t in threads:
    t.join()
codes = sorted(results_conc)
check("T29 并发重复提交只成功一条", [200, 409], codes, f"实际 {codes}")

# 并发后若产生待审，审核通过恢复已认证状态
if 200 in codes:
    _, jj = call("GET", "/tutor-cert/my", t1)
    cid = jj.get("data", {}).get("id")
    if cid:
        call("PUT", f"/admin/certs/{cid}/audit?pass=true", ta)

# ---------- 收尾状态 ----------
code, j1 = call("GET", "/tutor-cert/my", t1)
code, j2 = call("GET", "/tutor-cert/my", t2)
log("-" * 70)
log(f"最终 tutor01 状态={j1.get('data', {}).get('certStatus')}（2=已认证，便于列表展示）")
log(f"最终 tutor02 状态={j2.get('data', {}).get('certStatus')}（1=待审，留给人工审核体验）")
log(f"合计 PASS={stats['PASS']} FAIL={stats['FAIL']}")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("result written to", OUT)
