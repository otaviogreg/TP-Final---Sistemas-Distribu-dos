package br.edu.cefet.tp.chat.dto;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> messages,
        int page,
        int size,
        boolean hasNext
) {
}
