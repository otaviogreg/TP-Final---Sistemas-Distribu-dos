package br.edu.cefet.tp.auth.controller;

import br.edu.cefet.tp.auth.dto.UserResponse;
import br.edu.cefet.tp.auth.exception.UserNotFoundException;
import br.edu.cefet.tp.auth.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
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
}