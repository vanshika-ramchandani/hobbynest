import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { api } from "./api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    if (!localStorage.getItem("hn_access")) {
      setUser(null);
      return null;
    }
    try {
      const u = await api("/auth/me/");
      setUser(u);
      return u;
    } catch {
      setUser(null);
      return null;
    }
  }, []);

  useEffect(() => {
    refreshUser().finally(() => setLoading(false));
  }, [refreshUser]);

  const saveTokens = ({ access, refresh }) => {
    localStorage.setItem("hn_access", access);
    localStorage.setItem("hn_refresh", refresh);
  };

  const login = async (username, password) => {
    saveTokens(await api("/auth/login/", { method: "POST", body: { username, password }, auth: false }));
    return refreshUser();
  };

  const register = async (payload) => {
    const data = await api("/auth/register/", { method: "POST", body: payload, auth: false });
    saveTokens(data);
    setUser(data.user);
    return data.user;
  };

  const logout = () => {
    localStorage.removeItem("hn_access");
    localStorage.removeItem("hn_refresh");
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
