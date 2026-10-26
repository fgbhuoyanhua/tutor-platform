# -*- coding: utf-8 -*-
"""
为移动端 E2E 补造数据：
1. student01 有「已预约(status=1)」且属于 tutor01 的订单 → 移动端订单页出现「调课」按钮
2. tutor01 给 student01 发一条私信 → 消息列表有会话，可进聊天页
3. 老师给已完成订单写学习报告（已具备）
"""
import json
import urllib.request

API = "http://localhost:8080/api"


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


def login(u, p="123456"):
    r = call("POST", "/auth/login", None, {"username": u, "password": p})
    return r["data"]


def main():
    st = login("student01")
    tt = login("tutor01")
    s, t = st["token"], tt["token"]

    # 1) 造一条 status=1 的订单（学生下单 → 老师接单）
    tl = call("GET", "/tutors?page=1&size=20", s)["data"]["records"]
    tinfo = next(x for x in tl if x["userId"] == tt["userId"])
    orders = call("GET", "/appointments?page=1&size=200", s)["data"]["records"]
    used = {(o["appointDate"], o["timeSlot"]) for o in orders if o["tutorId"] == tt["userId"]}

    slots = ["08:00-10:00", "09:00-11:00", "10:00-12:00",
             "14:00-16:00", "15:00-17:00", "19:00-21:00"]
    dates = ["2027-04-05", "2027-04-12", "2027-04-19"]
    made = 0
    for d in dates:
        for sl in slots:
            if made >= 2:
                break
            if (d, sl) in used:
                continue
            r = call("POST", "/appointments", s, {
                "tutorId": tinfo["id"], "subjectId": tinfo["subjectId"],
                "appointDate": d, "timeSlot": sl})
            if "_http" in r:
                continue
            orders = call("GET", "/appointments?page=1&size=50", s)["data"]["records"]
            cur = max(orders, key=lambda o: o["id"])
            # 老师接单 → status=1
            call("PUT", f"/appointments/{cur['id']}/confirm", t)
            print(f"  已造已预约单 {cur['orderNo']} id={cur['id']} {d} {sl}")
            made += 1
        if made >= 2:
            break

    # 2) tutor01 给 student01 发私信
    r = call("POST", "/messages", t, {
        "toId": st["userId"],
        "content": "家长您好，关于下次上课的时间安排，我想和您确认一下。"})
    print("发私信:", json.dumps(r, ensure_ascii=False)[:150])

    # 3) 确认消息列表
    msgs = call("GET", "/messages", s)
    data = msgs.get("data")
    print("消息返回类型:", type(data).__name__,
          "条数:", len(data) if isinstance(data, list) else data)

    # 4) 确认订单状态分布
    orders = call("GET", "/appointments?page=1&size=200", s)["data"]["records"]
    from collections import Counter
    c = Counter(o["status"] for o in orders)
    print("订单状态分布:", dict(c))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
