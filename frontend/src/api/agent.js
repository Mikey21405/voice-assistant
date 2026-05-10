import request from '@/utils/request'

export function getTools() {
  return request({
    url: '/api/agent/tools',
    method: 'get'
  })
}

export function chat(prompt, systemPrompt) {
  return request({
    url: '/api/agent/chat',
    method: 'post',
    data: { prompt, systemPrompt }
  })
}
