import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as assistantApi from '@/api/assistant'

export const useAssistantStore = defineStore('assistant', () => {
  const assistants = ref([])
  const currentAssistant = ref(null)
  const loading = ref(false)

  async function fetchAssistants() {
    try {
      loading.value = true
      const res = await assistantApi.getAssistants()
      if (res.success && res.data) {
        assistants.value = res.data
        if (res.data.length > 0 && !currentAssistant.value) {
          currentAssistant.value = res.data[0]
        }
      }
    } catch (error) {
      console.error('获取助手列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  function setCurrentAssistant(assistant) {
    currentAssistant.value = assistant
  }

  async function createAssistant(assistant) {
    const res = await assistantApi.createAssistant(assistant)
    if (res.success) {
      await fetchAssistants()
    }
    return res
  }

  async function updateAssistant(assistant) {
    const res = await assistantApi.updateAssistant(assistant)
    if (res.success) {
      await fetchAssistants()
      // 如果当前选中的助手是被更新的，也要更新
      if (currentAssistant.value && currentAssistant.value.id === assistant.id) {
        currentAssistant.value = res.data
      }
    }
    return res
  }

  async function deleteAssistant(id) {
    const res = await assistantApi.deleteAssistant(id)
    if (res.success) {
      await fetchAssistants()
      // 如果删除的是当前选中的助手，重新选择第一个
      if (currentAssistant.value && currentAssistant.value.id === id) {
        if (assistants.value.length > 0) {
          currentAssistant.value = assistants.value[0]
        } else {
          currentAssistant.value = null
        }
      }
    }
    return res
  }

  return {
    assistants,
    currentAssistant,
    loading,
    fetchAssistants,
    setCurrentAssistant,
    createAssistant,
    updateAssistant,
    deleteAssistant
  }
})
