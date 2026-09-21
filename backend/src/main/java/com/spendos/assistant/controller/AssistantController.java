package com.spendos.assistant.controller;

import com.spendos.assistant.dto.AssistantDtos.AssistantAnswer;
import com.spendos.assistant.dto.AssistantDtos.QueryRequest;
import com.spendos.assistant.dto.AssistantDtos.Suggestion;
import com.spendos.assistant.service.AssistantService;
import com.spendos.common.dto.ApiResponse;
import com.spendos.common.exception.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/assistant")
@Tag(name = "Assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final boolean enabled;

    public AssistantController(AssistantService assistantService,
                               @Value("${features.ai-assistant-enabled:true}") boolean enabled) {
        this.assistantService = assistantService;
        this.enabled = enabled;
    }

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<AssistantAnswer>> query(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody QueryRequest request) {
        requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(assistantService.answer(userId, request)));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<Suggestion>>> suggestions(@AuthenticationPrincipal UUID userId) {
        requireEnabled();
        return ResponseEntity.ok(ApiResponse.success(assistantService.suggestions(userId)));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_DISABLED", "The assistant is turned off");
        }
    }
}
