package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.SecurityAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecurityAuditLogMapper {
    
    int insert(SecurityAuditLog auditLog);
}
