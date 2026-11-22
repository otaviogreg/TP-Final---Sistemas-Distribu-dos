package br.edu.cefet.tp.chat.controller;

import br.edu.cefet.tp.chat.dto.ConversationResponse;
import br.edu.cefet.tp.chat.dto.AddParticipantsRequest;
import br.edu.cefet.tp.chat.dto.CreateConversationRequest;
import br.edu.cefet.tp.chat.model.ConversationType;
import br.edu.cefet.tp.chat.service.ConversationService;
import org.springframework.core.MethodParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationsControllerTest {

    private ConversationService conversationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationsController(conversationService))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return Jwt.class.isAssignableFrom(parameter.getParameterType());
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return webRequest.getAttribute("testJwt", NativeWebRequest.SCOPE_REQUEST);
                    }
                })
                .setValidator(validator)
                .build();
    }

    @Test
    void createUsesAuthenticatedSubjectAsCreator() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversationService.create(eq(creatorId), anyString(), any(CreateConversationRequest.class)))
                .thenReturn(new ConversationResponse(
                        conversationId,
                        ConversationType.GROUP,
                        "Study group",
                        creatorId,
                        List.of(creatorId, participantId),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/conversations")
                        .with(authenticatedAs(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "GROUP",
                                  "name": "Study group",
                                  "participantIds": ["%s"]
                                }
                                """.formatted(participantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.participantIds.length()").value(2));

        verify(conversationService).create(eq(creatorId), anyString(), any(CreateConversationRequest.class));
    }

    @Test
    void createRejectsInvalidConversationType() throws Exception {
        mockMvc.perform(post("/api/v1/conversations")
                        .with(authenticatedAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "UNKNOWN",
                                  "name": "Study group",
                                  "participantIds": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addParticipantsUsesAuthenticatedSubjectAndConversationId() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversationService.addParticipants(
                eq(conversationId),
                eq(creatorId),
                anyString(),
                any(AddParticipantsRequest.class)
        )).thenReturn(new ConversationResponse(
                conversationId,
                ConversationType.GROUP,
                "Study group",
                creatorId,
                List.of(creatorId, participantId),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/conversations/{conversationId}/participants", conversationId)
                        .with(authenticatedAs(creatorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantIds": ["%s"]
                                }
                                """.formatted(participantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantIds.length()").value(2));

        verify(conversationService).addParticipants(
                eq(conversationId),
                eq(creatorId),
                eq("caller-token"),
                any(AddParticipantsRequest.class)
        );
    }

    @Test
    void addParticipantsRejectsEmptyRequest() throws Exception {
        mockMvc.perform(post("/api/v1/conversations/{conversationId}/participants", UUID.randomUUID())
                        .with(authenticatedAs(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantIds": []
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private RequestPostProcessor authenticatedAs(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("caller-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return request -> {
            request.setAttribute("testJwt", jwt);
            return request;
        };
    }
}