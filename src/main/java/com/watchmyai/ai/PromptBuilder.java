package com.watchmyai.ai;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildSystemPrompt(String mode, int maxOutputTokens) {
        String basePrompt = switch (mode) {
            case "translate" -> "You are WatchMyAI. Translate the text briefly and precisely. Return only the translation.";
            case "rewrite" -> "You are WatchMyAI. Rewrite the text clearly, naturally, and briefly. Return only the improved version.";
            case "explain" -> "You are WatchMyAI. Explain briefly, simply, and clearly. Use at most 5 sentences.";
            default -> "You are WatchMyAI, a very concise AI assistant for Apple Watch. Answer briefly, clearly, and in at most 5 sentences.";
        };

        return basePrompt + " Maximum answer length: " + maxOutputTokens + " tokens.";
    }

    public String buildUserPrompt(AskAIRequest request) {
        return languageInstruction(request.language()) + "\n\nUser text:\n" + request.input().trim();
    }

    private String languageInstruction(String language) {
        String languageName = languageName(language);
        if ("auto".equals(languageName)) {
            return "Language: Follow the user's language. If the user explicitly requests another language, use that requested language.";
        }

        return "Language: The app language is " + languageName + ". Use it as the default for ambiguous requests, but if the user writes in another language or explicitly asks for another language, answer in that language.";
    }

    private String languageName(String language) {
        return switch (language) {
            case "ar" -> "Arabic";
            case "cs" -> "Czech";
            case "da" -> "Danish";
            case "de" -> "German";
            case "el" -> "Greek";
            case "en" -> "English";
            case "es" -> "Spanish";
            case "fr" -> "French";
            case "hi" -> "Hindi";
            case "id" -> "Indonesian";
            case "it" -> "Italian";
            case "ja" -> "Japanese";
            case "ko" -> "Korean";
            case "nb", "no" -> "Norwegian";
            case "nl" -> "Dutch";
            case "pl" -> "Polish";
            case "pt-BR" -> "Brazilian Portuguese";
            case "ru" -> "Russian";
            case "sv" -> "Swedish";
            case "th" -> "Thai";
            case "tr" -> "Turkish";
            case "ur" -> "Urdu";
            case "vi" -> "Vietnamese";
            case "zh-Hans" -> "Simplified Chinese";
            default -> "auto";
        };
    }
}
