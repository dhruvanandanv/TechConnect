package com.techconnect.entity.enums;

/**
 * Lifecycle states of a Service Level Agreement (SLA) objective.
 */
public enum SlaStatus {
    /**
     * Sufficient SLA duration remains (> 20% of total policy time).
     */
    ON_TRACK,

    /**
     * Approaching deadline (<= 20% of total policy time remains).
     */
    AT_RISK,

    /**
     * Deadline has elapsed without meeting the SLA milestone.
     */
    BREACHED,

    /**
     * SLA timer paused (e.g. while awaiting user clarification/feedback).
     */
    PAUSED,

    /**
     * The target milestone (first response or resolution) was achieved within deadline.
     */
    COMPLETED
}
