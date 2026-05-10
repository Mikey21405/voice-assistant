<script setup>
import { ref, onMounted, computed } from 'vue'
import * as historyApi from '@/api/history'
import { useAssistantStore } from '@/stores/assistant'
import { ElMessage } from 'element-plus'

const assistantStore = useAssistantStore()
const historyList = ref([])
const selectedSession = ref(null)
const loading = ref(false)

// 按 sessionId 分组历史记录
const groupedHistory = computed(() => {
  const groups = {}
  historyList.value.forEach(item => {
    if (!groups[item.sessionId]) {
      groups[item.sessionId] = {
        sessionId: item.sessionId,
        messages: [],
        createTime: item.createTime
      }
    }
    groups[item.sessionId].messages.push({
      role: 'user',
      content: item.userMessage
    })
    groups[item.sessionId].messages.push({
      role: 'assistant',
      content: item.assistantResponse
    })
  })
  return Object.values(groups).sort((a, b) => 
    new Date(b.createTime) - new Date(a.createTime)
  )
})

async function fetchHistoryList() {
  if (!assistantStore.currentAssistant?.id) return
  
  try {
    loading.value = true
    const res = await historyApi.getRecentHistory(
      assistantStore.currentAssistant.id,
      50
    )
    historyList.value = res
  } catch (error) {
    ElMessage.error('获取历史记录失败')
  } finally {
    loading.value = false
  }
}

function selectHistory(session) {
  selectedSession.value = session
}

onMounted(() => {
  fetchHistoryList()
})
</script>

<template>
  <div class="history-page">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="list-card">
          <template #header>
            <div class="card-header">
              <span>对话历史</span>
              <el-button size="small" @click="fetchHistoryList">刷新</el-button>
            </div>
          </template>
          <div class="history-list">
            <div
              v-for="session in groupedHistory"
              :key="session.sessionId"
              :class="['history-item', { active: selectedSession?.sessionId === session.sessionId }]"
              @click="selectHistory(session)"
            >
              <div class="history-title">
                {{ session.messages[0]?.content?.substring(0, 20) || '无标题' }}{{ session.messages[0]?.content?.length > 20 ? '...' : '' }}
              </div>
              <div class="history-time">{{ new Date(session.createTime).toLocaleString() }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="18">
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <span>历史详情</span>
            </div>
          </template>
          <div v-if="selectedSession" class="detail-content">
            <div
              v-for="(msg, index) in selectedSession.messages"
              :key="index"
              :class="['message-item', msg.role]"
            >
              <div class="message-avatar">
                <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                <el-icon v-else><Service /></el-icon>
              </div>
              <div class="message-content">{{ msg.content }}</div>
            </div>
          </div>
          <el-empty v-else description="请选择一个历史记录" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.history-page {
  height: calc(100vh - 100px);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.list-card,
.detail-card {
  height: 100%;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 100%;
  overflow-y: auto;
}

.history-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.history-item:hover {
  background-color: #f5f7fa;
}

.history-item.active {
  background-color: #ecf5ff;
}

.history-title {
  font-weight: bold;
  margin-bottom: 4px;
  color: #303133;
}

.history-time {
  font-size: 12px;
  color: #909399;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-height: 100%;
  overflow-y: auto;
}

.message-item {
  display: flex;
  gap: 12px;
  max-width: 80%;
}

.message-item.user {
  flex-direction: row-reverse;
  align-self: flex-end;
}

.message-avatar {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.message-item.user .message-avatar {
  background-color: #67c23a;
}

.message-content {
  padding: 12px 16px;
  border-radius: 12px;
  background-color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  line-height: 1.6;
}

.message-item.user .message-content {
  background-color: #ecf5ff;
}
</style>
