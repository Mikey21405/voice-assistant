import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'

export const useChatStore = defineStore('chat', () => {
  // 按助手ID分组存储对话历史
  const assistantMessagesMap = reactive({})
  // 当前选择的助手ID
  const currentAssistantId = ref(null)
  // 当前显示的对话历史
  const messages = ref([])
  const isRecording = ref(false)
  const isConnected = ref(false)
  const isStreaming = ref(false)

  // 切换助手时加载对应对话历史
  function switchAssistant(assistantId) {
    currentAssistantId.value = assistantId
    // 如果该助手没有历史记录，初始化为空数组
    if (!assistantMessagesMap[assistantId]) {
      assistantMessagesMap[assistantId] = []
    }
    // 加载该助手的对话历史
    messages.value = assistantMessagesMap[assistantId]
  }

  function addMessage(message) {
    const msgWithId = {
      id: message.id || Date.now() + Math.random(),
      ...message
    }
    // 因为 messages.value 已经指向 assistantMessagesMap[currentAssistantId.value]
    // 所以只需要更新一次即可
    if (currentAssistantId.value && assistantMessagesMap[currentAssistantId.value]) {
      assistantMessagesMap[currentAssistantId.value].push(msgWithId)
    }
  }

  function clearMessages() {
    if (currentAssistantId.value) {
      assistantMessagesMap[currentAssistantId.value] = []
    }
  }

  function updateLastMessage(content) {
    if (currentAssistantId.value && assistantMessagesMap[currentAssistantId.value]) {
      const index = assistantMessagesMap[currentAssistantId.value].length - 1
      if (index >= 0) {
        const lastMessage = assistantMessagesMap[currentAssistantId.value][index]
        assistantMessagesMap[currentAssistantId.value][index] = {
          ...lastMessage,
          content: content
        }
      }
    }
  }

  function appendToLastMessage(token) {
    if (currentAssistantId.value && assistantMessagesMap[currentAssistantId.value]) {
      const index = assistantMessagesMap[currentAssistantId.value].length - 1
      if (index >= 0) {
        const lastMessage = assistantMessagesMap[currentAssistantId.value][index]
        assistantMessagesMap[currentAssistantId.value][index] = {
          ...lastMessage,
          content: lastMessage.content + token
        }
      }
    }
  }

  function setRecording(status) {
    isRecording.value = status
  }

  function setConnected(status) {
    isConnected.value = status
  }

  function setStreaming(status) {
    isStreaming.value = status
  }

  return {
    messages,
    isRecording,
    isConnected,
    isStreaming,
    currentAssistantId,
    addMessage,
    clearMessages,
    updateLastMessage,
    appendToLastMessage,
    setRecording,
    setConnected,
    setStreaming,
    switchAssistant
  }
})
