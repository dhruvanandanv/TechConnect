package com.techconnect.service;

import com.techconnect.dto.BreachedTicketResponse;
import com.techconnect.dto.SlaSummaryResponse;
import com.techconnect.dto.TicketSlaResponse;
import com.techconnect.entity.Ticket;
import com.techconnect.entity.User;
import com.techconnect.entity.enums.TicketStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface SlaService {

    /**
     * Determines and attaches the applicable SLA policy and sets initial response and resolution deadlines
     * on a newly created ticket.
     */
    void applySlaToNewTicket(Ticket ticket);

    /**
     * Records response milestone if this is the first operational action by IT staff.
     */
    void recordResponseMilestone(Ticket ticket, User actor);

    /**
     * Handles SLA implications when a ticket's status changes (e.g. pause on WAITING_FOR_USER,
     * resume on leaving WAITING_FOR_USER, resolve on RESOLVED).
     */
    void handleStatusTransition(Ticket ticket, TicketStatus oldStatus, TicketStatus newStatus, User actor);

    /**
     * Retrieves SLA calculation details for a specific ticket, subject to ownership and role authorization.
     */
    TicketSlaResponse getTicketSla(Long ticketId, String currentUserEmail);

    /**
     * Dynamically calculates SLA statuses and remaining times for a ticket at a given point in time.
     */
    TicketSlaResponse calculateTicketSla(Ticket ticket, LocalDateTime now);

    /**
     * Retrieves list of breached tickets for managerial/administrative oversight.
     */
    List<BreachedTicketResponse> getBreachedTickets();

    /**
     * Retrieves aggregate SLA metrics for dashboard display.
     */
    SlaSummaryResponse getSlaSummary();
}
