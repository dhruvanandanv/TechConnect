import React, { useState } from 'react';
import { Link } from 'react-router-dom';

const getSectionBadgeClass = (section) => {
  switch (section?.toUpperCase()) {
    case 'RESOLUTION':
      return 'bg-success text-white';
    case 'PROBLEM':
      return 'bg-danger text-white';
    case 'CAUSE':
      return 'bg-warning text-dark';
    case 'SUMMARY':
      return 'bg-info text-dark';
    default:
      return 'bg-secondary text-white';
  }
};

const CopilotSourceCard = ({ source, index }) => {
  const [expanded, setExpanded] = useState(false);
  const similarityPct = Math.round((source.similarity || 0) * 100);

  return (
    <div className="card border-0 shadow-sm mb-3">
      <div className="card-body">
        <div className="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-2">
          <div>
            <div className="d-flex align-items-center gap-2 mb-1">
              <span className="badge bg-light text-muted border">Source #{index + 1}</span>
              <span className={`badge ${getSectionBadgeClass(source.section)}`}>
                {source.section || 'GENERAL'}
              </span>
              {source.category && (
                <span className="badge bg-light text-dark border">
                  {source.category}
                </span>
              )}
            </div>
            <h6 className="card-title fw-bold text-slate-900 mb-0">
              {source.title || `Article #${source.articleId}`}
            </h6>
          </div>
          <div className="d-flex align-items-center gap-2">
            <span
              className={`badge ${
                similarityPct >= 80 ? 'bg-success' : similarityPct >= 60 ? 'bg-primary' : 'bg-secondary'
              }`}
              title={`Cosine similarity: ${source.similarity}`}
            >
              <i className="bi bi-bullseye me-1"></i>
              {similarityPct}% Similarity
            </span>
          </div>
        </div>

        <div className="card-text text-muted small bg-light p-3 rounded mb-3">
          <p className="mb-0" style={{ whiteSpace: 'pre-line' }}>
            {expanded
              ? source.content
              : source.content.length > 220
              ? `${source.content.substring(0, 220)}...`
              : source.content}
          </p>
          {source.content.length > 220 && (
            <button
              type="button"
              className="btn btn-link btn-sm p-0 mt-1 text-decoration-none"
              onClick={() => setExpanded(!expanded)}
            >
              {expanded ? 'Show Less' : 'Show Full Chunk Content'}
            </button>
          )}
        </div>

        <div className="d-flex justify-content-between align-items-center">
          <span className="text-muted small">
            <i className="bi bi-file-earmark-text me-1"></i>
            ID: <code className="text-secondary">{source.articleId}</code> (v{source.articleVersion || 1})
          </span>
          <Link
            to={`/knowledge/articles/${source.articleId}`}
            className="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
          >
            <span>View Article</span>
            <i className="bi bi-arrow-up-right-square"></i>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default CopilotSourceCard;
