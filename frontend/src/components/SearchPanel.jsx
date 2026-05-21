import { useState } from 'react';
import { BrainCircuit, Search, Sparkles } from 'lucide-react';
import api from '../api/client';

export default function SearchPanel({ aiAccess, events, socketReady }) {
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    if (!query.trim()) return;
    if (aiAccess && !socketReady) {
      setError('Connecting to AI websocket... please wait a moment.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await api.post('/search', { query });
    } catch (err) {
      setError(err.response?.data?.error || 'Search failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className={`panel search-panel ${aiAccess ? 'ai-mode' : 'semantic-mode'}`}>
      <div className="panel-header">
        <h2>{aiAccess ? 'AI Summary' : 'Semantic Search'}</h2>
        <span className="mode-pill">{aiAccess ? <Sparkles size={15} /> : <Search size={15} />}{aiAccess ? 'AI' : 'Ranked'}</span>
      </div>
      <form onSubmit={submit} className="search-form">
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Ask across your files" />
        <button className="primary-button" disabled={loading || (aiAccess && !socketReady)}>
          {aiAccess ? <BrainCircuit size={18} /> : <Search size={18} />}
          {loading ? 'Searching...' : 'Search'}
        </button>
      </form>
      {error && <p className="error">{error}</p>}
      {aiAccess && !socketReady && !loading && <p className="info">Connecting to the AI websocket...</p>}
      <SearchOutput aiAccess={aiAccess} events={events} loading={loading} />
    </section>
  );
}

function SearchOutput({ aiAccess, events, loading }) {
  const status = [...events].reverse().find((event) => event.type === 'status')?.payload;
  const done = [...events].reverse().find((event) => event.type === 'done')?.payload;
  const resultsEvent = [...events].reverse().find((event) => event.type === 'results' || event.type === 'ai-start');
  const tokens = events.filter((event) => event.type === 'ai-token').map((event) => event.payload).join('');
  const showPlaceholder = aiAccess && !tokens && done;

  if (events.length === 0) {
    if (loading) {
      return <div className="search-output idle">{aiAccess ? 'Waiting for AI summary...' : 'Waiting for results...'}</div>;
    }
    return <div className="search-output idle">{aiAccess ? 'AI mode is on' : 'Semantic mode is on'}</div>;
  }

  if (aiAccess) {
    return (
      <div className="search-output">
        {status && <p className="status-text">{status}</p>}
        {done && <p className="status-text success">{done}</p>}
        {resultsEvent?.payload?.length > 0 && (
          <div className="mini-results">
            {resultsEvent.payload.map((result) => <span key={result.id}>{result.name}</span>)}
          </div>
        )}
        <div className="summary-text">
          {tokens || (showPlaceholder ? 'AI summary completed, but no tokens were received.' : 'Waiting for tokens...')}
        </div>
      </div>
    );
  }

  return (
    <div className="search-output">
      {status && <p className="status-text">{status}</p>}
      <div className="ranked-list">
        {(resultsEvent?.payload || []).map((result, index) => (
          <article key={result.id} className="ranked-row">
            <span>{index + 1}</span>
            <strong>{result.name}</strong>
            <em>{Math.max(result.similarityScore, 0).toFixed(3)}</em>
          </article>
        ))}
      </div>
    </div>
  );
}
