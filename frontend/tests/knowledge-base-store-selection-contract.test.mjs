import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const knowledgeBaseStore = new URL('../src/stores/knowledge-base.store.ts', import.meta.url)

test('knowledge-base store reconciles selected knowledge base after list refresh', async () => {
  const source = await readFile(knowledgeBaseStore, 'utf8')

  assert.match(source, /function reconcileCurrentKbSelection\(\)/)
  assert.match(source, /const selectedKbId = currentKb\.value\?\.id/)
  assert.match(source, /const refreshedKb = knowledgeBases\.value\.find\(kb => kb\.id === selectedKbId\)/)
  assert.match(source, /currentKb\.value = refreshedKb \?\? null/)
  assert.match(source, /knowledgeBases\.value = res\.records[\s\S]*reconcileCurrentKbSelection\(\)/)
})

test('knowledge-base store keeps total count aligned with local create and delete mutations', async () => {
  const source = await readFile(knowledgeBaseStore, 'utf8')

  assert.match(source, /knowledgeBases\.value\.unshift\(kb\)[\s\S]*total\.value \+= 1/)
  assert.match(source, /knowledgeBases\.value = knowledgeBases\.value\.filter\(kb => kb\.id !== id\)[\s\S]*total\.value = Math\.max\(0, total\.value - 1\)/)
})
