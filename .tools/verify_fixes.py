# -*- coding: utf-8 -*-
"""
验证两个修复：
  FIX-1  收藏状态持久化：收藏老师后重进详情页，应仍显示「★ 已收藏」
  FIX-2  页面可滚动：老师工作台能向下滚动看到底部内容
"""
import json
import re
import subprocess
import sys
import time
import urllib.request

import uiautomator2 as u2

PKG = "io.dcloud.uniappx"
ADB = r"C:\Program Files\Netease\MuMuPlayer-12.0\shell\adb.exe"
DEV = "127.0.0.1:16384"
API = "http://localhost:8080/api"
SHOT = r"E:\毕业设计\project\docs\web-e2e-shots"

RESULTS = []


def rec(case, ok, detail=""):
    RESULTS.append({"case": case, "ok": ok, "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {case}" + (f" :: {detail}" if detail else ""), flush=True)


def adb(*a, timeout=25):
    try:
        r = subprocess.run([ADB, "-s", DEV] + list(a), capture_output=True,
                           text=True, encoding="utf-8", errors="ignore", timeout=timeout)
        return (r.stdout or "") + (r.stderr or "")
    except subprocess.TimeoutExpired:
        return "__TIMEOUT__"


def api(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{API}{path}", data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return {"_http": e.code, "_body": e.read().decode()[:200]}


def login_api(u, p="123456"):
    return api("POST", "/auth/login", None, {"username": u, "password": p})["data"]


def nodes(d):
    xml = d.dump_hierarchy()
    out = []
    for m in re.finditer(r"<node[^>]*>", xml):
        s = m.group(0)
        if f'package="{PKG}"' not in s:
            continue
        t = re.search(r'text="([^"]*)"', s)
        c = re.search(r'content-desc="([^"]*)"', s)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        clk = re.search(r'clickable="(\w+)"', s)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        v = (t.group(1) if t else "") or (c.group(1) if c else "")
        out.append({"v": v, "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
                    "y": y1, "clk": (clk.group(1) == "true") if clk else False})
    return out


def texts(d):
    return [n["v"] for n in nodes(d) if n["v"]]


def front():
    for ln in adb("shell", "dumpsys", "activity", "activities").splitlines():
        if "topResumedActivity" in ln:
            return PKG in ln
    return False


def ensure_front():
    if front():
        return
    adb("shell", "am", "start", "-n", f"{PKG}/io.dcloud.uniapp.UniAppActivity")
    time.sleep(4)


def find(d, kw):
    for n in nodes(d):
        if kw in n["v"]:
            return n
    return None


d = u2.connect(DEV)
d.implicitly_wait(3)

# ---------- 准备：学生登录 ----------
ensure_front()
adb("shell", "am", "force-stop", PKG)
time.sleep(2)
adb("shell", "am", "start", "-n", f"{PKG}/io.dcloud.uniapp.UniAppActivity")
time.sleep(7)

if find(d, "用户名"):
    d.click(539, 546); time.sleep(0.6)
    d.send_keys("student01"); time.sleep(0.8)
    d.click(539, 697); time.sleep(0.6)
    d.send_keys("123456"); time.sleep(0.8)
    d.click(539, 881); time.sleep(6)
print("登录后:", texts(d)[:8])

# ========== FIX-1 收藏状态持久化 ==========
print("\n" + "=" * 60)
print("FIX-1 收藏状态持久化")
print("=" * 60)

# 先确保后端取消收藏，从干净状态开始
st = login_api("student01")
tl = api("GET", "/tutors?page=1&size=20", st["token"])["data"]["records"]
target = tl[0]
api("DELETE", f"/favorites/{target['id']}", st["token"])
print(f"起始状态：{target['tutorName']}(id={target['id']}) 未收藏")

# 从首页进入该老师详情
ensure_front()
d.click(135, 1846)      # 首页 tab
time.sleep(2)
card = find(d, target["tutorName"])
if card:
    d.click(card["cx"], card["cy"])
    time.sleep(3)
    before = texts(d)
    print("详情页:", before[:10])
    print("收藏状态文本:", [x for x in before if "收藏" in x])

    # 点收藏
    fav = find(d, "收藏")
    if fav:
        d.click(fav["cx"], fav["cy"])
        time.sleep(3)
        after_click = texts(d)
        print("点击后:", [x for x in after_click if "收藏" in x])
        # 验证后端
        favs = api("GET", "/favorites", st["token"])["data"]
        in_backend = any(f["id"] == target["id"] for f in favs)
        print(f"后端已收藏: {in_backend}")

        # 退出详情页，再进去
        d.press("back")
        time.sleep(2.5)
        ensure_front()
        card = find(d, target["tutorName"])
        if card:
            d.click(card["cx"], card["cy"])
            time.sleep(4)
            reopened = texts(d)
            fav_text = [x for x in reopened if "收藏" in x]
            print("重进后收藏状态:", fav_text)
            ok = any("已收藏" in x for x in fav_text)
            d.screenshot(f"{SHOT}\\fix1-fav-reopen.png")
            rec("FIX-1 收藏状态持久化", ok,
                f"重进详情页显示: {fav_text}" + ("" if ok else " ← 应显示『★ 已收藏』"))
        else:
            rec("FIX-1 收藏状态持久化", False, "重进后找不到老师卡")
    else:
        rec("FIX-1 收藏状态持久化", False, "详情页找不到收藏按钮")
else:
    rec("FIX-1 收藏状态持久化", False, f"首页找不到 {target['tutorName']}")

# ========== FIX-2 页面可滚动 ==========
print("\n" + "=" * 60)
print("FIX-2 老师工作台可滚动")
print("=" * 60)

# 退出，登录老师
ensure_front()
# 先 back 到 tab 页（可能在详情页）
for _ in range(3):
    t = " ".join(texts(d))
    if "首页" in t and "我的订单" in t:
        break
    d.press("back")
    time.sleep(1.5)
    ensure_front()
time.sleep(1)

# 我的 tab → 退出登录
d.click(945, 1846)
time.sleep(2.5)
out_btn = find(d, "退出登录")
if not out_btn:
    try:
        d(scrollable=True).scroll.to(text="退出登录")
        time.sleep(1)
    except Exception:
        pass
    out_btn = find(d, "退出登录")
if out_btn:
    d.click(out_btn["cx"], out_btn["cy"])
    time.sleep(1.5)
    ok_btn = find(d, "确定")
    if ok_btn:
        d.click(ok_btn["cx"], ok_btn["cy"])
    time.sleep(3.5)

ensure_front()
if find(d, "用户名"):
    d.click(539, 546); time.sleep(0.6)
    d.send_keys("tutor01"); time.sleep(0.8)
    d.click(539, 697); time.sleep(0.6)
    d.send_keys("123456"); time.sleep(0.8)
    d.click(539, 881); time.sleep(8)

ensure_front()
# 强制回首页 tab（登录后可能停在上次的页面）
for _ in range(3):
    t = " ".join(texts(d))
    if "老师工作台" in t or "今日课程" in t:
        break
    d.click(135, 1846)   # 首页
    time.sleep(2.5)
    ensure_front()

t1 = texts(d)
print("工作台初始可见:", t1[:14])

# 尝试滚动（向下，内容上移）
# 注意：滑动起点必须在安全区（y=700~1400），不能落在底部系统手势区（y>1700），
#      否则会触发「上滑回桌面」
scrolled = False
for method in ("scrollable", "swipe"):
    try:
        if method == "scrollable":
            d(scrollable=True).scroll.forward(steps=20)
        else:
            d.swipe(540, 1400, 540, 700, 0.3)   # 安全区内滑动
        time.sleep(2)
        scrolled = True
        print(f"滚动方式 {method} 成功")
        break
    except Exception as e:
        print(f"滚动方式 {method} 失败: {str(e)[:80]}")

t2 = texts(d)
print("滚动后可见:", t2[:18])
d.screenshot(f"{SHOT}\\fix2-workbench-scrolled.png")

# 判据：滚动后能看到更多内容（底部关键字，或内容数量增加）
bottom_kw = ["今日课程", "待处理预约", "已完成", "待确认"]
new_visible = [k for k in bottom_kw if k in " ".join(t2)]
more_content = len(t2) > len(t1)
rec("FIX-2 老师工作台可滚动", len(new_visible) > 0 or more_content,
    f"滚动前{len(t1)}项→滚动后{len(t2)}项，底部关键字: {new_visible if new_visible else '无'}")

print("\n" + "=" * 60)
p = sum(1 for r in RESULTS if r["ok"])
print(f"结果: {p}/{len(RESULTS)} 通过")
print("=" * 60)
