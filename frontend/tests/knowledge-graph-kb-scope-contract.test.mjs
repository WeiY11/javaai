import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'

const graphApi = new URL('../src/api/knowledge-graph.ts', import.meta.url)
const graphView = new URL('../src/views/KnowledgeGraphView.vue', import.meta.url)

test('neighbor queries keep the selected knowledge-base scope', async () => {
  const [apiSource, viewSource] = await Promise.all([
    readFile(graphApi, 'utf8'),
    readFile(graphView, 'utf8')
  ])

  assert.match(apiSource, /getNeighbors\(kbId: number, entityId: number\)/)
  assert.doesNotMatch(apiSource, /knowledge-bases\/0\/graph\/entities/)
  assert.match(viewSource, /const knowledgeBaseId = selectedKbId\.value/)
  assert.match(viewSource, /kgApi\.getNeighbors\(knowledgeBaseId, entityId\)/)
})
