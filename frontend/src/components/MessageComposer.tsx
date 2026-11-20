import { useState, type FormEvent, type KeyboardEvent } from "react";

interface MessageComposerProps {
  disabled: boolean;
  onSend: (content: string) => boolean;
}

export function MessageComposer({ disabled, onSend }: MessageComposerProps) {
  const [content, setContent] = useState("");

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = content.trim();
    if (!message || disabled) return;
    if (onSend(message)) setContent("");
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }

  return (
    <form className="composer" onSubmit={submit}>
      <div className="composer-box">
        <textarea
          aria-label="Escreva uma mensagem"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={disabled ? "Conectando ao chat..." : "Escreva uma mensagem..."}
          rows={1}
          maxLength={4000}
          disabled={disabled}
        />
        <div className="composer-actions">
          <span className="composer-hint">Enter para enviar · Shift + Enter para quebrar linha</span>
          <button
            className="send-button"
            type="submit"
            aria-label="Enviar mensagem"
            disabled={disabled || !content.trim()}
          >
            <span>↑</span>
          </button>
        </div>
      </div>
    </form>
  );
}