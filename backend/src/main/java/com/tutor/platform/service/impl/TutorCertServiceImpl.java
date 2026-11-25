package com.tutor.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tutor.platform.common.BizException;
import com.tutor.platform.common.CacheEvictor;
import com.tutor.platform.common.MaskUtil;
import com.tutor.platform.common.UserContext;
import com.tutor.platform.dto.CertSubmitDTO;
import com.tutor.platform.entity.TutorCertEntity;
import com.tutor.platform.entity.TutorEntity;
import com.tutor.platform.entity.UserEntity;
import com.tutor.platform.mapper.TutorCertMapper;
import com.tutor.platform.mapper.TutorMapper;
import com.tutor.platform.mapper.UserMapper;
import com.tutor.platform.service.MessageService;
import com.tutor.platform.service.TutorCertService;
import com.tutor.platform.vo.TutorCertVO;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 老师资格认证实现。
 *
 * <p>关键约束：
 * <ul>
 *   <li>同一老师同一时间只允许一条待审申请，由 uk_cert_pending(tutor_id, pending_flag) 兜底，服务层先查后插再捕获 DuplicateKeyException。</li>
 *   <li>证明材料存本地私有目录，只能通过带鉴权的接口读取，不映射为静态资源。</li>
 *   <li>认证通过后才把学校/专业/年级写入 tutor 表，避免未认证信息出现在前台列表。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TutorCertServiceImpl implements TutorCertService {

    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final List<String> ALLOWED_EXT = Arrays.asList("jpg", "jpeg", "png", "pdf");
    private static final String FILE_PATH_PREFIX = "/api/tutor-cert/file/";
    private static final String FILE_NAME_PATTERN = "^[a-zA-Z0-9]{8,64}\\.(jpg|jpeg|png|pdf)$";

    private final TutorCertMapper tutorCertMapper;
    private final TutorMapper tutorMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;
    private final CacheEvictor cacheEvictor;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public Long submit(Long tutorUserId, CertSubmitDTO dto) {
        if (dto == null) {
            throw new BizException(400, "提交内容不能为空");
        }
        // 1. 必须先有家教信息，认证结果才有展示载体
        List<TutorEntity> tutors = tutorMapper.selectList(
                new LambdaQueryWrapper<TutorEntity>().eq(TutorEntity::getUserId, tutorUserId));
        if (tutors.isEmpty()) {
            throw new BizException(400, "请先发布家教信息，再申请资格认证");
        }
        // 2. 字段校验
        validate(dto);
        // 3. 同一时间只允许一条待审
        Long pending = tutorCertMapper.selectCount(new LambdaQueryWrapper<TutorCertEntity>()
                .eq(TutorCertEntity::getTutorId, tutorUserId)
                .eq(TutorCertEntity::getStatus, 0));
        if (pending != null && pending > 0) {
            throw new BizException(409, "已有一条待审核的认证申请，请等待审核结果");
        }

        TutorCertEntity e = new TutorCertEntity();
        e.setTutorId(tutorUserId);
        e.setSchool(dto.getSchool().trim());
        e.setMajor(dto.getMajor().trim());
        e.setGradeLevel(dto.getGradeLevel().trim());
        e.setCertType(dto.getCertType());
        e.setCertNo(StringUtils.hasText(dto.getCertNo()) ? dto.getCertNo().trim() : null);
        e.setCertUrl(StringUtils.hasText(dto.getCertUrl()) ? dto.getCertUrl().trim() : null);
        e.setNote(StringUtils.hasText(dto.getNote()) ? dto.getNote().trim() : null);
        e.setStatus(0);
        e.setCreateTime(LocalDateTime.now());
        try {
            tutorCertMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            // 并发提交时由唯一索引兜底
            throw new BizException(409, "已有一条待审核的认证申请，请等待审核结果");
        }

        // 4. 老师整体状态置为待审
        tutorMapper.update(null, new LambdaUpdateWrapper<TutorEntity>()
                .eq(TutorEntity::getUserId, tutorUserId)
                .set(TutorEntity::getCertStatus, 1));
        cacheEvictor.evictTutorList();
        return e.getId();
    }

    @Override
    public TutorCertVO my(Long tutorUserId) {
        TutorEntity tutor = firstTutor(tutorUserId);
        Integer certStatus = tutor == null || tutor.getCertStatus() == null ? 0 : tutor.getCertStatus();
        List<TutorCertEntity> list = tutorCertMapper.selectList(new LambdaQueryWrapper<TutorCertEntity>()
                .eq(TutorCertEntity::getTutorId, tutorUserId)
                .orderByDesc(TutorCertEntity::getCreateTime)
                .orderByDesc(TutorCertEntity::getId)
                .last("LIMIT 1"));
        if (list.isEmpty()) {
            return TutorCertVO.builder()
                    .tutorId(tutorUserId)
                    .certStatus(certStatus)
                    .school(tutor == null ? null : tutor.getSchool())
                    .major(tutor == null ? null : tutor.getMajor())
                    .gradeLevel(tutor == null ? null : tutor.getGradeLevel())
                    .build();
        }
        return toVO(list.get(0), certStatus, false);
    }

    @Override
    public String upload(Long tutorUserId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BizException(400, "请选择要上传的证明文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(400, "证明文件不能超过 5MB");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(400, "仅支持 JPG、PNG、PDF 格式的证明材料");
        }
        // 文件名由服务端生成，不采用客户端文件名，避免路径穿越与同名覆盖
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(uploadDir, "cert", String.valueOf(tutorUserId));
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("证明材料保存失败: {}", e.getMessage());
            throw new BizException(500, "证明材料保存失败，请稍后重试");
        }
        return FILE_PATH_PREFIX + tutorUserId + "/" + fileName;
    }

    @Override
    public Resource loadFile(Long requesterId, Integer requesterRole, Long ownerUserId, String fileName) {
        if (ownerUserId == null || !StringUtils.hasText(fileName) || !fileName.matches(FILE_NAME_PATTERN)) {
            throw new BizException(404, "文件不存在");
        }
        // 越权拦截：管理员可读全部，其他角色只能读自己的
        boolean isAdmin = requesterRole != null && requesterRole == UserContext.ROLE_ADMIN;
        if (!isAdmin && !ownerUserId.equals(requesterId)) {
            throw new BizException(403, "无权查看该证明材料");
        }
        Path base = Paths.get(uploadDir, "cert").toAbsolutePath().normalize();
        Path target = base.resolve(ownerUserId.toString()).resolve(fileName).normalize();
        if (!target.startsWith(base) || !Files.exists(target)) {
            throw new BizException(404, "文件不存在");
        }
        return new FileSystemResource(target);
    }

    @Override
    public List<TutorCertVO> pendingList() {
        List<TutorCertEntity> list = tutorCertMapper.selectList(new LambdaQueryWrapper<TutorCertEntity>()
                .eq(TutorCertEntity::getStatus, 0)
                .orderByAsc(TutorCertEntity::getCreateTime));
        return list.stream()
                .map(c -> toVO(c, 1, true))
                .collect(Collectors.toList());
    }

    @Override
    public void audit(Long certId, boolean pass, String reason, Long adminId) {
        TutorCertEntity cert = tutorCertMapper.selectById(certId);
        if (cert == null) {
            throw new BizException(404, "认证申请不存在");
        }
        if (cert.getStatus() == null || cert.getStatus() != 0) {
            throw new BizException(400, "该认证申请已处理，不能重复审核");
        }
        cert.setStatus(pass ? 1 : 2);
        cert.setReviewerId(adminId);
        cert.setReviewTime(LocalDateTime.now());
        if (!pass) {
            cert.setReviewNote(StringUtils.hasText(reason) ? reason.trim() : "证明材料不清晰，请重新提交");
        }
        tutorCertMapper.updateById(cert);

        // 同步老师整体认证状态；通过时把学校/专业/年级落到 tutor 表供前台展示
        LambdaUpdateWrapper<TutorEntity> uw = new LambdaUpdateWrapper<TutorEntity>()
                .eq(TutorEntity::getUserId, cert.getTutorId())
                .set(TutorEntity::getCertStatus, pass ? 2 : 3);
        if (pass) {
            uw.set(TutorEntity::getSchool, cert.getSchool())
              .set(TutorEntity::getMajor, cert.getMajor())
              .set(TutorEntity::getGradeLevel, cert.getGradeLevel())
              .set(TutorEntity::getCertTime, LocalDateTime.now());
        }
        tutorMapper.update(null, uw);
        cacheEvictor.evictTutorList();

        // 站内消息通知老师（系统消息 fromId=0，type=3 与既有审核通知一致）
        String msg = pass
                ? "您的资格认证已通过，主页将展示已认证标识与院校信息"
                : "您的资格认证未通过，原因：" + cert.getReviewNote() + "，请核对后重新提交";
        try {
            messageService.send(0L, cert.getTutorId(), 3, msg);
        } catch (Exception e) {
            log.warn("认证结果消息发送失败（不影响审核结果）: {}", e.getMessage());
        }
    }

    /** 字段校验：长度、类型、编号必填、文件路径归属 */
    private void validate(CertSubmitDTO dto) {
        if (!StringUtils.hasText(dto.getSchool()) || dto.getSchool().trim().length() > 100) {
            throw new BizException(400, "就读院校不能为空且不超过100字");
        }
        if (!StringUtils.hasText(dto.getMajor()) || dto.getMajor().trim().length() > 100) {
            throw new BizException(400, "专业不能为空且不超过100字");
        }
        if (!StringUtils.hasText(dto.getGradeLevel()) || dto.getGradeLevel().trim().length() > 20) {
            throw new BizException(400, "年级不能为空且不超过20字");
        }
        if (dto.getCertType() == null || dto.getCertType() < 1 || dto.getCertType() > 5) {
            throw new BizException(400, "证明类型不合法");
        }
        if (dto.getCertType() == 2 && !StringUtils.hasText(dto.getCertNo())) {
            throw new BizException(400, "学信网在线验证报告需填写报告编号");
        }
        if (dto.getCertNo() != null && dto.getCertNo().trim().length() > 64) {
            throw new BizException(400, "证明编号不能超过64字");
        }
        if (dto.getNote() != null && dto.getNote().trim().length() > 500) {
            throw new BizException(400, "补充说明不能超过500字");
        }
        if (StringUtils.hasText(dto.getCertUrl())) {
            String url = dto.getCertUrl().trim();
            if (!url.startsWith(FILE_PATH_PREFIX) || url.length() > 255) {
                throw new BizException(400, "证明文件路径不合法");
            }
        }
    }

    private TutorEntity firstTutor(Long tutorUserId) {
        List<TutorEntity> list = tutorMapper.selectList(
                new LambdaQueryWrapper<TutorEntity>().eq(TutorEntity::getUserId, tutorUserId).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private TutorCertVO toVO(TutorCertEntity c, Integer certStatus, boolean maskPhone) {
        UserEntity u = userMapper.selectById(c.getTutorId());
        return TutorCertVO.builder()
                .id(c.getId())
                .tutorId(c.getTutorId())
                .tutorName(u == null ? null : u.getRealName())
                .phone(u == null ? null : (maskPhone ? MaskUtil.maskPhone(u.getPhone()) : u.getPhone()))
                .school(c.getSchool())
                .major(c.getMajor())
                .gradeLevel(c.getGradeLevel())
                .certType(c.getCertType())
                .certTypeLabel(certTypeLabel(c.getCertType()))
                .certNo(c.getCertNo())
                .certUrl(c.getCertUrl())
                .note(c.getNote())
                .status(c.getStatus())
                .reviewNote(c.getReviewNote())
                .reviewTime(c.getReviewTime())
                .createTime(c.getCreateTime())
                .certStatus(certStatus)
                .build();
    }

    private String certTypeLabel(Integer type) {
        if (type == null) {
            return "未知";
        }
        switch (type) {
            case 1: return "学生证";
            case 2: return "学信网在线验证报告";
            case 3: return "校园卡";
            case 4: return "校园邮箱";
            case 5: return "其他";
            default: return "未知";
        }
    }
}
