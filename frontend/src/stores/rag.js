import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as ragApi from '@/api/rag'

export const useRAGStore = defineStore('rag', () => {
  const enabled = ref(false)
  const topK = ref(5)
  const loading = ref(false)

  async function fetchConfig() {
    try {
      const res = await ragApi.getConfig()
      if (res.success) {
        enabled.value = res.enabled
        topK.value = res.topK
      }
    } catch (error) {
      console.error('获取RAG配置失败:', error)
    }
  }

  async function toggleRAG() {
    try {
      loading.value = true
      const res = await ragApi.toggleRAG()
      if (res.success) {
        enabled.value = res.enabled
      }
      return res
    } catch (error) {
      console.error('切换RAG失败:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  async function updateTopK(value) {
    try {
      topK.value = value
      const res = await ragApi.setTopK(value)
      return res
    } catch (error) {
      console.error('更新topK失败:', error)
      throw error
    }
  }

  return {
    enabled,
    topK,
    loading,
    fetchConfig,
    toggleRAG,
    updateTopK
  }
})
