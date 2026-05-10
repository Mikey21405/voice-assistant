<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAssistantStore } from '@/stores/assistant'
import { useRAGStore } from '@/stores/rag'
import ChatBox from '@/components/ChatBox.vue'
import VoiceRecorder from '@/components/VoiceRecorder.vue'
import { useWebSocket } from '@/utils/websocket'
import { ElMessage, ElMessageBox } from 'element-plus'

const chatStore = useChatStore()
const assistantStore = useAssistantStore()
const ragStore = useRAGStore()

const { isConnected, message, connect, send, disconnect } = useWebSocket(`ws://${location.host}/ws/frontend`)

// 状态管理
const isPaired = ref(false)
const isConnecting = ref(false)

// 监听当前助手切换
watch(() => assistantStore.currentAssistant, (newAssistant) => {
  if (newAssistant && newAssistant.id) {
    chatStore.switchAssistant(newAssistant.id)
    // 切换助手时只切换对话历史，保持PBX连接
  }
}, { immediate: true })

watch(isConnected, (val) => {
  chatStore.setConnected(val)
  if (!val) {
    isPaired.value = false
  }
})

watch(message, (msg) => {
  if (!msg || !msg.command) return

  // 处理握手成功
  if (msg.command === 'ANSWER') {
    isPaired.value = true
    ElMessage.success('与PBX握手成功！可以开始对话了')
  }
  // 处理流式回答 token
  else if (msg.command === 'ANSWER_STREAM') {
    chatStore.setStreaming(true)
    chatStore.appendToLastMessage(msg.payload?.text || '')
  } 
  // 处理流式回答完成
  else if (msg.command === 'ANSWER_STREAM_DONE') {
    chatStore.setStreaming(false)
    console.log('LLM回答完成')
  }
  // 处理错误
  else if (msg.command === 'ERROR') {
    chatStore.setStreaming(false)
    ElMessage.error(msg.payload?.message || '发生错误')
  }
}, { deep: true })

onMounted(() => {
  connect()
})

onUnmounted(() => {
  disconnect()
})

// 与PBX握手
async function handleInvite() {
  if (!isConnected.value) {
    ElMessage.error('请先连接WebSocket')
    return
  }
  
  if (!assistantStore.currentAssistant) {
    ElMessage.error('请先选择助手')
    return
  }
  
  isConnecting.value = true
  try {
    const inviteMsg = {
      command: 'INVITE',
      requestId: Date.now().toString(),
      assistantId: assistantStore.currentAssistant.id,
      payload: {}
    }
    send(inviteMsg)
    ElMessage.info('正在与PBX握手...')
  } catch (error) {
    ElMessage.error('握手失败')
    isConnecting.value = false
  }
}

function handleStartRecording() {
  chatStore.setRecording(true)
  // 这里可以添加开始录音的逻辑
}

function handleStopRecording() {
  chatStore.setRecording(false)
  // 这里可以添加停止录音的逻辑
}

function handleSendMessage(content) {
  if (!isConnected.value) {
    ElMessage.error('WebSocket未连接')
    return
  }
  
  if (!isPaired.value) {
    ElMessageBox.confirm(
      '尚未与PBX完成握手，是否现在握手？',
      '提示',
      {
        confirmButtonText: '握手',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(async () => {
      await handleInvite()
    }).catch(() => {})
    return
  }
  
  if (!assistantStore.currentAssistant) {
    ElMessage.error('请先选择助手')
    return
  }
  
  const messageId = Date.now()
  chatStore.addMessage({
    id: messageId,
    role: 'user',
    content: content
  })
  chatStore.addMessage({
    id: messageId + 1,
    role: 'assistant',
    content: ''
  })

  // 发送 ASR_FINAL 命令给后端
  const asrMsg = {
    command: 'ASR_FINAL',
    requestId: Date.now().toString(),
    assistantId: assistantStore.currentAssistant.id,
    payload: {
      text: content,
      callId: `call_${Date.now()}`
    }
  }
  send(asrMsg)
}
</script>

<template>
  <div class="voice-chat-page">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="sidebar-card">
          <template #header>
            <div class="card-header">
              <span>助手选择</span>
            </div>
          </template>
          <div class="assistant-list">
            <div
              v-for="assistant in assistantStore.assistants"
              :key="assistant.id"
              :class="['assistant-item', { active: assistantStore.currentAssistant?.id === assistant.id }]"
              @click="assistantStore.setCurrentAssistant(assistant)"
            >
              <el-icon><Service /></el-icon>
              <span>{{ assistant.name }}</span>
            </div>
          </div>
          <el-divider />
          <div class="status-info">
            <div class="status-item">
              <span>WebSocket：</span>
              <el-tag :type="isConnected ? 'success' : 'danger'">
                {{ isConnected ? '已连接' : '未连接' }}
              </el-tag>
            </div>
            <div class="status-item">
              <span>PBX握手：</span>
              <el-tag :type="isPaired ? 'success' : 'warning'">
                {{ isPaired ? '已握手' : '未握手' }}
              </el-tag>
            </div>
            <div class="status-item">
              <span>RAG状态：</span>
              <el-tag :type="ragStore.enabled ? 'success' : 'info'">
                {{ ragStore.enabled ? '已开启' : '已关闭' }}
              </el-tag>
            </div>
            <el-button
              type="primary"
              :loading="isConnecting"
              :disabled="!isConnected || isPaired"
              @click="handleInvite"
              style="width: 100%; margin-top: 10px"
            >
              {{ isPaired ? '已握手' : '与PBX握手' }}
            </el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="18">
        <el-card class="chat-card">
          <template #header>
            <div class="card-header">
              <span>{{ assistantStore.currentAssistant?.name || '语音助手' }}</span>
              <div class="header-status">
                <el-tag :type="isPaired ? 'success' : 'info'" size="small">
                  {{ isPaired ? '✓ 可以对话' : '请先握手' }}
                </el-tag>
              </div>
            </div>
          </template>
          <div class="chat-content">
            <ChatBox
              :messages="chatStore.messages"
              @send-message="handleSendMessage"
            />
            <div class="voice-control">
              <VoiceRecorder
                :disabled="!isConnected || !isPaired"
                @start="handleStartRecording"
                @stop="handleStopRecording"
              />
              <div v-if="chatStore.isRecording" class="recording-tip">
                <el-icon class="recording-icon"><Microphone /></el-icon>
                <span>正在录音...</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.voice-chat-page {
  height: calc(100vh - 100px);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sidebar-card {
  height: 100%;
}

.assistant-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.assistant-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.assistant-item:hover {
  background-color: #f5f7fa;
}

.assistant-item.active {
  background-color: #ecf5ff;
  color: #409eff;
}

.status-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-card {
  height: 100%;
}

.chat-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.voice-control {
  padding: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
}

.recording-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #f56c6c;
}

.recording-icon {
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>
