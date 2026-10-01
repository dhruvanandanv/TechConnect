package com.techconnect.mapper;

import com.techconnect.dto.*;
import com.techconnect.entity.Ticket;
import com.techconnect.entity.TicketAssignment;
import com.techconnect.entity.TicketComment;
import com.techconnect.entity.TicketStatusHistory;
import com.techconnect.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketMapper {

    private final UserMapper userMapper;

    public TicketResponse toTicketResponse(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        return TicketResponse.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .requester(userMapper.toUserResponse(ticket.getCreatedBy()))
                .assignedEngineer(userMapper.toUserResponse(ticket.getAssignedEngineer()))
                .teamId(ticket.getAssignedTeam() != null ? ticket.getAssignedTeam().getId() : null)
                .teamName(ticket.getAssignedTeam() != null ? ticket.getAssignedTeam().getName() : null)
                .departmentId(ticket.getDepartment() != null ? ticket.getDepartment().getId() : null)
                .departmentName(ticket.getDepartment() != null ? ticket.getDepartment().getName() : null)
                .slaDeadline(ticket.getSlaDeadline())
                .resolvedAt(ticket.getResolvedAt())
                .resolutionDescription(ticket.getResolutionDescription())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    public TicketSummaryResponse toTicketSummaryResponse(Ticket ticket) {
        if (ticket == null) {
            return null;
        }

        String requesterName = null;
        String requesterEmail = null;
        Long requesterId = null;
        if (ticket.getCreatedBy() != null) {
            requesterId = ticket.getCreatedBy().getId();
            requesterEmail = ticket.getCreatedBy().getEmail();
            requesterName = formatFullName(ticket.getCreatedBy());
        }

        String assignedEngineerName = null;
        Long assignedEngineerId = null;
        if (ticket.getAssignedEngineer() != null) {
            assignedEngineerId = ticket.getAssignedEngineer().getId();
            assignedEngineerName = formatFullName(ticket.getAssignedEngineer());
        }

        String teamName = ticket.getAssignedTeam() != null ? ticket.getAssignedTeam().getName() : null;
        String departmentName = ticket.getDepartment() != null ? ticket.getDepartment().getName() : null;

        return TicketSummaryResponse.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .requesterId(requesterId)
                .requesterName(requesterName)
                .requesterEmail(requesterEmail)
                .assignedEngineerId(assignedEngineerId)
                .assignedEngineerName(assignedEngineerName)
                .teamName(teamName)
                .departmentName(departmentName)
                .slaDeadline(ticket.getSlaDeadline())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    public TicketCommentResponse toTicketCommentResponse(TicketComment comment) {
        if (comment == null) {
            return null;
        }

        String authorName = null;
        String authorEmail = null;
        String authorRole = null;
        Long authorId = null;

        if (comment.getAuthor() != null) {
            authorId = comment.getAuthor().getId();
            authorEmail = comment.getAuthor().getEmail();
            authorName = formatFullName(comment.getAuthor());
            if (comment.getAuthor().getRole() != null) {
                authorRole = comment.getAuthor().getRole().getName().name();
            }
        }

        return TicketCommentResponse.builder()
                .id(comment.getId())
                .ticketId(comment.getTicket() != null ? comment.getTicket().getId() : null)
                .authorId(authorId)
                .authorName(authorName)
                .authorEmail(authorEmail)
                .authorRole(authorRole)
                .content(comment.getContent())
                .isInternal(comment.getIsInternal())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    public TicketStatusHistoryResponse toTicketStatusHistoryResponse(TicketStatusHistory history) {
        if (history == null) {
            return null;
        }

        String changedByName = null;
        Long changedById = null;
        if (history.getChangedBy() != null) {
            changedById = history.getChangedBy().getId();
            changedByName = formatFullName(history.getChangedBy());
        }

        return TicketStatusHistoryResponse.builder()
                .id(history.getId())
                .ticketId(history.getTicket() != null ? history.getTicket().getId() : null)
                .oldStatus(history.getOldStatus())
                .newStatus(history.getNewStatus())
                .changedById(changedById)
                .changedByName(changedByName)
                .changeReason(history.getChangeReason())
                .changedAt(history.getChangedAt())
                .build();
    }

    public TicketAssignmentResponse toTicketAssignmentResponse(TicketAssignment assignment) {
        if (assignment == null) {
            return null;
        }

        String engineerName = null;
        Long engineerId = null;
        if (assignment.getAssignedEngineer() != null) {
            engineerId = assignment.getAssignedEngineer().getId();
            engineerName = formatFullName(assignment.getAssignedEngineer());
        }

        String assignedByName = null;
        Long assignedById = null;
        if (assignment.getAssignedBy() != null) {
            assignedById = assignment.getAssignedBy().getId();
            assignedByName = formatFullName(assignment.getAssignedBy());
        }

        String teamName = assignment.getAssignedTeam() != null ? assignment.getAssignedTeam().getName() : null;
        Long teamId = assignment.getAssignedTeam() != null ? assignment.getAssignedTeam().getId() : null;

        return TicketAssignmentResponse.builder()
                .id(assignment.getId())
                .ticketId(assignment.getTicket() != null ? assignment.getTicket().getId() : null)
                .assignedEngineerId(engineerId)
                .assignedEngineerName(engineerName)
                .assignedById(assignedById)
                .assignedByName(assignedByName)
                .assignedTeamId(teamId)
                .assignedTeamName(teamName)
                .notes(assignment.getNotes())
                .assignedAt(assignment.getAssignedAt())
                .build();
    }

    private String formatFullName(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        return (first + " " + last).trim();
    }
}
