package com.techconnect.repository;

import com.techconnect.entity.Ticket;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Page<Ticket> findByCreatedById(Long userId, Pageable pageable);

    Page<Ticket> findByAssignedEngineerId(Long engineerId, Pageable pageable);

    Page<Ticket> findByAssignedTeamId(Long teamId, Pageable pageable);

    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    Page<Ticket> findByPriority(Priority priority, Pageable pageable);

    Page<Ticket> findByCategory(TicketCategory category, Pageable pageable);

    long countByStatus(TicketStatus status);

    long countByPriority(Priority priority);

    long countByCreatedById(Long userId);

    long countByAssignedEngineerIdAndStatus(Long engineerId, TicketStatus status);

    @Query("SELECT t FROM Ticket t WHERE t.status NOT IN ('RESOLVED', 'CLOSED') AND t.slaDeadline < :now")
    List<Ticket> findBreachedTickets(@Param("now") LocalDateTime now);

    @Query("SELECT t FROM Ticket t WHERE t.status NOT IN ('RESOLVED', 'CLOSED') AND t.slaDeadline BETWEEN :now AND :threshold")
    List<Ticket> findTicketsApproachingDeadline(@Param("now") LocalDateTime now, @Param("threshold") LocalDateTime threshold);

    List<Ticket> findByStatusNot(TicketStatus status);
}
