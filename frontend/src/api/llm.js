import request from '@/utils/request'

export function chat(messages) {
  return request({
    url: '/api/llm/chat',
    method: 'post',
    data: { messages }
  })
}
