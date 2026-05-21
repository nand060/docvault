import { useEffect, useMemo, useState } from 'react';
import { BrainCircuit, Loader2, LogOut, Search, ShieldCheck, Vault, X } from 'lucide-react';
import api from '../api/client';
import { useAuth } from '../hooks/useAuth.jsx';
import { createStompClient } from '../websocket/stompClient';
import UploadPanel from '../components/UploadPanel.jsx';
import FilesPanel from '../components/FilesPanel.jsx';
import SearchPanel from '../components/SearchPanel.jsx';

export default function Dashboard() {
  const { token, user, logout, updateUser } = useAuth();
  const [files, setFiles] = useState([]);
  const [uploadProgress, setUploadProgress] = useState('');
  const [searchEvents, setSearchEvents] = useState([]);
  const [socketReady, setSocketReady] = useState(false);
  const [query, setQuery] = useState('');
  const [searchActive, setSearchActive] = useState(false);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [searchMessage, setSearchMessage] = useState('');
  const [fileModal, setFileModal] = useState({ open: false, loading: false, error: '', file: null });

  const aiAccess = Boolean(user?.aiAccess);

  const connectedUserId = user?.id;
  useEffect(() => {
    if (!token || !connectedUserId) return undefined;

    const client = createStompClient(token, () => {
      setSocketReady(true);
      client.subscribe(`/topic/upload-progress/${connectedUserId}`, (message) => {
        const event = JSON.parse(message.body);
        setUploadProgress(event.payload);
      });
      client.subscribe(`/topic/search-results/${connectedUserId}`, (message) => {
        const event = JSON.parse(message.body);
        if (event.type === 'status' && event.payload === 'Embedding query') {
          setSearchEvents([event]);
          return;
        }
        if (event.type === 'status' || event.type === 'results' || event.type === 'ai-start') {
          setSearchEvents((current) => [...current.filter((item) => item.type === 'ai-token'), event]);
          if (event.type === 'results') {
            setSearchMessage('');
          }
        } else if (event.type === 'done') {
          setSearchEvents((current) => [...current, event]);
          if (event.payload === 'No documents matched your query.') {
            setSearchMessage(event.payload);
          }
        } else {
          setSearchEvents((current) => [...current, event]);
        }
      });
    });

    client.onStompError = (frame) => {
      console.error('WebSocket STOMP error', frame);
      setSocketReady(false);
    };

    client.onWebSocketError = (error) => {
      console.error('WebSocket connection error', error);
      setSocketReady(false);
    };

    return () => {
      setSocketReady(false);
      client.deactivate();
    };
  }, [token, connectedUserId]);

  useEffect(() => {
    refreshFiles();
  }, []);

  async function refreshFiles() {
    const response = await api.get('/files');
    setFiles(response.data);
  }

  async function toggleAi(nextValue) {
    const response = await api.put('/users/ai-access', { aiAccess: nextValue });
    updateUser(response.data);
    setSearchEvents([]);
    setSearchMessage('');
  }

  async function submitSearch(event) {
    event.preventDefault();
    if (!query.trim()) return;
    if (aiAccess && !socketReady) {
      setSearchError('Connecting to AI websocket... please wait a moment.');
      return;
    }
    setSearchActive(true);
    setSearchError('');
    setSearchMessage('');
    setSearchEvents([]);
    setSearchLoading(true);
    try {
      const response = await api.post('/search', { query });
      setSearchMessage(response.data.message || '');
      if (!aiAccess) {
        setSearchEvents([{ type: 'results', payload: response.data.results || [] }]);
      }
    } catch (err) {
      setSearchError(err.response?.data?.error || 'Search failed');
    } finally {
      setSearchLoading(false);
    }
  }

  function clearSearch() {
    setSearchActive(false);
    setSearchEvents([]);
    setSearchMessage('');
    setSearchError('');
    setQuery('');
  }

  async function openFile(fileId) {
    setFileModal({ open: true, loading: true, error: '', file: null });
    try {
      const response = await api.get(`/files/${fileId}/content`);
      setFileModal({ open: true, loading: false, error: '', file: response.data });
    } catch (err) {
      setFileModal({
        open: true,
        loading: false,
        error: err.response?.data?.error || 'Could not load file content',
        file: null
      });
    }
  }

  function closeFileModal() {
    setFileModal({ open: false, loading: false, error: '', file: null });
  }

  const fileCountLabel = useMemo(() => `${files.length} ${files.length === 1 ? 'file' : 'files'}`, [files.length]);

  return (
    <main className="app-shell">
      <header className="top-bar">
        <div>
          <div className="brand-mark compact"><Vault size={25} /> DocVault</div>
          <p><ShieldCheck size={15} /> {user?.username} · {fileCountLabel}</p>
        </div>
        <form className="search-bar-container" onSubmit={submitSearch}>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Ask across your files" />
          <button className="primary-button" disabled={searchLoading || (aiAccess && !socketReady)}>
            {searchLoading ? <Loader2 className="spin" size={18} /> : aiAccess ? <BrainCircuit size={18} /> : <Search size={18} />}
            {searchLoading ? 'Searching...' : 'Search'}
          </button>
        </form>
        <button className="ghost-button" onClick={logout}><LogOut size={17} /> Sign out</button>
      </header>

      {!searchActive && (
        <section className="dashboard-grid">
          <UploadPanel progress={uploadProgress} onUploaded={refreshFiles} />
          <FilesPanel files={files} aiAccess={aiAccess} onToggleAi={toggleAi} onDeleted={refreshFiles} onOpenFile={openFile} />
        </section>
      )}

      {searchActive && (
        <SearchPanel
          aiAccess={aiAccess}
          events={searchEvents}
          socketReady={socketReady}
          loading={searchLoading}
          error={searchError}
          message={searchMessage}
          onBack={clearSearch}
          onOpenFile={openFile}
        />
      )}
      <FileContentModal modal={fileModal} onClose={closeFileModal} />
    </main>
  );
}

function FileContentModal({ modal, onClose }) {
  useEffect(() => {
    if (!modal.open) return undefined;
    function handleKeyDown(event) {
      if (event.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [modal.open, onClose]);

  if (!modal.open) return null;

  return (
    <div className="file-modal-overlay" onMouseDown={onClose}>
      <section className="file-modal" onMouseDown={(event) => event.stopPropagation()}>
        <header className="file-modal-header">
          <h2>{modal.file?.name || 'File content'}</h2>
          <button className="file-modal-close" onClick={onClose} aria-label="Close file content">
            <X size={20} />
          </button>
        </header>
        <div className="file-modal-body">
          {modal.loading && <div className="modal-state"><Loader2 className="spin" /> Loading content...</div>}
          {modal.error && <p className="error">{modal.error}</p>}
          {modal.file && <pre>{modal.file.content}</pre>}
        </div>
      </section>
    </div>
  );
}
