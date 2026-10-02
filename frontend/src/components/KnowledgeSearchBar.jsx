import React, { useState, useEffect } from 'react';

const KnowledgeSearchBar = ({ initialValue = '', onSearch, placeholder = 'Search knowledge base by keywords, symptoms, or tags...' }) => {
  const [searchTerm, setSearchTerm] = useState(initialValue);

  useEffect(() => {
    setSearchTerm(initialValue);
  }, [initialValue]);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSearch(searchTerm.trim());
  };

  const handleClear = () => {
    setSearchTerm('');
    onSearch('');
  };

  return (
    <form onSubmit={handleSubmit} className="knowledge-search-bar w-100">
      <div className="input-group input-group-lg shadow-sm">
        <span className="input-group-text bg-dark border-secondary text-secondary">
          <i className="bi bi-search fs-5 text-primary"></i>
        </span>
        <input
          type="text"
          className="form-control bg-dark text-light border-secondary fs-6"
          placeholder={placeholder}
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
        <button className="btn btn-primary px-4 fw-semibold" type="submit">
          Search
        </button>
      </div>
    </form>
  );
};

export default KnowledgeSearchBar;
