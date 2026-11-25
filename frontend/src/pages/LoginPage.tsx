import { useState, type FormEvent } from "react";
import { login, register } from "../services/authApi";
import type { UserProfile } from "../types/auth";
import { Wordmark } from "../components/Logo";
import { NetworkBackdrop } from "../components/NetworkBackdrop";

interface LoginPageProps {
  onAuthenticated: (token: string, user: UserProfile) => void;
}

export function LoginPage({ onAuthenticated }: LoginPageProps) {
  const [isRegistering, setIsRegistering] = useState(false);
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setLoading(true);

    try {
      if (isRegistering) {
        await register(displayName.trim(), email.trim(), password);
      }
      const session = await login(email.trim(), password);
      onAuthenticated(session.accessToken, session.user);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível autenticar.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-intro">
        <div className="auth-brand">
          <Wordmark size={38} />
        </div>
        <div className="auth-intro-copy">
          <span className="eyebrow">CONVERSAS QUE APROXIMAM</span>
          <h1>Seu espaço para boas conversas.</h1>
          <p>Mensagens em tempo real, do seu jeito e com quem importa.</p>
        </div>
        <NetworkBackdrop />
        <div className="auth-footer-note">
          <span className="presence-dot" /> Sistema de chat distribuído · CEFET-MG
        </div>
      </section>

      <section className="auth-panel">
        <div className="auth-form-wrap">
          <span className="eyebrow">{isRegistering ? "CRIE SUA CONTA" : "BEM-VINDO DE VOLTA"}</span>
          <h2>{isRegistering ? "Vamos começar" : "Entre na sua conta"}</h2>
          <p className="auth-subtitle">
            {isRegistering
              ? "Preencha seus dados para entrar na conversa."
              : "Que bom ter você por aqui novamente."}
          </p>

          <form className="auth-form" onSubmit={(event) => void submit(event)}>
            {isRegistering && (
              <label className="field-label" htmlFor="display-name">
                Nome
                <input
                  id="display-name"
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                  placeholder="Como podemos chamar você?"
                  autoComplete="name"
                  maxLength={80}
                  required
                />
              </label>
            )}
            <label className="field-label" htmlFor="email">
              E-mail
              <input
                id="email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="voce@exemplo.com"
                autoComplete="email"
                required
              />
            </label>
            <label className="field-label" htmlFor="password">
              Senha
              <input
                id="password"
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="Mínimo de 8 caracteres"
                autoComplete={isRegistering ? "new-password" : "current-password"}
                minLength={8}
                required
              />
            </label>

            {error && <p className="inline-error" role="alert">{error}</p>}

            <button className="primary-button auth-submit" type="submit" disabled={loading}>
              {loading ? "Aguarde..." : isRegistering ? "Criar conta" : "Entrar"}
              {!loading && <span aria-hidden="true">→</span>}
            </button>
          </form>

          <p className="auth-switch">
            {isRegistering ? "Já tem uma conta?" : "Ainda não tem uma conta?"}{" "}
            <button
              onClick={() => {
                setError(null);
                setIsRegistering((value) => !value);
              }}
            >
              {isRegistering ? "Entrar" : "Criar conta"}
            </button>
          </p>
          <p className="auth-privacy">Sua senha é protegida e nunca aparece nas conversas.</p>
        </div>
      </section>
    </main>
  );
}