package com.example.evimind.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.evimind.audit.AuditService;
import com.example.evimind.model.dto.ApiResponse;
import com.example.evimind.service.AutoReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

  private final AutoReportService autoReportService;
  private final AuditService auditService;

  /** 生成知识库使用报告 */
  @PostMapping("/knowledge-bases/{kbId}")
  public ResponseEntity<ApiResponse<String>> generateReport(
      @PathVariable Long kbId, @RequestBody(required = false) Map<String, String> body) {
    String period = body != null ? body.getOrDefault("period", "weekly") : "weekly";
    String report = autoReportService.generateReport(kbId, period);

    auditService.log("REPORT_GENERATE", "KNOWLEDGE_BASE", kbId, Map.of("period", period));

    return ResponseEntity.ok(ApiResponse.success(report));
  }
}
