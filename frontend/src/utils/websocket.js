import { ref } from 'vue'

export function useWebSocket(url) {
  const ws = ref(null)
  const isConnected = ref(false)
  const message = ref(null)
  const error = ref(null)

  function connect() {
    ws.value = new WebSocket(url)

    ws.value.onopen = () => {
      isConnected.value = true
      console.log('WebSocket连接成功')
    }

    ws.value.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        // 使用一个临时变量来确保响应式更新
        const newMessage = { ...data, _timestamp: Date.now() }
        message.value = newMessage
      } catch (e) {
        message.value = event.data
      }
    }

    ws.value.onerror = (err) => {
      error.value = err
      console.error('WebSocket错误:', err)
    }

    ws.value.onclose = () => {
      isConnected.value = false
      console.log('WebSocket连接关闭')
    }
  }

  function send(data) {
    if (ws.value && isConnected.value) {
      ws.value.send(JSON.stringify(data))
    }
  }

  function disconnect() {
    ws.value?.close()
  }

  return {
    isConnected,
    message,
    error,
    connect,
    send,
    disconnect
  }
}
