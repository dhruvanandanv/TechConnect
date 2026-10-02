import React, { useState } from 'react';
import knowledgeService from '../services/knowledgeService';

const KnowledgeFeedback = ({ articleId, helpfulCount = 0, notHelpfulCount = 0, initialHasVoted = false }) => {
  const [helpful, setHelpful] = useState(helpfulCount);
  const [notHelpful, setNotHelpful] = useState(notHelpfulCount);
  const [hasVoted, setHasVoted] = useState(initialHasVoted);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');

  const handleVote = async (isHelpful) => {
    if (hasVoted || submitting) return;
    setSubmitting(true);
    try {
      const updated = await knowledgeService.submitFeedback(articleId, { helpful: isHelpful });
      setHelpful(updated.helpfulCount);
      setNotHelpful(updated.notHelpfulCount);
      setHasVoted(true);
      setMessage(isHelpful ? 'Thank you! Glad this article helped resolve your issue.' : 'Thank you for your feedback. Our team will review and improve this guide.');
    } catch (err) {
      console.error('Failed to submit feedback:', err);
      setMessage('Unable to submit feedback at this moment.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="card bg-dark border-secondary p-3 mt-4 text-center">
      <div className="d-flex flex-column flex-sm-row justify-content-between align-items-center gap-3">
        <div className="text-start">
          <h6 className="text-white mb-1">
            <i className="bi bi-question-circle text-primary me-2"></i>
            Was this article helpful?
          </h6>
          <p className="text-secondary small mb-0">
            Your feedback helps our IT Support team maintain high quality troubleshooting resources.
          </p>
        </div>

        <div className="d-flex align-items-center gap-2">
          <button
            type="button"
            className={`btn btn-sm d-flex align-items-center gap-2 ${
              hasVoted
                ? 'btn-outline-success disabled'
                : 'btn-outline-success hover-success'
            }`}
            onClick={() => handleVote(true)}
            disabled={hasVoted || submitting}
          >
            <i className="bi bi-hand-thumbs-up-fill"></i>
            <span>Yes</span>
            <span className="badge bg-success bg-opacity-25 text-success">{helpful}</span>
          </button>

          <button
            type="button"
            className={`btn btn-sm d-flex align-items-center gap-2 ${
              hasVoted
                ? 'btn-outline-secondary disabled'
                : 'btn-outline-danger hover-danger'
            }`}
            onClick={() => handleVote(false)}
            disabled={hasVoted || submitting}
          >
            <i className="bi bi-hand-thumbs-down-fill"></i>
            <span>No</span>
            <span className="badge bg-secondary bg-opacity-25 text-light">{notHelpful}</span>
          </button>
        </div>
      </div>

      {message && (
        <div className="mt-2 text-info small">
          <i className="bi bi-check2-circle me-1"></i> {message}
        </div>
      )}
    </div>
  );
};

export default KnowledgeFeedback;
