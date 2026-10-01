package com.techconnect.repository;

import com.techconnect.entity.SLA;
import com.techconnect.entity.enums.Priority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SLARepository extends JpaRepository<SLA, Long> {
    Optional<SLA> findByPriority(Priority priority);
    boolean existsByPriority(Priority priority);
}
