# -*- coding: utf-8 -*-
"""答辩插图：用例图 / 业务流程图 / 下单时序图"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import draw_core as C
from PIL import Image, ImageDraw

BORDER = C.BORDER
FILL = C.FILL
FILL2 = C.FILL2
LINE = C.LINE
WHITE = C.WHITE
TEXT = C.TEXT
GRAY = C.GRAY


# ---------------- 3. 系统用例图 ----------------
def draw_usecase():
    W, H = 1600, 980
    img = Image.new("RGB", (W, H), C.BG)
    d = ImageDraw.Draw(img)
    C.title(d, "系统用例图", W, 26, 44)

    d.rounded_rectangle([320, 116, 1576, 782], radius=18,
                        fill=(250, 251, 254), outline=BORDER, width=3)
    d.text((344, 130), "大学生家教服务预约平台", font=C.font(24, True), fill=BORDER)

    def actor(cx, cy, name):
        d.ellipse([cx - 24, cy - 98, cx + 24, cy - 50], outline=BORDER, width=3, fill=FILL)
        d.line([(cx, cy - 50), (cx, cy + 12)], fill=BORDER, width=3)
        d.line([(cx - 38, cy - 30), (cx + 38, cy - 30)], fill=BORDER, width=3)
        d.line([(cx, cy + 12), (cx - 32, cy + 58)], fill=BORDER, width=3)
        d.line([(cx, cy + 12), (cx + 32, cy + 58)], fill=BORDER, width=3)
        f = C.font(20, True)
        tw = d.textlength(name, font=f)
        d.text((cx - tw / 2, cy + 66), name, font=f, fill=TEXT)

    def ell(cx, cy, text, w=176, h=86, size=18):
        d.ellipse([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2],
                  fill=FILL, outline=BORDER, width=3)
        f = C.font(size)
        lines = C.wrap(d, text, f, w - 40)
        lh = size * 1.35
        ty = cy - lh * len(lines) / 2 + 2
        for ln in lines:
            tw = d.textlength(ln, font=f)
            d.text((cx - tw / 2, ty), ln, font=f, fill=TEXT)
            ty += lh

    rows = [230, 452, 674]
    roles = ["学生 / 家长", "家教老师", "系统管理员"]
    shorts = ["学生", "老师", "管理员"]
    sets = [
        ["注册登录", "浏览与搜索", "收藏老师", "预约下单", "调课与取消", "评价与课表"],
        ["入驻与认证", "维护家教信息", "管理可约时段", "确认与开始授课", "完成并上传报告"],
        ["审核老师", "用户与科目管理", "订单监管", "数据看板", "操作日志"],
    ]

    for i, cy in enumerate(rows):
        actor(200, cy, roles[i])
        C.arrow(d, (240, cy - 30), (328, cy), BORDER, 2, 10)
        C.box(d, 330, cy - 44, 100, 88, shorts[i], size=20, bold=True, fill=FILL2)
        for j, t in enumerate(sets[i]):
            ell(554 + j * 180, cy, t, w=168)

    f = C.font(19)
    d.text((110, 828), "说明 1：三类角色共用同一套 RESTful 接口与同一套 JWT 鉴权模型，角色权限由令牌中的 role 声明决定。",
           font=f, fill=GRAY)
    d.text((110, 866), "说明 2：管理员的审核、监管、看板、日志均为观察或审批类用例，统一走 RBAC 校验，不绕过业务层的状态机。",
           font=f, fill=GRAY)
    d.text((110, 904), "说明 3：预约下单与调课用例内部包含时段占用校验，规则在服务端执行，客户端只负责采集与呈现。",
           font=f, fill=GRAY)
    C.save(img, "03_系统用例图.png")


# ---------------- 4. 核心业务流程图 ----------------
def draw_flow():
    W, H = 1600, 1985
    img = Image.new("RGB", (W, H), C.BG)
    d = ImageDraw.Draw(img)
    C.title(d, "预约主业务流程", W, 26, 44)

    CX = 760

    def term(cy, text, w=300, h=68):
        f = C.font(22, True)
        tw = d.textlength(text, font=f)
        w = max(w, tw + 70)
        d.rounded_rectangle([CX - w / 2, cy - h / 2, CX + w / 2, cy + h / 2],
                            radius=h / 2, fill=FILL2, outline=BORDER, width=3)
        d.text((CX - tw / 2, cy - 16), text, font=f, fill=TEXT)

    def step(cy, text, w=520, h=92, size=20):
        C.box(d, CX - w / 2, cy - h / 2, w, h, text, size=size)

    def dia(cy, text, w=380, h=142, size=21):
        d.polygon([(CX, cy - h / 2), (CX + w / 2, cy), (CX, cy + h / 2), (CX - w / 2, cy)],
                  fill=(250, 251, 254), outline=BORDER)
        f = C.font(size)
        lines = C.wrap(d, text, f, w - 110)
        lh = size * 1.35
        ty = cy - lh * len(lines) / 2 + 2
        for ln in lines:
            tw = d.textlength(ln, font=f)
            d.text((CX - tw / 2, ty), ln, font=f, fill=TEXT)
            ty += lh

    def vline(y1, y2):
        C.arrow(d, (CX, y1), (CX, y2), LINE, 3, 13)

    Y0, Y1, Y2, Y3, Y4 = 96, 206, 330, 452, 578
    Y5, Y6, Y7, Y8, Y9 = 742, 872, 1032, 1172, 1312
    Y10, Y11, Y12, Y13, Y14 = 1470, 1588, 1700, 1812, 1912

    term(Y0, "开始：学生进入平台")
    vline(Y0 + 34, Y1 - 46)

    step(Y1, "① 浏览 / 搜索家教（列表缓存 180 秒）")
    vline(Y1 + 46, Y2 - 46)
    step(Y2, "② 选择日期与时段（7 个固定时间片）")
    vline(Y2 + 46, Y3 - 46)
    step(Y3, "③ 提交预约（携带 JWT 令牌）")
    vline(Y3 + 46, Y4 - 71)

    dia(Y4, "登录态有效且角色为学生？")
    vline(Y4 + 71, Y5 - 46)
    C.tag(d, CX + 66, (Y4 + 71 + Y5 - 46) / 2, "是")

    step(Y5, "④ 查 active_flag 订单，校验该时段是否空闲")
    vline(Y5 + 46, Y6 - 71)

    dia(Y6, "该时段未被占用？")
    C.tag(d, CX + 66, (Y6 + 71 + Y7 - 46) / 2, "是")
    vline(Y6 + 71, Y7 - 46)

    step(Y7, "⑤ 事务内写订单 status=0\n生成列 active_flag=1，唯一索引 uk_slot_active 兜底", h=100)
    vline(Y7 + 50, Y8 - 46)

    step(Y8, "⑥ 下发站内消息给老师 · 清 dashboard 缓存")
    vline(Y8 + 46, Y9 - 71)

    dia(Y9, "老师在有效期内处理？")
    C.tag(d, CX + 66, (Y9 + 71 + Y10 - 46) / 2, "确认")
    vline(Y9 + 71, Y10 - 46)

    step(Y10, "⑦ status=1 已预约，学生收到确认")
    vline(Y10 + 46, Y11 - 46)
    step(Y11, "⑧ 老师开始授课，status=2 授课中")
    vline(Y11 + 46, Y12 - 46)
    step(Y12, "⑨ 学生确认完成，status=3 已完成")
    vline(Y12 + 46, Y13 - 46)
    step(Y13, "⑩ 学生评价老师 · 老师上传学习报告")
    vline(Y13 + 46, Y14 - 34)

    term(Y14, "时段释放，可被再次预约：结束")

    # 右侧分支：无权限
    BX = 1050
    d.rounded_rectangle([BX, Y4 - 46, BX + 290, Y4 + 46], radius=12,
                        fill=(255, 245, 245), outline=(170, 70, 70), width=3)
    f = C.font(19)
    for k, ln in enumerate(["401 未登录 / 403 无权限", "直接返回，不进入业务"]):
        tw = d.textlength(ln, font=f)
        d.text((BX + 145 - tw / 2, Y4 - 26 + k * 30), ln, font=f, fill=TEXT)
    C.arrow(d, (CX + 190, Y4), (BX, Y4), LINE, 3, 13)
    C.tag(d, CX + 250, Y4 - 22, "否", 19)

    # 右侧分支：时段被占用，回流重选
    C.arrow(d, (CX + 190, Y6), (BX, Y6), LINE, 3, 13)
    C.tag(d, CX + 250, Y6 - 22, "否", 19)
    d.rounded_rectangle([BX, Y6 - 46, BX + 290, Y6 + 46], radius=12,
                        fill=(255, 245, 245), outline=(170, 70, 70), width=3)
    for k, ln in enumerate(["提示该时段已被占用", "返回第 ② 步重新选择"]):
        tw = d.textlength(ln, font=f)
        d.text((BX + 145 - tw / 2, Y6 - 26 + k * 30), ln, font=f, fill=TEXT)
    C.elbow(d, [(BX + 290, Y6), (1530, Y6), (1530, Y2), (CX + 262, Y2)], (120, 120, 120), 2)
    C.tag(d, 1276, Y2 - 20, "重选", 18)

    # 右侧分支：拒绝
    d.rounded_rectangle([1090, Y9 - 46, 1490, Y9 + 46], radius=12,
                        fill=(255, 245, 245), outline=(170, 70, 70), width=3)
    for k, ln in enumerate(["拒绝 → status=5，超时 → status=4", "active_flag 置空，时段即释放"]):
        tw = d.textlength(ln, font=f)
        d.text((1290 - tw / 2, Y9 - 26 + k * 30), ln, font=f, fill=TEXT)
    C.arrow(d, (CX + 190, Y9), (1090, Y9), LINE, 3, 13)
    C.tag(d, CX + 268, Y9 - 22, "拒绝 / 超时", 18)

    # 左侧：学生取消
    LX = 250
    d.rounded_rectangle([LX - 190, Y10 - 62, LX + 130, Y10 + 62], radius=12,
                        fill=(255, 250, 240), outline=(150, 110, 40), width=3)
    for k, ln in enumerate(["学生取消预约：0→4", "条件更新 + 时段释放"]):
        tw = d.textlength(ln, font=f)
        d.text((LX - 30 - tw / 2, Y10 - 42 + k * 30), ln, font=f, fill=TEXT)
    C.elbow(d, [(CX - 260, Y10), (LX + 130, Y10)], LINE, 3)

    # 说明
    g = C.font(19)
    d.text((100, 1958), "", font=g, fill=GRAY)
    C.save(img, "04_核心业务流程图.png")


# ---------------- 5. 预约下单时序图 ----------------
def draw_seq():
    W, H = 1600, 1430
    img = Image.new("RGB", (W, H), C.BG)
    d = ImageDraw.Draw(img)
    C.title(d, "预约下单时序图（含并发兜底）", W, 26, 44)

    objs = [(240, "学生客户端\nuni-app x"),
            (620, "服务端\nSpring Boot 3"),
            (890, "MySQL 8\nInnoDB"),
            (1130, "Redis"),
            (1400, "老师客户端")]
    TOP, BOT = 190, 1300

    for x, name in objs:
        C.box(d, x - 95, 110, 190, 68, name, size=18, bold=True, fill=FILL2)
        for yy in range(TOP, BOT, 14):
            d.line([(x, yy), (x, yy + 7)], fill=(170, 170, 170), width=2)

    def msg(y, x1, x2, text, size=17):
        C.arrow(d, (x1, y), (x2, y), LINE, 2, 11)
        C.tag(d, (x1 + x2) / 2, y - 24, text, size)

    def note(y, x1, x2, text2, size=16):
        d.rectangle([x1, y - 42, x2, y + 42], fill=WHITE, outline=(120, 120, 120), width=2)
        f = C.font(size, True)
        lh = size * 1.5
        ty = y - lh * len(text2) / 2 + 2
        for ln in text2:
            tw = d.textlength(ln, font=f)
            d.text(((x1 + x2) / 2 - tw / 2, ty), ln, font=f, fill=TEXT)
            ty += lh

    Y = [250, 340, 430, 510, 595, 680, 765, 850, 935, 1020, 1105, 1190]

    msg(Y[0], 240, 620, "1. 提交预约（教师 / 日期 / 时段）")
    note(Y[1], 608, 848, ["2. 解析 JWT 并校验角色", "role=1、status=1"], size=15)
    msg(Y[2], 620, 890, "3. 查该教师该日该时段的 active 订单")
    msg(Y[3], 890, 620, "4. 无有效占用")
    msg(Y[4], 620, 890, "5. INSERT 订单 status=0（active_flag 自动为 1）")
    msg(Y[5], 890, 620, "6. 成功 / 1062 重复键 → 提示已被占用")
    msg(Y[6], 620, 1130, "7. 清 dashboard:{tutorId}（SCAN + 抖动）")
    msg(Y[7], 620, 240, "8. 返回订单号与待确认状态")
    msg(Y[8], 620, 1400, "9. 站内消息：新预约待确认")
    msg(Y[9], 1400, 620, "10. 老师确认 0→1")
    msg(Y[10], 620, 890, "11. UPDATE … WHERE status=0（条件更新）")
    msg(Y[11], 620, 240, "12. 状态变更通知学生")

    g = C.font(19)
    d.text((100, 1330), "说明：③ 与 ⑤ 之间不再做二次判断，唯一索引 uk_slot_active（tutor_id, appoint_date, time_slot, active_flag）承担最终兜底；",
           font=g, fill=GRAY)
    d.text((100, 1366), "并发重复提交时数据库直接抛 1062，服务端转为友好提示，避免乐观锁冲突后的脏占用。",
           font=g, fill=GRAY)
    C.save(img, "05_预约下单时序图.png")


if __name__ == "__main__":
    draw_usecase()
    draw_flow()
    draw_seq()
