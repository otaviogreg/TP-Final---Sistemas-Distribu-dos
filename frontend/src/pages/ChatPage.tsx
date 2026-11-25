import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ConversationList, conversationTitle, rememberUsers } from "../components/ConversationList";
import { LogoMark, Wordmark } from "../components/Logo";
import { MessageComposer } from "../components/MessageComposer";
import { MessageList } from "../components/MessageList";
import { NewConversationDialog } from "../components/NewConversationDialog";
import { useChatSocket } from "../hooks/useChatSocket";
import {
  addConversationParticipants,
  createConversation,
  getMessageHistory,
  listConversations,
} from "../services/chatApi";
import type { UserProfile, UserSummary } from "../types/auth";
import type { Conversation, CreateConversationRequest } from "../types/conversation";
import type { ChatMessage } from "../types/message";

interface ChatPageProps {
  token: string;
  user: UserProfile;
  onLogout: () => void;
}

function deduplicateMessages(messages: ChatMessage[]) {
  const unique = new Map(messages.map((message) => [message.id, message]));
  return [...unique.values()].sort(
    (left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime(),
  );
}

export function ChatPage({ token, user, onLogout }: ChatPageProps) {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [knownUsers, setKnownUsers] = useState<Record<string, string>>({});
  const [showNewConversation, setShowNewConversation] = useState(false);
  const [showAddParticipants, setShowAddParticipants] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [hasOlderMessages, setHasOlderMessages] = useState(false);
  const [messagePage, setMessagePage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const selectedIdRef = useRef(selectedId);
  selectedIdRef.current = selectedId;

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedId) ?? null,
    [conversations, selectedId],
  );

  const refreshConversations = useCallback(async () => {
    setLoadingConversations(true);
    setError(null);
    try {
      const results = await listConversations(token);
      setConversations(results);
      setSelectedId((current) =>
        current && results.some((conversation) => conversation.id === current)
          ? current
          : results[0]?.id ?? null,
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível carregar conversas.");
    } finally {
      setLoadingConversations(false);
    }
  }, [token]);

  useEffect(() => {
    void refreshConversations();
  }, [refreshConversations]);

  useEffect(() => {
    if (!selectedId) {
      setMessages([]);
      setHasOlderMessages(false);
      return;
    }

    let active = true;
    setLoadingMessages(true);
    setMessages([]);
    setMessagePage(0);
    getMessageHistory(token, selectedId)
      .then((result) => {
        if (!active) return;
        setMessages((current) =>
          deduplicateMessages([
            ...result.messages,
            ...current.filter((message) => message.conversationId === selectedId),
          ]),
        );
        setHasOlderMessages(result.hasNext);
        setMessagePage(result.page);
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : "Não foi possível carregar mensagens.");
        }
      })
      .finally(() => {
        if (active) setLoadingMessages(false);
      });

    return () => {
      active = false;
    };
  }, [selectedId, token]);

  const handleIncomingMessage = useCallback((message: ChatMessage) => {
    if (message.conversationId !== selectedIdRef.current) return;
    setMessages((current) => deduplicateMessages([...current, message]));
  }, []);

  const socket = useChatSocket(token, selectedId, handleIncomingMessage);

  useEffect(() => {
    if (socket.status !== "connected" || !selectedId) return;

    let active = true;
    getMessageHistory(token, selectedId)
      .then((result) => {
        if (!active) return;
        const newestHistoryMessage = result.messages.at(-1);
        setMessages((current) =>
          deduplicateMessages([
            ...result.messages,
            ...current.filter(
              (message) =>
                message.conversationId === selectedId &&
                (!newestHistoryMessage ||
                  new Date(message.createdAt).getTime() >
                    new Date(newestHistoryMessage.createdAt).getTime()),
            ),
          ]),
        );
        setHasOlderMessages(result.hasNext);
        setMessagePage(result.page);
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : "Não foi possível sincronizar mensagens.");
        }
      });

    return () => {
      active = false;
    };
  }, [selectedId, socket.status, token]);

  async function handleCreateConversation(request: CreateConversationRequest) {
    const conversation = await createConversation(token, request);
    setConversations((current) => [
      conversation,
      ...current.filter((item) => item.id !== conversation.id),
    ]);
    setSelectedId(conversation.id);
    setShowNewConversation(false);
    setError(null);
  }

  async function handleAddParticipants(participantIds: string[]) {
    if (!selectedConversation) return;
    const updated = await addConversationParticipants(token, selectedConversation.id, {
      participantIds,
    });
    setConversations((current) =>
      current.map((conversation) => conversation.id === updated.id ? updated : conversation),
    );
    setShowAddParticipants(false);
    setError(null);
  }

  async function loadOlderMessages() {
    if (!selectedId || loadingOlder || !hasOlderMessages) return;
    const conversationId = selectedId;
    setLoadingOlder(true);
    try {
      const result = await getMessageHistory(token, conversationId, messagePage + 1);
      if (selectedIdRef.current !== conversationId) return;
      setMessages((current) => deduplicateMessages([...result.messages, ...current]));
      setHasOlderMessages(result.hasNext);
      setMessagePage(result.page);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível carregar mensagens.");
    } finally {
      setLoadingOlder(false);
    }
  }

  function handleSend(content: string) {
    try {
      socket.sendMessage(content);
      return true;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Não foi possível enviar a mensagem.");
      return false;
    }
  }

  const handleUsersFound = useCallback((users: UserSummary[]) => {
    setKnownUsers((current) => rememberUsers(current, users));
  }, []);

  const title = selectedConversation
    ? conversationTitle(selectedConversation, user.id, knownUsers)
    : "Suas conversas";
  const connectionLabel = {
    idle: "Selecione uma conversa",
    connecting: "Conectando...",
    connected: "Em tempo real",
    disconnected: "Reconectando...",
    error: "Conexão instável",
  }[socket.status];

  return (
    <main className={`chat-shell${sidebarOpen ? " sidebar-open" : ""}`}>
      <ConversationList
        conversations={conversations}
        selectedId={selectedId}
        currentUser={user}
        knownUsers={knownUsers}
        onSelect={(id) => {
          setSelectedId(id);
          setError(null);
          setSidebarOpen(false);
        }}
        onCreate={() => {
          setShowNewConversation(true);
          setSidebarOpen(false);
        }}
      />
      {sidebarOpen && (
        <button
          className="sidebar-backdrop"
          onClick={() => setSidebarOpen(false)}
          aria-label="Fechar lista de conversas"
        />
      )}

      <section className="chat-main">
        <header className="chat-topbar">
          <button
            className="mobile-menu-button"
            onClick={() => setSidebarOpen(true)}
            aria-label="Abrir lista de conversas"
          >
            ☰
          </button>
          <div className="mobile-brand">
            <Wordmark size={30} tone="light" />
          </div>
          <div className="chat-topbar-context">
            <span className="eyebrow">CHAT DISTRIBUÍDO</span>
            <span className="topbar-title">{title}</span>
          </div>
          <div className="topbar-actions">
            {selectedConversation && (
              <span className={`connection-status status-${socket.status}`}>
                <span /> {connectionLabel}
              </span>
            )}
            <button className="logout-button" onClick={onLogout} title="Sair da conta">
              <span aria-hidden="true">↗</span> Sair
            </button>
          </div>
        </header>

        {error && (
          <div className="notice-bar" role="alert">
            <span>{error}</span>
            <button onClick={() => setError(null)} aria-label="Fechar aviso">×</button>
          </div>
        )}
        {socket.error && selectedConversation && (
          <div className="socket-notice" role="status">{socket.error}</div>
        )}

        {loadingConversations ? (
          <div className="chat-placeholder"><span className="spinner" /> Carregando conversas...</div>
        ) : !selectedConversation ? (
          <div className="welcome-panel">
            <div className="welcome-illustration">
              <LogoMark size={104} tone="light" className="brand-logo" />
            </div>
            <span className="eyebrow">UM BOM PAPO COMEÇA AQUI</span>
            <h2>As melhores ideias surgem em conversa.</h2>
            <p>Crie uma conversa privada ou reúna seu grupo para começar.</p>
            <button className="primary-button" onClick={() => setShowNewConversation(true)}>
              Iniciar conversa <span aria-hidden="true">→</span>
            </button>
          </div>
        ) : (
          <>
            <div className="conversation-heading">
              <div className="conversation-heading-avatar">
                {selectedConversation.type === "GROUP" ? "✳" : title.charAt(0).toLocaleUpperCase("pt-BR")}
              </div>
              <div className="conversation-heading-copy">
                <h2>{title}</h2>
                <p>
                  {selectedConversation.type === "GROUP"
                    ? `${selectedConversation.participantIds.length} participantes`
                    : "Conversa privada"}
                  {" · "}
                  <span className="heading-status"><span /> {connectionLabel}</span>
                </p>
              </div>
              {selectedConversation.type === "GROUP" && selectedConversation.createdBy === user.id && (
                <button
                  className="add-participants-button"
                  onClick={() => setShowAddParticipants(true)}
                >
                  Adicionar participantes
                </button>
              )}
            </div>

            {loadingMessages ? (
              <div className="chat-placeholder"><span className="spinner" /> Carregando mensagens...</div>
            ) : (
              <MessageList
                messages={messages}
                currentUserId={user.id}
                knownUsers={knownUsers}
                hasOlderMessages={hasOlderMessages}
                loadingOlder={loadingOlder}
                onLoadOlder={() => void loadOlderMessages()}
              />
            )}

            <MessageComposer disabled={socket.status !== "connected"} onSend={handleSend} />
          </>
        )}
      </section>

      {showNewConversation && (
        <NewConversationDialog
          token={token}
          currentUserId={user.id}
          onClose={() => setShowNewConversation(false)}
          onCreate={handleCreateConversation}
          onUsersFound={handleUsersFound}
        />
      )}
      {showAddParticipants && selectedConversation && (
        <NewConversationDialog
          mode="addParticipants"
          token={token}
          currentUserId={user.id}
          existingParticipantIds={selectedConversation.participantIds}
          onClose={() => setShowAddParticipants(false)}
          onAddParticipants={handleAddParticipants}
          onUsersFound={handleUsersFound}
        />
      )}
    </main>
  );
}