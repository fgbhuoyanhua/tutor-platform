package com.tutor.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生提交评价请求参数
 */
@Data
public class EvaluationCreateDTO {

    @NotNull(message = "订单不能为空")
    private Long orderId;

    /** 评分1-5 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分需为1-5分")
    @Max(value = 5, message = "评分需为1-5分")
    private Integer score;

    @Size(max = 500, message = "评价内容不能超过500字")
    private String content;
}
