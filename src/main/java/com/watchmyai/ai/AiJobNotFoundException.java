package com.watchmyai.ai;

/**
 * Raised when a client polls for an AI job id that does not exist for the current user.
 */
public class AiJobNotFoundException extends RuntimeException {

    public AiJobNotFoundException(String clientRequestId) {
        super("AI job not found: " + clientRequestId);
    }
}
