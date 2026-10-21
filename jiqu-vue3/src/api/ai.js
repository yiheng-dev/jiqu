import request from '@/utils/request'

// 潮流 AI 助手问答（RAG + Function Calling）
export const aiChat = (message) => request.post('/ai/chat', { message })
