export interface FileItem {
  name: string
  path: string
  isDir: boolean
  size: number
  lastModified: number
  category: string
}

export interface FileListResult {
  currentDir: string
  items: FileItem[]
}

export interface BatchAnalysisRequest {
  paths: string[]
  provider?: string
  sessionId?: string
}

export interface BatchAnalysisStart {
  taskId: string
  fileCount?: number
  files?: string[]
}

export interface BatchProgress {
  taskId?: string
  total: number
  completed: number
  currentFile?: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | string
}

export interface BatchAnalysisItemResult {
  filePath: string
  fileName: string
  success: boolean
  error?: string
  content?: string
  resultId?: string
}

export interface BatchAnalysisResult {
  results: BatchAnalysisItemResult[]
  status: string
}

export interface AnalysisResult {
  id: string
  filePath: string
  fileName: string
  provider: string
  sessionId: string
  createdAt: string
  content: string
  fileSize: number
  fileCategory: string
}

export interface AnalysisResultPage {
  content?: AnalysisResult[]
  records?: AnalysisResult[]
  totalElements?: number
  total?: number
  size: number
  number?: number
  current?: number
}
