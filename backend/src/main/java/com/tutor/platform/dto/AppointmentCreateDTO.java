package com.tutor.platform.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

/**
 * 学生提交预约请求参数
 */
@Data
public class AppointmentCreateDTO {

    @NotNull(message = "家教信息不能为空")
    private Long tutorId;

    @NotNull(message = "科目不能为空")
    private Long subjectId;

    @NotNull(message = "预约日期不能为空")
    @FutureOrPresent(message = "预约日期不能早于今天")
    private LocalDate appointDate;

    @NotNull(message = "时间段不能为空")
    @Pattern(regexp = "^\\d{2}:\\d{2}-\\d{2}:\\d{2}$", message = "时间段格式应为 HH:mm-HH:mm（如 09:00-11:00）")
    private String timeSlot;
}
