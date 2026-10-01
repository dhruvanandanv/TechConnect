/**
 * TechConnect Utility Formatters
 */

export const formatDate = (dateString) => {
  if (!dateString) return '—';
  try {
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return dateString;
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
    }).format(date);
  } catch {
    return dateString;
  }
};

export const formatDurationMinutes = (minutes) => {
  if (minutes === null || minutes === undefined) return '—';
  if (minutes <= 0) return '0 min';

  const days = Math.floor(minutes / (24 * 60));
  const remainingHours = Math.floor((minutes % (24 * 60)) / 60);
  const remainingMins = minutes % 60;

  const parts = [];
  if (days > 0) parts.push(`${days}d`);
  if (remainingHours > 0) parts.push(`${remainingHours}h`);
  if (remainingMins > 0 || parts.length === 0) parts.push(`${remainingMins}m`);

  return parts.join(' ');
};

export const formatRole = (role) => {
  switch (role) {
    case 'ROLE_EMPLOYEE':
      return 'Employee';
    case 'ROLE_ENGINEER':
      return 'IT Engineer';
    case 'ROLE_MANAGER':
      return 'IT Manager';
    case 'ROLE_ADMIN':
      return 'Administrator';
    default:
      return role ? role.replace('ROLE_', '') : 'User';
  }
};

export const formatEnum = (value) => {
  if (!value) return '';
  return value
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
};
