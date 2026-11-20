package br.edu.cefet.tp.chat.websocket;

import br.edu.cefet.tp.chat.repository.ConversationRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StompAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern SUBSCRIPTION_DESTINATION =
            Pattern.compile("^/topic/conversations/([0-9a-fA-F-]{36})$");
    private static final Pattern SEND_DESTINATION =
            Pattern.compile("^/app/conversations/([0-9a-fA-F-]{36})/messages$");

    private final StompAuthenticationInterceptor authenticationInterceptor;
    private final ConversationRepository conversationRepository;

    public StompAuthorizationInterceptor(
            StompAuthenticationInterceptor authenticationInterceptor,
            ConversationRepository conversationRepository
    ) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.conversationRepository = conversationRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            return authenticationInterceptor.preSend(message, channel);
        }
        if (accessor.getCommand() != StompCommand.SUBSCRIBE
                && accessor.getCommand() != StompCommand.SEND) {
            return message;
        }

        Principal principal = accessor.getUser();
        String destination = accessor.getDestination();
        Pattern allowedPattern = accessor.getCommand() == StompCommand.SUBSCRIBE
                ? SUBSCRIPTION_DESTINATION
                : SEND_DESTINATION;
        Matcher matcher = destination == null ? null : allowedPattern.matcher(destination);
        if (principal == null || matcher == null || !matcher.matches()) {
            throw new AccessDeniedException("The destination is not authorized.");
        }

        UUID userId;
        UUID conversationId;
        try {
            userId = UUID.fromString(principal.getName());
            conversationId = UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("The destination or identity is invalid.", exception);
        }

        if (!conversationRepository.existsByIdAndParticipants_UserId(conversationId, userId)) {
            throw new AccessDeniedException("The user is not a participant in this conversation.");
        }
        return message;
    }
}
