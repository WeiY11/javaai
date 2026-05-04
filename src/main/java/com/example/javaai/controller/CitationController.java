package com.example.javaai.controller;

import com.example.javaai.model.dto.ApiResponse;
import com.example.javaai.service.CitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/citations")
@RequiredArgsConstructor
public class CitationController {

    private final CitationService citationService;

    @PostMapping("/export")
    public ResponseEntity<ApiResponse<String>> exportCitations(
            @RequestBody List<Long> documentIds,
            @RequestParam(defaultValue = "bibtex") String format) {
        String result;
        if ("apa".equalsIgnoreCase(format)) {
            result = citationService.generateApa(documentIds);
        } else {
            result = citationService.generateBibtex(documentIds);
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
