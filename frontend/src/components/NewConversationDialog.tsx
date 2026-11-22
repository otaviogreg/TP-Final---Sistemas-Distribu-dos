import { useEffect, useState } from "react";
import { searchUsers } from "../services/authApi";
import type { UserSummary } from "../types/auth";
import type { CreateConversationRequest } from "../types/conversation";

interface NewConversationDialogProps {
  token: string;
  currentUserId: string;
  onClose: () => void;
  onUsersFound: (users: UserSummary[]) => void;
  mode?: "create";
  onCreate: (request: CreateConversationRequest) => Promise<void>;
  existingParticipantIds?: never;
  onAddParticipants?: never;
}

interface AddParticipantsDialogProps {
  token: string;
  currentUserId: string;
  onClose: () => void;
  onUsersFound: (users: UserSummary[]) => void;
  mode: "addParticipants";
  existingParticipantIds: string[];
  onAddParticipants: (participantIds: string[]) => Promise<void>;
  onCreate?: never;
}

type DialogProps = NewConversationDialogProps | AddParticipantsDialogProps;

export function NewConversationDialog({
  mode,
  token,
  currentUserId,
  onClose,
  onCreate,
  onUsersFound,
  existingParticipantIds,
  onAddParticipants,
}: DialogProps) {
  const isAddingParticipants = mode === "addParticipants";
  const [selectedType, setSelectedType] = useState<"PRIVATE" | "GROUP">("PRIVATE");
  const type = isAddingParticipants ? "GROUP" : selectedType;
  const [query, setQuery] = useState("");
  const [groupName, setGroupName] = useState("");
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [selectedUsers, setSelectedUsers] = useState<UserSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const value = query.trim();
    if (value.length < 2) {
      setUsers([]);
      setLoading(false);
      return;
    }

    let active = true;
    const timer = window.setTimeout(() => {
      setLoading(true);
      setError(null);
      searchUsers(token, value)
        .then((results) => {
          if (!active) return;
          const eligibleUsers = results.filter(
            (user) =>
              user.id !== currentUserId &&
              !existingParticipantIds?.includes(user.id),
          );
          setUsers(eligibleUsers);
          onUsersFound(eligibleUsers);
        })
        .catch((cause: unknown) => {
          if (active) {
            setError(cause instanceof Error ? cause.message : "Não foi possível buscar pessoas.");
          }
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [currentUserId, existingParticipantIds, onUsersFound, query, token]);

  function toggleUser(user: UserSummary) {
    setSelectedUsers((current) =>
      current.some((selected) => selected.id === user.id)
        ? current.filter((selected) => selected.id !== user.id)
        : [...current, user],
    );
  }

  async function submit() {
    if (creating) return;
    setCreating(true);
    setError(null);
    try {
      if (isAddingParticipants) {
        await onAddParticipants(selectedUsers.map((user) => user.id));
      } else {
        await onCreate({
          type,
          ...(type === "GROUP" ? { name: groupName.trim() } : {}),
          participantIds: selectedUsers.map((user) => user.id),
        });
      }
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : isAddingParticipants
            ? "Não foi possível adicionar os participantes."
            : "Não foi possível criar a conversa.",
      );
      setCreating(false);
    }
  }

  const canCreate =
    selectedUsers.length > 0 &&
    (type === "PRIVATE" ? selectedUsers.length === 1 : groupName.trim().length > 0);

  return (
    <div
      className="dialog-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className="dialog new-conversation-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="new-conversation-title"
      >
        <div className="dialog-header">
          <div>
            <span className="eyebrow">{isAddingParticipants ? "AMPLIE O GRUPO" : "COMECE POR AQUI"}</span>
            <h2 id="new-conversation-title">
              {isAddingParticipants ? "Adicionar participantes" : "Nova conversa"}
            </h2>
          </div>
          <button className="icon-button close-button" onClick={onClose} aria-label="Fechar">
            ×
          </button>
        </div>

        {!isAddingParticipants && (
          <div className="conversation-type-switch" role="group" aria-label="Tipo de conversa">
            <button
              className={type === "PRIVATE" ? "active" : ""}
              onClick={() => {
                setSelectedType("PRIVATE");
                setSelectedUsers((current) => current.slice(0, 1));
              }}
            >
              <span>◉</span> Privada
            </button>
            <button
              className={type === "GROUP" ? "active" : ""}
              onClick={() => setSelectedType("GROUP")}
            >
              <span>♧</span> Grupo
            </button>
          </div>
        )}

        {type === "GROUP" && !isAddingParticipants && (
          <label className="field-label" htmlFor="group-name">
            Nome do grupo
            <input
              id="group-name"
              value={groupName}
              onChange={(event) => setGroupName(event.target.value)}
              placeholder="Ex.: Equipe do projeto"
              maxLength={80}
            />
          </label>
        )}

        <label className="field-label" htmlFor="user-search">
          {type === "PRIVATE" ? "Encontrar pessoa" : "Adicionar participantes"}
          <span className="search-field">
            <span aria-hidden="true">⌕</span>
            <input
              id="user-search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Busque por nome ou e-mail"
              autoComplete="off"
            />
          </span>
        </label>

        {selectedUsers.length > 0 && (
          <div className="selected-users" aria-label="Participantes selecionados">
            {selectedUsers.map((user) => (
              <button
                className="selected-user-chip"
                key={user.id}
                onClick={() => toggleUser(user)}
                aria-label={`Remover ${user.displayName}`}
              >
                {user.displayName} <span>×</span>
              </button>
            ))}
          </div>
        )}

        <div className="user-search-results" aria-live="polite">
          {loading && <p className="dialog-hint">Buscando pessoas...</p>}
          {!loading && query.trim().length < 2 && (
            <p className="dialog-hint">Digite pelo menos 2 caracteres para buscar.</p>
          )}
          {!loading && query.trim().length >= 2 && users.length === 0 && !error && (
            <p className="dialog-hint">Nenhuma pessoa encontrada.</p>
          )}
          {users.map((user) => {
            const selected = selectedUsers.some((item) => item.id === user.id);
            return (
              <button
                className="user-result"
                key={user.id}
                onClick={() => toggleUser(user)}
                aria-pressed={selected}
              >
                <span className="avatar avatar-search">
                  {user.displayName.trim().charAt(0).toLocaleUpperCase("pt-BR")}
                </span>
                <span className="user-result-name">{user.displayName}</span>
                <span className={`selection-check${selected ? " checked" : ""}`}>
                  {selected ? "✓" : "+"}
                </span>
              </button>
            );
          })}
        </div>

        {error && <p className="inline-error" role="alert">{error}</p>}

        <div className="dialog-footer">
          <span className="dialog-hint">
            {isAddingParticipants
              ? "Os participantes adicionados poderão ver o histórico do grupo."
              : type === "PRIVATE"
                ? "Uma pessoa por conversa"
                : "Adicione uma ou mais pessoas"}
          </span>
          <button
            className="primary-button"
            onClick={() => void submit()}
            disabled={!(isAddingParticipants ? selectedUsers.length > 0 : canCreate) || creating}
          >
            {creating
              ? isAddingParticipants ? "Adicionando..." : "Criando..."
              : isAddingParticipants ? "Adicionar" : "Criar conversa"}
            {!creating && <span aria-hidden="true">→</span>}
          </button>
        </div>
      </section>
    </div>
  );
}
