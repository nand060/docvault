import { Brain, Trash2 } from 'lucide-react';
import api from '../api/client';
import { sanitizeFilename } from '../utils/sanitize';

export default function FilesPanel({ files, aiAccess, onToggleAi, onDeleted, onOpenFile }) {
  async function remove(fileId) {
    await api.delete(`/files/${fileId}`);
    onDeleted();
  }

  return (
    <section className="panel files-panel">
      <div className="panel-header">
        <h2>My Files</h2>
        <label className="toggle">
          <input type="checkbox" checked={aiAccess} onChange={(event) => onToggleAi(event.target.checked)} />
          <span><Brain size={16} /> AI access</span>
        </label>
      </div>
      <div className="file-list">
        {files.length === 0 && <div className="empty-state">No files yet</div>}
        {files.map((file) => (
          <article className="file-row" key={file.id}>
            <div>
              <button className="file-name-button" onClick={() => onOpenFile(file.id)}>{sanitizeFilename(file.name)}</button>
              <time>{new Date(file.uploadedAt).toLocaleString()}</time>
            </div>
            <button className="icon-button danger" title="Delete file" onClick={() => remove(file.id)}>
              <Trash2 size={17} />
            </button>
          </article>
        ))}
      </div>
    </section>
  );
}
