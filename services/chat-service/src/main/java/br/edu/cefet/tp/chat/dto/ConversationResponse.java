package br.edu.cefet.tp.chat.dto;

import br.edu.cefet.tp.chat.model.Conversation;
import br.edu.cefet.tp.chat.model.ConversationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        ConversationType type,
        String name,
        UUID createdBy,
        List<UUID> participantIds,
        Instant createdAt,
        Instant updatedAt
) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getType(),
                conversation.getName(),
                conversation.getCreatedBy(),
                conversation.getParticipants().stream().map(participant -> participant.getUserId()).toList(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }
}