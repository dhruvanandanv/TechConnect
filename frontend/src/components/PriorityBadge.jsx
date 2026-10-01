import React from 'react';

const PRIORITY_CONFIG = {
  LOW: {
    label: 'Low',
    className: 'badge-priority-low',
    icon: 'bi-arrow-down',
  },
  MEDIUM: {
    label: 'Medium',
    className: 'badge-priority-medium',
    icon: 'bi-dash-lg',
  },
  HIGH: {
    label: 'High',
    className: 'badge-priority-high',
    icon: 'bi-arrow-up',
  },
  CRITICAL: {
    label: 'Critical',
    className: 'badge-priority-critical',
    icon: 'bi-exclamation-octagon-fill',
  },
};

const PriorityBadge = ({ priority }) => {
  const config = PRIORITY_CONFIG[priority] || {
    label: priority || 'Medium',
    className: 'badge-priority-medium',
    icon: 'bi-dash',
  };

  return (
    <span className={`badge-custom ${config.className}`}>
      <i className={`bi ${config.icon}`}></i>
      {config.label}
    </span>
  );
};

export default PriorityBadge;
