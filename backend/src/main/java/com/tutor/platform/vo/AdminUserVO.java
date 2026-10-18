package com.tutor.platform.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理端用户列表视图对象：手机号做脱敏，避免管理员列表页明文暴露完整号码。
 *
 * <p>密码字段不在此 VO 中，从结构上杜绝泄漏。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserVO {

    private Long id;

    private String username;

    /** 角色：1学生 2老师 3管理员 */
    private Integer role;

    private String realName;

    /** 脱敏手机号：138****8000 */
    private String phone;

    private String avatar;

    /** 0禁用 1正常 */
    private Integer status;

    private LocalDateTime createTime;
}
