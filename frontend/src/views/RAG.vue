<script setup>
import { ref, onMounted } from 'vue'
import { useRAGStore } from '@/stores/rag'
import * as ragApi from '@/api/rag'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Files, Delete, Plus, Refresh } from '@element-plus/icons-vue'

const ragStore = useRAGStore()

// 文件管理
const fileList = ref([])
const uploadLoading = ref(false)
const selectedFiles = ref([])

// 搜索和问答
const searchQuery = ref('')
const searchResults = ref([])
const questionQuery = ref('')
const questionAnswer = ref('')
const loading = ref({
  index: false,
  search: false,
  question: false,
  drop: false
})

// 配置操作
const handleToggleRAG = async () => {
  try {
    const res = await ragStore.toggleRAG()
    if (res.success) {
      ElMessage.success(res.message || `RAG 已${ragStore.enabled ? '开启' : '关闭'}`)
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

const handleTopKChange = async (value) => {
  try {
    const res = await ragStore.updateTopK(value)
    if (res.success) {
      ElMessage.success(`检索数量已设置为 ${value}`)
    } else {
      ElMessage.error(res.message || '设置失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '设置失败')
  }
}

// 文件上传钩子
const handleBeforeUpload = (file) => {
  const validTypes = ['.txt', '.md']
  const fileName = file.name.toLowerCase()
  const isValid = validTypes.some(type => fileName.endsWith(type))
  
  if (!isValid) {
    ElMessage.error('只支持 .txt 和 .md 文件')
    return false
  }
  
  const maxSize = 10 * 1024 * 1024 // 10MB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  
  return true
}

const handleUpload = async (rawFile) => {
  uploadLoading.value = true
  try {
    const res = await ragApi.uploadFile(rawFile)
    if (res.success) {
      ElMessage.success('文件上传并索引成功')
      await fetchFiles()
    } else {
      ElMessage.error(res.message || '上传失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '上传失败')
  } finally {
    uploadLoading.value = false
  }
  return false // 阻止默认上传
}

const fetchFiles = async () => {
  try {
    const res = await ragApi.listFiles()
    if (res.success) {
      fileList.value = res.files || []
    }
  } catch (error) {
    console.error('获取文件列表失败', error)
  }
}

const handleDeleteFile = async (file) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件 "${file.name}"吗？`, '确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const res = await ragApi.deleteFile(file.name)
    if (res.success) {
      ElMessage.success(res.message || '删除成功')
      await fetchFiles()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const handleIndex = async () => {
  try {
    loading.value.index = true
    const res = await ragApi.indexDocuments()
    if (res.success) {
      ElMessage.success(`文档索引成功: ${res.successCount}/${res.totalFiles} 个文件`)
    } else {
      ElMessage.error(res.message || '索引失败')
    }
  } catch (error) {
    ElMessage.error('索引失败')
  } finally {
    loading.value.index = false
  }
}

const handleSearch = async () => {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入搜索内容')
    return
  }
  try {
    loading.value.search = true
    const res = await ragApi.searchDocuments(searchQuery.value, ragStore.topK)
    if (res.success) {
      searchResults.value = res.results || []
    }
  } catch (error) {
    ElMessage.error('搜索失败')
  } finally {
    loading.value.search = false
  }
}

const handleQuestion = async () => {
  if (!questionQuery.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  try {
    loading.value.question = true
    questionAnswer.value = ''
    const res = await ragApi.ragQuestion(questionQuery.value, ragStore.topK)
    if (res.success) {
      questionAnswer.value = res.answer
    } else {
      ElMessage.error(res.message || '问答失败')
    }
  } catch (error) {
    ElMessage.error(error.message || '问答失败')
  } finally {
    loading.value.question = false
  }
}

const handleDropCollection = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要删除所有向量数据吗？此操作不可逆！',
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    loading.value.drop = true
    const res = await ragApi.dropCollection()
    if (res.success) {
      ElMessage.success('向量表删除成功')
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  } finally {
    loading.value.drop = false
  }
}

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const formatDate = (timestamp) => {
  return new Date(timestamp).toLocaleString()
}

onMounted(() => {
  fetchFiles()
})
</script>

<template>
  <div class="rag-page">
    <el-row :gutter="20">
      <!-- 左侧配置区 -->
      <el-col :span="24">
        <el-card class="config-card">
          <template #header>
            <div class="card-header">
              <span>RAG 配置</span>
            </div>
          </template>
          <div class="config-content">
            <div class="config-item">
              <span>RAG 总开关：</span>
              <el-switch
                :model-value="ragStore.enabled"
                :loading="ragStore.loading"
                @change="handleToggleRAG"
              />
              <el-tag :type="ragStore.enabled ? 'success' : 'info'">
                {{ ragStore.enabled ? '已开启' : '已关闭' }}
              </el-tag>
            </div>
            <div class="config-item">
              <span>检索数量 (topK)：</span>
              <el-input-number
                v-model="ragStore.topK"
                :min="1"
                :max="20"
                @change="handleTopKChange"
              />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 文件管理区 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card class="file-card">
          <template #header>
            <div class="card-header">
              <span>文档管理</span>
              <div class="header-actions">
                <el-button
                  type="primary"
                  :loading="loading.index"
                  @click="handleIndex"
                >
                  <el-icon><Refresh /></el-icon>
                  重新索引
                </el-button>
                <el-button
                  type="danger"
                  :loading="loading.drop"
                  @click="handleDropCollection"
                >
                  <el-icon><Delete /></el-icon>
                  删除向量
                </el-button>
              </div>
            </div>
          </template>

          <!-- 上传区域 -->
          <div class="upload-area">
            <el-upload
              drag
              action="#"
              :auto-upload="false"
              :before-upload="handleBeforeUpload"
              :on-change="(file) => handleUpload(file.raw)"
              :loading="uploadLoading"
              :limit="10"
              :file-list="selectedFiles"
            >
              <el-icon class="el-icon--upload"><upload /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  只支持 .txt 和 .md 文件，大小不超过 10MB
                </div>
              </template>
            </el-upload>
          </div>

          <!-- 文件列表 -->
          <div class="file-list-section">
            <h4>已上传的文件 ({{ fileList.length }})</h4>
            <el-table :data="fileList" style="width: 100%">
              <el-table-column prop="name" label="文件名">
                <template #default="{ row }">
                  <el-icon><Files /></el-icon>
                  {{ row.name }}
                </template>
              </el-table-column>
              <el-table-column prop="size" label="大小" width="120">
                <template #default="{ row }">
                  {{ formatFileSize(row.size) }}
                </template>
              </el-table-column>
              <el-table-column prop="lastModified" label="上传时间" width="200">
                <template #default="{ row }">
                  {{ formatDate(row.lastModified) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button
                    type="danger"
                    size="small"
                    link
                    @click="handleDeleteFile(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索和问答区 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card class="search-card">
          <template #header>
            <div class="card-header">
              <span>向量搜索测试</span>
            </div>
          </template>
          <div class="search-content">
            <el-input
              v-model="searchQuery"
              placeholder="输入搜索内容"
              clearable
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button
                  :loading="loading.search"
                  @click="handleSearch"
                >
                  搜索
                </el-button>
              </template>
            </el-input>
            <div v-if="searchResults.length > 0" class="search-results">
              <el-divider>搜索结果 ({{ searchResults.length }})</el-divider>
              <div
                v-for="(result, index) in searchResults"
                :key="index"
                class="result-item"
              >
                <div class="result-score">
                  <el-tag type="info">
                    相似度: {{ (result.score * 100).toFixed(2) }}%
                  </el-tag>
                </div>
                <div class="result-content">{{ result.content }}</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="qa-card">
          <template #header>
            <div class="card-header">
              <span>RAG 问答测试</span>
            </div>
          </template>
          <div class="qa-content">
            <el-input
              v-model="questionQuery"
              type="textarea"
              :rows="3"
              placeholder="输入你的问题"
            />
            <el-button
              type="primary"
              style="margin-top: 10px"
              :loading="loading.question"
              @click="handleQuestion"
            >
              提问
            </el-button>
            <div v-if="questionAnswer" class="answer">
              <el-divider>回答</el-divider>
              <div class="answer-content">{{ questionAnswer }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.rag-page {
  padding: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.config-content {
  display: flex;
  gap: 40px;
  align-items: center;
}

.config-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-area {
  margin-bottom: 20px;
}

.file-list-section h4 {
  margin: 20px 0 10px 0;
  color: #303133;
}

.search-content,
.qa-content {
  min-height: 300px;
}

.search-results {
  margin-top: 20px;
}

.result-item {
  margin-bottom: 16px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 8px;
}

.result-score {
  margin-bottom: 8px;
}

.result-content {
  line-height: 1.6;
  color: #606266;
}

.answer-content {
  padding: 16px;
  background-color: #f0f9eb;
  border-radius: 8px;
  line-height: 1.8;
}
</style>
