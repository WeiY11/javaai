import type {
  AnalysisResultPage,
  BatchAnalysisRequest,
  BatchAnalysisResult,
  BatchAnalysisStart,
  BatchProgress,
  FileListResult
} from '../types/analysis.types'
import { rootGet, rootPost } from '../utils/request'

export async function listFiles(dir = ''): Promise<FileListResult> {
  return rootGet('/files', { dir })
}

export async function startBatchAnalysis(data: BatchAnalysisRequest): Promise<BatchAnalysisStart> {
  return rootPost('/analysis/batch', data)
}

export async function startDirectoryAnalysis(dir: string, provider = 'deepseek'): Promise<BatchAnalysisStart> {
  return rootPost('/analysis/batch-dir', { dir, provider })
}

export async function getBatchProgress(taskId: string): Promise<BatchProgress> {
  return rootGet('/analysis/batch/progress', { taskId })
}

export async function getBatchResult(taskId: string): Promise<BatchAnalysisResult> {
  return rootGet('/analysis/batch/result', { taskId })
}

export async function listAnalysisResults(page = 0, size = 20): Promise<AnalysisResultPage> {
  return rootGet('/analysis/results', { page, size })
}

export function markdownExportUrl(resultIds: string[], title = '分析报告'): string {
  return `/api/analysis/export/markdown?resultIds=${encodeURIComponent(resultIds.join(','))}&title=${encodeURIComponent(title)}`
}

export function pdfExportUrl(resultIds: string[], title = '分析报告'): string {
  return `/api/analysis/export/pdf?resultIds=${encodeURIComponent(resultIds.join(','))}&title=${encodeURIComponent(title)}`
}
