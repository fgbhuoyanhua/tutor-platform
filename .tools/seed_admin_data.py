# -*- coding: utf-8 -*-
"""
为管理端测试补造前置数据：
1. 一条「待审核」家教信息（供审核通过/驳回测试）
2. 登录若干次（产生 admin_log？—— 实际 admin_log 只在管理操作时写，见后）
"""
import json
import urllib.request

API = "http://localhost:8080/api"


def call(method, path, token, body=None):
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


def main():
    admin = login("admin")
    at = admin["token"]

    # 看当前待审核家教
    pend = call("GET", "/admin/tutors/pending", at)
    print("待审核家教数:", len(pend.get("data") or []))

    # 看全部用户
    users = call("GET", "/admin/users?page=1&size=50", at)
    print("\n用户列表:")
    for u in users["data"]["records"]:
        print(f"  id={u['id']:>2} {u['username']:<10} role={u['role']} status={u['status']} "
              f"phone={u['phone']}")

    # 用 tutor02 发布一条家教信息 → 变成待审核
    t2 = login("tutor02")
    t2t = t2["token"]
    print(f"\ntutor02 uid={t2['userId']}")

    # 先看 tutor02 现有家教信息
    my = call("GET", "/tutors/my", t2t)
    print("tutor02 现有家教信息:", json.dumps(my.get("data"), ensure_ascii=False)[:200])

    r = call("POST", "/tutors", t2t, {
        "subjectId": 2, "grade": "初中", "price": 88.00,
        "introduce": "管理端审核测试用：tutor02 发布的家教信息（2026-10-26）",
    })
    print("发布家教信息:", json.dumps(r, ensure_ascii=False)[:250])

    pend = call("GET", "/admin/tutors/pending", at)
    print("\n发布后待审核家教数:", len(pend.get("data") or []))
    for t in (pend.get("data") or []):
        print(f"  id={t['id']} userId={t['userId']} subjectId={t['subjectId']} "
              f"price={t['price']} status={t['status']}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
