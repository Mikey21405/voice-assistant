import request from '@/utils/request'

export function getAssistants() {
  return request({
    url: '/api/assistant',
    method: 'get'
  })
}

export function getAssistantById(id) {
  return request({
    url: `/api/assistant/${id}`,
    method: 'get'
  })
}

export function createAssistant(assistant) {
  return request({
    url: '/api/assistant',
    method: 'post',
    data: assistant
  })
}

export function updateAssistant(assistant) {
  return request({
    url: '/api/assistant',
    method: 'put',
    data: assistant
  })
}

export function deleteAssistant(id) {
  return request({
    url: `/api/assistant/${id}`,
    method: 'delete'
  })
}

