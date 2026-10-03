import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import copilotService from '../services/copilotService';
import ticketService from '../services/ticketService';
import CopilotInput from '../components/copilot/CopilotInput';
import CopilotMessage from '../components/copilot/CopilotMessage';
import CopilotSourceCard from '../components/copilot/CopilotSourceCard';
import CopilotLoading from '../components/copilot/CopilotLoading';
import CopilotEmptyState from '../components/copilot/CopilotEmptyState';
import ErrorAlert from '../components/ErrorAlert';
import { extractErrorMessage } from '../services/api';

const AiSupportCopilot = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const ticketIdParam = searchParams.get('ticketId');

  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('ALL');
  const [ticketContext, setTicketContext] = useState(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [response, setResponse] = useState(null);

  // Load ticket details if ticketId was passed in query string
  useEffect(() => {
    if (ticketIdParam) {
      const loadTicket = async () => {
        try {
          const t = await ticketService.getTicketById(ticketIdParam);
          setTicketContext(t);
          if (t.category) {
            setCategory(t.category);
          }
          if (!query) {
            setQuery(`What steps should I take to troubleshoot this ticket: "${t.title}"?`);
          }
        } catch (err) {
          setError(`Could not attach ticket #${ticketIdParam} context: ${extractErrorMessage(err)}`);
        }
      };
      loadTicket();
    }
  }, [ticketIdParam]);

  const handleClearTicketContext = () => {
    setTicketContext(null);
    const newParams = new URLSearchParams(searchParams);
    newParams.delete('ticketId');
    setSearchParams(newParams);
  };

  const handleExecuteQuery = async (customQuery, customCategory) => {
    const q = customQuery !== undefined ? customQuery : query;
    const cat = customCategory !== undefined ? customCategory : category;

    if (!q || !q.trim()) return;

    try {
      setLoading(true);
      setError(null);
      setResponse(null);

      const res = await copilotService.getAnswer({
        query: q.trim(),
        category: cat !== 'ALL' ? cat : undefined,
        ticketId: ticketContext?.id || (ticketIdParam ? Number(ticketIdParam) : undefined),
        topK: 5,
        minSimilarity: 0.30,
      });

      setResponse(res);
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  const handleSelectSuggestion = (suggestedText, suggestedCat) => {
    setQuery(suggestedText);
    if (suggestedCat) {
      setCategory(suggestedCat);
    }
    handleExecuteQuery(suggestedText, suggestedCat);
  };

  return (
    <div className="container-fluid py-4">
      {/* Page Header */}
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4">
        <div>
          <div className="d-flex align-items-center gap-2 mb-1">
            <span className="badge bg-primary-subtle text-primary border border-primary-subtle px-2 py-1">
              <i className="bi bi-stars me-1"></i>Phase 12
            </span>
            <span className="text-muted small">Retrieval-Augmented Generation</span>
          </div>
          <h2 className="fw-bold text-slate-900 mb-0">TechConnect AI Support Copilot</h2>
          <p className="text-muted small mb-0">
            Advisory IT support recommendations synthesized directly from verified Knowledge Base articles.
          </p>
        </div>

        <div className="d-flex align-items-center gap-2">
          <Link to="/knowledge" className="btn btn-outline-secondary d-flex align-items-center gap-2">
            <i className="bi bi-journal-bookmark"></i>
            <span>Browse Knowledge Base</span>
          </Link>
        </div>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError(null)} />

      {/* Query Input Area */}
      <CopilotInput
        query={query}
        setQuery={setQuery}
        category={category}
        setCategory={setCategory}
        ticketContext={ticketContext}
        onClearTicketContext={handleClearTicketContext}
        onSubmit={() => handleExecuteQuery()}
        loading={loading}
      />

      {/* Loading State */}
      {loading && <CopilotLoading />}

      {/* Answer & Sources Display */}
      {!loading && response && (
        <>
          <CopilotMessage response={response} />

          {/* Sources Section */}
          {response.sources && response.sources.length > 0 && (
            <div className="mt-4">
              <div className="d-flex align-items-center justify-content-between mb-3">
                <div className="d-flex align-items-center gap-2">
                  <i className="bi bi-link-45deg text-primary fs-4"></i>
                  <h5 className="fw-bold text-slate-900 mb-0">Retrieved Provenance Sources</h5>
                  <span className="badge bg-light text-dark border">
                    {response.sources.length} Cited {response.sources.length === 1 ? 'Article' : 'Articles'}
                  </span>
                </div>
                {response.retrieval && (
                  <span className="text-muted small">
                    Best cosine similarity: {(response.retrieval.bestSimilarity * 100).toFixed(1)}%
                  </span>
                )}
              </div>

              <div className="row">
                {response.sources.map((src, idx) => (
                  <div key={src.chunkId || idx} className="col-12">
                    <CopilotSourceCard source={src} index={idx} />
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* No Relevant Sources State */}
          {(!response.sources || response.sources.length === 0) && !response.grounded && (
            <CopilotEmptyState noSources={true} />
          )}
        </>
      )}

      {/* Initial Empty State */}
      {!loading && !response && (
        <CopilotEmptyState
          noSources={false}
          onSelectSuggestion={handleSelectSuggestion}
        />
      )}
    </div>
  );
};

export default AiSupportCopilot;
