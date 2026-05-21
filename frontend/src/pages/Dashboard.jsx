import { useEffect, useMemo, useState } from 'react';
import { LogOut, ShieldCheck, Vault } from 'lucide-react';
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
        } else if (event.type === 'done') {
          setSearchEvents((current) => [...current, event]);
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
  }

  const fileCountLabel = useMemo(() => `${files.length} ${files.length === 1 ? 'file' : 'files'}`, [files.length]);

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <div className="brand-mark compact"><Vault size={25} /> DocVault</div>
          <p><ShieldCheck size={15} /> {user?.username} · {fileCountLabel}</p>
        </div>
        <button className="ghost-button" onClick={logout}><LogOut size={17} /> Sign out</button>
      </header>

      <section className="dashboard-grid">
        <UploadPanel progress={uploadProgress} onUploaded={refreshFiles} />
        <FilesPanel files={files} aiAccess={aiAccess} onToggleAi={toggleAi} onDeleted={refreshFiles} />
        <SearchPanel aiAccess={aiAccess} events={searchEvents} socketReady={socketReady} />
      </section>
    </main>
  );
}
