# -*- coding: utf-8 -*-
"""
移动端补齐测试：之前未覆盖的页面
  - 注册页、修改密码页
  - 老师详情、收入、授课日历、我的学生、我的评价
  - 学生课程表、收藏列表
  - 老师角色登录（之前只测过学生）

按 project-rules E2E 附录的 6 个坑规避：
  1. 只认 package="io.dcloud.uniappx" 的节点
  2. tab 用固定坐标（文字不可点）
  3. 优先可点击节点（标题与按钮同名时）
  4. 不用上滑手势（会退到桌面）
  5. 连按 DEL 会退到桌面
  6. 判断前台用 topResumedActivity
"""
import json
import re
import subprocess
import sys
import time
from pathlib import Path

import uiautomator2 as u2

DEVICE = "127.0.0.1:16384"
ADB = r"C:\Program Files\Netease\MuMuPlayer-12.0\shell\adb.exe"
SHOT = Path(r"E:\毕业设计\project\docs\web-e2e-shots")
PKG = "io.dcloud.uniappx"
API = "http://localhost:8080/api"

TAB = {"首页": (135, 1846), "消息": (405, 1846), "我的订单": (675, 1846), "我的": (945, 1846)}

RESULTS = []


def rec(case, ok, detail=""):
    RESULTS.append({"case": case, "ok": ok, "detail": detail})
    mark = "PASS" if ok is True else ("FAIL" if ok is False else "GAP ")
    print(f"[{mark}] {case}" + (f" :: {detail}" if detail else ""), flush=True)


def adb(*a, timeout=25):
    try:
        r = subprocess.run([ADB, "-s", DEVICE] + list(a), capture_output=True,
                           text=True, encoding="utf-8", errors="ignore", timeout=timeout)
        return (r.stdout or "") + (r.stderr or "")
    except subprocess.TimeoutExpired:
        return "__TIMEOUT__"


def app_in_front():
    out = adb("shell", "dumpsys", "activity", "activities")
    for ln in out.splitlines():
        if "topResumedActivity" in ln or "mResumedActivity" in ln:
            return PKG in ln
    return False


def ensure_front():
    if app_in_front():
        return True
    adb("shell", "am", "start", "-n", f"{PKG}/io.dcloud.uniapp.UniAppActivity")
    time.sleep(4)
    return app_in_front()


def dump(d):
    """只返回本 app 的节点（规避坑1：MuMu 商店/桌面/状态栏叠加）"""
    xml = d.dump_hierarchy()
    out = []
    for m in re.finditer(r"<node[^>]*>", xml):
        s = m.group(0)
        pkg = re.search(r'package="([^"]*)"', s)
        if not pkg or pkg.group(1) != PKG:
            continue
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if not b:
            continue
        t = re.search(r'text="([^"]*)"', s)
        c = re.search(r'content-desc="([^"]*)"', s)
        clk = re.search(r'clickable="(\w+)"', s)
        x1, y1, x2, y2 = map(int, b.groups())
        out.append({
            "text": (t.group(1) if t else "") or "",
            "desc": (c.group(1) if c else "") or "",
            "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
            "bounds": (x1, y1, x2, y2),
            "clickable": (clk.group(1) == "true") if clk else False,
        })
    return out


def texts(d):
    return [n["text"] for n in dump(d) if n["text"]]


def tap_text(d, kw, timeout=6, exact=False):
    end = time.time() + timeout
    while time.time() < end:
        if not app_in_front():      # 防止在桌面上误点
            ensure_front()
            time.sleep(1)
        cands = []
        for n in dump(d):
            v, c = n["text"], n["desc"]
            hit = (v == kw or c == kw) if exact else (kw in v or kw in c)
            if hit:
                cands.append(n)
        if cands:
            # 优先可点击（规避坑3：标题与按钮同名）
            clickable = [n for n in cands if n["clickable"]]
            target = clickable[0] if clickable else cands[0]
            d.click(target["cx"], target["cy"])
            return True
        time.sleep(0.5)
    return False


