import React from 'react';

const ErrorAlert = ({ message, onDismiss, variant = 'danger' }) => {
  if (!message) return null;

  return (
    <div className={`alert alert-${variant} alert-dismissible fade show d-flex align-items-center mb-3`} role="alert">
      <i className="bi bi-exclamation-triangle-fill me-2 fs-5"></i>
      <div className="flex-grow-1">{message}</div>
      {onDismiss && (
        <button
          type="button"
          className="btn-close"
          aria-label="Close"
          onClick={onDismiss}
        ></button>
      )}
    </div>
  );
};

export default ErrorAlert;
