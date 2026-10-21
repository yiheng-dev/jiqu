<script setup>
import { ref, nextTick } from 'vue'
import { MagicStick, Promotion } from '@element-plus/icons-vue'
import { aiChat } from '@/api/ai'

const messages = ref([
  {
    role: 'ai',
    content:
      '你好，我是集趣潮流 AI 助手。你可以用自然语言问我关于潮流店铺、限量发售的资讯，例如：「帮我找带有潮流玩具的店铺」。'
  }
])

const input = ref('')
const sending = ref(false)

const suggestions = [
  '帮我找带有潮流玩具的店铺',
  '本周有哪些限量发售？',
  '推荐几家球鞋潮店',
  '搜一下千元以内的潮玩手办'
]

const send = async (text) => {
  const content = (text ?? input.value).trim()
  if (!content || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', content })
  sending.value = true
  await nextTick()
  scrollToBottom()

  try {
    const res = await aiChat(content)
    messages.value.push({ role: 'ai', content: res?.data || '抱歉，暂时没有获取到答案，请稍后再试。' })
  } catch (error) {
    console.error('AI 问答失败:', error)
    messages.value.push({
      role: 'ai',
      content: 'AI 服务暂时不可用，请稍后再试，或换个问题问问我～'
    })
  } finally {
    sending.value = false
    nextTick(scrollToBottom)
  }
}

const scrollToBottom = () => {
  const el = document.getElementById('chat-body')
  if (el) el.scrollTop = el.scrollHeight
}
</script>

<template>
  <div class="ai-page">
    <div class="ai-header">
      <div class="ai-title-row">
        <span class="ai-icon"><el-icon><MagicStick /></el-icon></span>
        <div>
          <h1 class="ai-title">潮流 AI 助手</h1>
          <p class="ai-desc">自然语言 · 潮流资讯 · 智能选品</p>
        </div>
      </div>
    </div>

    <div class="chat-body" id="chat-body">
      <div
        class="msg-row"
        :class="m.role === 'user' ? 'user' : 'ai'"
        v-for="(m, i) in messages"
        :key="i"
      >
        <div class="msg-avatar" v-if="m.role === 'ai'">
          <el-icon><MagicStick /></el-icon>
        </div>
        <div class="msg-avatar user" v-else>
          <span>我</span>
        </div>
        <div class="msg-bubble">{{ m.content }}</div>
      </div>
      <div class="msg-row ai" v-if="sending">
        <div class="msg-avatar"><el-icon><MagicStick /></el-icon></div>
        <div class="msg-bubble typing">正在思考…</div>
      </div>
    </div>

    <!-- 快捷提问 -->
    <div class="suggest" v-if="messages.length <= 2">
      <div class="suggest-title">试试这样问</div>
      <div class="suggest-list">
        <div
          class="suggest-chip"
          v-for="(s, i) in suggestions"
          :key="i"
          @click="send(s)"
        >
          {{ s }}
        </div>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="chat-input">
      <input
        v-model="input"
        class="chat-input-field"
        placeholder="输入你想了解的潮流资讯…"
        @keyup.enter="send()"
      />
      <button class="chat-send" @click="send()">
        <el-icon><Promotion /></el-icon>
      </button>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 24px 0 48px;
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--navbar-height) - 80px);
}
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.ai-title-row {
  display: flex;
  align-items: center;
  gap: 14px;
}
.ai-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: var(--accent-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: var(--shadow-glow);
}
.ai-title {
  font-size: 22px;
  font-weight: var(--font-black);
  color: var(--text-primary);
  margin: 0;
}
.ai-desc {
  font-size: 12px;
  color: var(--text-muted);
  margin: 2px 0 0;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}
.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 18px;
}
.msg-row.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-gradient);
  color: #fff;
  font-size: 17px;
}
.msg-avatar.user {
  background: var(--card-hover);
  font-size: 12px;
  border: 1px solid var(--border);
}
.msg-bubble {
  max-width: 72%;
  padding: 12px 16px;
  border-radius: var(--radius-lg);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg-row.ai .msg-bubble {
  background: var(--card);
  color: var(--text-primary);
  border: 1px solid var(--border);
  border-top-left-radius: 4px;
}
.msg-row.user .msg-bubble {
  background: var(--accent-gradient);
  color: #fff;
  border-top-right-radius: 4px;
}
.msg-bubble.typing {
  color: var(--text-muted);
}

.suggest {
  padding: 16px 4px;
}
.suggest-title {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 12px;
}
.suggest-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.suggest-chip {
  font-size: 13px;
  color: var(--accent-light);
  background: rgba(255, 77, 0, 0.08);
  border: 1px solid rgba(255, 77, 0, 0.25);
  border-radius: var(--radius-full);
  padding: 8px 18px;
  cursor: pointer;
  transition: all 0.2s;
}
.suggest-chip:hover {
  background: var(--accent-gradient);
  color: #fff;
  border-color: transparent;
}

.chat-input {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 16px;
}
.chat-input-field {
  flex: 1;
  height: 48px;
  border: 1px solid var(--border);
  outline: none;
  background: var(--card);
  border-radius: var(--radius-full);
  padding: 0 22px;
  font-size: 15px;
  color: var(--text-primary);
  transition: border-color 0.2s;
}
.chat-input-field:focus {
  border-color: var(--accent);
}
.chat-input-field::placeholder {
  color: var(--text-muted);
}
.chat-send {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: none;
  background: var(--accent-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  cursor: pointer;
  box-shadow: var(--shadow-glow);
  transition: transform 0.2s;
}
.chat-send:hover {
  transform: scale(1.06);
}

@media (max-width: 768px) {
  .ai-page {
    height: calc(100vh - var(--navbar-height) - 40px);
    padding: 12px 0 24px;
  }
}
</style>
