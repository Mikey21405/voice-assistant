import { createRouter, createWebHistory } from 'vue-router'
import RAG from '@/views/RAG.vue'
import VoiceChat from '@/views/VoiceChat.vue'
import History from '@/views/History.vue'
import Assistants from '@/views/Assistants.vue'
import Agent from '@/views/Agent.vue'

const routes = [
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'VoiceChat',
    component: VoiceChat
  },
  {
    path: '/assistants',
    name: 'Assistants',
    component: Assistants
  },
  {
    path: '/rag',
    name: 'RAG',
    component: RAG
  },
  {
    path: '/agent',
    name: 'Agent',
    component: Agent
  },
  {
    path: '/history',
    name: 'History',
    component: History
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
