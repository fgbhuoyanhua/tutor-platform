# -*- coding: utf-8 -*-
"""答辩插图绘制：统一风格（白底 / 深蓝边框 / 浅蓝填充 / 黑字）。"""
import os
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = r"E:\毕业设计\project\docs\答辩插图"
FONT_R = r"C:\Windows\Fonts\msyh.ttc"
FONT_B = r"C:\Windows\Fonts\msyhbd.ttc"
FONT_H = r"C:\Windows\Fonts\simhei.ttf"

BG = (255, 255, 255)
BORDER = (43, 76, 126)
FILL = (238, 243, 251)
FILL2 = (222, 232, 246)
TEXT = (25, 25, 25)
GRAY = (90, 90, 90)
LINE = (70, 70, 70)
WHITE = (255, 255, 255)

_cache = {}


def font(size, bold=False, hei=False):
    key = (size, bold, hei)
    if key not in _cache:
        path = FONT_H if hei else (FONT_B if bold else FONT_R)
        _cache[key] = ImageFont.truetype(path, size)
    return _cache[key]


def wrap(draw, text, f, maxw):
    lines = []
    for para in text.split("\n"):
        cur = ""
        for ch in para:
            trial = cur + ch
            if draw.textlength(trial, font=f) <= maxw:
                cur = trial
            else:
                if cur:
                    lines.append(cur)
                cur = ch
        lines.append(cur)
    return lines


def box(draw, x, y, w, h, text, size=24, bold=False, fill=FILL, border=BORDER,
        radius=12, pad=16, color=TEXT, width=3, align="center", shrink=True):
    draw.rounded_rectangle([x, y, x + w, y + h], radius=radius, fill=fill,
                           outline=border, width=width)
    f = font(size, bold)
    while True:
        lines = wrap(draw, text, f, w - 2 * pad)
        lh = size * 1.32
        total = lh * len(lines)
        if total <= h - 2 * pad or not shrink or size <= 14:
            break
        size -= 2
        f = font(size, bold)
    ty = y + (h - total) / 2
    for ln in lines:
        tw = draw.textlength(ln, font=f)
        tx = x + (w - tw) / 2 if align == "center" else x + pad
        draw.text((tx, ty), ln, font=f, fill=color)
        ty += lh


def arrow(draw, p1, p2, color=LINE, w=3, head=14):
    x1, y1 = p1
    x2, y2 = p2
    draw.line([(x1, y1), (x2, y2)], fill=color, width=w)
    import math
    ang = math.atan2(y2 - y1, x2 - x1)
    a1 = ang + math.pi * 0.82
    a2 = ang - math.pi * 0.82
    pts = [(x2, y2),
           (x2 + head * math.cos(a1), y2 + head * math.sin(a1)),
           (x2 + head * math.cos(a2), y2 + head * math.sin(a2))]
    draw.polygon(pts, fill=color)


def elbow(draw, pts, color=LINE, w=3):
    for i in range(len(pts) - 1):
        x1, y1 = pts[i]
        x2, y2 = pts[i + 1]
        draw.line([(x1, y1), (x2, y2)], fill=color, width=w)
    x1, y1 = pts[-2]
    x2, y2 = pts[-1]
    arrow(draw, (x1, y1), (x2, y2), color, w)


def tag(draw, cx, cy, text, size=19, color=LINE):
    f = font(size, True)
    tw = draw.textlength(text, font=f)
    th = size + 10
    draw.rectangle([cx - tw / 2 - 8, cy - th / 2, cx + tw / 2 + 8, cy + th / 2],
                   fill=WHITE)
    draw.text((cx - tw / 2, cy - th / 2 + 4), text, font=f, fill=color)


def title(draw, text, W, y=34, size=44):
    f = font(size, True, hei=True)
    tw = draw.textlength(text, font=f)
    draw.text(((W - tw) / 2, y), text, font=f, fill=(20, 20, 20))


def save(img, name):
    os.makedirs(OUT_DIR, exist_ok=True)
    p = os.path.join(OUT_DIR, name)
    img.save(p, "PNG", optimize=True)
    print(name, img.size, os.path.getsize(p))


