package com.example.javaai.service;

import com.example.javaai.model.AnalysisResult;
import com.example.javaai.repository.AnalysisResultRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AnalysisResultService {

    private final AnalysisResultRepository repository;

    public AnalysisResultService(AnalysisResultRepository repository) {
        this.repository = repository;
    }

    public void saveResult(AnalysisResult result) {
        repository.save(result);
    }

    public List<AnalysisResult> getResultsByFile(String filePath) {
        return repository.findByFilePathOrderByAnalyzedAtDesc(filePath);
    }

    public Map<String, Object> getAllResults(int page, int size) {
        Page<AnalysisResult> pageData = repository.findAllByOrderByAnalyzedAtDesc(PageRequest.of(page, size));
        return Map.of(
            "results", pageData.getContent(),
            "total", pageData.getTotalElements(),
            "page", page,
            "size", size
        );
    }

    public List<AnalysisResult> getResultsByIds(List<String> resultIds) {
        return repository.findAllById(resultIds);
    }
}

