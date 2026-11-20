package br.edu.cefet.tp.chat.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public StompAuthenticationInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AccessDeniedException("A bearer token is required to connect.");
        }

        try {
            Jwt jwt = jwtDecoder.decode(authorization.substring(7));
            UUID userId = UUID.fromString(jwt.getSubject());
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userId.toString(),
                    null,
                    List.of()
            ));
            return message;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AccessDeniedException("The bearer token is invalid.", exception);
        }
    }
}
