package br.edu.cefet.tp.chat.exception;

public class ConversationForbiddenException extends RuntimeException {

    public ConversationForbiddenException() {
        super("Only the group creator can add participants.");
    }
}
