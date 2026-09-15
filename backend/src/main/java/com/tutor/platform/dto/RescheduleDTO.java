package com.tutor.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 学生调课申请参数
 */
@Data
public class RescheduleDTO {

    @NotBlank(message = "新日期不能为空")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为 yyyy-MM-dd")
    private String newDate;

    @NotBlank(message = "新时间段不能为空")
    @Pattern(regexp = "\\d{2}:\\d{2}-\\d{2}:\\d{2}", message = "时间段格式应为 HH:mm-HH:mm")
    private String newSlot;
}
