import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import api from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('docvault.token'));
  const [user, setUser] = useState(() => {
    const raw = localStorage.getItem('docvault.user');
    return raw ? JSON.parse(raw) : null;
  });

  useEffect(() => {
    if (!token) return;
    api.get('/users/me')
      .then((response) => saveSession(token, response.data))
      .catch(() => clearSession());
  }, []);

  function saveSession(nextToken, nextUser) {
    localStorage.setItem('docvault.token', nextToken);
    localStorage.setItem('docvault.user', JSON.stringify(nextUser));
    setToken(nextToken);
    setUser(nextUser);
  }

  function clearSession() {
    localStorage.removeItem('docvault.token');
    localStorage.removeItem('docvault.user');
    setToken(null);
    setUser(null);
  }

  async function login(username, password) {
    const response = await api.post('/auth/login', { username, password });
    saveSession(response.data.token, {
      id: response.data.userId,
      username: response.data.username,
      aiAccess: response.data.aiAccess
    });
  }

  async function register(username, password) {
    const response = await api.post('/auth/register', { username, password });
    saveSession(response.data.token, {
      id: response.data.userId,
      username: response.data.username,
      aiAccess: response.data.aiAccess
    });
  }

  // CHECK IF NEEDED
  function updateUser(nextUser) {
    setUser(nextUser);
    localStorage.setItem('docvault.user', JSON.stringify(nextUser));
  }

  const value = useMemo(() => ({
    token,
    user,
    isAuthenticated: Boolean(token && user),
    login,
    register,
    logout: clearSession,
    updateUser
  }), [token, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
