import React from 'react';
import { formatEnum } from '../utils/formatters';

const CATEGORIES = [
  { id: 'ALL', label: 'All Categories', icon: 'bi-grid' },
  { id: 'HARDWARE', label: 'Hardware', icon: 'bi-laptop' },
  { id: 'SOFTWARE', label: 'Software', icon: 'bi-code-square' },
  { id: 'NETWORK', label: 'Network', icon: 'bi-router' },
  { id: 'SECURITY', label: 'Security', icon: 'bi-shield-check' },
  { id: 'ACCESS_MANAGEMENT', label: 'Access Management', icon: 'bi-key' },
  { id: 'EMAIL', label: 'Email', icon: 'bi-envelope' },
  { id: 'VPN', label: 'VPN', icon: 'bi-globe-americas' },
  { id: 'OTHER', label: 'Other', icon: 'bi-three-dots' },
];

const KnowledgeCategoryFilter = ({ selectedCategory = 'ALL', onSelectCategory }) => {
  return (
    <div className="d-flex flex-wrap gap-2 py-2">
      {CATEGORIES.map((cat) => {
        const isSelected = selectedCategory === cat.id;
        return (
          <button
            key={cat.id}
            type="button"
            className={`btn btn-sm d-inline-flex align-items-center gap-2 rounded-pill px-3 py-2 transition-all ${
              isSelected
                ? 'btn-primary shadow-sm'
                : 'btn-outline-secondary text-light border-secondary bg-dark'
            }`}
            onClick={() => onSelectCategory(cat.id)}
          >
            <i className={`bi ${cat.icon}`}></i>
            <span>{cat.label}</span>
          </button>
        );
      })}
    </div>
  );
};

export default KnowledgeCategoryFilter;
