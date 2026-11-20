package br.edu.cefet.tp.chat.controller;

import br.edu.cefet.tp.chat.dto.MessagePageResponse;
import br.edu.cefet.tp.chat.dto.MessageResponse;
import br.edu.cefet.tp.chat.dto.SendMessageRequest;
import br.edu.cefet.tp.chat.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.security.Principal;

@Validated
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
public class MessagesController {

    private final MessageService messageService;

    public MessagesController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public MessagePageResponse history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return messageService.history(
                conversationId,
                UUID.fromString(jwt.getSubject()),
                page,
                size
        );
    }

    @MessageMapping("/conversations/{conversationId}/messages")
    public MessageResponse send(
            Principal principal,
            @DestinationVariable UUID conversationId,
            @Valid @Payload SendMessageRequest request
    ) {
        return messageService.send(
                conversationId,
                UUID.fromString(principal.getName()),
                request
        );
    }
}