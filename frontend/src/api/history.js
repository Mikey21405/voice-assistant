import request from '@/utils/request'

export function getHistoryByAssistantId(assistantId) {
  return request({
    url: `/api/history/assistant/${assistantId}`,
    method: 'get'
  })
}

export function getHistoryBySessionId(sessionId) {
  return request({
    url: `/api/history/session/${sessionId}`,
    method: 'get'
  })
}

export function getRecentHistory(assistantId, limit = 10) {
  return request({
    url: `/api/history/recent/${assistantId}`,
    method: 'get',
    params: { limit }
  })
}
