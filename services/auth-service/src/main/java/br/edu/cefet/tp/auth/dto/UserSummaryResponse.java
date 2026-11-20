package br.edu.cefet.tp.auth.dto;

import br.edu.cefet.tp.auth.model.User;

import java.util.UUID;

public record UserSummaryResponse(UUID id, String displayName) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getDisplayName());
    }
}
