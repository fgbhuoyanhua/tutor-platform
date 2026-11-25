package com.tutor.platform.service;

import com.tutor.platform.vo.BillVO;
import com.tutor.platform.vo.ComplaintVO;
import com.tutor.platform.dto.ComplaintCreateDTO;
import com.tutor.platform.vo.TutorViolationVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 治理与账单服务：
 * <ul>
 *   <li>老师课时账单（订单快照，按月聚合 + 逐笔明细）</li>
 *   <li>老师信用分与违规记录（投诉成立 / 完成订单加分）</li>
 *   <li>投诉举报：学生提交 → 管理员处理（成立自动扣分并记违规）</li>
 * </ul>
 */
public interface GovernanceService {

    /** 老师课时账单：month 形如 2026-11 */
    BillVO bill(Long tutorUserId, String month);

    /** 账单 CSV（UTF-8 带 BOM，Excel 直接打开不乱码） */
    String billCsv(Long tutorUserId, String month);

    /** 学生提交投诉 */
    ComplaintVO submitComplaint(Long complainantId, ComplaintCreateDTO dto);

    /** 投诉人查看自己提交的投诉 */
    List<ComplaintVO> myComplaints(Long complainantId);

    /** 管理员：投诉列表（status 为空则全部） */
    List<ComplaintVO> adminComplaints(Integer status);

    /**
     * 管理员处理投诉。
     *
     * @param accept    true 成立 / false 驳回
     * @param deduct    成立时扣减信用分（1~50，驳回时忽略）
     * @param note      处理说明（驳回必填）
     */
    ComplaintVO handleComplaint(Long complaintId, boolean accept, Integer deduct, String note, Long adminId);

    /** 订单完成：老师 +1 信用分（由订单状态机保证同一订单只调一次） */
    void onOrderFinished(Long tutorUserId);

    /** 管理员：违规记录列表（tutorId 为空查全部，按时间倒序） */
    List<TutorViolationVO> violations(Long tutorId, Integer limit);

    /** 学生上传投诉证据（jpg/png/pdf，≤5MB），返回后端访问路径 */
    String uploadEvidence(Long userId, MultipartFile file);

    /** 读取投诉证据（本人 / 管理员可读） */
    Resource loadEvidence(Long requesterId, Integer requesterRole, Long ownerUserId, String fileName);
}
