package br.edu.cefet.tp.chat.exception;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversation not found.");
    }
}
