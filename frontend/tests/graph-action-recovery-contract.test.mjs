import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const sourceRoot = fileURLToPath(new URL('../src', import.meta.url))

async function readView(name) {
  return readFile(path.join(sourceRoot, 'views', name), 'utf8')
}

test('knowledge graph actions expose recoverable failure states', async () => {
  const source = await readView('KnowledgeGraphView.vue')

  assert.match(source, /graphActionError/)
  assert.match(source, /pathSearchError/)
  assert.match(source, /class="[^"]*\bkg-graph-action-status\b[^"]*"/)
  assert.match(source, /class="[^"]*\bpath-action-status\b[^"]*"/)
  assert.match(source, /@click="loadGraph"/)
  assert.match(source, /@click="handlePathSearch"/)
  assert.match(source, /async function loadGraph\(\)[\s\S]*catch \(e: any\)[\s\S]*graphActionError\.value = e\.response\?\.data\?\.message \|\| '加载知识图谱失败'/)
  assert.match(source, /async function handlePathSearch\(\)[\s\S]*catch \(e: any\)[\s\S]*pathSearchError\.value = e\.response\?\.data\?\.message \|\| '路径搜索失败'/)
})

test('academic graph and review actions expose recoverable failure states', async () => {
  const source = await readView('AcademicGraphView.vue')

  assert.match(source, /graphActionError/)
  assert.match(source, /reviewActionError/)
  assert.match(source, /class="[^"]*\bacademic-graph-action-status\b[^"]*"/)
  assert.match(source, /class="[^"]*\breview-action-status\b[^"]*"/)
  assert.match(source, /@click="loadGraph"/)
  assert.match(source, /@click="handleGenerateReview"/)
  assert.match(source, /async function loadGraph\(\)[\s\S]*catch \(e: any\)[\s\S]*graphActionError\.value = e\.response\?\.data\?\.message \|\| '加载引用图谱失败'/)
  assert.match(source, /async function handleGenerateReview\(\)[\s\S]*catch \(e: any\)[\s\S]*reviewActionError\.value = e\.response\?\.data\?\.message \|\| '文献综述生成失败'/)
})
