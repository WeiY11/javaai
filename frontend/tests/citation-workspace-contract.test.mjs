import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

test('citation page exposes an export workbench before the document table', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/CitationView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\bcitation-workbench\b[^"]*"/)
  assert.match(source, /引用工作台/)
  assert.match(source, /citationReadiness/)
  assert.match(source, /citationScopeSummary/)
  assert.match(source, /selectedDocumentSummary/)
  assert.match(source, /选择知识库/)
  assert.match(source, /选择文档/)
  assert.match(source, /生成引用/)
})

test('citation page summarizes export output and guides empty states', async () => {
  const source = await readFile(path.join(sourceRoot, 'views/CitationView.vue'), 'utf8')

  assert.match(source, /class="[^"]*\bcitation-document-summary\b[^"]*"/)
  assert.match(source, /exportFormatLabel/)
  assert.match(source, /class="[^"]*\bcitation-empty-guide\b[^"]*"/)
  assert.match(source, /emptyDocumentGuide/)
  assert.match(source, /class="[^"]*\bcitation-output-summary\b[^"]*"/)
  assert.match(source, /citationHasOutput/)
  assert.match(source, /citationLineCount/)
  assert.match(source, /可复制结果/)
})
