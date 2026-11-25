package com.tutor.platform.service;

import com.tutor.platform.dto.CertSubmitDTO;
import com.tutor.platform.vo.TutorCertVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 老师资格认证服务：提交、状态查询、文件上传与鉴权读取、管理员审核
 */
public interface TutorCertService {

    /** 老师提交认证申请（同一时间只允许一条待审） */
    Long submit(Long tutorUserId, CertSubmitDTO dto);

    /** 老师查看自己的认证情况（无记录时返回仅含当前状态的对象） */
    TutorCertVO my(Long tutorUserId);

    /** 上传证明材料，返回带鉴权的访问路径 */
    String upload(Long tutorUserId, MultipartFile file);

    /** 读取证明文件：管理员可读任意，老师仅可读自己的 */
    Resource loadFile(Long requesterId, Integer requesterRole, Long ownerUserId, String fileName);

    /** 管理员：待审核的认证申请列表 */
    List<TutorCertVO> pendingList();

    /** 管理员：审核（通过/驳回），同步老师认证状态并发站内消息 */
    void audit(Long certId, boolean pass, String reason, Long adminId);
}
