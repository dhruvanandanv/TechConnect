package com.techconnect.service.impl;

import com.techconnect.dto.*;
import com.techconnect.entity.*;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import com.techconnect.exception.InvalidTicketAssignmentException;
import com.techconnect.exception.InvalidTicketStatusTransitionException;
import com.techconnect.exception.TicketAccessDeniedException;
import com.techconnect.exception.TicketNotFoundException;
import com.techconnect.mapper.TicketMapper;
import com.techconnect.repository.*;
import com.techconnect.service.SlaService;
import com.techconnect.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final TicketAssignmentRepository ticketAssignmentRepository;
    private final TicketStatusHistoryRepository ticketStatusHistoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final TicketMapper ticketMapper;
    private final SlaService slaService;

    // Allowed status transitions map based on the ITIL/ITSM workflow
    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = Map.of(
            TicketStatus.OPEN, Set.of(TicketStatus.ASSIGNED),
            TicketStatus.ASSIGNED, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.OPEN),
            TicketStatus.IN_PROGRESS, Set.of(TicketStatus.WAITING_FOR_USER, TicketStatus.RESOLVED, TicketStatus.ESCALATED),
            TicketStatus.WAITING_FOR_USER, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
            TicketStatus.ESCALATED, Set.of(TicketStatus.MANAGER_REVIEW, TicketStatus.IN_PROGRESS),
            TicketStatus.MANAGER_REVIEW, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.ASSIGNED, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.RESOLVED, Set.of(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS),
            TicketStatus.CLOSED, Collections.emptySet()
    );

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request, String currentUserEmail) {
        User requester = loadUserByEmail(currentUserEmail);

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElse(requester.getDepartment());
        } else {
            department = requester.getDepartment();
        }

        Ticket ticket = Ticket.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .category(request.getCategory())
                .priority(request.getPriority())
                .status(TicketStatus.OPEN)
                .createdBy(requester)
                .department(department)
                .build();

        // Determine and attach SLA policy and deadlines
        slaService.applySlaToNewTicket(ticket);

        Ticket savedTicket = ticketRepository.save(ticket);

        // Record initial status history
        TicketStatusHistory statusHistory = TicketStatusHistory.builder()
                .ticket(savedTicket)
                .changedBy(requester)
                .oldStatus(null)
                .newStatus(TicketStatus.OPEN)
                .changeReason("Ticket created by " + requester.getEmail())
                .build();
        ticketStatusHistoryRepository.save(statusHistory);

        // Record audit log
        recordAuditLog("TICKET_CREATED", "Ticket", savedTicket.getId(), requester,
                "Ticket #" + savedTicket.getId() + " created with priority " + savedTicket.getPriority());

        log.info("Ticket created successfully: ID {} by user {}", savedTicket.getId(), requester.getEmail());
        return ticketMapper.toTicketResponse(savedTicket);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketSummaryResponse> getMyTickets(
            String currentUserEmail,
            TicketStatus status,
            Priority priority,
            TicketCategory category,
            Pageable pageable) {

        User currentUser = loadUserByEmail(currentUserEmail);
        RoleName role = currentUser.getRole().getName();

        Specification<Ticket> spec = Specification.where(null);

        // Role-based visibility scoping
        switch (role) {
            case ROLE_EMPLOYEE ->
                spec = spec.and((root, query, cb) ->
                        cb.equal(root.get("createdBy").get("id"), currentUser.getId()));

            case ROLE_ENGINEER ->
                spec = spec.and((root, query, cb) -> cb.or(
                        cb.equal(root.get("assignedEngineer").get("id"), currentUser.getId()),
                        cb.equal(root.get("createdBy").get("id"), currentUser.getId()),
                        cb.and(cb.equal(root.get("status"), TicketStatus.OPEN), cb.isNull(root.get("assignedEngineer")))
                ));

            case ROLE_MANAGER -> {
                if (currentUser.getTeam() != null) {
                    spec = spec.and((root, query, cb) -> cb.or(
                            cb.equal(root.get("assignedTeam").get("id"), currentUser.getTeam().getId()),
                            cb.equal(root.get("createdBy").get("id"), currentUser.getId())
                    ));
                } else if (currentUser.getDepartment() != null) {
                    spec = spec.and((root, query, cb) -> cb.or(
                            cb.equal(root.get("department").get("id"), currentUser.getDepartment().getId()),
                            cb.equal(root.get("createdBy").get("id"), currentUser.getId())
                    ));
                } else {
                    spec = spec.and((root, query, cb) ->
                            cb.equal(root.get("createdBy").get("id"), currentUser.getId()));
                }
            }

            case ROLE_ADMIN -> {
                // Admin sees all tickets without ownership filter
            }
        }

        // Optional query filters
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (priority != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), priority));
        }
        if (category != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }

        Page<Ticket> page = ticketRepository.findAll(spec, pageable);
        return page.map(ticketMapper::toTicketSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id, String currentUserEmail) {
        Ticket ticket = loadTicketById(id);
        User currentUser = loadUserByEmail(currentUserEmail);

        assertCanViewTicket(ticket, currentUser);

        return ticketMapper.toTicketResponse(ticket);
    }

    @Override
    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request, String currentUserEmail) {
        Ticket ticket = loadTicketById(id);
        User currentUser = loadUserByEmail(currentUserEmail);

        RoleName role = currentUser.getRole().getName();
        boolean isCreator = ticket.getCreatedBy().getId().equals(currentUser.getId());
        boolean isAssignedEngineer = ticket.getAssignedEngineer() != null
                && ticket.getAssignedEngineer().getId().equals(currentUser.getId());
        boolean isAdminOrManager = (role == RoleName.ROLE_ADMIN || role == RoleName.ROLE_MANAGER);

        if (role == RoleName.ROLE_EMPLOYEE) {
            if (!isCreator) {
                throw new TicketAccessDeniedException("You do not have permission to update this ticket");
            }
            if (ticket.getStatus() != TicketStatus.OPEN) {
                throw new TicketAccessDeniedException("Employees can only update tickets that are still in OPEN status");
            }

            // Employee updates title, description, category, priority
            if (request.getTitle() != null && !request.getTitle().isBlank()) {
                ticket.setTitle(request.getTitle().trim());
            }
            if (request.getDescription() != null && !request.getDescription().isBlank()) {
                ticket.setDescription(request.getDescription().trim());
            }
            if (request.getCategory() != null) {
                ticket.setCategory(request.getCategory());
            }
            if (request.getPriority() != null) {
                ticket.setPriority(request.getPriority());
            }
        } else if (isAssignedEngineer) {
            // Engineer updates category, priority, resolution description
            if (request.getCategory() != null) {
                ticket.setCategory(request.getCategory());
            }
            if (request.getPriority() != null) {
                ticket.setPriority(request.getPriority());
            }
            if (request.getResolutionDescription() != null) {
                ticket.setResolutionDescription(request.getResolutionDescription());
            }
        } else if (isAdminOrManager) {
            // Manager/Admin can update title, description, category, priority, resolutionDescription
            if (request.getTitle() != null && !request.getTitle().isBlank()) {
                ticket.setTitle(request.getTitle().trim());
            }
            if (request.getDescription() != null && !request.getDescription().isBlank()) {
                ticket.setDescription(request.getDescription().trim());
            }
            if (request.getCategory() != null) {
                ticket.setCategory(request.getCategory());
            }
            if (request.getPriority() != null) {
                ticket.setPriority(request.getPriority());
            }
            if (request.getResolutionDescription() != null) {
                ticket.setResolutionDescription(request.getResolutionDescription());
            }
        } else {
            throw new TicketAccessDeniedException("You do not have permission to update this ticket");
        }

        Ticket updatedTicket = ticketRepository.save(ticket);
        recordAuditLog("TICKET_UPDATED", "Ticket", ticket.getId(), currentUser, "Ticket details updated");

        log.info("Ticket #{} updated by user {}", id, currentUser.getEmail());
        return ticketMapper.toTicketResponse(updatedTicket);
    }

    @Override
    @Transactional
    public TicketResponse updateTicketStatus(Long id, TicketStatusUpdateRequest request, String currentUserEmail) {
        Ticket ticket = loadTicketById(id);
        User currentUser = loadUserByEmail(currentUserEmail);

        TicketStatus currentStatus = ticket.getStatus();
        TicketStatus targetStatus = request.getStatus();

        // 1. Validate user role permissions for this transition
        validateRoleStatusPermission(ticket, currentUser, currentStatus, targetStatus);

        // 2. Validate workflow state machine transition
        validateStatusTransition(currentStatus, targetStatus);

        // 3. Apply state transition logic
        ticket.setStatus(targetStatus);

        if (targetStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(LocalDateTime.now());
            if (request.getResolutionDescription() != null && !request.getResolutionDescription().isBlank()) {
                ticket.setResolutionDescription(request.getResolutionDescription());
            } else if (request.getReason() != null && !request.getReason().isBlank()) {
                ticket.setResolutionDescription(request.getReason());
            }
        } else if (currentStatus == TicketStatus.RESOLVED && targetStatus == TicketStatus.IN_PROGRESS) {
            // Reopened
            ticket.setResolvedAt(null);
        }

        // Trigger SLA state transitions (pause, resume, resolution milestone)
        slaService.handleStatusTransition(ticket, currentStatus, targetStatus, currentUser);

        Ticket savedTicket = ticketRepository.save(ticket);

        // 4. Record status history
        TicketStatusHistory statusHistory = TicketStatusHistory.builder()
                .ticket(savedTicket)
                .changedBy(currentUser)
                .oldStatus(currentStatus)
                .newStatus(targetStatus)
                .changeReason(request.getReason() != null ? request.getReason() : "Status changed to " + targetStatus)
                .build();
        ticketStatusHistoryRepository.save(statusHistory);

        // 5. Record audit log
        recordAuditLog("STATUS_CHANGED", "Ticket", ticket.getId(), currentUser,
                "Status transitioned from " + currentStatus + " to " + targetStatus);

        log.info("Ticket #{} status transitioned from {} to {} by {}", id, currentStatus, targetStatus, currentUser.getEmail());
        return ticketMapper.toTicketResponse(savedTicket);
    }

    @Override
    @Transactional
    public TicketResponse assignTicket(Long id, TicketAssignmentRequest request, String currentUserEmail) {
        Ticket ticket = loadTicketById(id);
        User currentUser = loadUserByEmail(currentUserEmail);
        RoleName role = currentUser.getRole().getName();

        // Validate who can assign
        if (role == RoleName.ROLE_EMPLOYEE) {
            throw new TicketAccessDeniedException("Employees are not authorized to assign engineers to tickets");
        }

        if (role == RoleName.ROLE_ENGINEER) {
            // Engineers can only self-assign open, unassigned tickets
            if (!request.getEngineerId().equals(currentUser.getId())) {
                throw new TicketAccessDeniedException("Engineers can only self-assign open tickets to themselves");
            }
            if (ticket.getAssignedEngineer() != null && !ticket.getAssignedEngineer().getId().equals(currentUser.getId())) {
                throw new TicketAccessDeniedException("Ticket is already assigned to another engineer");
            }
        }

        // Validate target engineer
        User engineer = userRepository.findById(request.getEngineerId())
                .orElseThrow(() -> new InvalidTicketAssignmentException("Engineer not found with ID: " + request.getEngineerId()));

        RoleName engineerRole = engineer.getRole().getName();
        if (engineerRole != RoleName.ROLE_ENGINEER && engineerRole != RoleName.ROLE_ADMIN) {
            throw new InvalidTicketAssignmentException("Cannot assign ticket to a user without an ENGINEER or ADMIN role (User role: " + engineerRole + ")");
        }

        if (!Boolean.TRUE.equals(engineer.getIsActive())) {
            throw new InvalidTicketAssignmentException("Cannot assign ticket to an inactive engineer");
        }

        // Resolve assigned team
        Team assignedTeam = null;
        if (request.getTeamId() != null) {
            assignedTeam = teamRepository.findById(request.getTeamId())
                    .orElse(engineer.getTeam());
        } else {
            assignedTeam = engineer.getTeam();
        }

        // Assign engineer and team to ticket
        ticket.setAssignedEngineer(engineer);
        if (assignedTeam != null) {
            ticket.setAssignedTeam(assignedTeam);
        }

        // Transition status from OPEN to ASSIGNED if currently OPEN
        TicketStatus previousStatus = ticket.getStatus();
        if (previousStatus == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.ASSIGNED);
            TicketStatusHistory statusHistory = TicketStatusHistory.builder()
                    .ticket(ticket)
                    .changedBy(currentUser)
                    .oldStatus(previousStatus)
                    .newStatus(TicketStatus.ASSIGNED)
                    .changeReason("Auto-transition to ASSIGNED upon engineer assignment to " + engineer.getEmail())
                    .build();
            ticketStatusHistoryRepository.save(statusHistory);
        }

        // Operational milestone: engineer assignment counts as first response
        slaService.recordResponseMilestone(ticket, currentUser);

        Ticket savedTicket = ticketRepository.save(ticket);

        // Record historical assignment
        TicketAssignment assignment = TicketAssignment.builder()
                .ticket(savedTicket)
                .assignedBy(currentUser)
                .assignedEngineer(engineer)
                .assignedTeam(assignedTeam)
                .notes(request.getNotes())
                .build();
        ticketAssignmentRepository.save(assignment);

        // Record audit log
        recordAuditLog("TICKET_ASSIGNED", "Ticket", savedTicket.getId(), currentUser,
                "Assigned to " + engineer.getEmail() + " by " + currentUser.getEmail());

        log.info("Ticket #{} assigned to engineer {} by {}", id, engineer.getEmail(), currentUser.getEmail());
        return ticketMapper.toTicketResponse(savedTicket);
    }

    @Override
    @Transactional
    public TicketCommentResponse addComment(Long id, TicketCommentRequest request, String currentUserEmail) {
        Ticket ticket = loadTicketById(id);
        User currentUser = loadUserByEmail(currentUserEmail);

        assertCanViewTicket(ticket, currentUser);

        boolean isInternal = Boolean.TRUE.equals(request.getIsInternal());
        if (currentUser.getRole().getName() == RoleName.ROLE_EMPLOYEE) {
            // Employees can never create internal notes
            isInternal = false;
        }

        TicketComment comment = TicketComment.builder()
                .ticket(ticket)
                .author(currentUser)
                .content(request.getContent().trim())
                .isInternal(isInternal)
                .build();

        TicketComment savedComment = ticketCommentRepository.save(comment);

        // Operational milestone: staff comment on ticket counts as response
        slaService.recordResponseMilestone(ticket, currentUser);

        recordAuditLog("COMMENT_ADDED", "Ticket", ticket.getId(), currentUser,
                "Comment added (internal: " + isInternal + ")");

        log.info("Comment added to ticket #{} by user {}", id, currentUser.getEmail());
        return ticketMapper.toTicketCommentResponse(savedComment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketCommentResponse> getComments(Long id, String currentUserEmail) {
        Ticket ticket = loadTicketById(id);
        User currentUser = loadUserByEmail(currentUserEmail);

        assertCanViewTicket(ticket, currentUser);

        List<TicketComment> comments;
        if (currentUser.getRole().getName() == RoleName.ROLE_EMPLOYEE) {
            // Employees can only view public comments
            comments = ticketCommentRepository.findByTicketIdAndIsInternalFalseOrderByCreatedAtAsc(id);
        } else {
            // Engineers, managers, and admins see all comments (including internal notes)
            comments = ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(id);
        }

        return comments.stream()
                .map(ticketMapper::toTicketCommentResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketStatusHistoryResponse> getStatusHistory(Long id, String currentUserEmail) {
        Ticket ticket = loadTicketById(id);
        User currentUser = loadUserByEmail(currentUserEmail);

        assertCanViewTicket(ticket, currentUser);

        return ticketStatusHistoryRepository.findByTicketIdOrderByChangedAtAsc(id).stream()
                .map(ticketMapper::toTicketStatusHistoryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketAssignmentResponse> getAssignmentHistory(Long id, String currentUserEmail) {
        Ticket ticket = loadTicketById(id);
        User currentUser = loadUserByEmail(currentUserEmail);

        assertCanViewTicket(ticket, currentUser);

        return ticketAssignmentRepository.findByTicketIdOrderByAssignedAtDesc(id).stream()
                .map(ticketMapper::toTicketAssignmentResponse)
                .toList();
    }

    // =========================================================================
    // HELPER & AUTHORIZATION METHODS
    // =========================================================================

    private void validateStatusTransition(TicketStatus current, TicketStatus target) {
        if (current == target) {
            return;
        }

        Set<TicketStatus> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(current, Collections.emptySet());
        if (!allowedTargets.contains(target)) {
            throw new InvalidTicketStatusTransitionException(
                    "Invalid ticket status transition from " + current + " to " + target
            );
        }
    }

    private void validateRoleStatusPermission(Ticket ticket, User user, TicketStatus current, TicketStatus target) {
        RoleName role = user.getRole().getName();
        boolean isCreator = ticket.getCreatedBy().getId().equals(user.getId());
        boolean isAssignedEngineer = ticket.getAssignedEngineer() != null
                && ticket.getAssignedEngineer().getId().equals(user.getId());

        if (role == RoleName.ROLE_ADMIN) {
            return; // Admins can execute all valid transitions
        }

        if (role == RoleName.ROLE_MANAGER) {
            // Managers can perform valid transitions on tickets within team/department or assigned to them
            return;
        }

        if (role == RoleName.ROLE_ENGINEER) {
            if (!isAssignedEngineer && !isCreator) {
                throw new TicketAccessDeniedException("Engineers can only update status on tickets assigned to them");
            }
            // Engineers cannot transition to MANAGER_REVIEW or perform manager-specific actions
            if (target == TicketStatus.MANAGER_REVIEW) {
                throw new TicketAccessDeniedException("Only managers or administrators can review escalated tickets");
            }
            return;
        }

        if (role == RoleName.ROLE_EMPLOYEE) {
            if (!isCreator) {
                throw new TicketAccessDeniedException("You do not have permission to modify status on this ticket");
            }
            // Employees can only confirm resolution (RESOLVED -> CLOSED) or resume progress (WAITING_FOR_USER -> IN_PROGRESS)
            if (current == TicketStatus.RESOLVED && target == TicketStatus.CLOSED) {
                return;
            }
            if (current == TicketStatus.WAITING_FOR_USER && target == TicketStatus.IN_PROGRESS) {
                return;
            }
            throw new TicketAccessDeniedException(
                    "Employees are only permitted to close resolved tickets or resume waiting tickets"
            );
        }

        throw new TicketAccessDeniedException("Unauthorized to change ticket status");
    }

    private void assertCanViewTicket(Ticket ticket, User user) {
        RoleName role = user.getRole().getName();

        if (role == RoleName.ROLE_ADMIN) {
            return;
        }

        // Creator can always view their own ticket
        if (ticket.getCreatedBy().getId().equals(user.getId())) {
            return;
        }

        // Assigned engineer can view
        if (ticket.getAssignedEngineer() != null && ticket.getAssignedEngineer().getId().equals(user.getId())) {
            return;
        }

        // Engineers can view open/unassigned tickets to evaluate and self-assign
        if (role == RoleName.ROLE_ENGINEER && ticket.getStatus() == TicketStatus.OPEN && ticket.getAssignedEngineer() == null) {
            return;
        }

        // Manager can view tickets assigned to their team
        if (role == RoleName.ROLE_MANAGER) {
            if (user.getTeam() != null && ticket.getAssignedTeam() != null
                    && ticket.getAssignedTeam().getId().equals(user.getTeam().getId())) {
                return;
            }
            if (user.getDepartment() != null && ticket.getDepartment() != null
                    && ticket.getDepartment().getId().equals(user.getDepartment().getId())) {
                return;
            }
        }

        throw new TicketAccessDeniedException("You do not have permission to access ticket #" + ticket.getId());
    }

    private User loadUserByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new TicketAccessDeniedException("Authenticated user not found in database: " + email));
    }

    private Ticket loadTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }

    private void recordAuditLog(String action, String entityName, Long entityId, User performedBy, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .entityName(entityName)
                    .entityId(entityId)
                    .performedBy(performedBy)
                    .details(details)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log for {} on {}: {}", action, entityName, e.getMessage());
        }
    }
}
