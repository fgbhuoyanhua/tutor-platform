package com.tutor.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 投诉举报请求参数
 */
@Data
public class ComplaintCreateDTO {

    /** 被投诉老师 user.id */
    @NotNull(message = "被投诉老师不能为空")
    private Long tutorId;

    /** 关联订单 id（可选） */
    private Long orderId;

    /** 1爽约 2私改价格 3骚扰辱骂 4虚假信息 5其他 */
    @NotNull(message = "请选择投诉类型")
    @Min(value = 1, message = "投诉类型不合法")
    @Max(value = 5, message = "投诉类型不合法")
    private Integer type;

    @NotBlank(message = "请填写投诉说明")
    @Size(max = 500, message = "投诉说明不能超过500字")
    private String content;

    /** 证据材料路径（可选，复用证明材料上传接口返回的路径） */
    private String evidenceUrl;
}
