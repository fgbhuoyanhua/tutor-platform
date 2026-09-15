package com.tutor.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
@TableName("user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 加密密码，序列化时忽略 */
    @JsonIgnore
    private String password;

    /** 角色：1学生 2老师 3管理员 */
    private Integer role;

    private String realName;

    private String phone;

    private String avatar;

    /** 0禁用 1正常 */
    private Integer status;

    private LocalDateTime createTime;
}
