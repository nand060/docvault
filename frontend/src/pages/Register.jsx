import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LockKeyhole, Mail, Vault } from 'lucide-react';
import { useAuth } from '../hooks/useAuth.jsx';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      await register(form.username, form.password);
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.error || 'Registration failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-panel">
        <div className="brand-mark"><Vault size={28} /> DocVault</div>
        <h1>Create your vault</h1>
        <form onSubmit={submit} className="auth-form">
          <label>
            <span>Email</span>
            <div className="input-row">
              <Mail size={18} />
              <input type="email" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required />
            </div>
          </label>
          <label>
            <span>Password</span>
            <div className="input-row">
              <LockKeyhole size={18} />
              <input type="password" minLength="8" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
            </div>
          </label>
          {error && <p className="error">{error}</p>}
          <button className="primary-button" disabled={loading}>{loading ? 'Creating...' : 'Create account'}</button>
        </form>
        <p className="auth-link">Already registered? <Link to="/login">Sign in</Link></p>
      </section>
    </main>
  );
}
