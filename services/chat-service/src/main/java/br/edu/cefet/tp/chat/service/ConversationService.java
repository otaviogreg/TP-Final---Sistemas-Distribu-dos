package br.edu.cefet.tp.chat.service;

import br.edu.cefet.tp.chat.dto.ConversationResponse;
import br.edu.cefet.tp.chat.dto.CreateConversationRequest;
import br.edu.cefet.tp.chat.client.AuthServiceClient;
import br.edu.cefet.tp.chat.exception.ConversationNotFoundException;
import br.edu.cefet.tp.chat.exception.InvalidConversationException;
import br.edu.cefet.tp.chat.model.Conversation;
import br.edu.cefet.tp.chat.model.ConversationType;
import br.edu.cefet.tp.chat.repository.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final AuthServiceClient authServiceClient;

    public ConversationService(
            ConversationRepository conversationRepository,
            AuthServiceClient authServiceClient
    ) {
        this.conversationRepository = conversationRepository;
        this.authServiceClient = authServiceClient;
    }

    public ConversationResponse create(UUID creatorId, String accessToken, CreateConversationRequest request) {
        List<UUID> members = new ArrayList<>(new LinkedHashSet<>(request.participantIds()));
        if (members.size() != request.participantIds().size()) {
            throw new InvalidConversationException("A participant can only be listed once.");
        }
        if (members.contains(creatorId)) {
            throw new InvalidConversationException("The creator is added automatically.");
        }
        members.add(creatorId);

        String name = request.name() == null ? null : request.name().trim();
        if (request.type() == ConversationType.PRIVATE) {
            if (members.size() != 2) {
                throw new InvalidConversationException("A private conversation must have exactly two participants.");
            }
            if (name != null && !name.isBlank()) {
                throw new InvalidConversationException("A private conversation cannot have a group name.");
            }
            name = null;
        } else if (name == null || name.isBlank()) {
            throw new InvalidConversationException("A group conversation must have a name.");
        }

        authServiceClient.validateUsers(members.stream()
                .filter(memberId -> !memberId.equals(creatorId))
                .toList(), accessToken);

        Conversation conversation = new Conversation(request.type(), name, creatorId);
        members.forEach(conversation::addParticipant);
        return ConversationResponse.from(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listForUser(UUID userId) {
        return conversationRepository.findDistinctByParticipants_UserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(ConversationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Conversation requireMembership(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndParticipants_UserId(conversationId, userId)
                .orElseThrow(ConversationNotFoundException::new);
    }
}