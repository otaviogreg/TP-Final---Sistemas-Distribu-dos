package br.edu.cefet.tp.chat.service;

import br.edu.cefet.tp.chat.client.AuthServiceClient;
import br.edu.cefet.tp.chat.dto.CreateConversationRequest;
import br.edu.cefet.tp.chat.exception.InvalidConversationException;
import br.edu.cefet.tp.chat.model.Conversation;
import br.edu.cefet.tp.chat.model.ConversationType;
import br.edu.cefet.tp.chat.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    private ConversationRepository conversationRepository;
    private AuthServiceClient authServiceClient;
    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        authServiceClient = mock(AuthServiceClient.class);
        conversationService = new ConversationService(conversationRepository, authServiceClient);
        when(conversationRepository.save(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createGroupAddsCreatorAndValidatesOtherUsersWithAuthService() {
        UUID creatorId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        var result = conversationService.create(
                creatorId,
                "caller-token",
                new CreateConversationRequest(
                        ConversationType.GROUP,
                        "  Study group  ",
                        List.of(participantId)
                )
        );

        assertEquals(ConversationType.GROUP, result.type());
        assertEquals("Study group", result.name());
        assertEquals(creatorId, result.createdBy());
        assertEquals(2, result.participantIds().size());
        verify(authServiceClient).validateUsers(List.of(participantId), "caller-token");
    }

    @Test
    void createPrivateConversationRequiresExactlyOneOtherUser() {
        UUID creatorId = UUID.randomUUID();

        assertThrows(InvalidConversationException.class, () -> conversationService.create(
                creatorId,
                "caller-token",
                new CreateConversationRequest(ConversationType.PRIVATE, null, List.of())
        ));

        verify(authServiceClient, never()).validateUsers(any(), any());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void createRejectsDuplicateParticipantsBeforeCallingAuthService() {
        UUID creatorId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        assertThrows(InvalidConversationException.class, () -> conversationService.create(
                creatorId,
                "caller-token",
                new CreateConversationRequest(
                        ConversationType.GROUP,
                        "Study group",
                        List.of(participantId, participantId)
                )
        ));

        verify(authServiceClient, never()).validateUsers(any(), any());
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void createRejectsGroupWithoutName() {
        UUID creatorId = UUID.randomUUID();

        assertThrows(InvalidConversationException.class, () -> conversationService.create(
                creatorId,
                "caller-token",
                new CreateConversationRequest(ConversationType.GROUP, " ", List.of(UUID.randomUUID()))
        ));

        verify(authServiceClient, never()).validateUsers(any(), any());
    }
}
