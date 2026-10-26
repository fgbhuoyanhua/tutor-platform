#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
移动端一键部署脚本（uni-app x → MuMu 模拟器）

解决三个真实浪费（2026-10-26 实操总结）：
  1. 每次都重新 `cli open` 启动 HBuilderX —— 实际上它常在运行，先查进程再决定
  2. 编译完不校验，出现「以为编译了、其实 app 里还是旧代码」→ 本次会话最大事故根因
  3. 编译完要手工拉 app 并确认前台

注意：本脚本只做「编译 + 部署 + 校验 + 拉起」，不改变被测对象。
     测的仍然是 Android App，不是 H5/小程序。

用法：
    python .tools/deploy_mobile.py            # 完整流程
    python .tools/deploy_mobile.py --check    # 只检查当前 app 版本，不编译
    python .tools/deploy_mobile.py --force    # 强制重新部署（跳过时间戳判断）

退出码：0 成功可测；1 失败
"""
import argparse
import re
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

# ---------- 环境常量 ----------
PROJECT = Path(r"E:\毕业设计\project")
MOBILE_PRJ = "mobile-app"
HBX_CLI = r"E:\HBuilderX\cli.exe"
HBX_EXE_NAME = "HBuilderX.exe"
ADB = r"C:\Program Files\Netease\MuMuPlayer-12.0\shell\adb.exe"
MUMU_MGR = r"C:\Program Files\Netease\MuMuPlayer-12.0\nx_main\MuMuManager.exe"
DEVICE = "127.0.0.1:16384"
PKG = "io.dcloud.uniappx"
ACTIVITY = f"{PKG}/io.dcloud.uniapp.UniAppActivity"
APP_WWW = "/sdcard/Android/data/io.dcloud.uniappx/apps/__UNI__uniappx/www"
LOG = Path(r"C:\Users\33846\AppData\Local\Temp\hbx_deploy.log")


def say(msg):
    print(msg, flush=True)


# ---------- 基础探测 ----------
def adb(*args, timeout=25):
    try:
        r = subprocess.run([ADB, "-s", DEVICE] + list(args),
                           capture_output=True, text=True, encoding="utf-8",
                           errors="ignore", timeout=timeout)
        return (r.stdout or "") + (r.stderr or "")
    except subprocess.TimeoutExpired:
        return "__TIMEOUT__"
    except Exception as e:
        return f"__ERR__{e}"


def hbx_running():
    """HBuilderX 进程是否在运行（避免每次 cli open 的浪费）"""
    try:
        r = subprocess.run(["tasklist", "/FI", f"IMAGENAME eq {HBX_EXE_NAME}"],
                           capture_output=True, text=True, encoding="gbk",
                           errors="ignore", timeout=15)
        return HBX_EXE_NAME.lower() in (r.stdout or "").lower()
    except Exception:
        return False


def device_connected():
    out = adb("devices", timeout=10)
    return f"{DEVICE}\tdevice" in out


def ensure_device():
    """确保模拟器 + adb 连接可用"""
    if device_connected():
        say(f"  adb 已连接 {DEVICE}")
        return True
    say("  adb 未连接，尝试重连...")
    adb("disconnect", DEVICE, timeout=10)
    adb("connect", DEVICE, timeout=15)
    for _ in range(8):
        if device_connected():
            say(f"  重连成功 {DEVICE}")
            return True
        time.sleep(2)
    say("  ✗ 无法连接模拟器。请确认 MuMu 实例 0 已启动：")
    say(f'    "{MUMU_MGR}" control -v 0 launch')
    return False


def app_mtime():
    """读取 app 资源目录最后修改时间（判断是否是真新代码）"""
    out = adb("shell", f"ls -la {APP_WWW} 2>/dev/null", timeout=20)
    if out.startswith("__") or not out.strip():
        return None
    # 取条目里最大的时间（含 . / .. 行）
    times = re.findall(r"(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})", out)
    if not times:
        return None
    best = max(times)
    try:
        return datetime.strptime(f"{best[0]} {best[1]}", "%Y-%m-%d %H:%M")
    except ValueError:
        return None


def app_in_front():
    out = adb("shell", "dumpsys", "activity", "activities", timeout=20)
    for ln in out.splitlines():
        if "topResumedActivity" in ln or "mResumedActivity" in ln:
            return PKG in ln
    return False


def start_app():
    adb("shell", "am", "start", "-n", ACTIVITY, timeout=20)
    time.sleep(3.5)
    if app_in_front():
        return True
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1",
        timeout=20)
    time.sleep(4)
    return app_in_front()


# ---------- 编译部署 ----------
def ensure_hbx():
    if hbx_running():
        say("  HBuilderX 已在运行，跳过启动")
        return True
    say("  HBuilderX 未运行，启动中...")
    subprocess.run([HBX_CLI, "open"], capture_output=True, timeout=60)
    for _ in range(15):
        if hbx_running():
            say("  HBuilderX 启动完成")
            return True
        time.sleep(2)
    say("  ✗ HBuilderX 启动失败")
    return False


def deploy():
    """调用 HBuilderX CLI 编译部署。输出必须重定向到文件——接管道会被杀"""
    if LOG.exists():
        LOG.unlink()
    err = Path(str(LOG) + ".err")

    say(f"  开始编译（17 个页面约 35 秒）...")
    t0 = time.time()
    # 关键：Start-Process 等价操作，输出重定向到文件，不接管道
    p = subprocess.Popen(
        [HBX_CLI, "launch", "app-android", "--project", MOBILE_PRJ,
         "--deviceId", DEVICE],
        cwd=str(PROJECT),
        stdout=open(LOG, "w", encoding="utf-8", errors="ignore"),
        stderr=open(err, "w", encoding="utf-8", errors="ignore"),
    )

    # 轮询日志，最多 180 秒
    ok = False
    for _ in range(60):
        time.sleep(3)
        txt = LOG.read_text(encoding="utf-8", errors="ignore") if LOG.exists() else ""
        if "应用【" in txt and "已启动" in txt:
            ok = True
            break
        if "编译失败" in txt or "error" in txt.lower():
            break
        if p.poll() is not None and "编译成功" in txt:
            ok = True
            break

    elapsed = int(time.time() - t0)
    txt = LOG.read_text(encoding="utf-8", errors="ignore") if LOG.exists() else ""

    if not ok:
        say(f"  ✗ 编译未确认成功（{elapsed}s）。日志尾部：")
        for ln in txt.splitlines()[-12:]:
            say(f"     {ln}")
        return False

    say(f"  ✓ 编译并启动成功（{elapsed}s）")
    return True


# ---------- 主流程 ----------
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="只检查版本，不编译")
    ap.add_argument("--force", action="store_true", help="强制重新部署")
    args = ap.parse_args()

    say("=" * 62)
    say("移动端部署 / 检查")
    say("=" * 62)

    # 1. 设备
    say("\n[1/5] 检查设备")
    if not ensure_device():
        return 1

    # 2. 当前 app 版本
    say("\n[2/5] 检查当前 app 版本")
    mt = app_mtime()
    if mt:
        age_min = int((datetime.now() - mt).total_seconds() // 60)
        say(f"  app 资源时间: {mt}  （{age_min} 分钟前）")
    else:
        say("  ⚠️ 读不到 app 资源时间戳")

    if args.check:
        say("\n--check 模式，结束。")
        return 0

    # 3. HBuilderX
    say("\n[3/5] 检查 HBuilderX")
    if not ensure_hbx():
        return 1

    # 4. 编译部署
    say("\n[4/5] 编译部署")
    if not deploy():
        return 1

    # 5. 校验：时间戳必须更新
    say("\n[5/5] 校验部署已生效")
    time.sleep(2)
    new_mt = app_mtime()
    if new_mt and mt and new_mt <= mt and not args.force:
        say(f"  ✗ app 资源时间未更新（仍是 {new_mt}）—— 修复不会生效！")
        say("    可能原因：编译产物未同步 / 模拟器上 app 未重启")
        return 1
    say(f"  ✓ app 资源时间已更新为 {new_mt}")

    # 拉起并确认前台
    if not start_app():
        say("  ⚠️ app 未在前台，请手动检查")
    else:
        say("  ✓ app 已在前台")

    say("\n" + "=" * 62)
    say("✅ 部署完成，可以开始移动端测试")
    say(f"   下一步: python .tools/mobile_e2e.py")
    say("=" * 62)
    return 0


if __name__ == "__main__":
    sys.exit(main())
