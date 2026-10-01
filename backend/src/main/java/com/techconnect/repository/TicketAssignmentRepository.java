package com.techconnect.repository;

import com.techconnect.entity.TicketAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketAssignmentRepository extends JpaRepository<TicketAssignment, Long> {
    List<TicketAssignment> findByTicketIdOrderByAssignedAtDesc(Long ticketId);
}
