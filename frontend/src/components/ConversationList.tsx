import type { UserProfile, UserSummary } from "../types/auth";
import type { Conversation } from "../types/conversation";

interface ConversationListProps {
  conversations: Conversation[];
  selectedId: string | null;
  currentUser: UserProfile;
  knownUsers: Record<string, string>;
  onSelect: (id: string) => void;
  onCreate: () => void;
}

function initials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toLocaleUpperCase("pt-BR") ?? "")
    .join("");
}

export function conversationTitle(
  conversation: Conversation,
  currentUserId: string,
  knownUsers: Record<string, string>,
) {
  if (conversation.name) return conversation.name;
  if (conversation.type === "GROUP") return "Grupo sem nome";

  const otherId = conversation.participantIds.find((id) => id !== currentUserId);
  return (otherId && knownUsers[otherId]) || "Conversa privada";
}

export function rememberUsers(
  current: Record<string, string>,
  users: UserSummary[],
): Record<string, string> {
  return {
    ...current,
    ...Object.fromEntries(users.map((user) => [user.id, user.displayName])),
  };
}

export function ConversationList({
  conversations,
  selectedId,
  currentUser,
  knownUsers,
  onSelect,
  onCreate,
}: ConversationListProps) {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <span className="brand-mark">c</span>
        <span>conversa</span>
      </div>

      <div className="sidebar-heading">
        <div>
          <span className="eyebrow">ESPAÇO DE TRABALHO</span>
          <h1>Mensagens</h1>
        </div>
        <button className="icon-button new-chat-button" onClick={onCreate} aria-label="Nova conversa">
          +
        </button>
      </div>

      <button className="new-conversation-button" onClick={onCreate}>
        <span>＋</span> Nova conversa
      </button>

      <div className="conversation-section-title">
        <span>CONVERSAS</span>
        <span className="conversation-count">{conversations.length}</span>
      </div>

      <nav className="conversation-list" aria-label="Conversas">
        {conversations.length === 0 ? (
          <div className="sidebar-empty">
            <span className="empty-icon">✳</span>
            <p>Suas conversas aparecerão aqui.</p>
            <button onClick={onCreate}>Começar uma conversa</button>
          </div>
        ) : (
          conversations.map((conversation) => {
            const title = conversationTitle(conversation, currentUser.id, knownUsers);
            const isGroup = conversation.type === "GROUP";
            return (
              <button
                className={`conversation-item${selectedId === conversation.id ? " selected" : ""}`}
                key={conversation.id}
                onClick={() => onSelect(conversation.id)}
                aria-current={selectedId === conversation.id ? "page" : undefined}
              >
                <span className={`avatar ${isGroup ? "avatar-group" : ""}`}>
                  {isGroup ? "✳" : initials(title) || "?"}
                </span>
                <span className="conversation-item-copy">
                  <span className="conversation-item-title">{title}</span>
                  <span className="conversation-item-meta">
                    {isGroup
                      ? `Grupo · ${conversation.participantIds.length} participantes`
                      : "Conversa privada"}
                  </span>
                </span>
                <span className="conversation-dot" aria-hidden="true" />
              </button>
            );
          })
        )}
      </nav>

      <div className="profile-card">
        <span className="avatar avatar-profile">{initials(currentUser.displayName) || "?"}</span>
        <span className="profile-copy">
          <strong>{currentUser.displayName}</strong>
          <span>Conectado</span>
        </span>
        <span className="presence-dot" title="Conectado" />
      </div>
    </aside>
  );
}