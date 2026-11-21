# -*- coding: utf-8 -*-
"""ER 图重排版：拉开间距、连线端点贴边界、标注白底、说明移到底部空白。"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import draw_core as C
from PIL import Image, ImageDraw

W, H = 1600, 2147
ROW, HEAD = 34, 44


def entity(d, x, y, name, fields, w=340):
    h = HEAD + ROW * len(fields)
    d.rounded_rectangle([x, y, x + w, y + h], radius=8, fill=(255, 255, 255),
                        outline=C.BORDER, width=3)
    d.rectangle([x, y, x + w, y + HEAD], fill=C.BORDER)
    f = C.font(21, True)
    tw = d.textlength(name, font=f)
    d.text((x + (w - tw) / 2, y + 8), name, font=f, fill=C.WHITE)
    for i, (fn, mark) in enumerate(fields):
        yy = y + HEAD + i * ROW
        if i % 2 == 1:
            d.rectangle([x + 2, yy, x + w - 2, yy + ROW], fill=(247, 249, 253))
        d.line([(x, yy), (x + w, yy)], fill=(205, 214, 228), width=1)
        d.text((x + 12, yy + 7), fn, font=C.font(18), fill=C.TEXT)
        if mark:
            mf = C.font(15, True)
            mw = d.textlength(mark, font=mf)
            d.text((x + w - 12 - mw, yy + 9), mark, font=mf, fill=(170, 45, 45))
    return (x, y, w, h)


img = Image.new("RGB", (W, H), C.BG)
d = ImageDraw.Draw(img)
C.title(d, "数据库实体关系图（MySQL 8）", W, 22, 42)

# ------- 实体位置（列/行拉开） -------
LOG = entity(d, 830, 60, "ADMIN_LOG 操作日志", [
    ("id  BIGINT", "PK"), ("admin_id  BIGINT", "FK"), ("admin_name  VARCHAR", ""),
    ("action  VARCHAR", ""), ("target_type  VARCHAR", ""), ("target_id  BIGINT", ""),
    ("detail  VARCHAR", ""), ("create_time  DATETIME", "")], w=340)
MSG = entity(d, 40, 470, "MESSAGE 消息", [
    ("id  BIGINT", "PK"), ("from_id  BIGINT", "FK"), ("to_id  BIGINT", "FK"),
    ("type  TINYINT", ""), ("content  VARCHAR", ""), ("is_read  TINYINT", ""),
    ("create_time  DATETIME", "")], w=310)
USER = entity(d, 440, 470, "USER 用户", [
    ("id  BIGINT", "PK"), ("username  VARCHAR", ""), ("password  VARCHAR", ""),
    ("role  TINYINT", ""), ("real_name  VARCHAR", ""), ("phone  VARCHAR", ""),
    ("avatar  VARCHAR", ""), ("status  TINYINT", ""), ("create_time  DATETIME", "")])
SUBJ = entity(d, 830, 470, "SUBJECT 科目", [
    ("id  BIGINT", "PK"), ("name  VARCHAR", ""), ("parent_id  BIGINT", ""),
    ("sort  INT", "")], w=300)
FAV = entity(d, 60, 940, "FAVORITE 收藏", [
    ("id  BIGINT", "PK"), ("student_id  BIGINT", "FK"), ("tutor_id  BIGINT", "FK"),
    ("create_time  DATETIME", "")], w=330)
TUTOR = entity(d, 440, 900, "TUTOR 家教信息", [
    ("id  BIGINT", "PK"), ("user_id  BIGINT", "FK"), ("subject_id  BIGINT", "FK"),
    ("grade  VARCHAR", ""), ("price  DECIMAL", ""), ("introduce  TEXT", ""),
    ("rating  DECIMAL", ""), ("status  TINYINT", ""), ("create_time  DATETIME", "")])
APPT = entity(d, 1000, 740, "APPOINTMENT 预约订单", [
    ("id  BIGINT", "PK"), ("order_no  VARCHAR", ""), ("student_id  BIGINT", "FK"),
    ("tutor_id  BIGINT", "FK"), ("subject_id  BIGINT", "FK"),
    ("appoint_date  DATE", ""), ("time_slot  VARCHAR", ""),
    ("total_price  DECIMAL", ""), ("status  TINYINT", ""),
    ("pay_status  TINYINT", ""), ("active_flag  TINYINT", "生成列"),
    ("create_time  DATETIME", "")], w=470)
EVAL = entity(d, 60, 1450, "EVALUATION 评价", [
    ("id  BIGINT", "PK"), ("order_id  BIGINT", "FK"), ("student_id  BIGINT", "FK"),
    ("tutor_id  BIGINT", "FK"), ("score  TINYINT", ""), ("content  VARCHAR", ""),
    ("reply  VARCHAR", ""), ("create_time  DATETIME", "")])
RESC = entity(d, 440, 1450, "RESCHEDULE 调课", [
    ("id  BIGINT", "PK"), ("order_id  BIGINT", "FK"), ("student_id  BIGINT", "FK"),
    ("old_date  DATE", ""), ("old_slot  VARCHAR", ""), ("new_date  DATE", ""),
    ("new_slot  VARCHAR", ""), ("status  TINYINT", ""),
    ("create_time  DATETIME", "")])
REPORT = entity(d, 830, 1450, "STUDY_REPORT 学习报告", [
    ("id  BIGINT", "PK"), ("order_id  BIGINT", "FK"), ("teacher_id  BIGINT", "FK"),
    ("student_id  BIGINT", "FK"), ("content  TEXT", ""),
    ("create_time  DATETIME", "")])

# ------- 关系线 -------
# USER -> ADMIN_LOG（上）
C.elbow(d, [(700, 470), (700, 440), (1000, 440), (1000, 376)])
C.tag(d, 850, 425, "操作日志")
# USER -> TUTOR（下）
C.elbow(d, [(610, 820), (610, 900)])
C.tag(d, 700, 862, "老师账号")
# USER -> MESSAGE（左，平进）
C.elbow(d, [(440, 620), (351, 620)])
C.tag(d, 405, 595, "发送")
C.tag(d, 405, 648, "接收")
# USER -> FAVORITE
C.elbow(d, [(440, 800), (400, 800), (400, 1020), (390, 1020)])
C.tag(d, 330, 845, "学生收藏")
# TUTOR -> FAVORITE（顶）
C.elbow(d, [(440, 980), (415, 980), (415, 940), (225, 940)])
C.tag(d, 300, 900, "被收藏")
# TUTOR -> SUBJECT（上）
C.elbow(d, [(700, 900), (700, 700), (1000, 700), (1000, 650)])
C.tag(d, 850, 680, "教授科目")
# TUTOR -> APPOINTMENT（右，平进）
C.elbow(d, [(780, 1150), (1000, 1150)])
C.tag(d, 900, 1124, "老师接单")
# USER -> APPOINTMENT（右，平进）
C.elbow(d, [(780, 760), (1000, 760)])
C.tag(d, 890, 734, "学生下单")
# APPOINTMENT -> EVALUATION
C.elbow(d, [(1080, 1192), (1080, 1290), (230, 1290), (230, 1450)])
C.tag(d, 420, 1290, "完成后评价")
# APPOINTMENT -> RESCHEDULE
C.elbow(d, [(1180, 1192), (1180, 1320), (610, 1320), (610, 1450)])
C.tag(d, 720, 1320, "申请调课")
# APPOINTMENT -> STUDY_REPORT
C.elbow(d, [(1280, 1192), (1280, 1350), (1000, 1350), (1000, 1450)])
C.tag(d, 1060, 1350, "课后报告")

# ------- 说明 -------
f = C.font(19)
d.text((60, 1900), "说明 1：active_flag 是由 status 派生的生成列（状态 0/1/2 时为 1，其余为 NULL），应用无需手工维护。",
       font=f, fill=C.GRAY)
d.text((60, 1940), "说明 2：唯一索引 uk_slot_active（tutor_id, appoint_date, time_slot, active_flag）保证同一教师同一日期同一时段不被重复占用。",
       font=f, fill=C.GRAY)
d.text((60, 1980), "说明 3：MySQL 唯一索引允许多个 NULL，故已完成 / 已取消 / 已拒绝的订单可以共存，对应时段可再次被预约。",
       font=f, fill=C.GRAY)
d.text((60, 2020), "说明 4：order_id 指向 appointment.id；tutor_id（订单表）指向家教老师 user.id，favorite.tutor_id 指向 tutor.id。",
       font=f, fill=C.GRAY)

C.save(img, "02_数据库ER图.png")
