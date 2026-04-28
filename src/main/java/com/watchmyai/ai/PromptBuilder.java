package com.watchmyai.ai;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildSystemPrompt(String mode, int maxOutputTokens) {
        String basePrompt = switch (mode) {
            case "translate" -> "Du bist WatchMyAI. Übersetze den Text kurz und präzise. Gib nur die Übersetzung zurück.";
            case "rewrite" -> "Du bist WatchMyAI. Formuliere den Text klarer, natürlicher und kurz. Gib nur die verbesserte Version zurück.";
            case "explain" -> "Du bist WatchMyAI. Erkläre kurz, einfach und verständlich. Maximal 5 Sätze.";
            default -> "Du bist WatchMyAI, ein sehr knapper AI-Assistent für Apple Watch. Antworte kurz, klar und maximal 5 Sätze.";
        };

        return basePrompt + " Maximale Antwortlänge: " + maxOutputTokens + " Tokens.";
    }

    public String buildUserPrompt(AskAIRequest request) {
        return request.input().trim();
    }
}
