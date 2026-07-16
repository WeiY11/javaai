import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { KnowledgeBase } from '../types/knowledge-base.types'
import * as kbApi from '../api/knowledge-base'

export const useKnowledgeBaseStore = defineStore('knowledgeBase', () => {
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const currentKb = ref<KnowledgeBase | null>(null)
  const total = ref(0)

  function reconcileCurrentKbSelection() {
    const selectedKbId = currentKb.value?.id
    if (selectedKbId === undefined) return

    const refreshedKb = knowledgeBases.value.find(kb => kb.id === selectedKbId)
    currentKb.value = refreshedKb ?? null
  }

  async function loadKnowledgeBases() {
    const res = await kbApi.listKnowledgeBases()
    knowledgeBases.value = res.records
    total.value = res.total
    reconcileCurrentKbSelection()
  }

  async function createKb(data: Partial<KnowledgeBase>) {
    const kb = await kbApi.createKnowledgeBase(data)
    knowledgeBases.value.unshift(kb)
    total.value += 1
    return kb
  }

  function selectKb(kb: KnowledgeBase) {
    currentKb.value = kb
  }

  async function deleteKb(id: number) {
    await kbApi.deleteKnowledgeBase(id)
    knowledgeBases.value = knowledgeBases.value.filter(kb => kb.id !== id)
    total.value = Math.max(0, total.value - 1)
    if (currentKb.value?.id === id) currentKb.value = null
  }

  return { knowledgeBases, currentKb, total, loadKnowledgeBases, createKb, selectKb, deleteKb }
})
