import request from '@/utils/request'

export function getConfig() {
  return request({
    url: '/api/rag/config',
    method: 'get'
  })
}

export function toggleRAG() {
  return request({
    url: '/api/rag/toggle',
    method: 'post'
  })
}

export function enableRAG() {
  return request({
    url: '/api/rag/enable',
    method: 'post'
  })
}

export function disableRAG() {
  return request({
    url: '/api/rag/disable',
    method: 'post'
  })
}

export function indexDocuments() {
  return request({
    url: '/api/rag/index',
    method: 'post'
  })
}

export function searchDocuments(query, topK = 5) {
  return request({
    url: '/api/rag/search',
    method: 'post',
    params: { query, topK }
  })
}

export function ragQuestion(query, topK = 5) {
  return request({
    url: '/api/rag/qa',
    method: 'post',
    params: { query, topK }
  })
}

export function setTopK(topK) {
  return request({
    url: '/api/rag/topK',
    method: 'post',
    params: { topK }
  })
}

export function dropCollection() {
  return request({
    url: '/api/rag/collection/biz',
    method: 'delete'
  })
}

// 文件上传相关 API
export function uploadFile(file, onUploadProgress) {
  const formData = new FormData()
  formData.append('file', file)
  
  return request({
    url: '/api/rag/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress
  })
}

export function listFiles() {
  return request({
    url: '/api/rag/files',
    method: 'get'
  })
}

export function deleteFile(fileName) {
  return request({
    url: `/api/rag/files/${fileName}`,
    method: 'delete'
  })
}