# ---------------- 1. 系统架构图（四层，单体分层） ----------------
def draw_arch():
    W, H = 1600, 1503
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    title(d, "系统体系结构（四层）", W, 30, 44)

    LX, LW = 100, 1400

    # 层1 客户层
    L1Y, L1H = 110, 210
    box(d, LX, L1Y, LW, L1H, "", fill=(250, 251, 254), border=BORDER, width=3)
    d.text((LX + 22, L1Y + 12), "客户层", font=font(26, True), fill=BORDER)
    box(d, 190, L1Y + 62, 520, 112, "Web 端（Vue3）\n管理后台", 25)
    box(d, 890, L1Y + 62, 520, 112, "移动端（uni-app x）\n学生端 / 老师端", 25)

    # 层2 接口层
    L2Y, L2H = 380, 170
    box(d, LX, L2Y, LW, L2H, "", fill=(250, 251, 254), border=BORDER, width=3)
    d.text((LX + 22, L2Y + 12), "接口层", font=font(26, True), fill=BORDER)
    for i, t in enumerate(["RESTful API", "JWT 身份认证", "RBAC 角色权限"]):
        box(d, 190 + i * 430, L2Y + 62, 380, 86, t, 24)

    # 层3 业务层
    L3Y, L3H = 610, 350
    box(d, LX, L3Y, LW, L3H, "", fill=(250, 251, 254), border=BORDER, width=3)
    d.text((LX + 22, L3Y + 12), "业务层（单体分层内的业务模块）", font=font(26, True), fill=BORDER)
    row1 = ["用户认证", "老师展示", "预约下单", "调课退课", "收藏评价", "站内消息"]
    bw, gap, start = 200, 30, 130
    for i, t in enumerate(row1):
        box(d, start + i * (bw + gap), L3Y + 62, bw, 100, t, 23)
    row2 = ["学习报告", "课程表", "数据看板", "管理后台"]
    bw2, gap2, s2 = 280, 40, 180
    for i, t in enumerate(row2):
        box(d, s2 + i * (bw2 + gap2), L3Y + 190, bw2, 100, t, 23)

    # 层4 数据层
    L4Y, L4H = 1020, 220
    box(d, LX, L4Y, LW, L4H, "", fill=(250, 251, 254), border=BORDER, width=3)
    d.text((LX + 22, L4Y + 12), "数据层", font=font(26, True), fill=BORDER)
    box(d, 330, L4Y + 62, 420, 120, "MySQL 8\n业务数据 · 唯一索引 · 事务", 23)
    box(d, 850, L4Y + 62, 420, 120, "Redis\n缓存 · 失败计数 · 验证码", 23)

    # 层间箭头
    for y1, y2 in ((320, 378), (550, 608), (960, 1018)):
        arrow(d, (W / 2, y1), (W / 2, y2), BORDER, 4, 16)

    f = font(20)
    d.text((110, 1300), "说明：双端共用同一套 RESTful 接口与同一套鉴权模型；业务层为单体分层内的业务模块，不是微服务拆分。",
           font=f, fill=GRAY)
    d.text((110, 1340), "客户端只负责呈现与采集，规则判断（权限、时段占用、状态流转）全部在服务端完成。",
           font=f, fill=GRAY)

    save(img, "01_系统架构图.png")


