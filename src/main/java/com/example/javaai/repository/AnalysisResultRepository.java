package com.example.javaai.repository;

import com.example.javaai.model.AnalysisResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, String> {
    List<AnalysisResult> findByFilePathOrderByAnalyzedAtDesc(String filePath);
    Page<AnalysisResult> findAllByOrderByAnalyzedAtDesc(Pageable pageable);
}
