package br.edu.cefet.tp.chat.repository;

import br.edu.cefet.tp.chat.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findDistinctByParticipants_UserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<Conversation> findByIdAndParticipants_UserId(UUID id, UUID userId);

    boolean existsByIdAndParticipants_UserId(UUID id, UUID userId);
}