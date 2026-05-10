package com.example.evimind.controller;

import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.model.entity.ResearchNote;
import com.example.evimind.service.ResearchNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class ResearchNoteController {

    private final ResearchNoteService researchNoteService;

    @PostMapping
    public ResponseEntity<ApiResponse<ResearchNote>> create(@RequestBody ResearchNote note) {
        return ResponseEntity.ok(ApiResponse.success(researchNoteService.create(note)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ResearchNote>> update(@PathVariable Long id, @RequestBody ResearchNote note) {
        return ResponseEntity.ok(ApiResponse.success(researchNoteService.update(id, note)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        researchNoteService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ResearchNote>>> list(
            @RequestParam(required = false) Long chunkId,
            @RequestParam(required = false) Long documentId) {
        if (chunkId != null) {
            return ResponseEntity.ok(ApiResponse.success(researchNoteService.listByChunk(chunkId)));
        }
        if (documentId != null) {
            return ResponseEntity.ok(ApiResponse.success(researchNoteService.listByDocument(documentId)));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Must provide chunkId or documentId"));
    }
}
