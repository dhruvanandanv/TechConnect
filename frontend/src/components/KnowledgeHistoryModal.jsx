import React from 'react';
import { formatDate } from '../utils/formatters';

const KnowledgeHistoryModal = ({ isOpen, onClose, history = [], loading = false }) => {
  if (!isOpen) return null;

  return (
    <div className="modal show d-block" tabIndex="-1" style={{ backgroundColor: 'rgba(0,0,0,0.7)' }}>
      <div className="modal-dialog modal-dialog-centered modal-lg">
        <div className="modal-content bg-dark border-secondary text-light">
          <div className="modal-header border-secondary">
            <h5 className="modal-title d-flex align-items-center gap-2">
              <i className="bi bi-clock-history text-primary"></i>
              Article Version & Audit History
            </h5>
            <button type="button" className="btn-close btn-close-white" onClick={onClose}></button>
          </div>

          <div className="modal-body">
            {loading ? (
              <div className="text-center py-4">
                <div className="spinner-border text-primary" role="status"></div>
                <div className="text-secondary small mt-2">Loading history records...</div>
              </div>
            ) : history.length === 0 ? (
              <div className="text-center py-4 text-secondary">
                <i className="bi bi-info-circle fs-3 d-block mb-2"></i>
                No historical revisions recorded for this article.
              </div>
            ) : (
              <div className="table-responsive">
                <table className="table table-dark table-hover align-middle mb-0">
                  <thead className="table-secondary">
                    <tr>
                      <th>Action</th>
                      <th>Version</th>
                      <th>Performed By</th>
                      <th>Date & Time</th>
                      <th>Details</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.map((item) => (
                      <tr key={item.id}>
                        <td>
                          <span className={`badge ${
                            item.action === 'PUBLISHED' ? 'bg-success' :
                            item.action === 'ARCHIVED' ? 'bg-secondary' :
                            item.action === 'CREATED' ? 'bg-primary' :
                            item.action === 'RESTORED' ? 'bg-info' : 'bg-warning text-dark'
                          }`}>
                            {item.action}
                          </span>
                        </td>
                        <td>
                          <span className="badge bg-secondary">v{item.version}</span>
                        </td>
                        <td>
                          <div className="fw-semibold small">{item.performedByName}</div>
                          <div className="text-secondary" style={{ fontSize: '0.72rem' }}>{item.performedByEmail}</div>
                        </td>
                        <td className="small text-secondary">
                          {formatDate(item.performedAt)}
                        </td>
                        <td className="small text-light">
                          {item.details || '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="modal-footer border-secondary">
            <button type="button" className="btn btn-secondary btn-sm" onClick={onClose}>
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default KnowledgeHistoryModal;
