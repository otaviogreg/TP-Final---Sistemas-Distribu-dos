package br.edu.cefet.tp.chat.messaging;

import br.edu.cefet.tp.chat.model.Message;

import java.time.Instant;
import java.util.UUID;

public record ChatEvent(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        Instant createdAt
) {

    public static ChatEvent from(Message message) {
        return new ChatEvent(
                message.getId(),
                message.getConversation().getId(),
                message.getSenderId(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}