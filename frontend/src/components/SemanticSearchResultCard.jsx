import React from 'react';
import { Link } from 'react-router-dom';
import { formatEnum } from '../utils/formatters';

const SemanticSearchResultCard = ({ result }) => {
  const similarityPercent = Math.round((result.similarity || 0) * 100);

  // Section badge color mapping
  const getSectionBadgeClass = (section) => {
    switch ((section || '').toUpperCase()) {
      case 'RESOLUTION':
        return 'bg-success bg-opacity-20 text-success border-success';
      case 'PROBLEM':
        return 'bg-danger bg-opacity-20 text-danger border-danger';
      case 'CAUSE':
        return 'bg-warning bg-opacity-20 text-warning border-warning';
      case 'SUMMARY':
        return 'bg-info bg-opacity-20 text-info border-info';
      default:
        return 'bg-secondary bg-opacity-20 text-light border-secondary';
    }
  };

  // Similarity color indicator
  const getSimilarityBadge = (score) => {
    if (score >= 80) return 'text-success border-success';
    if (score >= 60) return 'text-info border-info';
    return 'text-warning border-warning';
  };

  return (
    <div className="card bg-dark border-secondary h-100 shadow-sm hover-shadow transition-all">
      <div className="card-body d-flex flex-column">
        {/* Top Header: Category, Section, and Similarity Badge */}
        <div className="d-flex justify-content-between align-items-center mb-2 flex-wrap gap-2">
          <div className="d-flex align-items-center gap-2">
            {result.category && (
              <span className="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 px-2 py-1">
                <i className="bi bi-tag-fill me-1"></i> {formatEnum(result.category)}
              </span>
            )}
            <span className={`badge border px-2 py-1 ${getSectionBadgeClass(result.section)}`}>
              Section: {result.section || 'CONTENT'}
            </span>
          </div>

          <div
            className={`badge bg-dark border px-2 py-1 ${getSimilarityBadge(similarityPercent)}`}
            title={`Cosine similarity score: ${result.similarity}`}
          >
            <i className="bi bi-graph-up-arrow me-1"></i>
            Semantic similarity: {similarityPercent}%
          </div>
        </div>

        {/* Article Title */}
        <h5 className="card-title text-white mb-2">
          <Link
            to={`/knowledge/articles/${result.articleId}`}
            className="text-decoration-none text-white text-hover-primary"
          >
            {result.title || `Article #${result.articleId}`}
          </Link>
        </h5>

        {/* Content Snippet */}
        <div className="p-3 bg-black bg-opacity-40 rounded border border-secondary border-opacity-25 mb-3 flex-grow-1">
          <p
            className="card-text text-light small mb-0 font-monospace"
            style={{ whiteSpace: 'pre-wrap', lineHeight: '1.45' }}
          >
            {result.content}
          </p>
        </div>

        {/* Tags Row */}
        {result.tags && result.tags.length > 0 && (
          <div className="d-flex flex-wrap gap-1 mb-3">
            {result.tags.map((tag) => (
              <span
                key={tag}
                className="badge bg-secondary bg-opacity-25 text-light fw-normal"
                style={{ fontSize: '0.72rem' }}
              >
                #{tag}
              </span>
            ))}
          </div>
        )}

        {/* Bottom Actions */}
        <div className="border-top border-secondary border-opacity-50 pt-2 mt-auto d-flex justify-content-between align-items-center">
          <span className="text-secondary small">
            Chunk ID: <span className="font-monospace">{result.chunkId}</span> (v{result.articleVersion || 1})
          </span>
          <Link
            to={`/knowledge/articles/${result.articleId}`}
            className="btn btn-sm btn-outline-info d-inline-flex align-items-center gap-1"
          >
            <span>View Full Article</span>
            <i className="bi bi-arrow-right"></i>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default SemanticSearchResultCard;
