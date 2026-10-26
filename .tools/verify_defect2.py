# -*- coding: utf-8 -*-
"""
DEFECT-2 专项回归：同一老师 / 同一日期 / 同一时段
连续完成两笔订单（复购场景），验证「确认完成」不再抛 uk_slot 冲突。

修复前：第二笔 order.finish() 抛
        Duplicate entry '<tutor>-<date>-<slot>-3' for key 'uk_slot'
修复后：两笔都能正常流转到 已完成(3)
"""
import json
import sys
import time
import urllib.request

API = "http://localhost:8080/api"


def call(method, path, token, body=None, expect_error=False):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        f"{API}{path}", data=data, method=method,
        headers={"Authorization": f"Bearer {token}",
                 "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        if expect_error:
            return {"_http": e.code, "_body": e.read().decode()[:200]}
        raise


def login(u):
    return call("POST", "/auth/login", None,
                {"username": u, "password": "123456"})["data"]


def main():
    student = login("student01")
    tutor = login("tutor01")
    st, tt = student["token"], tutor["token"]

    # 注意：AppointmentCreateDTO.tutorId 是【家教信息表 tutor.id】，不是 user.id
    tl = call("GET", "/tutors?page=1&size=20", st)["data"]["records"]
    tinfo = next((t for t in tl if t["userId"] == tutor["userId"]), None)
    if not tinfo:
        print("找不到 tutor01 的家教信息，退出")
        return 1
    tutorInfoId = tinfo["id"]
    subjectId = 2
    print(f"tutor01 -> 家教信息id={tutorInfoId} 科目id={subjectId}", flush=True)

    # 找一个 tutor01 完全没有订单的日期，避免既有数据干扰
    orders = call("GET", "/appointments?page=1&size=200", st)["data"]["records"]
    used = {o["appointDate"] for o in orders if o["tutorId"] == tutor["userId"]}
    date = None
    for d in ["2027-01-05", "2027-01-12", "2027-01-19", "2027-02-02", "2027-02-09"]:
        if d not in used:
            date = d
            break
    if not date:
        print("找不到空闲日期，退出")
        return 1
    slot = "15:00-17:00"
    print(f"测试时段: 老师(userId)={tutor['userId']} 日期={date} 时段={slot}", flush=True)

    created = []
    # 复购场景：同一老师/同一日期/同一时段，连续完成两笔
    for round_no in (1, 2):
        print(f"\n--- 第 {round_no} 笔 ---", flush=True)
        try:
            call("POST", "/appointments", st,
                 {"tutorId": tutorInfoId, "appointDate": date,
                  "timeSlot": slot, "subjectId": subjectId})

            orders = call("GET", "/appointments?page=1&size=50", st)["data"]["records"]
            cur = max(orders, key=lambda o: o["id"])
            oid = cur["id"]
            created.append(oid)
            print(f"  下单 OK {cur['orderNo']} id={oid} status={cur['status']}", flush=True)

            call("PUT", f"/appointments/{oid}/confirm", tt)
            print("  接单 OK (0→1)", flush=True)
            call("PUT", f"/appointments/{oid}/start", tt)
            print("  开始授课 OK (1→2)", flush=True)
            call("PUT", f"/appointments/{oid}/finish", st)
            print("  确认完成 OK (2→3)  <-- 修复前这里会 uk_slot 冲突", flush=True)
        except urllib.error.HTTPError as e:
            body = e.read().decode()[:300]
            print(f"  ❌ HTTP {e.code}: {body}", flush=True)
            return 1

    # 校验两笔都已完成
    orders = call("GET", "/appointments?page=1&size=200", st)["data"]["records"]
    finals = [o for o in orders if o["id"] in created]
    print(f"\n最终状态:", flush=True)
    for o in finals:
        print(f"  id={o['id']} status={o['status']} {o['appointDate']} {o['timeSlot']}",
              flush=True)

    ok = len(finals) == 2 and all(o["status"] == 3 for o in finals)
    print(f"\n{'✅ DEFECT-2 修复验证通过' if ok else '❌ 验证失败'}", flush=True)
    print(f"   同一老师/同一日期/同一时段 两笔均已完成 = {ok}", flush=True)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
