package com.example.evimind.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.evimind.model.AnalysisResult;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, String> {
  List<AnalysisResult> findByFilePathOrderByAnalyzedAtDesc(String filePath);

  Page<AnalysisResult> findAllByOrderByAnalyzedAtDesc(Pageable pageable);
}
