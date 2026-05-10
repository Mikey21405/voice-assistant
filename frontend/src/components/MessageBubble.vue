<script setup>
defineProps({
  message: {
    type: Object,
    required: true
  },
  isStreaming: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <div :class="['message-bubble', message.role]">
    <div class="avatar">
      <el-icon v-if="message.role === 'user'" :size="32"><User /></el-icon>
      <el-icon v-else :size="32"><Service /></el-icon>
    </div>
    <div class="content">
      {{ message.content }}
      <span v-if="isStreaming && message.role === 'assistant'" class="typing-indicator">
        <span class="dot"></span>
        <span class="dot"></span>
        <span class="dot"></span>
      </span>
    </div>
  </div>
</template>

<style scoped>
.message-bubble {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  max-width: 80%;
  opacity: 0;
  transform: translateY(10px);
  animation: fadeInUp 0.3s forwards;
}

@keyframes fadeInUp {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message-bubble.user {
  flex-direction: row-reverse;
  align-self: flex-end;
}

.message-bubble.assistant {
  align-self: flex-start;
}

.avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.message-bubble.user .avatar {
  background-color: #67c23a;
}

.content {
  padding: 12px 16px;
  border-radius: 12px;
  background-color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  line-height: 1.6;
  word-break: break-word;
  min-height: 24px;
}

.message-bubble.user .content {
  background-color: #ecf5ff;
}

.typing-indicator {
  display: inline-block;
  margin-left: 8px;
  vertical-align: middle;
}

.typing-indicator .dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #909399;
  margin: 0 2px;
  animation: typingAnimation 1.4s infinite ease-in-out both;
}

.typing-indicator .dot:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-indicator .dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typingAnimation {
  0%, 80%, 100% {
    transform: scale(0.8);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
