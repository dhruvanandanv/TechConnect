import React from 'react';

const SLA_CONFIG = {
  ON_TRACK: {
    label: 'On Track',
    className: 'badge-sla-on-track',
    icon: 'bi-shield-check',
  },
  AT_RISK: {
    label: 'At Risk',
    className: 'badge-sla-at-risk',
    icon: 'bi-shield-exclamation',
  },
  BREACHED: {
    label: 'Breached',
    className: 'badge-sla-breached',
    icon: 'bi-shield-x',
  },
  PAUSED: {
    label: 'Paused',
    className: 'badge-sla-paused',
    icon: 'bi-pause-circle',
  },
  COMPLETED: {
    label: 'Completed',
    className: 'badge-sla-completed',
    icon: 'bi-check2-all',
  },
};

const SlaBadge = ({ status }) => {
  const config = SLA_CONFIG[status] || {
    label: status || '—',
    className: 'badge-sla-paused',
    icon: 'bi-clock',
  };

  return (
    <span className={`badge-custom ${config.className}`}>
      <i className={`bi ${config.icon}`}></i>
      {config.label}
    </span>
  );
};

export default SlaBadge;
