package br.edu.cefet.tp.chat.repository;

import br.edu.cefet.tp.chat.model.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findDistinctByParticipants_UserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<Conversation> findByIdAndParticipants_UserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from Conversation conversation where conversation.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByIdAndParticipants_UserId(UUID id, UUID userId);
}