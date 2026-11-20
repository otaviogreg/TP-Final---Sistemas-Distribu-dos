import type {
  Conversation,
  CreateConversationRequest,
} from "../types/conversation";
import type { MessagePage } from "../types/message";
import { requestJson } from "./http";

export function listConversations(token: string): Promise<Conversation[]> {
  return requestJson("/api/v1/conversations", token);
}

export function createConversation(
  token: string,
  request: CreateConversationRequest,
): Promise<Conversation> {
  return requestJson("/api/v1/conversations", token, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function getMessageHistory(
  token: string,
  conversationId: string,
  page = 0,
  size = 50,
): Promise<MessagePage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return requestJson(
    `/api/v1/conversations/${encodeURIComponent(conversationId)}/messages?${params.toString()}`,
    token,
  );
}