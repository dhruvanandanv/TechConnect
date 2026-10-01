package com.techconnect.repository;

import com.techconnect.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByEntityNameAndEntityIdOrderByTimestampDesc(String entityName, Long entityId, Pageable pageable);
    List<AuditLog> findByPerformedByIdOrderByTimestampDesc(Long userId);
}
