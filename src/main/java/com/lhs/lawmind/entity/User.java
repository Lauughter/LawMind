package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String role;
    private Date createTime;
    private Date updateTime;
    private Date lastLoginTime;
    private Integer isDeleted;

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}