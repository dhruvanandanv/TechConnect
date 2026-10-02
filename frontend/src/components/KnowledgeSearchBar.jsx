import React, { useState, useEffect } from 'react';

const KnowledgeSearchBar = ({
  initialValue = '',
  searchType = 'SEMANTIC',
  onSearch,
  onSearchTypeChange,
}) => {
  const [searchTerm, setSearchTerm] = useState(initialValue);
  const [currentType, setCurrentType] = useState(searchType);

  useEffect(() => {
    setSearchTerm(initialValue);
  }, [initialValue]);

  useEffect(() => {
    setCurrentType(searchType);
  }, [searchType]);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSearch(searchTerm.trim(), currentType);
  };

  const handleClear = () => {
    setSearchTerm('');
    onSearch('', currentType);
  };

  const handleTypeToggle = (type) => {
    setCurrentType(type);
    if (onSearchTypeChange) {
      onSearchTypeChange(type);
    }
  };

  const isSemantic = currentType === 'SEMANTIC';

  return (
    <form onSubmit={handleSubmit} className="knowledge-search-bar w-100">
      {/* Mode selection pills */}
      <div className="d-flex align-items-center justify-content-between mb-2 px-1">
        <div className="btn-group btn-group-sm" role="group" aria-label="Search Type Selector">
          <button
            type="button"
            className={`btn ${isSemantic ? 'btn-info text-dark fw-bold' : 'btn-dark text-secondary'}`}
            onClick={() => handleTypeToggle('SEMANTIC')}
          >
            <i className="bi bi-cpu-fill me-1"></i>
            Semantic Search (AI Vector)
          </button>
          <button
            type="button"
            className={`btn ${!isSemantic ? 'btn-primary fw-bold' : 'btn-dark text-secondary'}`}
            onClick={() => handleTypeToggle('KEYWORD')}
          >
            <i className="bi bi-fonts me-1"></i>
            Keyword Search
          </button>
        </div>

        <span className="badge bg-dark border border-secondary text-secondary small d-none d-sm-inline-block">
          {isSemantic ? (
            <span><i className="bi bi-lightning text-info me-1"></i>Dense Vector (all-MiniLM-L6-v2)</span>
          ) : (
            <span><i className="bi bi-card-text text-primary me-1"></i>Multi-Field Regex Match</span>
          )}
        </span>
      </div>

      <div className="input-group input-group-lg shadow-sm">
        <span className="input-group-text bg-dark border-secondary text-secondary">
          {isSemantic ? (
            <i className="bi bi-cpu fs-5 text-info"></i>
          ) : (
            <i className="bi bi-search fs-5 text-primary"></i>
          )}
        </span>
        <input
          type="text"
          className="form-control bg-dark text-light border-secondary fs-6"
          placeholder={
            isSemantic
              ? "Describe your IT issue in natural language (e.g., 'VPN keeps disconnecting when working from home')..."
              : "Search knowledge base by keywords, symptoms, error codes, or tags..."
          }
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
        {searchTerm && (
          <button
            type="button"
            className="btn btn-dark border-secondary text-secondary"
            onClick={handleClear}
            title="Clear search"
          >
            <i className="bi bi-x-lg"></i>
          </button>
        )}
        <button
          className={`btn ${isSemantic ? 'btn-info text-dark' : 'btn-primary'} px-4 fw-semibold`}
          type="submit"
        >
          {isSemantic ? 'Semantic Search' : 'Keyword Search'}
        </button>
      </div>
    </form>
  );
};

export default KnowledgeSearchBar;
