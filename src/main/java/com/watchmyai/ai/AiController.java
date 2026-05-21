package com.watchmyai.ai;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/ask")
    public AskAIResponse ask(@Valid @RequestBody AskAIRequest request) {
        return aiService.ask(request);
    }

    @GetMapping("/ask/{clientRequestId}")
    public AskAIResponse askStatus(@PathVariable String clientRequestId) {
        return aiService.jobStatus(clientRequestId);
    }
}