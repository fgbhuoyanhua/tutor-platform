# -*- coding: utf-8 -*-
"""
为回归测试补造前置数据：
  - 若干 status=0 待确认订单（供「学生取消」「老师拒绝」用）
  - 若干 status=1 已预约订单（供「申请调课」用）
"""
import json
import urllib.request

API = "http://localhost:8080/api"


def call(method, path, token, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        f"{API}{path}", data=data, method=method,
        headers={"Authorization": f"Bearer {token}",
                 "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.loads(r.read().decode())


def login(u):
    return call("POST", "/auth/login", None,
                {"username": u, "password": "123456"})["data"]


def main():
    student = login("student01")
    tutor = login("tutor01")
    st, tt = student["token"], tutor["token"]

    tl = call("GET", "/tutors?page=1&size=20", st)["data"]["records"]
    tinfo = next(t for t in tl if t["userId"] == tutor["userId"])
    tid, sid = tinfo["id"], tinfo["subjectId"]

    orders = call("GET", "/appointments?page=1&size=200", st)["data"]["records"]
    used = {(o["appointDate"], o["timeSlot"]) for o in orders
            if o["tutorId"] == tutor["userId"]}

    slots = ["08:00-10:00", "09:00-11:00", "10:00-12:00", "14:00-16:00",
             "15:00-17:00", "19:00-21:00"]
    dates = ["2027-03-03", "2027-03-10", "2027-03-17", "2027-03-24"]

    def free_slot():
        for d in dates:
            for s in slots:
                if (d, s) not in used:
                    used.add((d, s))
                    return d, s
        return None, None

    created = []
    # 3 条待确认（供取消/拒绝）
    for _ in range(3):
        d, s = free_slot()
        if not d:
            break
        call("POST", "/appointments", st,
             {"tutorId": tid, "subjectId": sid, "appointDate": d, "timeSlot": s})
        orders = call("GET", "/appointments?page=1&size=50", st)["data"]["records"]
        cur = max(orders, key=lambda o: o["id"])
        created.append((cur["id"], cur["status"]))
        print(f"  待确认单 {cur['orderNo']} id={cur['id']} {d} {s}", flush=True)

    # 2 条已预约（供调课：需老师接单 → status=1）
    for _ in range(2):
        d, s = free_slot()
        if not d:
            break
        call("POST", "/appointments", st,
             {"tutorId": tid, "subjectId": sid, "appointDate": d, "timeSlot": s})
        orders = call("GET", "/appointments?page=1&size=50", st)["data"]["records"]
        cur = max(orders, key=lambda o: o["id"])
        call("PUT", f"/appointments/{cur['id']}/confirm", tt)
        created.append((cur["id"], 1))
        print(f"  已预约单 {cur['orderNo']} id={cur['id']} {d} {s}", flush=True)

    print(f"\n共补造 {len(created)} 条订单", flush=True)

    # 校验
    orders = call("GET", "/appointments?page=1&size=200", st)["data"]["records"]
    p0 = [o for o in orders if o["status"] == 0]
    p1 = [o for o in orders if o["status"] == 1 and o["tutorId"] == tutor["userId"]]
    print(f"当前 status=0 数量: {len(p0)}", flush=True)
    print(f"当前 status=1(tutor01) 数量: {len(p1)}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
