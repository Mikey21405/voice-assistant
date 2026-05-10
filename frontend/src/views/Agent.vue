<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, User, Service, Loading } from '@element-plus/icons-vue'
import * as agentApi from '@/api/agent'

const tools = ref([])
const loadingTools = ref(false)
const sending = ref(false)
const prompt = ref('')
const systemPrompt = ref('')

const messages = ref([])

onMounted(() => {
  fetchTools()
})

async function fetchTools() {
  loadingTools.value = true
  try {
    const res = await agentApi.getTools()
    if (res.success) {
      tools.value = res.tools || []
    }
  } catch (e) {
    ElMessage.error('获取工具列表失败')
  } finally {
    loadingTools.value = false
  }
}

async function handleSend() {
  const text = prompt.value.trim()
  if (!text) return

  messages.value.push({ role: 'user', content: text })
  prompt.value = ''

  messages.value.push({ role: 'assistant', content: '', loading: true })

  sending.value = true
  try {
    const res = await agentApi.chat(text, systemPrompt.value || null)
    const last = messages.value[messages.value.length - 1]
    if (res.success) {
      last.content = res.response || '(空响应)'
      last.costMs = res.costMs
    } else {
      last.content = '请求失败: ' + (res.message || '未知错误')
    }
  } catch (e) {
    const last = messages.value[messages.value.length - 1]
    last.content = '网络错误: ' + (e.message || '请检查后端服务')
  } finally {
    const last = messages.value[messages.value.length - 1]
    last.loading = false
    sending.value = false
  }
}

function handleKeyup(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}
</script>

<template>
  <div class="agent-page">
    <el-row :gutter="20" style="height: 100%">
      <el-col :span="8">
        <el-card class="tool-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon :size="18"><Service /></el-icon>
              <span>已注册工具</span>
              <el-tag v-if="tools.length > 0" type="success" size="small">{{ tools.length }} 个</el-tag>
              <el-tag v-else type="info" size="small">无</el-tag>
            </div>
          </template>

          <div v-loading="loadingTools" class="tool-list">
            <el-empty v-if="!loadingTools && tools.length === 0" description="暂无注册工具" />

            <div v-for="tool in tools" :key="tool.name" class="tool-item">
              <div class="tool-name">
                <el-tag type="primary" size="small">{{ tool.name }}</el-tag>
              </div>
              <div class="tool-desc">{{ tool.description }}</div>
              <div v-if="tool.parameters && tool.parameters.properties" class="tool-params">
                <div class="param-label">参数：</div>
                <div v-for="(prop, key) in tool.parameters.properties" :key="key" class="param-item">
                  <el-tag size="small" type="warning">{{ key }}</el-tag>
                  <span class="param-type">{{ prop.type }}</span>
                  <span class="param-desc">{{ prop.description }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card class="chat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon :size="18"><User /></el-icon>
              <span>Agent 对话测试</span>
              <el-tag v-if="messages.length > 0" size="small" type="info">{{ messages.length }} 条消息</el-tag>
            </div>
          </template>

          <div class="system-prompt-area">
            <el-input
              v-model="systemPrompt"
              type="textarea"
              :rows="2"
              placeholder="系统提示词（可选）"
              :disabled="sending"
            />
          </div>

          <div class="chat-area">
            <div v-if="messages.length === 0" class="empty-chat">
              <el-empty description="在下方输入消息测试 Agent 功能" />
            </div>

            <div v-for="(msg, idx) in messages" :key="idx" class="msg-row" :class="msg.role">
              <div class="msg-role">
                <el-tag v-if="msg.role === 'user'" size="small" type="primary">用户</el-tag>
                <el-tag v-else size="small" type="success">Agent</el-tag>
                <span v-if="msg.costMs" class="msg-cost">{{ msg.costMs }}ms</span>
              </div>
              <div class="msg-content" :class="{ loading: msg.loading }">
                <template v-if="msg.loading">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>Agent 思考中...</span>
                </template>
                <template v-else>{{ msg.content }}</template>
              </div>
            </div>
          </div>

          <div class="input-area">
            <el-input
              v-model="prompt"
              type="textarea"
              :rows="2"
              placeholder="输入问题测试 Agent..."
              :disabled="sending"
              @keyup="handleKeyup"
            />
            <el-button
              type="primary"
              :loading="sending"
              :disabled="!prompt.trim()"
              @click="handleSend"
            >
              <el-icon><Promotion /></el-icon>
              发送
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.agent-page {
  height: calc(100vh - 140px);
  padding: 10px 0;
}

.tool-card,
.chat-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.tool-card :deep(.el-card__body),
.chat-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.tool-list {
  flex: 1;
  overflow-y: auto;
}

.tool-item {
  padding: 12px;
  border-bottom: 1px solid #ebeef5;
  border-radius: 4px;
  margin-bottom: 8px;
  background: #fafafa;
}

.tool-item:last-child {
  margin-bottom: 0;
}

.tool-name {
  margin-bottom: 6px;
}

.tool-desc {
  color: #606266;
  font-size: 13px;
  margin-bottom: 8px;
}

.tool-params {
  font-size: 12px;
}

.param-label {
  color: #909399;
  margin-bottom: 4px;
}

.param-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.param-type {
  color: #409eff;
  font-size: 11px;
}

.param-desc {
  color: #909399;
}

.system-prompt-area {
  margin-bottom: 12px;
}

.chat-area {
  flex: 1;
  overflow-y: auto;
  margin-bottom: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  background: #fafafa;
  min-height: 200px;
}

.empty-chat {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  min-height: 200px;
}

.msg-row {
  margin-bottom: 16px;
}

.msg-row:last-child {
  margin-bottom: 0;
}

.msg-role {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.msg-cost {
  font-size: 11px;
  color: #909399;
}

.msg-content {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-row.user .msg-content {
  background: #ecf5ff;
  color: #303133;
}

.msg-row.assistant .msg-content {
  background: #f0f9eb;
  color: #303133;
}

.msg-content.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-style: italic;
}

.input-area {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.input-area :deep(.el-textarea) {
  flex: 1;
}

.input-area .el-button {
  height: 60px;
}
</style>
