<script setup>
import { ref, watch, nextTick, toRefs } from 'vue'
import { useChatStore } from '@/stores/chat'
import MessageBubble from './MessageBubble.vue'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  }
})

// ✅ 解构保持响应式
const { messages } = toRefs(props)

const chatStore = useChatStore()
const emit = defineEmits(['send-message'])

const inputValue = ref('')
const chatContainer = ref(null)

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

// ✅ 只监听长度变化（更高效）
watch(() => messages.value.length, () => {
  scrollToBottom()
})

// 发送消息
function handleSend() {
  if (inputValue.value.trim()) {
    emit('send-message', inputValue.value)
    inputValue.value = ''
  }
}
</script>

<template>
  <div class="chat-box">
    <div ref="chatContainer" class="chat-container">
      <div v-if="messages.length === 0" class="empty-tip">
        <el-empty description="开始对话吧" />
      </div>

      <MessageBubble
        v-for="(msg, index) in messages"
        :key="msg.id || index"
        :message="msg"
        :is-streaming="chatStore.isStreaming && index === messages.length - 1"
      />
    </div>

    <div class="input-area">
      <el-input
        v-model="inputValue"
        type="textarea"
        :rows="2"
        placeholder="输入消息..."
        :disabled="chatStore.isStreaming"
        @keyup.enter="handleSend"
      />
      <el-button
        type="primary"
        @click="handleSend"
        :disabled="chatStore.isStreaming"
      >
        <el-icon><Promotion /></el-icon>
        发送
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.chat-box {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: white;
  border-radius: 8px;
  overflow: hidden;
}

.chat-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
}

.empty-tip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.input-area {
  padding: 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-area .el-textarea {
  flex: 1;
}
</style>