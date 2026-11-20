package br.edu.cefet.tp.chat.service;

import br.edu.cefet.tp.chat.dto.MessagePageResponse;
import br.edu.cefet.tp.chat.dto.MessageResponse;
import br.edu.cefet.tp.chat.dto.SendMessageRequest;
import br.edu.cefet.tp.chat.exception.InvalidMessageException;
import br.edu.cefet.tp.chat.model.Conversation;
import br.edu.cefet.tp.chat.model.Message;
import br.edu.cefet.tp.chat.repository.MessageRepository;
import br.edu.cefet.tp.chat.messaging.ChatEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MessageService {

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MessageService(
            ConversationService conversationService,
            MessageRepository messageRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MessageResponse send(UUID conversationId, UUID senderId, SendMessageRequest request) {
        Conversation conversation = conversationService.requireMembership(conversationId, senderId);
        String content = request.content().strip();
        if (content.isEmpty()) {
            throw new InvalidMessageException("Message content must not be blank.");
        }

        Message saved = messageRepository.saveAndFlush(new Message(conversation, senderId, content));
        conversation.touch();
        eventPublisher.publishEvent(ChatEvent.from(saved));
        return MessageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse history(UUID conversationId, UUID userId, int page, int size) {
        conversationService.requireMembership(conversationId, userId);
        Page<Message> result = messageRepository.findByConversation_IdOrderByCreatedAtDesc(
                conversationId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        var messages = result.getContent().stream()
                .map(MessageResponse::from)
                .toList()
                .reversed();
        return new MessagePageResponse(messages, result.getNumber(), result.getSize(), result.hasNext());
    }
}