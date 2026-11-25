import { useEffect, useRef } from "react";
import type { ChatMessage } from "../types/message";
import { LogoMark } from "./Logo";

interface MessageListProps {
  messages: ChatMessage[];
  currentUserId: string;
  knownUsers: Record<string, string>;
  hasOlderMessages: boolean;
  loadingOlder: boolean;
  onLoadOlder: () => void;
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatDay(value: string) {
  return new Intl.DateTimeFormat("pt-BR", {
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(new Date(value));
}

export function MessageList({
  messages,
  currentUserId,
  knownUsers,
  hasOlderMessages,
  loadingOlder,
  onLoadOlder,
}: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const firstMessageId = messages[0]?.id;
  const previousFirstMessageId = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (
      previousFirstMessageId.current === undefined ||
      previousFirstMessageId.current === firstMessageId
    ) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
    }
    previousFirstMessageId.current = firstMessageId;
  }, [firstMessageId, messages.length]);

  if (messages.length === 0) {
    return (
      <div className="message-list empty-messages">
        <div className="empty-chat-mark"><LogoMark size={40} tone="light" /></div>
        <h3>Este é o começo da conversa</h3>
        <p>Envie uma mensagem para iniciar o papo.</p>
      </div>
    );
  }

  return (
    <div className="message-list" aria-label="Mensagens da conversa">
      {hasOlderMessages && (
        <button
          className="load-older-button"
          onClick={onLoadOlder}
          disabled={loadingOlder}
        >
          {loadingOlder ? "Carregando..." : "Carregar mensagens anteriores"}
        </button>
      )}
      <div className="day-divider"><span>{formatDay(messages[0].createdAt)}</span></div>
      {messages.map((message, index) => {
        const ownMessage = message.senderId === currentUserId;
        const previous = messages[index - 1];
        const showSender = !ownMessage && previous?.senderId !== message.senderId;
        const startsNewDay =
          previous &&
          new Date(previous.createdAt).toDateString() !== new Date(message.createdAt).toDateString();

        return (
          <div key={message.id}>
            {startsNewDay && (
              <div className="day-divider"><span>{formatDay(message.createdAt)}</span></div>
            )}
            <article className={`message-row${ownMessage ? " own-message" : ""}`}>
              {!ownMessage && <span className="message-avatar">{showSender ? "•" : ""}</span>}
              <div className="message-content">
                {showSender && (
                  <span className="message-sender">
                    {knownUsers[message.senderId] ?? "Participante"}
                  </span>
                )}
                <div className="message-bubble">
                  <p>{message.content}</p>
                  <time dateTime={message.createdAt}>{formatTime(message.createdAt)}</time>
                </div>
              </div>
            </article>
          </div>
        );
      })}
      <div ref={bottomRef} />
    </div>
  );
}