package br.edu.cefet.tp.chat.dto;

import br.edu.cefet.tp.chat.model.ConversationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateConversationRequest(
        @NotNull ConversationType type,
        @Size(max = 100) String name,
        @NotEmpty @Size(max = 99) List<@NotNull UUID> participantIds
) {
}