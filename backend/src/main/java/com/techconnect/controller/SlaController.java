package com.techconnect.controller;

import com.techconnect.dto.BreachedTicketResponse;
import com.techconnect.dto.SlaSummaryResponse;
import com.techconnect.dto.TicketSlaResponse;
import com.techconnect.service.SlaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SlaController {

    private final SlaService slaService;

    /**
     * Retrieves SLA metrics and deadline state for a specific ticket.
     * Authorized based on ticket ownership/assignment/role.
     */
    @GetMapping("/api/tickets/{id}/sla")
    public ResponseEntity<TicketSlaResponse> getTicketSla(
            @PathVariable Long id,
            Authentication authentication) {
        log.debug("Fetching SLA for ticket #{} by user {}", id, authentication.getName());
        TicketSlaResponse response = slaService.getTicketSla(id, authentication.getName());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all currently breached tickets across the platform.
     * Restricted to Managers and Administrators.
     */
    @GetMapping("/api/sla/breached")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<BreachedTicketResponse>> getBreachedTickets() {
        log.debug("Fetching all breached tickets");
        List<BreachedTicketResponse> breached = slaService.getBreachedTickets();
        return ResponseEntity.ok(breached);
    }

    /**
     * Retrieves aggregate SLA performance indicators and active ticket counts.
     * Restricted to Managers and Administrators.
     */
    @GetMapping("/api/sla/summary")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<SlaSummaryResponse> getSlaSummary() {
        log.debug("Fetching SLA summary dashboard metrics");
        SlaSummaryResponse summary = slaService.getSlaSummary();
        return ResponseEntity.ok(summary);
    }
}
