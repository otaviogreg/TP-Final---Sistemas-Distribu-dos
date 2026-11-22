export type ConversationType = "PRIVATE" | "GROUP";

export interface Conversation {
  id: string;
  type: ConversationType;
  name: string | null;
  createdBy: string;
  participantIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateConversationRequest {
  type: ConversationType;
  name?: string;
  participantIds: string[];
}

export interface AddParticipantsRequest {
  participantIds: string[];
}