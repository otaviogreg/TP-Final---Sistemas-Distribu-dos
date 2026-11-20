package br.edu.cefet.tp.chat.service;

import br.edu.cefet.tp.chat.dto.SendMessageRequest;
import br.edu.cefet.tp.chat.exception.ConversationNotFoundException;
import br.edu.cefet.tp.chat.exception.InvalidMessageException;
import br.edu.cefet.tp.chat.model.Conversation;
import br.edu.cefet.tp.chat.model.ConversationType;
import br.edu.cefet.tp.chat.model.Message;
import br.edu.cefet.tp.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    private ConversationService conversationService;
    private MessageRepository messageRepository;
    private ApplicationEventPublisher eventPublisher;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        messageRepository = mock(MessageRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        messageService = new MessageService(conversationService, messageRepository, eventPublisher);
    }

    @Test
    void sendPersistsMessageAndPublishesEventForAuthorizedMember() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Conversation conversation = mock(Conversation.class);
        Message saved = mock(Message.class);
        when(conversationService.requireMembership(conversationId, senderId)).thenReturn(conversation);
        when(messageRepository.saveAndFlush(any(Message.class))).thenReturn(saved);
        when(saved.getConversation()).thenReturn(conversation);
        when(saved.getId()).thenReturn(UUID.randomUUID());
        when(saved.getSenderId()).thenReturn(senderId);
        when(saved.getContent()).thenReturn("Hello");
        when(saved.getCreatedAt()).thenReturn(java.time.Instant.now());
        when(conversation.getId()).thenReturn(conversationId);

        var result = messageService.send(conversationId, senderId, new SendMessageRequest("  Hello  "));

        assertEquals("Hello", result.content());
        verify(messageRepository).saveAndFlush(any(Message.class));
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void sendRejectsBlankContentWithoutPersisting() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        when(conversationService.requireMembership(conversationId, senderId))
                .thenReturn(new Conversation(ConversationType.PRIVATE, null, senderId));

        assertThrows(InvalidMessageException.class, () -> messageService.send(
                conversationId,
                senderId,
                new SendMessageRequest("   ")
        ));

        verify(messageRepository, never()).saveAndFlush(any(Message.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sendRejectsUsersOutsideConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        when(conversationService.requireMembership(conversationId, senderId))
                .thenThrow(new ConversationNotFoundException());

        assertThrows(ConversationNotFoundException.class, () -> messageService.send(
                conversationId,
                senderId,
                new SendMessageRequest("Hello")
        ));

        verify(messageRepository, never()).saveAndFlush(any(Message.class));
    }
}