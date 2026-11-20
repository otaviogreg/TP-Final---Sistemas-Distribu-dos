package br.edu.cefet.tp.chat.client;

import br.edu.cefet.tp.chat.dto.ValidateUsersRequest;
import br.edu.cefet.tp.chat.exception.InvalidConversationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class AuthServiceClient {

    private final RestClient restClient;

    public AuthServiceClient(
            RestClient.Builder builder,
            @Value("${app.auth-service.url}") String authServiceUrl
    ) {
        this.restClient = builder.baseUrl(authServiceUrl).build();
    }

    public void validateUsers(List<UUID> userIds, String accessToken) {
        try {
            restClient.post()
                    .uri("/api/v1/users/validate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .body(new ValidateUsersRequest(userIds))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new InvalidConversationException("One or more participants do not exist.");
        }
    }
}
