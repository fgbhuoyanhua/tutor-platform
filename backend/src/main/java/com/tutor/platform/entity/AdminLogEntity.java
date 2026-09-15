package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员操作日志表
 */
@Data
@TableName("admin_log")
public class AdminLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作管理员ID */
    private Long adminId;

    /** 管理员用户名 */
    private String adminName;

    /** 操作类型：如 审核家教 / 用户状态变更 */
    private String action;

    /** 操作对象类型：tutor / user */
    private String targetType;

    /** 操作对象ID */
    private Long targetId;

    /** 操作详情 */
    private String detail;

    private LocalDateTime createTime;
}
