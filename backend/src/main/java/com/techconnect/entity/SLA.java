package com.techconnect.entity;

import com.techconnect.entity.enums.Priority;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sla_rules", indexes = {
    @Index(name = "idx_sla_priority", columnList = "priority")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SLA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private Priority priority;

    @Column(name = "resolution_time_hours", nullable = false)
    private Integer resolutionTimeHours;

    @Column(name = "warning_threshold_hours", nullable = false)
    private Integer warningThresholdHours;

    @Column(length = 255)
    private String description;
}
