import { Client, type IMessage } from "@stomp/stompjs";
import { useCallback, useEffect, useRef, useState } from "react";
import type { ChatMessage } from "../types/message";

export type SocketStatus =
  | "idle"
  | "connecting"
  | "connected"
  | "disconnected"
  | "error";

export function useChatSocket(
  token: string,
  conversationId: string | null,
  onMessage: (message: ChatMessage) => void,
) {
  const clientRef = useRef<Client | null>(null);
  const [status, setStatus] = useState<SocketStatus>("idle");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!conversationId) {
      setStatus("idle");
      setError(null);
      return;
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setStatus("connected");
        setError(null);
        client.subscribe(`/topic/conversations/${conversationId}`, (frame: IMessage) => {
          try {
            onMessage(JSON.parse(frame.body) as ChatMessage);
          } catch {
            setError("O servidor enviou uma mensagem em formato inválido.");
          }
        });
      },
      onWebSocketClose: () => setStatus("disconnected"),
      onWebSocketError: () => {
        setStatus("error");
        setError("Conexão em tempo real indisponível. Tentando reconectar...");
      },
      onStompError: (frame) => {
        setStatus("error");
        setError(frame.headers.message ?? "Falha na conexão com o chat.");
      },
    });

    clientRef.current = client;
    setStatus("connecting");
    setError(null);
    client.activate();

    return () => {
      clientRef.current = null;
      void client.deactivate();
    };
  }, [conversationId, onMessage, token]);

  const sendMessage = useCallback((content: string) => {
    const client = clientRef.current;
    if (!client?.connected || !conversationId) {
      throw new Error("A conexão com o chat ainda não está pronta.");
    }

    client.publish({
      destination: `/app/conversations/${conversationId}/messages`,
      body: JSON.stringify({ content }),
    });
  }, [conversationId]);

  return { status, error, sendMessage };
}