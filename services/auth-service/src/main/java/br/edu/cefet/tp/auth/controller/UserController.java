package br.edu.cefet.tp.auth.controller;

import br.edu.cefet.tp.auth.dto.UserResponse;
import br.edu.cefet.tp.auth.dto.UserSummaryResponse;
import br.edu.cefet.tp.auth.dto.ValidateUsersRequest;
import br.edu.cefet.tp.auth.exception.UserNotFoundException;
import br.edu.cefet.tp.auth.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(UserNotFoundException::new);
    }

    @GetMapping
    public List<UserSummaryResponse> search(
            @RequestParam @NotBlank @Size(max = 100) String query
    ) {
        String normalizedQuery = query.trim();
        return userRepository
                .findTop50ByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByDisplayNameAsc(
                        normalizedQuery,
                        normalizedQuery
                )
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
    }

    @PostMapping("/validate")
    public ResponseEntity<Void> validateUsers(@Valid @RequestBody ValidateUsersRequest request) {
        long existingUsers = userRepository.findAllById(request.userIds()).size();
        if (existingUsers != request.userIds().stream().distinct().count()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.noContent().build();
    }
}