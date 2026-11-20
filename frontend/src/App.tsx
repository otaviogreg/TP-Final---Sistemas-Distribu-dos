import { useEffect, useState } from "react";
import { ChatPage } from "./pages/ChatPage";
import { LoginPage } from "./pages/LoginPage";
import { getCurrentUser } from "./services/authApi";
import { ApiError } from "./services/http";
import type { UserProfile } from "./types/auth";

const TOKEN_KEY = "chat.accessToken";

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState<UserProfile | null>(null);
  const [checkingSession, setCheckingSession] = useState(Boolean(token));
  const [sessionError, setSessionError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setCheckingSession(false);
      return;
    }

    let active = true;
    getCurrentUser(token)
      .then((profile) => {
        if (active) {
          setUser(profile);
          setSessionError(null);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          if (cause instanceof ApiError && [401, 403].includes(cause.status)) {
            sessionStorage.removeItem(TOKEN_KEY);
            setToken(null);
            setUser(null);
          } else {
            setSessionError(
              cause instanceof Error
                ? cause.message
                : "Não foi possível verificar sua sessão.",
            );
          }
        }
      })
      .finally(() => {
        if (active) setCheckingSession(false);
      });

    return () => {
      active = false;
    };
  }, [token]);

  function handleAuthenticated(accessToken: string, profile: UserProfile) {
    sessionStorage.setItem(TOKEN_KEY, accessToken);
    setUser(profile);
    setToken(accessToken);
  }

  function handleLogout() {
    sessionStorage.removeItem(TOKEN_KEY);
    setToken(null);
    setUser(null);
  }

  async function retrySessionCheck() {
    if (!token) return;
    setCheckingSession(true);
    setSessionError(null);
    try {
      setUser(await getCurrentUser(token));
    } catch (cause) {
      if (cause instanceof ApiError && [401, 403].includes(cause.status)) {
        sessionStorage.removeItem(TOKEN_KEY);
        setToken(null);
        setUser(null);
      } else {
        setSessionError(
          cause instanceof Error ? cause.message : "Não foi possível verificar sua sessão.",
        );
      }
    } finally {
      setCheckingSession(false);
    }
  }

  if (checkingSession) {
    return (
      <main className="session-loading" aria-label="Carregando sessão">
        <span className="brand-mark">c</span>
        <span className="spinner" />
      </main>
    );
  }

  if (sessionError) {
    return (
      <main className="session-error">
        <span className="brand-mark">c</span>
        <h1>Não foi possível validar sua sessão</h1>
        <p>{sessionError}</p>
        <button className="primary-button" onClick={() => void retrySessionCheck()}>
          Tentar novamente
        </button>
      </main>
    );
  }

  if (!token || !user) {
    return <LoginPage onAuthenticated={handleAuthenticated} />;
  }

  return <ChatPage token={token} user={user} onLogout={handleLogout} />;
}