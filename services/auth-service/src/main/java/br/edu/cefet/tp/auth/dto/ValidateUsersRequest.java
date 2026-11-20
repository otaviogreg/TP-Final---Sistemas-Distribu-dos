package br.edu.cefet.tp.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ValidateUsersRequest(@NotEmpty @Size(max = 99) List<@NotNull UUID> userIds) {
}
