import React from 'react';

const STATUS_CONFIG = {
  OPEN: {
    label: 'Open',
    className: 'badge-status-open',
    icon: 'bi-record-circle',
  },
  ASSIGNED: {
    label: 'Assigned',
    className: 'badge-status-assigned',
    icon: 'bi-person-check',
  },
  IN_PROGRESS: {
    label: 'In Progress',
    className: 'badge-status-in-progress',
    icon: 'bi-gear-wide-connected',
  },
  WAITING_FOR_USER: {
    label: 'Waiting For User',
    className: 'badge-status-waiting',
    icon: 'bi-pause-circle',
  },
  RESOLVED: {
    label: 'Resolved',
    className: 'badge-status-resolved',
    icon: 'bi-check-circle-fill',
  },
  CLOSED: {
    label: 'Closed',
    className: 'badge-status-closed',
    icon: 'bi-archive',
  },
  ESCALATED: {
    label: 'Escalated',
    className: 'badge-status-escalated',
    icon: 'bi-arrow-up-circle-fill',
  },
  MANAGER_REVIEW: {
    label: 'Manager Review',
    className: 'badge-status-manager-review',
    icon: 'bi-eye-fill',
  },
};

const StatusBadge = ({ status }) => {
  const config = STATUS_CONFIG[status] || {
    label: status || 'Unknown',
    className: 'badge-status-open',
    icon: 'bi-circle',
  };

  return (
    <span className={`badge-custom ${config.className}`}>
      <i className={`bi ${config.icon}`}></i>
      {config.label}
    </span>
  );
};

export default StatusBadge;
