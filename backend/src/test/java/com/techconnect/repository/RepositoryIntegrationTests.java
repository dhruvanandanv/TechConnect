package com.techconnect.repository;

import com.techconnect.entity.*;
import com.techconnect.entity.enums.Priority;
import com.techconnect.entity.enums.RoleName;
import com.techconnect.entity.enums.TicketCategory;
import com.techconnect.entity.enums.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class RepositoryIntegrationTests {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SLARepository slaRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketCommentRepository commentRepository;

    @Autowired
    private TicketAssignmentRepository assignmentRepository;

    @Autowired
    private TicketStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private TicketAttachmentRepository attachmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Should verify seeded roles and SLA rules on startup")
    void testSeededBaselines() {
        assertThat(roleRepository.count()).isGreaterThanOrEqualTo(4);
        assertThat(roleRepository.findByName(RoleName.ROLE_ADMIN)).isPresent();
        assertThat(roleRepository.findByName(RoleName.ROLE_ENGINEER)).isPresent();

        assertThat(slaRepository.count()).isGreaterThanOrEqualTo(4);
        Optional<SLA> criticalSla = slaRepository.findByPriority(Priority.CRITICAL);
        assertThat(criticalSla).isPresent();
        assertThat(criticalSla.get().getResolutionTimeHours()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should create ticket with full relational lifecycle entities")
    void testTicketAndRelatedEntitiesLifecycle() {
        // 1. Fetch seeded user and department
        User employee = userRepository.findByEmail("employee@techconnect.com")
                .orElseThrow(() -> new IllegalStateException("Employee user should be seeded"));
        User engineer = userRepository.findByEmail("engineer@techconnect.com")
                .orElseThrow(() -> new IllegalStateException("Engineer user should be seeded"));
        Department dept = departmentRepository.findByCode("IT-OPS")
                .orElseThrow(() -> new IllegalStateException("IT-OPS department should be seeded"));

        // 2. Create and persist Ticket
        Ticket ticket = Ticket.builder()
                .title("Cannot connect to corporate VPN from remote office")
                .description("Getting error 800: The remote connection was not made because the attempted VPN tunnels failed.")
                .category(TicketCategory.VPN)
                .priority(Priority.HIGH)
                .status(TicketStatus.OPEN)
                .createdBy(employee)
                .department(dept)
                .slaDeadline(LocalDateTime.now().plusHours(4))
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        assertThat(savedTicket.getId()).isNotNull();
        assertThat(savedTicket.getCreatedAt()).isNotNull();

        // 3. Add Ticket Comment
        TicketComment comment = TicketComment.builder()
                .ticket(savedTicket)
                .author(employee)
                .content("I verified my home internet is stable.")
                .isInternal(false)
                .build();
        commentRepository.save(comment);

        List<TicketComment> comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(savedTicket.getId());
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).getContent()).contains("home internet");

        // 4. Assign Ticket to Engineer
        TicketAssignment assignment = TicketAssignment.builder()
                .ticket(savedTicket)
                .assignedBy(engineer)
                .assignedEngineer(engineer)
                .notes("Taking ownership of VPN ticket.")
                .build();
        assignmentRepository.save(assignment);

        savedTicket.setAssignedEngineer(engineer);
        savedTicket.setStatus(TicketStatus.IN_PROGRESS);
        ticketRepository.save(savedTicket);

        // 5. Record Status History
        TicketStatusHistory history = TicketStatusHistory.builder()
                .ticket(savedTicket)
                .changedBy(engineer)
                .oldStatus(TicketStatus.OPEN)
                .newStatus(TicketStatus.IN_PROGRESS)
                .changeReason("Engineer accepted ticket.")
                .build();
        statusHistoryRepository.save(history);

        List<TicketStatusHistory> histories = statusHistoryRepository.findByTicketIdOrderByChangedAtAsc(savedTicket.getId());
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getNewStatus()).isEqualTo(TicketStatus.IN_PROGRESS);

        // 6. Add Attachment
        TicketAttachment attachment = TicketAttachment.builder()
                .ticket(savedTicket)
                .uploadedBy(employee)
                .fileName("vpn_error_log.txt")
                .fileType("text/plain")
                .fileSize(1024L)
                .fileUrl("/uploads/tickets/vpn_error_log.txt")
                .build();
        attachmentRepository.save(attachment);

        assertThat(attachmentRepository.findByTicketId(savedTicket.getId())).hasSize(1);

        // 7. Notification
        Notification notification = Notification.builder()
                .recipient(employee)
                .ticket(savedTicket)
                .title("Ticket Assigned")
                .message("Your ticket has been assigned to Alex Engineer.")
                .type("TICKET_ASSIGNED")
                .build();
        notificationRepository.save(notification);

        assertThat(notificationRepository.countByRecipientIdAndIsReadFalse(employee.getId())).isGreaterThanOrEqualTo(1);

        // 8. Resolve and Submit Feedback
        savedTicket.setStatus(TicketStatus.RESOLVED);
        savedTicket.setResolvedAt(LocalDateTime.now());
        savedTicket.setResolutionDescription("Reset client certificate profile in VPN gateway.");
        ticketRepository.save(savedTicket);

        Feedback feedback = Feedback.builder()
                .ticket(savedTicket)
                .submittedBy(employee)
                .rating(5)
                .comments("Prompt and effective resolution, thank you!")
                .build();
        feedbackRepository.save(feedback);

        assertThat(feedbackRepository.findByTicketId(savedTicket.getId())).isPresent();
        assertThat(feedbackRepository.findAverageRating()).isEqualTo(5.0);

        // 9. Audit Log
        AuditLog auditLog = AuditLog.builder()
                .action("TICKET_RESOLVED")
                .entityName("Ticket")
                .entityId(savedTicket.getId())
                .performedBy(engineer)
                .details("Ticket resolved with certificate reset.")
                .build();
        auditLogRepository.save(auditLog);

        assertThat(auditLogRepository.findByEntityNameAndEntityIdOrderByTimestampDesc("Ticket", savedTicket.getId(), PageRequest.of(0, 10)).getContent())
                .hasSize(1);
    }
}
