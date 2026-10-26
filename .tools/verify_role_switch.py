# -*- coding: utf-8 -*-
"""
验证缺陷：切换账号后首页角色不更新

场景：student01 登录 → 退出 → tutor01 登录 → 首页应显示「老师工作台」
实测：仍显示「找家教」（学生首页）
对照：冷启动 app 直接登录 tutor01 → 正常显示「老师工作台」

判断依据：getUser() 读的是本地存储，若退出时未清或登录时未覆盖，会读到旧角色
"""
import re
import subprocess
import time

import uiautomator2 as u2

PKG = "io.dcloud.uniappx"
ADB = r"C:\Program Files\Netease\MuMuPlayer-12.0\shell\adb.exe"

# 设备端口由 MuMuManager 实测得出（端口会浮动，别写死）
try:
    from deploy_mobile import resolve_serial
    DEV = resolve_serial()
except Exception:
    DEV = "127.0.0.1:16384"
USERNAME = (539, 546)
PASSWORD = (539, 697)
LOGIN_BTN = (539, 881)
TAB_ME = (945, 1846)


def adb(*a):
    r = subprocess.run([ADB, "-s", DEV] + list(a), capture_output=True,
                       text=True, encoding="utf-8", errors="ignore", timeout=25)
    return (r.stdout or "") + (r.stderr or "")


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


def values(d):
    xml = d.dump_hierarchy()
    out = []
    for m in re.finditer(r"<node[^>]*>", xml):
        s = m.group(0)
        if f'package="{PKG}"' not in s:
            continue
        t = re.search(r'text="([^"]*)"', s)
        c = re.search(r'content-desc="([^"]*)"', s)
        v = (t.group(1) if t else "") or (c.group(1) if c else "")
        if v:
            out.append(v)
    return out


def find(kw):
    for m in re.finditer(r"<node[^>]*>", d.dump_hierarchy()):
        s = m.group(0)
        if f'package="{PKG}"' not in s:
            continue
        if f'"{kw}"' in s:
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                return ((x1 + x2) // 2, (y1 + y2) // 2)
    return None


def do_login(user):
    ensure_front()
    pos = find("用户名")
    if not pos:
        return False
    d.click(*USERNAME)
    time.sleep(0.5)
    d.send_keys(user)
    time.sleep(0.7)
    d.click(*PASSWORD)
    time.sleep(0.5)
    d.send_keys("123456")
    time.sleep(0.7)
    d.click(*LOGIN_BTN)
    time.sleep(5)
    return True


def do_logout():
    ensure_front()
    # 点「我的」tab
    d.click(*TAB_ME)
    time.sleep(2)
    pos = find("退出登录")
    if not pos:
        try:
            d(scrollable=True).scroll.to(text="退出登录")
            time.sleep(1)
            pos = find("退出登录")
        except Exception:
            pass
    if not pos:
        return False
    d.click(*pos)
    time.sleep(1.5)
    pos = find("确定")
    if pos:
        d.click(*pos)
    time.sleep(3)
    return True


d = u2.connect(DEV)

print("=" * 60)
print("测试 A：冷启动 → 直接登录 tutor01")
print("=" * 60)
adb("shell", "am", "force-stop", PKG)
time.sleep(2)
adb("shell", "am", "start", "-n", f"{PKG}/io.dcloud.uniapp.UniAppActivity")
time.sleep(5)
do_login("tutor01")
time.sleep(3)
va = values(d)
print("首页文本:", va[:14])
print("→ 包含『老师工作台』:", any("老师工作台" in v for v in va))

print()
print("=" * 60)
print("测试 B：退出 → 登录 student01 → 退出 → 登录 tutor01")
print("=" * 60)
print("退出:", do_logout())
time.sleep(2)
do_login("student01")
time.sleep(3)
vb1 = values(d)
print("student01 首页:", vb1[:8])

print("退出:", do_logout())
time.sleep(2)
do_login("tutor01")
time.sleep(6)
vb2 = values(d)
print("tutor01 首页:", vb2[:14])
print("→ 包含『老师工作台』:", any("老师工作台" in v for v in vb2))

print()
print("=" * 60)
print("结论")
print("=" * 60)
a_ok = any("老师工作台" in v for v in va)
b_ok = any("老师工作台" in v for v in vb2)
print(f"冷启动登录老师 → 工作台正常: {a_ok}")
print(f"切换账号后登录老师 → 工作台正常: {b_ok}")
if a_ok and not b_ok:
    print("❌ 确认缺陷：切换账号后首页角色不刷新")
elif a_ok and b_ok:
    print("✅ 两种方式都正常")
else:
    print("⚠️ 需要进一步排查")
