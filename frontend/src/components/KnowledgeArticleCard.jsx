import React from 'react';
import { Link } from 'react-router-dom';
import KnowledgeArticleStatusBadge from './KnowledgeArticleStatusBadge';
import { formatDate, formatEnum } from '../utils/formatters';

const KnowledgeArticleCard = ({ article, showStatus = false, onTagClick }) => {
  return (
    <div className="card bg-dark border-secondary h-100 shadow-sm hover-shadow transition-all">
      <div className="card-body d-flex flex-column">
        {/* Top metadata row */}
        <div className="d-flex justify-content-between align-items-center mb-2">
          <span className="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 px-2 py-1">
            <i className="bi bi-tag-fill me-1"></i> {formatEnum(article.category)}
          </span>
          {showStatus && <KnowledgeArticleStatusBadge status={article.status} />}
        </div>

        {/* Title */}
        <h5 className="card-title text-white mb-2">
          <Link
            to={`/knowledge/articles/${article.id}`}
            className="text-decoration-none text-white text-hover-primary"
          >
            {article.title}
          </Link>
        </h5>

        {/* Summary */}
        <p className="card-text text-secondary small flex-grow-1 line-clamp-3 mb-3">
          {article.summary}
        </p>

        {/* Tags */}
        {article.tags && article.tags.length > 0 && (
          <div className="d-flex flex-wrap gap-1 mb-3">
            {article.tags.map((tag) => (
              <span
                key={tag}
                className="badge bg-secondary bg-opacity-25 text-light fw-normal cursor-pointer"
                style={{ fontSize: '0.72rem' }}
                onClick={() => onTagClick && onTagClick(tag)}
              >
                #{tag}
              </span>
            ))}
          </div>
        )}

        {/* Card Footer Info */}
        <div className="border-top border-secondary border-opacity-50 pt-2 mt-auto d-flex justify-content-between align-items-center text-secondary" style={{ fontSize: '0.78rem' }}>
          <div>
            <span>By {article.authorName || 'IT Support'}</span>
            <span className="mx-1">•</span>
            <span>{formatDate(article.updatedAt || article.publishedAt)}</span>
          </div>
          <div className="d-flex align-items-center gap-2">
            <span title="Views">
              <i className="bi bi-eye me-1"></i> {article.viewCount || 0}
            </span>
            <span title="Helpful votes" className="text-success">
              <i className="bi bi-hand-thumbs-up me-1"></i> {article.helpfulCount || 0}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default KnowledgeArticleCard;
