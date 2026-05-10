<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRAGStore } from './stores/rag'
import { useAssistantStore } from './stores/assistant'
import { ChatDotRound, Microphone, Document, Clock, User, Cpu } from '@element-plus/icons-vue'

const router = useRouter()
const ragStore = useRAGStore()
const assistantStore = useAssistantStore()

onMounted(async () => {
  await ragStore.fetchConfig()
  await assistantStore.fetchAssistants()
})
</script>

<template>
  <div id="app">
    <el-container class="layout-container">
      <el-header class="header">
        <div class="header-left">
          <el-icon :size="24"><ChatDotRound /></el-icon>
          <span class="title">智能语音助手</span>
        </div>
        <el-menu
          :default-active="router.currentRoute.value.path"
          mode="horizontal"
          :ellipsis="false"
          router
          class="header-menu"
        >
          <el-menu-item index="/chat">
            <el-icon><Microphone /></el-icon>
            <span>语音聊天</span>
          </el-menu-item>
          <el-menu-item index="/assistants">
            <el-icon><User /></el-icon>
            <span>助手管理</span>
          </el-menu-item>
          <el-menu-item index="/rag">
            <el-icon><Document /></el-icon>
            <span>RAG管理</span>
          </el-menu-item>
          <el-menu-item index="/agent">
            <el-icon><Cpu /></el-icon>
            <span>Agent</span>
          </el-menu-item>
          <el-menu-item index="/history">
            <el-icon><Clock /></el-icon>
            <span>对话历史</span>
          </el-menu-item>
        </el-menu>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<style scoped>
.layout-container {
  height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #409eff;
  color: white;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title {
  font-size: 20px;
  font-weight: bold;
}

.header-menu {
  background-color: transparent;
  border: none;
  width: 400px;
}

.header-menu .el-menu-item {
  color: white;
}

.header-menu .el-menu-item:hover,
.header-menu .el-menu-item.is-active {
  background-color: rgba(255, 255, 255, 0.2);
}

.main {
  padding: 20px;
  background-color: #f5f7fa;
}
</style>
