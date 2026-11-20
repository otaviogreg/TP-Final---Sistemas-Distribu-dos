package br.edu.cefet.tp.chat.websocket;

import br.edu.cefet.tp.chat.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthorizationInterceptorTest {

    private ConversationRepository conversationRepository;
    private StompAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        interceptor = new StompAuthorizationInterceptor(
                mock(StompAuthenticationInterceptor.class),
                conversationRepository
        );
    }

    @Test
    void deniesSubscriptionWhenUserIsNotConversationMember() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.existsByIdAndParticipants_UserId(conversationId, userId))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                stompMessage(StompCommand.SUBSCRIBE, userId, "/topic/conversations/" + conversationId),
                mock(MessageChannel.class)
        ));
    }

    @Test
    void deniesSendingToNonConversationDestination() {
        UUID userId = UUID.randomUUID();

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                stompMessage(StompCommand.SEND, userId, "/app/admin/broadcast"),
                mock(MessageChannel.class)
        ));
    }

    private Message<byte[]> stompMessage(StompCommand command, UUID userId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of()
        ));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
