import { useRef, useState } from 'react';
import { FileUp, Loader2 } from 'lucide-react';
import api from '../api/client';

export default function UploadPanel({ progress, onUploaded }) {
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  async function upload(file) {
    if (!file) return;
    setError('');
    if (!file.name.toLowerCase().endsWith('.txt')) {
      setError('Only .txt files are supported');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);
    setUploading(true);
    try {
      await api.post('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      onUploaded();
    } catch (err) {
      setError(err.response?.data?.error || 'Upload failed');
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  return (
    <section className="panel upload-panel">
      <div className="panel-header">
        <h2>Upload</h2>
        <span className="panel-kicker">TXT · 1MB</span>
      </div>
      <button
        type="button"
        className={`drop-zone ${dragging ? 'is-dragging' : ''}`}
        onClick={() => inputRef.current?.click()}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          upload(event.dataTransfer.files[0]);
        }}
      >
        <span className="drop-icon">{uploading ? <Loader2 className="spin" /> : <FileUp />}</span>
        <span>{uploading ? 'Working...' : 'Drop a text file'}</span>
      </button>
      <input ref={inputRef} className="hidden-input" type="file" accept=".txt,text/plain" onChange={(event) => upload(event.target.files[0])} />
      <div className="status-strip">{progress || 'Ready'}</div>
      {error && <p className="error">{error}</p>}
    </section>
  );
}
