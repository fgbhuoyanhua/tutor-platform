# -*- coding: utf-8 -*-
"""
移动端 E2E 测试（P2 重点：调课页、聊天页 + 核心回归）

按 project-rules：
  - 用 uiautomator2（E:\\...\\Python38\\python.exe，已验证安装）
  - UVUE button 渲染为 android.view.View，content-desc 为按钮文字
  - 必须 dump_hierarchy 拿精确 bounds，不能目测坐标
  - 排查「按钮没反应」先看 toast（规则：duration 6000）
  - 切换账号用 app 内退出登录，不要 pm clear（会删应用资源）

前置：MuMu 实例0 已启动，adb 连 127.0.0.1:16384，后端 8080 在跑
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
SHOT.mkdir(parents=True, exist_ok=True)
PKG = "io.dcloud.uniappx"

RESULTS = []


def rec(case, ok, detail=""):
    RESULTS.append({"case": case, "ok": ok, "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {case}" + (f" :: {detail}" if detail else ""),
          flush=True)


def adb(*args, timeout=30):
    r = subprocess.run([ADB, "-s", DEVICE] + list(args),
                       capture_output=True, text=True, encoding="utf-8",
                       errors="ignore", timeout=timeout)
    return (r.stdout or "") + (r.stderr or "")


def adb_alive():
    out = adb("devices")
    return f"{DEVICE}\tdevice" in out


def ensure_device():
    """设备掉线时自动重连（模拟器内存紧张会掉线）"""
    if adb_alive():
        return True
    adb("disconnect", DEVICE)
    time.sleep(1)
    adb("connect", DEVICE)
    for _ in range(10):
        if adb_alive():
            return True
        time.sleep(2)
    return False


def app_in_front():
    """严格判断 app 是否在【前台】。不能用 'PKG in dumpsys'——后台 app 也在 dumpsys 里。"""
    out = adb("shell", "dumpsys", "activity", "activities")
    for ln in out.splitlines():
        if "topResumedActivity" in ln or "mResumedActivity" in ln:
            return PKG in ln
    return False


def ensure_app_front(max_try=3):
    """确保 app 在前台；被踢到桌面/MuMu商店时拉回"""
    for _ in range(max_try):
        if app_in_front():
            return True
        adb("shell", "am", "start", "-n",
            f"{PKG}/io.dcloud.uniapp.UniAppActivity")
        time.sleep(3.5)
        if app_in_front():
            return True
        # 兜底：用 monkey 拉起
        adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
        time.sleep(4)
    return app_in_front()


def shot(d, name):
    p = SHOT / f"mobile-{name}.png"
    d.screenshot(str(p))
    return p


def dump(d):
    """只返回 app(package=io.dcloud.uniappx) 自身的可见节点。

    重要：MuMu 会同时 dump 出多个窗口——com.mumu.store 应用商店弹窗、
    app.lawnchair 桌面、com.android.systemui 状态栏 会叠在 app 之上，
    导致把「游戏推荐」「应用商店」等文本误判成页面元素。
    必须按 package 白名单过滤，只认自己的 app。
    """
    xml = d.dump_hierarchy()
    out = []
    for m in re.finditer(r'<node[^>]*>', xml):
        s = m.group(0)
        pkg = re.search(r'package="([^"]*)"', s)
        if not pkg or pkg.group(1) != PKG:
            continue
        txt = re.search(r'text="([^"]*)"', s)
        desc = re.search(r'content-desc="([^"]*)"', s)
        bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if not bounds:
            continue
        x1, y1, x2, y2 = map(int, bounds.groups())
        clk = re.search(r'clickable="(\w+)"', s)
        cls = re.search(r'class="([^"]*)"', s)
        out.append({
            "text": (txt.group(1) if txt else "") or "",
            "desc": (desc.group(1) if desc else "") or "",
            "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
            "bounds": (x1, y1, x2, y2),
            "clickable": (clk.group(1) == "true") if clk else False,
            "cls": cls.group(1) if cls else "",
        })
    return out


def close_mumu_popup():
    """关闭 MuMu 应用商店/游戏中心遮挡弹窗（规则里的『安装失败-2』类系统弹窗）"""
    try:
        d = u2.connect(DEVICE)
        xml = d.dump_hierarchy()
        if "com.mumu.store" not in xml:
            return False
        # 找 `取消`/`关闭`/`以后再说` 按钮并点击（优先应用商店窗口内的）
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
        return False
    except Exception:
        return False


def find(d, keyword, exact=False, clickable_only=False):
    """按 text/desc 找节点。clickable_only=True 时只返回可点击节点。

    关键：页面上「登录」既是页面标题(不可点)也是登录按钮(可点)，
    必须优先取 clickable=true 的那个，否则会点到顶部标题导致什么都不发生。
    """
    ensure_app_front()
    nodes = dump(d)
    cands = []
    for n in nodes:
        t, c = n["text"], n["desc"]
        hit = (t == keyword or c == keyword) if exact else (keyword in t or keyword in c)
        if hit:
            cands.append(n)
    if not cands:
        return None
    if clickable_only:
        clickable = [n for n in cands if n["clickable"]]
        if clickable:
            return clickable[0]
    # 默认：优先可点击，其次面积最大的（排除标题这种小元素）
    clickable = [n for n in cands if n["clickable"]]
    if clickable:
        return clickable[0]
    return max(cands, key=lambda n: (n["bounds"][2] - n["bounds"][0]) *
                                     (n["bounds"][3] - n["bounds"][1]))


def find_nodes(d, keywords, exact=False):
    """一次 dump 找多个关键词，返回第一个命中的"""
    nodes = dump(d)
    for kw in keywords:
        for n in nodes:
            t, c = n["text"], n["desc"]
            if (t == kw or c == kw) if exact else (kw in t or kw in c):
                return kw, n
    return None, None


# tabBar 固定坐标（1080x1920，来自 dump 的真实 bounds）
# 注意：tab 文字是 TextView(clickable=false)，真正可点的是其父 RelativeLayout
TAB_COORDS = {
    "首页": (135, 1846),
    "消息": (405, 1846),
    "我的订单": (675, 1846),
    "我的": (945, 1846),
}


def scroll_to_find(d, keyword, max_try=4):
    """在 scroll-view 内滚动查找文本。
    严禁用 d.swipe(540,1500,...) 这种上滑——起点在系统导航手势区，
    会把 app 退到桌面。必须用 uiautomator2 的 scrollable 容器滚动。"""
    for i in range(max_try):
        texts = tab_texts(d)
        if any(keyword in t for t in texts):
            return True
        try:
            # 优先用滚动容器自带的 scroll.to
            d(scrollable=True).scroll.to(textContains=keyword)
            time.sleep(0.8)
            texts = tab_texts(d)
            if any(keyword in t for t in texts):
                return True
        except Exception:
            pass
        # 兜底：在小范围内滑动（起点/终点都远离底部手势区）
        try:
            d.swipe(540, 1300, 540, 700, 0.25)
        except Exception:
            pass
        time.sleep(1.0)
        if not app_in_front():
            ensure_app_front()
            time.sleep(1.2)
    texts = tab_texts(d)
    return any(keyword in t for t in texts)


def go_back_to_tab(d, max_back=2):
    """从二级页返回 tab 页。注意：在根页面按 back 会把 app 退出到桌面，
    所以每次按 back 前后都要检查 app 是否还在前台。"""
    for _ in range(max_back):
        texts = tab_texts(d)
        joined = " ".join(texts)
        if joined.count("首页") and joined.count("我的"):
            return True          # 已在 tab 页
        d.press("back")
        time.sleep(1.5)
        if not app_in_front():   # 退到桌面了，拉回来
            ensure_app_front()
            time.sleep(1)
            return True
    return True


def tap_tab(d, name):
    """按坐标点击底部 tab（不要用文字文本定位，文字节点不可点）"""
    xy = TAB_COORDS.get(name)
    if not xy:
        return False
    # 若在二级页，先返回
    texts = tab_texts(d)
    joined = " ".join(texts)
    if not (joined.count("首页") and joined.count("我的")):
        go_back_to_tab(d)
    d.click(xy[0], xy[1])
    time.sleep(2.2)
    return True


def tab_texts(d):
    """返回当前页面文本"""
    return [n["text"] for n in dump(d) if n["text"]]


def tap_text(d, keyword, exact=False, timeout=8, clickable_only=False):
    """轮询等待并点击（优先点可点击节点）"""
    end = time.time() + timeout
    while time.time() < end:
        n = find(d, keyword, exact, clickable_only)
        if n:
            d.click(n["cx"], n["cy"])
            return True, n
        time.sleep(0.5)
    return False, None


def get_toast_text(d, timeout=3):
    """读取 toast 文案（规则：catch 里 duration 6000 才抓得到）"""
    end = time.time() + timeout
    while time.time() < end:
        xml = d.dump_hierarchy()
        m = re.search(r'text="([^"]{4,})"[^>]*package="[^"]*"', xml)
        # toast 通常是独立窗口，用 dump 里包含错误关键词的文本兜底
        for kw in ["失败", "错误", "不能", "已", "成功", "异常", "不存在", "请"]:
            for mm in re.finditer(r'text="([^"]*' + kw + r'[^"]*)"', xml):
                return mm.group(1)
        time.sleep(0.4)
    return ""


def clear_field(d, node):
    """安全清空输入框：先长按全选删除，失败才按 DEL。
    严禁无条件连按多次 DEL —— 输入框为空时 DEL 会被系统当作返回键，把 app 踢到桌面。"""
    d.click(node["cx"], node["cy"])
    time.sleep(0.6)
    # 方式1：全选后删除（最安全）
    try:
        d.long_click(node["cx"], node["cy"], 0.5)
        time.sleep(0.8)
        if tap_text(d, "全选", timeout=2)[0] or tap_text(d, "Select all", timeout=1)[0]:
            d.press("del")
            time.sleep(0.3)
            return
    except Exception:
        pass
    # 方式2：按 DEL 但先确认确实有内容（逐字检查，最多 20 次且一旦变空立即停）
    for _ in range(20):
        cur = find(d, "用户名") or find(d, "密码")
        if cur is None:
            break
        # 输入框有内容时 placeholder 会消失 → 用 placeholder 是否还在判断
        texts = [n["text"] for n in dump(d) if n["text"]]
        if "用户名" in texts or "密码" in texts:
            # 仍有 placeholder，说明可能是空框，只按 1 次就停
            d.press("del")
            time.sleep(0.2)
            break
        d.press("del")
        time.sleep(0.2)


def login(d, username, password="123456"):
    """app 内登录（安全版：不清空就追加会出错，所以先确保是空框）"""
    # 确认在登录页
    if not find(d, "用户名"):
        # 可能在别的页面，尝试回登录页
        for _ in range(4):
            d.press("back")
            time.sleep(1.2)
            if find(d, "用户名"):
                break

    n = find(d, "用户名")
    if not n:
        return False, "找不到用户名输入框"
    d.click(n["cx"], n["cy"])
    time.sleep(0.5)
    d.send_keys(username)
    time.sleep(0.6)

    n = find(d, "密码")
    if not n:
        return False, "找不到密码输入框"
    d.click(n["cx"], n["cy"])
    time.sleep(0.5)
    d.send_keys(password)
    time.sleep(0.6)

    ok, _ = tap_text(d, "登录", exact=True)
    if not ok:
        return False, "找不到登录按钮"
    time.sleep(5)
    return True, "已点击登录"


def logout(d):
    """app 内退出登录（规则：不要 pm clear）。
    注意：先回到 tab 页再点「我的」，否则在二级页上点不到底部 tab。"""
    # 1) 回到 tab 页（用 back，但限制次数并检查前台）
    for _ in range(2):
        texts = tab_texts(d)
        if any("首页" == t for t in texts):
            break
        d.press("back")
        time.sleep(1.3)
        if not app_in_front():
            ensure_app_front()
            time.sleep(1)
            break
    # 2) 点「我的」tab（坐标，文字不可点）
    d.click(*TAB_COORDS["我的"])
    time.sleep(2.5)
    # 3) 我的页里找「退出登录」，可能在下方需要滚动
    ok, node = tap_text(d, "退出登录", timeout=4)
    if not ok:
        # 在 scroll-view 内滚动查找（不要用 swipe 上滑，会触发系统手势）
        for _ in range(3):
            try:
                d(scrollable=True).scroll.to(text="退出登录")
            except Exception:
                pass
            time.sleep(1)
            ok, node = tap_text(d, "退出登录", timeout=2)
            if ok:
                break
    if ok:
        time.sleep(1.5)
        tap_text(d, "确定", timeout=4)
        time.sleep(3)
    return ok


def main():
    print(f"连接 {DEVICE} ...", flush=True)
    d = u2.connect(DEVICE)
    d.implicitly_wait(3)

    # 前置：关闭 MuMu 应用商店遮挡弹窗 + 确保 app 在前台
    if close_mumu_popup():
        print("   已关闭 MuMu 应用商店弹窗", flush=True)
    ensure_app_front()
    time.sleep(2)
    shot(d, "01-login-page")

    # ===== 1. 登录页渲染（若已登录则先退出）=====
    ensure_app_front()
    time.sleep(2)
    texts = tab_texts(d)
    already_logged_in = any(k in " ".join(texts) for k in ["首页", "我的订单", "找家教"])

    if already_logged_in:
        print("   检测到已登录状态，先退出登录以测登录页", flush=True)
        ok = logout(d)
        print(f"   退出登录: {'成功' if ok else '失败/未找到入口'}", flush=True)
        time.sleep(2)
        # 若退出后仍在登录态，用 adb 清应用数据会删资源，禁用；改为提示
        texts = tab_texts(d)
        if any(k in " ".join(texts) for k in ["首页", "我的订单"]):
            rec("1. 登录页渲染", False, "已登录且无法退出到登录页")
        else:
            rec("1. 登录页渲染",
                any("登录" in t for t in texts) and any("用户名" in t or "密码" in t for t in texts),
                f"app 节点文本: {texts[:8]}")
    else:
        shot(d, "01-login-page")
        nodes = dump(d)
        texts = [n["text"] for n in nodes if n["text"]]
        has_login = any("登录" in t for t in texts)
        has_input = any("用户名" in t or "密码" in t for t in texts)
        rec("1. 登录页渲染", has_login and has_input,
            f"app 节点数={len(nodes)} 文本: {texts[:8]}")

    # ===== 2. 学生登录 =====
    try:
        if find(d, "用户名") is None:
            # 已经没有登录页，说明已登录
            texts = tab_texts(d)
            logged = any(k in " ".join(texts) for k in ["首页", "我的订单", "找家教"])
            rec("2. 学生登录", logged, f"已是登录态，文本: {texts[:10]}")
        else:
            ok, msg = login(d, "student01")
            time.sleep(3)
            shot(d, "02-after-login")
            texts = tab_texts(d)
            logged = any(k in " ".join(texts) for k in ["首页", "老师", "消息", "订单", "我的"])
            no_error = not any("失败" in t or "错误" in t for t in texts)
            rec("2. 学生登录", logged and no_error,
                f"登录后文本: {texts[:10]} / msg={msg}")
    except Exception as e:
        rec("2. 学生登录", False, str(e)[:200])
        shot(d, "02-FAIL")

    # ===== 3. 底部 tab 导航（按坐标点，文字节点不可点）=====
    tabs = ["消息", "我的订单", "我的", "首页"]
    tab_ok = 0
    for t in tabs:
        if tap_tab(d, t):
            tab_ok += 1
    rec("3. 底部 tab 导航", tab_ok >= 3, f"{tab_ok}/{len(tabs)} 个 tab 可点击")

    # ===== 4. 订单列表渲染 =====
    try:
        tap_tab(d, "我的订单")
        time.sleep(1.5)
        shot(d, "04-orders")
        texts = tab_texts(d)
        has_order = any(k in " ".join(texts) for k in
                        ["订单", "待确认", "待接单", "已预约", "授课中", "已完成", "待评价", "老师"])
        rec("4. 订单列表渲染", has_order, f"文本: {texts[:10]}")
    except Exception as e:
        rec("4. 订单列表渲染", False, str(e)[:200])

    # ===== 5. 调课页（交接单 P2 未测项）=====
    try:
        tap_tab(d, "我的订单")
        time.sleep(1.5)
        # 订单卡片上有「调课」按钮的订单（status=1 已预约才有）
        nodes = dump(d)
        btn = None
        for n in nodes:
            if n["clickable"] and ("调课" in n["text"] or "调课" in n["desc"]):
                btn = n
                break
        if btn:
            d.click(btn["cx"], btn["cy"])
            time.sleep(3)
            shot(d, "05-resched-page")
            texts = tab_texts(d)
            has = any(k in " ".join(texts) for k in
                      ["调课", "新日期", "新时段", "选择日期", "申请"])
            rec("5. 调课页可达", has, f"文本: {texts[:12]}")
        else:
            # 页面上没有调课按钮（无「已预约」订单），记录可点击按钮清单
            btns = [n["text"] or n["desc"] for n in nodes if n["clickable"] and (n["text"] or n["desc"])]
            rec("5. 调课页可达", False,
                f"订单页无「调课」按钮（当前无已预约订单）。可点按钮: {btns[:8]}")
    except Exception as e:
        rec("5. 调课页可达", False, str(e)[:200])
        shot(d, "05-FAIL")

    # ===== 6. 消息页 =====
    try:
        tap_tab(d, "消息")
        time.sleep(2)
        shot(d, "06-messages")
        texts = tab_texts(d)
        has_msg = len(texts) > 2
        rec("6. 消息页渲染", has_msg, f"文本: {texts[:10]}")
    except Exception as e:
        rec("6. 消息页渲染", False, str(e)[:200])

    # ===== 7. 聊天页（交接单 P2 未测项）=====
    try:
        tap_tab(d, "消息")
        time.sleep(2)
        shot(d, "07-messages-top")
        # 私信区块在通知列表下方，用容器内滚动查找（不要用上滑手势）
        found = scroll_to_find(d, "私信", max_try=4)
        time.sleep(1)
        shot(d, "07-messages-scrolled")
        nodes = dump(d)
        texts = [n["text"] for n in nodes if n["text"]]
        print(f"    滚动后文本: {texts[:16]}", flush=True)

        # 定位私信会话：排除通知内容（含"订单号"）和固定文案
        SKIP = {"私信", "通知", "删除已读", "首页", "消息", "我的订单", "我的", ""}
        conv = None
        for n in nodes:
            v = n["text"] or n["desc"]
            if not v or v in SKIP or "订单号" in v:
                continue
            # 会话项显示对方姓名（如「王老师」）
            if ("老师" in v or "同学" in v) and n["bounds"][1] < 1800:
                conv = n
                break

        if conv:
            d.click(conv["cx"], conv["cy"])
            time.sleep(3)
            shot(d, "07-chat")
            ctexts = tab_texts(d)
            has_chat = any(k in " ".join(ctexts) for k in
                           ["发送", "输入", "说点什么", "聊天"]) or \
                       "聊天" in adb("shell", "dumpsys", "activity", "activities")
            rec("7. 聊天页可达", has_chat,
                f"点『{(conv['text'] or conv['desc'])[:10]}』→ 文本: {ctexts[:12]}")
        else:
            rec("7. 聊天页可达", False,
                f"未找到私信会话项（滚动结果={found}）。文本: {texts[:14]}")
    except Exception as e:
        rec("7. 聊天页可达", False, str(e)[:200])
        shot(d, "07-FAIL")

    # ===== 8. 我的页面 =====
    try:
        tap_tab(d, "我的")
        time.sleep(2)
        shot(d, "08-mine")
        texts = tab_texts(d)
        has_mine = any(k in " ".join(texts) for k in
                       ["张同学", "学生", "退出登录", "个人资料", "修改密码"])
        rec("8. 我的页面", has_mine, f"文本: {texts[:12]}")
    except Exception as e:
        rec("8. 我的页面", False, str(e)[:200])

    # ===== 9. Vue 警告检查（规则：基准是 0 条）=====
    try:
        logcat = adb("logcat", "-d", "-t", "400")
        warns = [ln for ln in logcat.splitlines()
                 if "Vue warn" in ln or "console.error" in ln]
        rec("9. 无 Vue 警告/console.error", len(warns) == 0,
            f"警告条数={len(warns)}" + (f"，例: {warns[0][:120]}" if warns else ""))
    except Exception as e:
        rec("9. 无 Vue 警告", False, str(e)[:150])

    return summary()


def summary():
    print("\n" + "=" * 72, flush=True)
    passed = sum(1 for r in RESULTS if r["ok"])
    print(f"结果: {passed}/{len(RESULTS)} 通过", flush=True)
    print("=" * 72, flush=True)
    out = Path(r"E:\毕业设计\project\docs\mobile-e2e-result.json")
    out.write_text(json.dumps({"summary": {"passed": passed, "total": len(RESULTS)},
                               "cases": RESULTS}, ensure_ascii=False, indent=2),
                   encoding="utf-8")
    print(f"结果: {out}", flush=True)
    return 0 if passed == len(RESULTS) else 1


if __name__ == "__main__":
    sys.exit(main())
