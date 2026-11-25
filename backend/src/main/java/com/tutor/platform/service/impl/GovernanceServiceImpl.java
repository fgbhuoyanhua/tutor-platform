package com.tutor.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tutor.platform.common.BizException;
import com.tutor.platform.common.CacheEvictor;
import com.tutor.platform.common.CreditRules;
import com.tutor.platform.common.MaskUtil;
import com.tutor.platform.dto.ComplaintCreateDTO;
import com.tutor.platform.entity.AppointmentEntity;
import com.tutor.platform.entity.ComplaintEntity;
import com.tutor.platform.entity.SubjectEntity;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.entity.TutorViolationEntity;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.mapper.AppointmentMapper;
import com.tutor.platform.mapper.ComplaintMapper;
import com.tutor.platform.mapper.SubjectMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.TutorViolationMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.GovernanceService;
import com.tutor.platform.service.MessageService;
import com.tutor.platform.vo.BillItemVO;
import com.tutor.platform.vo.BillVO;
import com.tutor.platform.vo.ComplaintVO;
import com.tutor.platform.vo.TutorViolationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 治理闭环实现：课时账单（订单快照）+ 信用分账本 + 投诉处理。
 *
 * <p>关键设计：
 * <ul>
 *   <li>账单金额/单价一律取 appointment 快照字段，老师之后改价不影响历史账单；</li>
 *   <li>信用分所有变动都在 {@link #addCredit} 一处完成（夹在 [0,100] 之间），并同时在
 *       tutor_violation 留痕，管理员可追溯；</li>
 *   <li>投诉「同一人对同一老师同一订单同时只能一条待处理」由 uk_complaint_pending 唯一索引兜底，
 *       与资格认证的 uk_cert_pending 是同一套思路。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GovernanceServiceImpl implements GovernanceService {

    private static final int FINISH_ADD = CreditRules.FINISH_ADD;
    private static final int DEFAULT_DEDUCT = 10;
    private static final int MAX_DEDUCT = 50;
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final List<String> ALLOWED_EXT = Arrays.asList("jpg", "jpeg", "png", "pdf");
    private static final String EVIDENCE_PATH_PREFIX = "/api/complaints/evidence/";
    private static final String FILE_NAME_PATTERN = "^[a-zA-Z0-9]{8,64}\\.(jpg|jpeg|png|pdf)$";

    private final AppointmentMapper appointmentMapper;
    private final TutorMapper tutorMapper;
    private final UserMapper userMapper;
    private final SubjectMapper subjectMapper;
    private final ComplaintMapper complaintMapper;
    private final TutorViolationMapper violationMapper;
    private final MessageService messageService;
    private final CacheEvictor cacheEvictor;

    @Value("${app.upload.dir}")
    private String uploadDir;

    // ==================== 课时账单 ====================

    @Override
    public BillVO bill(Long tutorUserId, String month) {
        String m = normalizeMonth(month);
        List<AppointmentEntity> orders = appointmentMapper.selectList(new LambdaQueryWrapper<AppointmentEntity>()
                .eq(AppointmentEntity::getTutorId, tutorUserId)
                .apply("DATE_FORMAT(appoint_date, '%Y-%m') = {0}", m)
                .orderByAsc(AppointmentEntity::getAppointDate)
                .orderByAsc(AppointmentEntity::getTimeSlot));
        List<BillItemVO> items = new ArrayList<>();
        int finished = 0;
        int minutes = 0;
        BigDecimal total = BigDecimal.ZERO;
        int pending = 0;
        for (AppointmentEntity a : orders) {
            int duration = slotMinutes(a.getTimeSlot());
            boolean done = a.getStatus() != null && a.getStatus() == 3;
            if (done) {
                finished++;
                minutes += duration;
                total = total.add(a.getTotalPrice() == null ? BigDecimal.ZERO : a.getTotalPrice());
            } else if (a.getStatus() != null && a.getStatus() <= 2) {
                pending++;
            }
            items.add(BillItemVO.builder()
                    .orderId(a.getId())
                    .orderNo(a.getOrderNo())
                    .studentName(MaskUtil.maskName(nameOf(a.getStudentId())))
                    .subjectName(subjectNameOf(a.getSubjectId()))
                    .appointDate(a.getAppointDate() == null ? null : a.getAppointDate().toString())
                    .timeSlot(a.getTimeSlot())
                    .durationMinutes(duration)
                    .unitPrice(priceOf(a.getTutorId()))
                    .amount(a.getTotalPrice())
                    .status(a.getStatus())
                    .statusName(statusName(a.getStatus()))
                    .build());
        }
        return BillVO.builder()
                .month(m)
                .finishedCount(finished)
                .finishedMinutes(minutes)
                .totalAmount(total)
                .pendingCount(pending)
                .items(items)
                .build();
    }

    @Override
    public String billCsv(Long tutorUserId, String month) {
        BillVO b = bill(tutorUserId, month);
        StringBuilder sb = new StringBuilder("\uFEFF"); // UTF-8 BOM，Excel 打开中文不乱码
        sb.append("账期,").append(b.getMonth()).append('\n');
        sb.append("已完成节数,").append(b.getFinishedCount()).append('\n');
        sb.append("已完成时长(小时),").append(round1(b.getFinishedMinutes() / 60.0)).append('\n');
        sb.append("已完成金额(元),").append(b.getTotalAmount()).append('\n');
        sb.append("未结算订单数,").append(b.getPendingCount()).append("\n\n");
        sb.append("订单号,学生,科目,授课日期,时段,时长(分钟),订单单价(元),订单金额(元),订单状态\n");
        for (BillItemVO i : b.getItems()) {
            sb.append(csv(i.getOrderNo())).append(',')
              .append(csv(i.getStudentName())).append(',')
              .append(csv(i.getSubjectName())).append(',')
              .append(csv(i.getAppointDate())).append(',')
              .append(csv(i.getTimeSlot())).append(',')
              .append(i.getDurationMinutes()).append(',')
              .append(i.getUnitPrice()).append(',')
              .append(i.getAmount()).append(',')
              .append(csv(i.getStatusName())).append('\n');
        }
        return sb.toString();
    }

    /** 时间段转分钟：08:00-10:00 → 120；解析失败按 120 计 */
    public static int slotMinutes(String slot) {
        if (slot == null || !slot.contains("-")) {
            return 120;
        }
        try {
            String[] parts = slot.split("-");
            int sh = Integer.parseInt(parts[0].trim().split(":")[0]);
            int eh = Integer.parseInt(parts[1].trim().split(":")[0]);
            int diff = (eh - sh) * 60;
            return diff > 0 ? diff : 120;
        } catch (Exception e) {
            return 120;
        }
    }

    // ==================== 投诉举报 ====================

    @Override
    public ComplaintVO submitComplaint(Long complainantId, ComplaintCreateDTO dto) {
        if (dto == null || dto.getTutorId() == null) {
            throw new BizException(400, "被投诉老师不能为空");
        }
        if (complainantId.equals(dto.getTutorId())) {
            throw new BizException(400, "不能投诉自己");
        }
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new BizException(400, "请填写投诉说明");
        }
        if (dto.getContent().trim().length() > 500) {
            throw new BizException(400, "投诉说明不能超过500字");
        }
        // 证据文件必须来自本系统的上传接口，防止把任意外链塞进库里
        if (StringUtils.hasText(dto.getEvidenceUrl())) {
            String url = dto.getEvidenceUrl().trim();
            if (!url.startsWith(EVIDENCE_PATH_PREFIX) || url.length() > 255) {
                throw new BizException(400, "证据文件路径不合法");
            }
        }
        List<TutorEntity> tutors = tutorMapper.selectList(new LambdaQueryWrapper<TutorEntity>()
                .eq(TutorEntity::getUserId, dto.getTutorId()));
        if (tutors.isEmpty()) {
            throw new BizException(404, "被投诉老师不存在");
        }
        // 关联订单必须是投诉人自己的订单，且老师要对得上
        if (dto.getOrderId() != null) {
            AppointmentEntity a = appointmentMapper.selectById(dto.getOrderId());
            if (a == null || !complainantId.equals(a.getStudentId())) {
                throw new BizException(403, "只能关联自己的订单");
            }
            if (!dto.getTutorId().equals(a.getTutorId())) {
                throw new BizException(400, "订单与老师不匹配");
            }
        }
        Long dup = complaintMapper.selectCount(new LambdaQueryWrapper<ComplaintEntity>()
                .eq(ComplaintEntity::getComplainantId, complainantId)
                .eq(ComplaintEntity::getTutorId, dto.getTutorId())
                .eq(ComplaintEntity::getStatus, 0)
                .eq(dto.getOrderId() != null, ComplaintEntity::getOrderId, dto.getOrderId()));
        if (dup != null && dup > 0) {
            throw new BizException(409, "你已经提交过投诉，管理员正在处理，请勿重复提交");
        }
        ComplaintEntity c = new ComplaintEntity();
        c.setComplainantId(complainantId);
        c.setTutorId(dto.getTutorId());
        c.setOrderId(dto.getOrderId());
        c.setType(dto.getType() == null ? 5 : dto.getType());
        c.setContent(dto.getContent().trim());
        c.setEvidenceUrl(dto.getEvidenceUrl());
        c.setStatus(0);
        c.setDeductScore(0);
        c.setCreateTime(LocalDateTime.now());
        try {
            complaintMapper.insert(c);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "你已经提交过投诉，管理员正在处理，请勿重复提交");
        }
        return toVO(c);
    }

    @Override
    public List<ComplaintVO> myComplaints(Long complainantId) {
        return complaintMapper.selectList(new LambdaQueryWrapper<ComplaintEntity>()
                        .eq(ComplaintEntity::getComplainantId, complainantId)
                        .orderByDesc(ComplaintEntity::getCreateTime))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<ComplaintVO> adminComplaints(Integer status) {
        LambdaQueryWrapper<ComplaintEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(status != null, ComplaintEntity::getStatus, status)
          .orderByAsc(ComplaintEntity::getStatus)
          .orderByDesc(ComplaintEntity::getCreateTime);
        return complaintMapper.selectList(qw).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public ComplaintVO handleComplaint(Long complaintId, boolean accept, Integer deduct, String note, Long adminId) {
        ComplaintEntity c = complaintMapper.selectById(complaintId);
        if (c == null) {
            throw new BizException(404, "投诉不存在");
        }
        if (c.getStatus() == null || c.getStatus() != 0) {
            throw new BizException(400, "该投诉已处理，不能重复处理");
        }
        if (!accept && (note == null || note.trim().isEmpty())) {
            throw new BizException(400, "驳回投诉需填写处理说明");
        }
        int d = accept ? (deduct == null || deduct <= 0 ? DEFAULT_DEDUCT : Math.min(deduct, MAX_DEDUCT)) : 0;
        c.setStatus(accept ? 1 : 2);
        c.setHandlerId(adminId);
        c.setHandleNote(note == null ? null : note.trim());
        c.setHandlerTime(LocalDateTime.now());
        c.setDeductScore(d);
        complaintMapper.updateById(c);

        if (accept) {
            TutorViolationEntity v = new TutorViolationEntity();
            v.setTutorId(c.getTutorId());
            v.setType(1);
            v.setRefType("complaint");
            v.setRefId(c.getId());
            v.setDeduct(d);
            v.setReason(typeLabel(c.getType()) + "：" + c.getContent());
            v.setOperatorId(adminId);
            v.setStatus(1);
            v.setCreateTime(LocalDateTime.now());
            violationMapper.insert(v);
            addCredit(c.getTutorId(), -d, 1);
        }

        // 双方站内通知（系统消息 fromId=0，type=3）
        String tutorMsg = accept
                ? "你有 1 条投诉被判定成立，扣减信用分 " + d + "，当前信用分 "
                    + currentCredit(c.getTutorId()) + "。如有异议可联系平台客服"
                : "你被投诉的事项经核实不成立，平台未作处罚";
        String studentMsg = accept
                ? "你提交的投诉已处理：投诉成立，平台已对该老师扣减信用分 " + d
                : "你提交的投诉已处理：经核实不成立，未作处罚";
        safeSend(c.getTutorId(), tutorMsg);
        safeSend(c.getComplainantId(), studentMsg);
        cacheEvictor.evictTutorList();
        return toVO(c);
    }

    // ==================== 信用分 ====================

    /**
     * 信用分变动：delta 为负表示扣分，violationDelta > 0 时累加违规次数。
     * 用「读到的实体 + updateById」而不是 LambdaUpdateWrapper：后者在单测里需要 MyBatis-Plus 的
     * 表元数据缓存（只有真实启动容器才初始化），实体更新既省一次查询又可单测。
     */
    public int addCredit(Long tutorUserId, int delta, int violationDelta) {
        List<TutorEntity> list = tutorMapper.selectList(new LambdaQueryWrapper<TutorEntity>()
                .eq(TutorEntity::getUserId, tutorUserId).last("LIMIT 1"));
        if (list.isEmpty()) {
            return CreditRules.MAX_SCORE;
        }
        TutorEntity t = list.get(0);
        int cur = t.getCreditScore() == null ? CreditRules.MAX_SCORE : t.getCreditScore();
        int next = CreditRules.apply(cur, delta);
        TutorEntity upd = new TutorEntity();
        upd.setId(t.getId());
        upd.setCreditScore(next);
        if (violationDelta > 0) {
            int vc = t.getViolationCount() == null ? 0 : t.getViolationCount();
            upd.setViolationCount(vc + violationDelta);
        }
        tutorMapper.updateById(upd);
        cacheEvictor.evictTutorList();
        log.info("[信用分] userId={} {} -> {}（delta={}）", tutorUserId, cur, next, delta);
        return next;
    }

    /** 完成一单：加信用分（上限夹紧，由调用方保证只调一次） */
    @Override
    public void onOrderFinished(Long tutorUserId) {
        addCredit(tutorUserId, FINISH_ADD, 0);
    }

    // ==================== 违规记录（管理端） ====================

    @Override
    public List<TutorViolationVO> violations(Long tutorId, Integer limit) {
        int lim = (limit == null || limit <= 0) ? 100 : Math.min(limit, 200);
        List<TutorViolationEntity> list = violationMapper.selectList(new LambdaQueryWrapper<TutorViolationEntity>()
                .eq(tutorId != null, TutorViolationEntity::getTutorId, tutorId)
                .orderByDesc(TutorViolationEntity::getCreateTime)
                .orderByDesc(TutorViolationEntity::getId)
                .last("LIMIT " + lim));
        return list.stream().map(this::violationToVO).collect(Collectors.toList());
    }

    private TutorViolationVO violationToVO(TutorViolationEntity v) {
        return TutorViolationVO.builder()
                .id(v.getId())
                .tutorId(v.getTutorId())
                .tutorName(nameOf(v.getTutorId()))
                .type(v.getType())
                .typeLabel(violationTypeLabel(v.getType()))
                .refType(v.getRefType())
                .refId(v.getRefId())
                .deduct(v.getDeduct())
                .reason(v.getReason())
                .operatorId(v.getOperatorId())
                .operatorName(v.getOperatorId() == null ? "系统" : nameOf(v.getOperatorId()))
                .status(v.getStatus())
                .createTime(v.getCreateTime())
                .build();
    }

    public static String violationTypeLabel(Integer t) {
        if (t == null) {
            return "其他";
        }
        switch (t) {
            case 1: return "投诉成立";
            case 2: return "超时未确认";
            case 3: return "频繁拒绝";
            default: return "其他";
        }
    }

    // ==================== 投诉证据文件 ====================

    @Override
    public String uploadEvidence(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BizException(400, "请选择要上传的证据文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(400, "证据文件不能超过 5MB");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(400, "仅支持 JPG、PNG、PDF 格式的证据文件");
        }
        // 文件名由服务端生成，不采用客户端文件名，避免路径穿越与同名覆盖
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(uploadDir, "complaint", String.valueOf(userId));
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("投诉证据保存失败: {}", e.getMessage());
            throw new BizException(500, "证据文件保存失败，请稍后重试");
        }
        return EVIDENCE_PATH_PREFIX + userId + "/" + fileName;
    }

    @Override
    public Resource loadEvidence(Long requesterId, Integer requesterRole, Long ownerUserId, String fileName) {
        if (ownerUserId == null || !StringUtils.hasText(fileName) || !fileName.matches(FILE_NAME_PATTERN)) {
            throw new BizException(404, "文件不存在");
        }
        boolean isAdmin = requesterRole != null && requesterRole == UserContext.ROLE_ADMIN;
        if (!isAdmin && !ownerUserId.equals(requesterId)) {
            throw new BizException(403, "无权查看该证据文件");
        }
        Path base = Paths.get(uploadDir, "complaint").toAbsolutePath().normalize();
        Path target = base.resolve(ownerUserId.toString()).resolve(fileName).normalize();
        if (!target.startsWith(base) || !Files.exists(target)) {
            throw new BizException(404, "文件不存在");
        }
        return new FileSystemResource(target);
    }

    // ==================== 内部工具 ====================

    private int currentCredit(Long tutorUserId) {
        List<TutorEntity> list = tutorMapper.selectList(new LambdaQueryWrapper<TutorEntity>()
                .eq(TutorEntity::getUserId, tutorUserId).last("LIMIT 1"));
        return list.isEmpty() || list.get(0).getCreditScore() == null
                ? CreditRules.MAX_SCORE : list.get(0).getCreditScore();
    }

    private void safeSend(Long toId, String content) {
        try {
            messageService.send(0L, toId, 3, content);
        } catch (Exception e) {
            log.warn("投诉通知发送失败（不影响处理结果）: {}", e.getMessage());
        }
    }

    private String nameOf(Long uid) {
        UserEntity u = uid == null ? null : userMapper.selectById(uid);
        return u == null || u.getRealName() == null ? "" : u.getRealName();
    }

    private String subjectNameOf(Long sid) {
        SubjectEntity s = sid == null ? null : subjectMapper.selectById(sid);
        return s == null ? "" : s.getName();
    }

    /** 老师当前资费（账单展示用；金额本身取订单快照 total_price） */
    private BigDecimal priceOf(Long tutorUserId) {
        List<TutorEntity> list = tutorMapper.selectList(new LambdaQueryWrapper<TutorEntity>()
                .eq(TutorEntity::getUserId, tutorUserId).last("LIMIT 1"));
        return list.isEmpty() || list.get(0).getPrice() == null ? BigDecimal.ZERO : list.get(0).getPrice();
    }

    private ComplaintVO toVO(ComplaintEntity c) {
        AppointmentEntity a = c.getOrderId() == null ? null : appointmentMapper.selectById(c.getOrderId());
        return ComplaintVO.builder()
                .id(c.getId())
                .complainantId(c.getComplainantId())
                .complainantName(MaskUtil.maskName(nameOf(c.getComplainantId())))
                .tutorId(c.getTutorId())
                .tutorName(nameOf(c.getTutorId()))
                .orderId(c.getOrderId())
                .orderNo(a == null ? null : a.getOrderNo())
                .type(c.getType())
                .typeLabel(typeLabel(c.getType()))
                .content(c.getContent())
                .evidenceUrl(c.getEvidenceUrl())
                .status(c.getStatus())
                .statusLabel(statusLabel(c.getStatus()))
                .handlerId(c.getHandlerId())
                .handleNote(c.getHandleNote())
                .handlerTime(c.getHandlerTime())
                .deductScore(c.getDeductScore())
                .createTime(c.getCreateTime())
                .build();
    }

    public static String typeLabel(Integer t) {
        if (t == null) {
            return "其他";
        }
        switch (t) {
            case 1: return "爽约";
            case 2: return "私改价格";
            case 3: return "骚扰辱骂";
            case 4: return "虚假信息";
            default: return "其他";
        }
    }

    public static String statusLabel(Integer s) {
        if (s == null) {
            return "未知";
        }
        switch (s) {
            case 0: return "待处理";
            case 1: return "成立";
            case 2: return "驳回";
            default: return "未知";
        }
    }

    static String statusName(Integer s) {
        if (s == null) {
            return "未知";
        }
        switch (s) {
            case 0: return "待确认";
            case 1: return "已预约";
            case 2: return "授课中";
            case 3: return "已完成";
            case 4: return "已取消";
            case 5: return "已拒绝";
            default: return "未知";
        }
    }

    private static String normalizeMonth(String month) {
        if (month == null || !month.matches("^\\d{4}-\\d{2}$")) {
            throw new BizException(400, "月份格式应为 yyyy-MM，例如 2026-11");
        }
        return month;
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
