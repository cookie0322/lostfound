package edu.campus.lostfound.audit;

import edu.campus.lostfound.data.entity.AuditLog;
import edu.campus.lostfound.data.mapper.AuditLogMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditRecorder {
    private final AuditLogMapper auditLogMapper;

    // 依赖注入 Mapper
    public AuditRecorder(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }
    public void record(String targetType, long targetId, String action,
                       String fromValue, String toValue,
                       Long operatorId, String operatorRole, String detailJson) {
        AuditLog log = new AuditLog();
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setAction(action);
        log.setFromValue(fromValue);
        log.setToValue(toValue);
        log.setOperatorId(operatorId);
        log.setOperatorRole(operatorRole);
        log.setDetail(detailJson);
        log.setCreatedAt(LocalDateTime.now()); // 设置创建时间

        // 插入数据库
        auditLogMapper.insert(log);
    }
}
