package br.edu.cefet.tp.chat.messaging;

import br.edu.cefet.tp.chat.ChatServiceApplication;
import br.edu.cefet.tp.chat.model.Conversation;
import br.edu.cefet.tp.chat.model.ConversationType;
import br.edu.cefet.tp.chat.dto.MessagePageResponse;
import br.edu.cefet.tp.chat.dto.MessageResponse;
import br.edu.cefet.tp.chat.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = ChatServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "app.jwt.issuer=test-chat-auth",
        "app.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class RedisMessageIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("chat_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Test
    void authenticatedMemberSendsMessageAndReadsPersistedHistory() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();
        Conversation conversationEntity = new Conversation(
                ConversationType.GROUP,
                "Integration test group",
                creatorId
        );
        conversationEntity.addParticipant(creatorId);
        conversationEntity.addParticipant(otherMemberId);
        Conversation savedConversation = conversationRepository.save(conversationEntity);
        UUID conversationId = savedConversation.getId();
        String token = tokenFor(creatorId);

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.getObjectMapper().findAndRegisterModules();
        stompClient.setMessageConverter(converter);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost:5173");
        CompletableFuture<StompSession> connection = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        );
        StompSession session = connection.get(10, TimeUnit.SECONDS);
        try {
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<ChatEvent> deliveredEvent = new AtomicReference<>();
            session.subscribe(
                    "/topic/conversations/" + conversationId,
                    new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            return ChatEvent.class;
                        }

                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            deliveredEvent.set((ChatEvent) payload);
                            received.countDown();
                        }
                    }
            );
            awaitSubscription(creatorId, "/topic/conversations/" + conversationId);
            session.send(
                    "/app/conversations/" + conversationId + "/messages",
                    new br.edu.cefet.tp.chat.dto.SendMessageRequest("Distributed hello")
            );

            assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(deliveredEvent.get()).isNotNull();
            assertThat(deliveredEvent.get().conversationId()).isEqualTo(conversationId);
            assertThat(deliveredEvent.get().senderId()).isEqualTo(creatorId);
            assertThat(deliveredEvent.get().content()).isEqualTo("Distributed hello");
        } finally {
            session.disconnect();
            stompClient.stop();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<MessagePageResponse> history = restTemplate.exchange(
                "/api/v1/conversations/" + conversationId + "/messages",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                MessagePageResponse.class
        );

        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).isNotNull();
        assertThat(history.getBody().messages())
                .extracting(MessageResponse::content)
                .containsExactly("Distributed hello");
        assertThat(history.getBody().messages().getFirst().senderId()).isEqualTo(creatorId);

        HttpHeaders outsiderHeaders = new HttpHeaders();
        outsiderHeaders.setBearerAuth(tokenFor(UUID.randomUUID()));
        ResponseEntity<String> forbiddenHistory = restTemplate.exchange(
                "/api/v1/conversations/" + conversationId + "/messages",
                HttpMethod.GET,
                new HttpEntity<>(outsiderHeaders),
                String.class
        );
        assertThat(forbiddenHistory.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void conversationEndpointsRequireValidBearerToken() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/conversations",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String tokenFor(UUID userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("test-chat-auth")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject(userId.toString())
                .claim("email", "test@example.com")
                .claim("displayName", "Integration Test")
                .build();
        byte[] secret = Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        var key = new SecretKeySpec(secret, "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        return encoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(
                        org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256
                ).build(),
                claims
        )).getTokenValue();
    }

    private void awaitSubscription(UUID userId, String destination) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            SimpUser user = simpUserRegistry.getUser(userId.toString());
            if (user != null && user.getSessions().stream()
                    .flatMap(session -> session.getSubscriptions().stream())
                    .anyMatch(subscription -> destination.equals(subscription.getDestination()))) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("The WebSocket subscription was not registered in time.");
    }
}