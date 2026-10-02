import React from 'react';

const KnowledgeArticleStatusBadge = ({ status }) => {
  switch (status) {
    case 'PUBLISHED':
      return (
        <span className="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25 px-2 py-1">
          <i className="bi bi-check-circle-fill me-1"></i> Published
        </span>
      );
    case 'DRAFT':
      return (
        <span className="badge bg-warning bg-opacity-10 text-warning border border-warning border-opacity-25 px-2 py-1">
          <i className="bi bi-pencil-fill me-1"></i> Draft
        </span>
      );
    case 'ARCHIVED':
      return (
        <span className="badge bg-secondary bg-opacity-10 text-secondary border border-secondary border-opacity-25 px-2 py-1">
          <i className="bi bi-archive-fill me-1"></i> Archived
        </span>
      );
    default:
      return <span className="badge bg-dark px-2 py-1">{status}</span>;
  }
};

export default KnowledgeArticleStatusBadge;
