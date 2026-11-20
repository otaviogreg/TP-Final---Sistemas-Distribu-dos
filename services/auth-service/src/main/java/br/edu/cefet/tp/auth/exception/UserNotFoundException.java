package br.edu.cefet.tp.auth.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("The authenticated user no longer exists.");
    }
}