def tap_tab(d, name):
    """tab 用坐标（规避坑2：文字不可点）。
    注意：不要在这里按 back——根页面连按 back 会把 app 退到桌面。
    如果不在 tab 页，只按 1 次 back，然后强制校验前台。"""
    ensure_front()
    t = " ".join(texts(d))
    # 已在某个 tab 页（能看到底部 4 个 tab 文字）就直接点
    on_tab_page = ("首页" in t and "我的" in t)
    if not on_tab_page:
        d.press("back")
        time.sleep(1.2)
        if not app_in_front():
            ensure_front()
        time.sleep(0.8)
    # 再校验一次前台，避免桌面上乱点
    if not app_in_front():
        ensure_front()
        time.sleep(1)
    d.click(*TAB[name])
    time.sleep(2)
    if not app_in_front():
        ensure_front()
        time.sleep(1)
        return False
    return True


def shot(d, name):
    d.screenshot(str(SHOT / f"m-{name}.png"))


def api_login(u, p="123456"):
    import urllib.request
    body = json.dumps({"username": u, "password": p}).encode()
    req = urllib.request.Request(f"{API}/auth/login", data=body, method="POST",
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as r:
        return json.loads(r.read().decode())["data"]


def on_login_page(d):
    """判断是否在登录页。
    依据：同时出现『用户名』+『密码』占位符，且不含底部 tab 文字（tab 页一定不是登录页）。
    注意：不能用『没有账号？去注册』判断——该节点有时不带文本。"""
    t = " ".join(texts(d))
    if "我的订单" in t or "我的" in t and "首页" in t:
        return False
    return ("用户名" in t) and ("密码" in t)


def login(d, username, password="123456"):
    """用固定坐标登录（『登录』标题与按钮同名，坐标最稳）。
    成功判据：必须看到首页特征（底部 tab），且 app 在前台。
    注意：不能只用「不在登录页」判断——app 退到桌面时也满足该条件，会误判成功。"""
    if not ensure_front():
        return False

    if not on_login_page(d):
        print("    [login] 当前不在登录页，先退出登录", flush=True)
        if not logout(d):
            print("    [login] 退出失败", flush=True)
            return False

    if not find_node(d, "用户名"):
        return False

    safe_click(d, LOGIN_USERNAME, "用户名")
    time.sleep(0.6)
    d.send_keys(username)
    time.sleep(0.8)
    safe_click(d, LOGIN_PASSWORD, "密码")
    time.sleep(0.6)
    d.send_keys(password)
    time.sleep(0.8)
    # 点登录前再关一次商店弹窗（实测登录瞬间会被抢占焦点）
    close_mumu_popup(d)
    safe_click(d, LOGIN_BUTTON, "登录按钮")

    # 轮询：必须既在前台、又出现首页特征
    for _ in range(15):
        time.sleep(1)
        if not app_in_front():
            ensure_front()
            continue
        t = " ".join(texts(d))
        if on_login_page(d):
            continue
        if ("首页" in t) or ("我的订单" in t) or ("老师工作台" in t) or ("找家教" in t):
            time.sleep(1.5)
            return True
    return False


def wait_home(d, tutor, timeout=10):
    """轮询等待首页出现预期角色特征"""
    kw = ["老师工作台", "今日课程"] if tutor else ["找家教", "老师"]
    end = time.time() + timeout
    while time.time() < end:
        t = " ".join(texts(d))
        if any(k in t for k in kw):
            return True
        time.sleep(1)
    return False


def find_node(d, kw):
    for n in dump(d):
        if kw in n["text"] or kw in n["desc"]:
            return n
    return None


# 登录页实测坐标（1080x1920）：
#   用户名 (539,546)  密码 (539,697)  登录按钮 (539,881)
#   注意：『登录』有两次——标题 y=38(不可点) 和按钮 y=818(可点)，用坐标最稳
LOGIN_USERNAME = (539, 546)
LOGIN_PASSWORD = (539, 697)
LOGIN_BUTTON = (539, 881)


def close_mumu_popup(d):
    """关闭 MuMu 应用商店/游戏中心遮挡弹窗。
    2026-10-26 实测：点击登录后 2 秒 dump 到的是『热血江湖/仙逆/斗罗大陆』等游戏推荐，
    即 MuMu 商店窗口抢占了焦点，导致登录输入被吞、测试误判。"""
    try:
        xml = d.dump_hierarchy()
        if "com.mumu.store" not in xml:
            return False
        for m in re.finditer(r"<node[^>]*>", xml):
            s = m.group(0)
            if 'package="com.mumu.store"' not in s:
                continue
            for kw in ("取消", "关闭", "以后再说", "稍后"):
                if f'text="{kw}"' in s or f'content-desc="{kw}"' in s:
                    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
                    if b:
                        x1, y1, x2, y2 = map(int, b.groups())
                        d.click((x1 + x2) // 2, (y1 + y2) // 2)
                        time.sleep(1)
                        return True
        d.press("back")
        time.sleep(1)
        return True
    except Exception:
        return False


def safe_click(d, xy, what=""):
    """点击前强制确认 app 在前台且无商店遮挡"""
    if not app_in_front():
        print(f"    [safe_click] {what or xy} 前不在前台，拉回", flush=True)
        ensure_front()
        time.sleep(1.2)
    close_mumu_popup(d)
    if not app_in_front():
        ensure_front()
        time.sleep(1)
    d.click(xy[0], xy[1])
    time.sleep(0.5)


def logout(d):
    """退出登录。返回是否真的回到了登录页。
    注意：这里不用 tap_tab（tap_tab 内部可能触发 login→logout 递归），直接点坐标。"""
    ensure_front()
    safe_click(d, TAB["我的"], "我的tab")
    time.sleep(2.2)
    if not app_in_front():
        ensure_front()
        time.sleep(1)

    ok = tap_text(d, "退出登录", timeout=4)
    if not ok:
        try:
            d(scrollable=True).scroll.to(text="退出登录")
            time.sleep(1)
        except Exception:
            pass
        ok = tap_text(d, "退出登录", timeout=3)
    if not ok:
        print("    [logout] 找不到『退出登录』按钮", flush=True)
        return False

    time.sleep(1.5)
    tap_text(d, "确定", timeout=3)
    time.sleep(3)

    # 关键：校验真的回到登录页，且 app 在前台
    for _ in range(8):
        if not app_in_front():
            ensure_front()          # 退到桌面就拉回来
            time.sleep(1)
            continue
        if on_login_page(d):
            return True
        time.sleep(1)
    print(f"    [logout] 退出后仍在: {' '.join(texts(d))[:60]}", flush=True)
    return False


def main():
    d = u2.connect(DEVICE)
    print(f"连接 {DEVICE}", flush=True)
    ensure_front()

    # ===== 准备：先确保回到登录页，再以 student01 登录 =====
    if not on_login_page(d):
        print("    当前非登录页，先退出登录", flush=True)
        logout(d)
        time.sleep(2)
    if not login(d, "student01"):
        rec("0. 前置登录", False, "无法登录 student01")
        return summary()
    wait_home(d, tutor=False, timeout=10)
    print(f"    当前首页: {' '.join(texts(d))[:70]}", flush=True)

    # ---------- 学生侧 ----------
    # 1. 学生课程表（通过"我的"或首页入口）
    try:
        tap_tab(d, "首页")
        time.sleep(1)
        t = " ".join(texts(d))
        # 学生首页是老师列表，课程表在首页快捷入口
        hit = tap_text(d, "我的课程表", timeout=4) or tap_text(d, "课程表", timeout=3)
        time.sleep(2.5)
        shot(d, "01-student-calendar")
        tt = " ".join(texts(d))
        rec("1. 学生课程表", ("课程" in tt) or ("周" in tt), f"文本: {tt[:80]}")
        if hit:
            d.press("back")
            time.sleep(1.5)
    except Exception as e:
        rec("1. 学生课程表", False, str(e)[:120])

    # 2. 收藏列表
    try:
        ensure_front()
        t = " ".join(texts(d))
        if "我的收藏" in t:
            tap_text(d, "我的收藏", timeout=4)
            time.sleep(2.5)
            shot(d, "02-favorite")
            tt = " ".join(texts(d))
            rec("2. 收藏列表", ("收藏" in tt) or ("老师" in tt), f"文本: {tt[:80]}")
            d.press("back")
            time.sleep(1.5)
        else:
            # 从"我的"页找
            tap_tab(d, "我的")
            time.sleep(1.5)
            ok = tap_text(d, "我的收藏", timeout=4)
            time.sleep(2.5)
            tt = " ".join(texts(d))
            rec("2. 收藏列表", ok and ("收藏" in tt or "老师" in tt), f"文本: {tt[:80]}")
            if ok:
                d.press("back")
                time.sleep(1.5)
    except Exception as e:
        rec("2. 收藏列表", False, str(e)[:120])

    # ---------- 老师侧 ----------
    try:
        ensure_front()
        out = logout(d)
        print(f"    [老师切换] logout={out}", flush=True)
        time.sleep(3)
        logged = login(d, "tutor01")
        print(f"    [老师切换] login={logged}", flush=True)
        if logged:
            # 登录后 app 会 reLaunch 到首页，等足够久让 role 生效
            got = wait_home(d, tutor=True, timeout=15)
            print(f"    [老师切换] 等首页={got}", flush=True)
            time.sleep(2)
        t = " ".join(texts(d))
        rec("3. 老师登录(移动端首次)",
            logged and any(k in t for k in ["老师工作台", "今日课程", "待处理", "收到的预约"]),
            f"logged={logged} 文本: {t[:100]}")
        shot(d, "03-tutor-home")
    except Exception as e:
        rec("3. 老师登录(移动端首次)", False, str(e)[:120])

    tutor_pages = [
        # 老师首页实测显示：老师工作台 / 授课日历 / 我的学生 / 收到的预约 / 学生评价 / 收入统计 / 今日课程 / 待处理预约
        ("4. 老师工作台/首页", ["老师工作台", "工作台", "今日课程", "待处理"], None),
        ("5. 我的家教信息", ["家教信息", "科目", "价格", "发布"], "我的家教"),
        ("6. 我的收入", ["收入", "¥", "元", "统计"], "收入"),
        ("7. 授课日历", ["日历", "课程", "本周"], "日历"),
        ("8. 我的学生", ["学生", "张同学", "共"], "学生"),
        ("9. 我的评价", ["评价", "评分", "暂无"], "评价"),
    ]
    for case, kws, entry in tutor_pages:
        try:
            ensure_front()
            if entry:
                tap_tab(d, "首页")
                time.sleep(1.2)
                ok = tap_text(d, entry, timeout=5)
                time.sleep(2.5)
                shot(d, case.split(".")[0])
                tt = " ".join(texts(d))
                rec(case, ok and any(k in tt for k in kws), f"入口={entry} 文本: {tt[:90]}")
                if ok:
                    d.press("back")
                    time.sleep(1.2)
            else:
                tt = " ".join(texts(d))
                rec(case, any(k in tt for k in kws), f"文本: {tt[:90]}")
        except Exception as e:
            rec(case, False, str(e)[:120])

    # 10. 修改密码页（老师/学生都有）
    try:
        ensure_front()
        tap_tab(d, "我的")
        time.sleep(1.8)
        ok = tap_text(d, "修改密码", timeout=5)
        time.sleep(2.5)
        shot(d, "10-password")
        tt = " ".join(texts(d))
        rec("10. 修改密码页", ok and "密码" in tt, f"文本: {tt[:90]}")
        if ok:
            d.press("back")
            time.sleep(1.2)
    except Exception as e:
        rec("10. 修改密码页", False, str(e)[:120])

    # 11. 注册页（退出后从登录页进）
    try:
        ensure_front()
        logout(d)
        time.sleep(2)
        ok = tap_text(d, "去注册", timeout=5) or tap_text(d, "注册", timeout=3)
        time.sleep(2.5)
        shot(d, "11-register")
        tt = " ".join(texts(d))
        rec("11. 注册页", ok and ("注册" in tt or "手机" in tt or "验证码" in tt),
            f"文本: {tt[:90]}")
        if ok:
            d.press("back")
            time.sleep(1.5)
    except Exception as e:
        rec("11. 注册页", False, str(e)[:120])

    # 12. Vue 警告检查
    try:
        logcat = adb("logcat", "-d", "-t", "300")
        warns = [ln for ln in logcat.splitlines() if "Vue warn" in ln]
        rec("12. 无 Vue 警告", len(warns) == 0,
            f"警告数={len(warns)}" + (f"，例: {warns[0][:100]}" if warns else ""))
    except Exception as e:
        rec("12. 无 Vue 警告", False, str(e)[:100])

    return summary()


def summary():
    print("\n" + "=" * 70)
    p = sum(1 for r in RESULTS if r["ok"] is True)
    f = sum(1 for r in RESULTS if r["ok"] is False)
    g = sum(1 for r in RESULTS if r["ok"] is None)
    print(f"结果: {p} 通过 / {f} 失败 / {g} 缺口（共 {len(RESULTS)}）")
    print("=" * 70)
    out = Path(r"E:\毕业设计\project\docs\mobile-e2e-supplement.json")
    out.write_text(json.dumps({"summary": {"passed": p, "failed": f, "gaps": g,
                                           "total": len(RESULTS)},
                               "cases": RESULTS}, ensure_ascii=False, indent=2),
                   encoding="utf-8")
    print(f"结果: {out}")
    return 0 if f == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
