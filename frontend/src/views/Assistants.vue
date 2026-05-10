<script setup>
import { ref, onMounted } from 'vue'
import { useAssistantStore } from '@/stores/assistant'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Star } from '@element-plus/icons-vue'

const assistantStore = useAssistantStore()

// 对话框状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentAssistant = ref({
  name: '',
  systemPrompt: '',
  description: ''
})

// 表单引用
const formRef = ref(null)

// 表单验证
const rules = {
  name: [
    { required: true, message: '请输入助手名称', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  systemPrompt: [
    { required: true, message: '请输入系统提示词', trigger: 'blur' },
    { min: 5, message: '最少 5 个字符', trigger: 'blur' }
  ]
}

const handleOpenCreate = () => {
  isEdit.value = false
  currentAssistant.value = {
    name: '',
    systemPrompt: '',
    description: ''
  }
  dialogVisible.value = true
}

const handleOpenEdit = (assistant) => {
  isEdit.value = true
  currentAssistant.value = { ...assistant }
  dialogVisible.value = true
}

const handleDelete = async (assistant) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除助手"${assistant.name}"吗？`,
      '确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const res = await assistantStore.deleteAssistant(assistant.id)
    if (res.success) {
      ElMessage.success(res.message || '删除成功')
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const handleSelect = (assistant) => {
  assistantStore.setCurrentAssistant(assistant)
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        let res
        if (isEdit.value) {
          res = await assistantStore.updateAssistant(currentAssistant.value)
        } else {
          res = await assistantStore.createAssistant(currentAssistant.value)
        }
        
        if (res.success) {
          ElMessage.success(res.message || (isEdit.value ? '更新成功' : '创建成功'))
          dialogVisible.value = false
        } else {
          ElMessage.error(res.message || '操作失败')
        }
      } catch (error) {
        ElMessage.error(error.message || '操作失败')
      }
    }
  })
}

const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  dialogVisible.value = false
}

onMounted(() => {
  assistantStore.fetchAssistants()
})
</script>

<template>
  <div class="assistants-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>语音助手管理</span>
          <el-button type="primary" @click="handleOpenCreate">
            <el-icon><Plus /></el-icon>
            创建助手
          </el-button>
        </div>
      </template>

      <div class="assistants-grid">
        <el-card
          v-for="assistant in assistantStore.assistants"
          :key="assistant.id"
          :class="{ 'selected': assistant.id === assistantStore.currentAssistant?.id }"
          class="assistant-card"
          shadow="hover"
        >
          <div class="assistant-avatar">
            <el-avatar :size="60">
              {{ assistant.name.charAt(0).toUpperCase() }}
            </el-avatar>
            <el-icon
              v-if="assistant.id === assistantStore.currentAssistant?.id"
              class="star-icon"
            >
              <Star />
            </el-icon>
          </div>
          <div class="assistant-info">
            <h3>{{ assistant.name }}</h3>
            <p v-if="assistant.description" class="description">
              {{ assistant.description }}
            </p>
            <div class="system-prompt-preview">
              <span class="label">系统提示词：</span>
              <span class="content">
                {{ assistant.systemPrompt.length > 100
                  ? assistant.systemPrompt.slice(0, 100) + '...'
                  : assistant.systemPrompt }}
              </span>
            </div>
          </div>
          <div class="assistant-actions">
            <el-button
              type="primary"
              size="small"
              @click="handleSelect(assistant)"
              :disabled="assistant.id === assistantStore.currentAssistant?.id"
            >
              {{ assistant.id === assistantStore.currentAssistant?.id ? '已选择' : '选择' }}
            </el-button>
            <el-button size="small" @click="handleOpenEdit(assistant)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(assistant)"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </el-card>
      </div>

      <el-empty v-if="assistantStore.assistants.length === 0" description="暂无助手">
        <el-button type="primary" @click="handleOpenCreate">
          创建第一个助手
        </el-button>
      </el-empty>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑助手' : '创建助手'"
      width="600px"
      :close-on-click-modal="false"
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="currentAssistant"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="助手名称" prop="name">
          <el-input
            v-model="currentAssistant.name"
            placeholder="请输入助手名称"
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="currentAssistant.description"
            type="textarea"
            :rows="2"
            placeholder="请输入描述（可选）"
          />
        </el-form-item>
        <el-form-item label="系统提示词" prop="systemPrompt">
          <el-input
            v-model="currentAssistant.systemPrompt"
            type="textarea"
            :rows="8"
            placeholder="请输入系统提示词，定义助手的角色和行为..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetForm">取消</el-button>
        <el-button type="primary" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.assistants-page {
  padding: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.assistants-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.assistant-card {
  position: relative;
  cursor: pointer;
  transition: all 0.3s;
}

.assistant-card:hover {
  transform: translateY(-4px);
}

.assistant-card.selected {
  border: 2px solid #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.2);
}

.assistant-avatar {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
  position: relative;
}

.star-icon {
  position: absolute;
  top: -8px;
  right: 0;
  color: #f7ba2a;
  font-size: 24px;
}

.assistant-info {
  text-align: center;
}

.assistant-info h3 {
  margin: 0 0 8px 0;
  color: #303133;
}

.description {
  color: #909399;
  font-size: 14px;
  margin-bottom: 12px;
}

.system-prompt-preview {
  text-align: left;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
}

.system-prompt-preview .label {
  color: #606266;
  font-weight: 500;
}

.system-prompt-preview .content {
  color: #909399;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.assistant-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 16px;
}
</style>
