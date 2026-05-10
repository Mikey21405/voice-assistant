<script setup>
import { ref } from 'vue'

defineProps({
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['start', 'stop', 'result'])

const isRecording = ref(false)
const mediaRecorder = ref(null)
const audioChunks = ref([])

async function startRecording() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRecorder.value = new MediaRecorder(stream)
    audioChunks.value = []

    mediaRecorder.value.ondataavailable = (e) => {
      audioChunks.value.push(e.data)
    }

    mediaRecorder.value.onstop = () => {
      const audioBlob = new Blob(audioChunks.value, { type: 'audio/webm' })
      emit('stop', audioBlob)
    }

    mediaRecorder.value.start()
    isRecording.value = true
    emit('start')
  } catch (error) {
    console.error('录音失败:', error)
  }
}

function stopRecording() {
  if (mediaRecorder.value && isRecording.value) {
    mediaRecorder.value.stop()
    mediaRecorder.value.stream.getTracks().forEach(track => track.stop())
    isRecording.value = false
  }
}
</script>

<template>
  <el-button
    :type="isRecording ? 'danger' : 'primary'"
    :disabled="disabled"
    @mousedown="startRecording"
    @mouseup="stopRecording"
    @mouseleave="stopRecording"
    @touchstart="startRecording"
    @touchend="stopRecording"
    size="large"
  >
    <el-icon v-if="!isRecording"><Microphone /></el-icon>
    <el-icon v-else><VideoPause /></el-icon>
    <span>{{ isRecording ? '松开结束' : '按住说话' }}</span>
  </el-button>
</template>

<style scoped>
</style>
