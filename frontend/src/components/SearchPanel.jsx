import { ArrowLeft, Search, SearchX, Sparkles } from 'lucide-react';

export default function SearchPanel({ aiAccess, events, socketReady, loading, error, message, onBack, onOpenFile }) {
  return (
    <section className={`results-area ${aiAccess ? 'ai-mode' : 'semantic-mode'}`}>
      <div className="results-header">
        <button className="back-button" onClick={onBack}><ArrowLeft size={17} /> Back</button>
        <h2>{aiAccess ? 'AI Summary' : 'Semantic Search'}</h2>
        <span className="mode-pill">{aiAccess ? <Sparkles size={15} /> : <Search size={15} />}{aiAccess ? 'AI' : 'Ranked'}</span>
      </div>
      {error && <p className="error">{error}</p>}
      {aiAccess && !socketReady && !loading && <p className="info">Connecting to the AI websocket...</p>}
      <SearchOutput aiAccess={aiAccess} events={events} loading={loading} message={message} onOpenFile={onOpenFile} />
    </section>
  );
}

function SearchOutput({ aiAccess, events, loading, message, onOpenFile }) {
  const status = [...events].reverse().find((event) => event.type === 'status')?.payload;
  const done = [...events].reverse().find((event) => event.type === 'done')?.payload;
  const resultsEvent = [...events].reverse().find((event) => event.type === 'results' || event.type === 'ai-start');
  const tokens = events.filter((event) => event.type === 'ai-token').map((event) => event.payload).join('');
  const noMatches = message === 'No documents matched your query.' || done === 'No documents matched your query.';

  if (events.length === 0) {
    if (loading) {
      return <div className="search-output idle">{aiAccess ? 'Waiting for AI summary...' : 'Waiting for results...'}</div>;
    }
    return <div className="search-output idle">{aiAccess ? 'AI mode is on' : 'Semantic mode is on'}</div>;
  }

  if (aiAccess) {
    if (noMatches) {
      return <EmptySearchState />;
    }

    return (
      <div className="search-output">
        {status && <p className="status-text">{status}</p>}
        {done && <p className="status-text success">{done}</p>}
        <div className="summary-text">
          {tokens || 'Waiting for tokens...'}
        </div>
        {resultsEvent?.payload?.length > 0 && (
          <div className="mini-results">
            {resultsEvent.payload.map((result) => (
              <button key={result.id} onClick={() => onOpenFile(result.id)}>{result.name}</button>
            ))}
          </div>
        )}
      </div>
    );
  }

  if (noMatches || (resultsEvent && resultsEvent.payload.length === 0)) {
    return <EmptySearchState />;
  }

  return (
    <div className="search-output">
      {status && <p className="status-text">{status}</p>}
      <div className="ranked-list">
        {(resultsEvent?.payload || []).map((result, index) => (
          <article key={result.id} className="ranked-row">
            <span>{index + 1}</span>
            <button onClick={() => onOpenFile(result.id)}>{result.name}</button>
            <em>{Math.max(result.similarityScore, 0).toFixed(3)}</em>
          </article>
        ))}
      </div>
    </div>
  );
}

function EmptySearchState() {
  return (
    <div className="search-output idle empty-search-state">
      <SearchX size={32} />
      <strong>No documents matched your query.</strong>
      <span>Try a more specific term from one of your uploaded files.</span>
    </div>
  );
}