# ---------------- 2. 数据库 ER 图 ----------------
def draw_er():
    W, H = 1600, 2147
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    title(d, "数据库实体关系图（MySQL 8）", W, 26, 42)

    ROW = 34
    HEAD = 44

    def entity(x, y, name, fields, w=340, note=None):
        h = HEAD + ROW * len(fields)
        d.rounded_rectangle([x, y, x + w, y + h], radius=8, fill=(255, 255, 255),
                            outline=BORDER, width=3)
        d.rectangle([x, y, x + w, y + HEAD], fill=BORDER)
        f = font(21, True)
        tw = d.textlength(name, font=f)
        d.text((x + (w - tw) / 2, y + 8), name, font=f, fill=WHITE)
        for i, (fn, mark) in enumerate(fields):
            yy = y + HEAD + i * ROW
            if i % 2 == 1:
                d.rectangle([x + 2, yy, x + w - 2, yy + ROW], fill=(247, 249, 253))
            d.line([(x, yy), (x + w, yy)], fill=(200, 210, 225), width=1)
            d.text((x + 12, yy + 6), fn, font=font(18), fill=TEXT)
            if mark:
                d.text((x + w - 46, yy + 6), mark, font=font(16, True), fill=(160, 40, 40))
        return (x, y, w, h)

    LOG = entity(620, 80, "ADMIN_LOG 操作日志", [
        ("id  BIGINT", "PK"), ("admin_id  BIGINT", "FK"), ("admin_name  VARCHAR",
                                                           ""), ("action  VARCHAR", ""),
        ("target_type  VARCHAR", ""), ("target_id  BIGINT", ""), ("detail  VARCHAR", ""),
        ("create_time  DATETIME", "")])
    USER = entity(620, 390, "USER 用户", [
        ("id  BIGINT", "PK"), ("username  VARCHAR", ""), ("password  VARCHAR", ""),
        ("role  TINYINT", ""), ("real_name  VARCHAR", ""), ("phone  VARCHAR", ""),
        ("avatar  VARCHAR", ""), ("status  TINYINT", ""), ("create_time  DATETIME", "")])
    MSG = entity(70, 760, "MESSAGE 消息", [
        ("id  BIGINT", "PK"), ("from_id  BIGINT", "FK"), ("to_id  BIGINT", "FK"),
        ("type  TINYINT", ""), ("content  VARCHAR", ""), ("is_read  TINYINT", ""),
        ("create_time  DATETIME", "")], w=330)
    TUTOR = entity(620, 800, "TUTOR 家教信息", [
        ("id  BIGINT", "PK"), ("user_id  BIGINT", "FK"), ("subject_id  BIGINT", "FK"),
        ("grade  VARCHAR", ""), ("price  DECIMAL", ""), ("introduce  TEXT", ""),
        ("rating  DECIMAL", ""), ("status  TINYINT", ""), ("create_time  DATETIME", "")])
    FAV = entity(70, 1130, "FAVORITE 收藏", [
        ("id  BIGINT", "PK"), ("student_id  BIGINT", "FK"),
        ("tutor_id  BIGINT", "FK"), ("create_time  DATETIME", "")], w=330)
    SUBJ = entity(620, 1130, "SUBJECT 科目", [
        ("id  BIGINT", "PK"), ("name  VARCHAR", ""), ("parent_id  BIGINT", ""),
        ("sort  INT", "")], w=300)
    APPT = entity(1020, 1040, "APPOINTMENT 预约订单", [
        ("id  BIGINT", "PK"), ("order_no  VARCHAR", ""), ("student_id  BIGINT", "FK"),
        ("tutor_id  BIGINT", "FK"), ("subject_id  BIGINT", "FK"),
        ("appoint_date  DATE", ""), ("time_slot  VARCHAR", ""),
        ("total_price  DECIMAL", ""), ("status  TINYINT", ""),
        ("pay_status  TINYINT", ""), ("active_flag  TINYINT", "生成列"),
        ("create_time  DATETIME", "")], w=470)
    EVAL = entity(370, 1740, "EVALUATION 评价", [
        ("id  BIGINT", "PK"), ("order_id  BIGINT", "FK"), ("student_id  BIGINT", "FK"),
        ("tutor_id  BIGINT", "FK"), ("score  TINYINT", ""), ("content  VARCHAR", ""),
        ("reply  VARCHAR", ""), ("create_time  DATETIME", "")])
    RESC = entity(770, 1740, "RESCHEDULE 调课", [
        ("id  BIGINT", "PK"), ("order_id  BIGINT", "FK"), ("student_id  BIGINT", "FK"),
        ("old_date  DATE", ""), ("old_slot  VARCHAR", ""), ("new_date  DATE", ""),
        ("new_slot  VARCHAR", ""), ("status  TINYINT", ""),
        ("create_time  DATETIME", "")])
    REPORT = entity(1210, 1740, "STUDY_REPORT 学习报告", [
        ("id  BIGINT", "PK"), ("order_id  BIGINT", "FK"),
        ("teacher_id  BIGINT", "FK"), ("student_id  BIGINT", "FK"),
        ("content  TEXT", ""), ("create_time  DATETIME", "")], w=330)

    # 关系线（端点均落在框边界，避免悬空）
    elbow(d, [(790, 390), (790, 360)] )                       # USER -> ADMIN_LOG
    tag(d, 860, 375, "操作日志")
    elbow(d, [(790, 800), (790, 690)])                        # USER -> TUTOR
    tag(d, 875, 745, "老师账号")
    elbow(d, [(620, 600), (470, 600), (470, 760)])            # USER -> MESSAGE
    tag(d, 500, 660, "发送/接收")
    elbow(d, [(620, 1000), (300, 1000), (300, 1130)])         # USER -> FAVORITE
    tag(d, 350, 1065, "学生收藏")
    elbow(d, [(620, 1050), (480, 1050), (480, 1130)])         # TUTOR -> FAVORITE
    tag(d, 520, 1092, "被收藏")
    elbow(d, [(790, 1163), (790, 1200)])                      # TUTOR -> SUBJECT
    tag(d, 860, 1180, "教授科目")
    elbow(d, [(960, 1050), (1020, 1040)])                     # TUTOR -> APPT
    tag(d, 985, 1015, "老师接单")
    elbow(d, [(960, 700), (1100, 700), (1100, 1040)])         # USER -> APPT
    tag(d, 1105, 880, "学生下单")
    elbow(d, [(1020, 1450), (700, 1450), (700, 1740)])        # APPT -> EVAL
    tag(d, 790, 1452, "完成后评价")
    elbow(d, [(1280, 1492), (1280, 1700), (1000, 1700), (1000, 1740)])  # APPT -> RESC
    tag(d, 1120, 1700, "申请调课")
    elbow(d, [(1490, 1400), (1520, 1400), (1520, 1740), (1370, 1740)])  # APPT -> REPORT
    tag(d, 1150, 1560, "课后报告")

    f = font(19)
    d.text((70, 2000), "说明 1：APPOINTMENT 的 active_flag 是由 status 派生的生成列（状态 0/1/2 时为 1，其余为 NULL）。",
           font=f, fill=GRAY)
    d.text((70, 2035), "说明 2：唯一索引 uk_slot_active（tutor_id, appoint_date, time_slot, active_flag）保证同一教师同一日期同一时段不被重复占用。",
           font=f, fill=GRAY)
    d.text((70, 2070), "说明 3：MySQL 唯一索引允许多个 NULL，故已完成 / 已取消 / 已拒绝的订单可以共存，时段可被再次预约。",
           font=f, fill=GRAY)

    save(img, "02_数据库ER图.png")


if __name__ == "__main__":
    # 只出架构图；ER 图由 draw_er2.py 负责，避免旧布局覆盖新图
    draw_arch()
