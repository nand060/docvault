import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LockKeyhole, Mail, Vault } from 'lucide-react';
import { useAuth } from '../hooks/useAuth.jsx';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(form.username, form.password);
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.error || 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-panel">
        <div className="brand-mark"><Vault size={28} /> DocVault</div>
        <h1>Welcome back</h1>
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
              <input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required />
            </div>
          </label>
          {error && <p className="error">{error}</p>}
          <button className="primary-button" disabled={loading}>{loading ? 'Signing in...' : 'Sign in'}</button>
        </form>
        <p className="auth-link">New here? <Link to="/register">Create an account</Link></p>
      </section>
    </main>
  );
}
