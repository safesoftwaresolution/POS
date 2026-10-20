import React, { useState } from 'react';
import { User, KeyRound, CheckCircle2, ShieldAlert, X, Zap } from 'lucide-react';
import { loginApi } from '../../services/authApi';

export default function LoginModal({ isOpen, onClose, onLoginSuccess }) {
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('Password123');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const response = await loginApi(username, password);
      setLoading(false);
      if (onLoginSuccess) {
        onLoginSuccess(response);
      }
      onClose();
    } catch (err) {
      setLoading(false);
      setError(err.message || 'Credenciales inválidas. Verifica usuario y contraseña.');
    }
  };

  return (
    <div className="fixed inset-0 bg-navy-900/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bento-card max-w-md w-full bg-white p-6 rounded-2xl shadow-2xl space-y-5 animate-in fade-in zoom-in duration-150">
        <div className="flex items-center justify-between border-b border-gray-100 pb-3">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-blue-600 text-white flex items-center justify-center font-black">
              <Zap className="w-5 h-5 fill-white" />
            </div>
            <div>
              <h3 className="font-extrabold text-base text-[#17171B]">Iniciar Sesión en POS</h3>
              <p className="text-[11px] text-gray-500 font-medium">Ingresa tu usuario y contraseña</p>
            </div>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            <X className="w-5 h-5" />
          </button>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-xl text-xs font-bold text-red-600 flex items-center gap-2">
            <ShieldAlert className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4 text-xs font-bold text-gray-700">
          <div>
            <label className="block mb-1">Nombre de Usuario:</label>
            <div className="relative">
              <User className="w-4 h-4 text-gray-400 absolute left-3 top-3" />
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="admin"
                className="w-full pl-9 pr-3 py-2.5 bg-gray-50 border border-gray-300 rounded-xl font-semibold text-[#17171B] focus:outline-none focus:border-blue-600"
                required
              />
            </div>
          </div>

          <div>
            <label className="block mb-1">Contraseña de Acceso:</label>
            <div className="relative">
              <KeyRound className="w-4 h-4 text-gray-400 absolute left-3 top-3" />
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="Password123"
                className="w-full pl-9 pr-3 py-2.5 bg-gray-50 border border-gray-300 rounded-xl font-semibold text-[#17171B] focus:outline-none focus:border-blue-600"
                required
              />
            </div>
          </div>

          <div className="p-3 bg-blue-50/70 rounded-xl border border-blue-100 text-[11px] text-blue-900 font-medium space-y-1">
            <p className="font-bold flex items-center gap-1">
              <CheckCircle2 className="w-3.5 h-3.5 text-blue-600" /> Credenciales de Acceso por Defecto:
            </p>
            <p>• Usuario: <code className="bg-white px-1.5 py-0.5 rounded font-mono font-bold text-blue-700">admin</code></p>
            <p>• Contraseña: <code className="bg-white px-1.5 py-0.5 rounded font-mono font-bold text-blue-700">Password123</code></p>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-extrabold text-xs flex items-center justify-center gap-2 shadow-md shadow-blue-600/30 transition-all"
          >
            {loading ? 'Autenticando...' : 'Iniciar Sesión'}
          </button>
        </form>
      </div>
    </div>
  );
}
