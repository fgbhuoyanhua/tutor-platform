# -*- coding: utf-8 -*-
import subprocess
import sys
from pathlib import Path

REPO = Path(r"E:\毕业设计\project")
MSG = """文档: 测试脚本纳入版本管理，换电脑后可直接跑回归

背景：.tools/ 整体在 .gitignore 里（含 Maven 压缩包 9MB、日志、备份 SQL），
但其中 24 个回归测试脚本是可复用的资产——换电脑后要能直接跑：

Web 端（Playwright）
- web_e2e_order.py     订单全流程 12 用例
- web_e2e_admin.py     管理端 11 用例（含自动复原）
- verify_favorite_order.py  收藏页下单专项
- recon_web.py / recon_admin.py  DOM 侦察

移动端（uiautomator2）
- mobile_e2e.py        9 用例（含 6 个坑的规避）

数据造数
- seed_test_data.py / seed_admin_data.py / seed_mobile_data.py

后端接口（PowerShell）
- test_*.ps1 共 13 个：评价/收入/订单监管/科目管理/状态机/用户/限流/异常/生产自检等

改动：
- .gitignore 改为 .tools/** + !.tools/*.py + !.tools/*.ps1
- 提交 24 个脚本，Maven 包/日志/备份 SQL 仍忽略
"""


def run(args):
    r = subprocess.run(args, cwd=REPO, capture_output=True, text=True,
                       encoding="utf-8", errors="ignore")
    if r.stdout.strip():
        print(r.stdout.strip())
    return r.returncode


def main():
    mf = Path(r"C:\Users\33846\AppData\Local\Temp\commit_tools.txt")
    mf.write_text(MSG, encoding="utf-8", newline="\n")

    run(["git", "add", "-A"])
    print("=== 暂存文件数 ===")
    r = subprocess.run(["git", "diff", "--cached", "--name-only"], cwd=REPO,
                       capture_output=True, text=True, encoding="utf-8", errors="ignore")
    files = [f for f in r.stdout.splitlines() if f.strip()]
    print(f"共 {len(files)} 个：")
    for f in files:
        print("  ", f)

    print("\n=== commit ===")
    if run(["git", "commit", "-F", str(mf)]) != 0:
        return 1

    print("\n=== push ===")
    if run(["git", "push", "origin", "main"]) != 0:
        return 1

    print("\n=== 最近 3 条 ===")
    run(["git", "--no-pager", "log", "-3", "--oneline"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
