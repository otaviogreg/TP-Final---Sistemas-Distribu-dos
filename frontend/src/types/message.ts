export interface ChatMessage {
  id: string;
  conversationId: string;
  senderId: string;
  content: string;
  createdAt: string;
}

export interface MessagePage {
  messages: ChatMessage[];
  page: number;
  size: number;
  hasNext: boolean;
}

export interface SendMessageRequest {
  content: string;
}