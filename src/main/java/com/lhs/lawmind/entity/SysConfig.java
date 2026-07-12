package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

@Data
public class SysConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private Date createTime;
    private Date updateTime;
}