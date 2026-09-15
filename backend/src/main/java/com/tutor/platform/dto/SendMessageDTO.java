package com.tutor.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生/老师私信发送参数
 */
@Data
public class SendMessageDTO {

    @NotNull(message = "接收人不能为空")
    private Long toId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 500, message = "消息内容不能超过500字")
    private String content;
}
