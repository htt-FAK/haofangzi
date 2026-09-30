<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { del, get, put } from '../api/http'

/**
 * 肇庆好房子 · 智能选房顾问（Kimi 风格简洁对话界面）
 * 设计理念：
 *  - 温暖、简洁、单栏居中布局，拒绝"AI工作站"既视感
 *  - 柔和圆角卡片、宽松留白、友好对话氛围
 *  - 居中欢迎页 + 建议卡片网格
 *  - 底部居中输入框，干净利落
 */

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

export interface Turn {
  id: string
  role: 'user' | 'assistant'
  content: string
  thinking?: string
  showThinking?: boolean
  source?: 'ai' | 'fallback'
  time: string
}

interface ChatSession {
  id: string
  title: string
  updatedAt: string
  messages: Turn[]
}

const q = ref('')
const sending = ref(false)
const messages = ref<Turn[]>([])
const completeness = ref(100)
const box = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLTextAreaElement | null>(null)
const showProfileDrawer = ref(false)
const copiedId = ref<string | null>(null)
let abortCtrl: AbortController | null = null

// 会话管理
const sessions = ref<ChatSession[]>([
  {
    id: 'default',
    title: '选房顾问咨询',
    updatedAt: '刚刚',
    messages: [],
  },
])
const currentSessionId = ref('default')

const profile = reactive({
  budgetMin: undefined as number | undefined,
  budgetMax: undefined as number | undefined,
  familyStructure: 'FAMILY_3',
  mustRooms: 3,
  preferOrientation: 'S',
})

const SCENARIO_CARDS = [
  {
    emoji: '🏠',
    title: '三代同堂选房',
    prompt: '三代同住，预算 160 万内，想要 3-4 房，重视通风与双卫，推荐哪套户型？',
  },
  {
    emoji: '☀️',
    title: '高采光通透户型',
    prompt: '端州区星湖附近，哪套户型窗地比和采光日照最好？',
  },
  {
    emoji: '📐',
    title: '高得房率实用型',
    prompt: '注重得房率和收纳空间，走道少、异形少的户型有哪些？',
  },
  {
    emoji: '💰',
    title: '刚需高性价比',
    prompt: '预算 100 万左右，推荐两房一卫、居住舒适度最高的户型',
  },
]

const currentSession = computed(() => {
  return sessions.value.find((s) => s.id === currentSessionId.value) || sessions.value[0]
})

function formatTime(date = new Date()) {
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

function renderMarkdown(content: string) {
  if (!content) return ''
  return md.render(content)
}

async function scrollDown() {
  await nextTick()
  if (box.value) {
    box.value.scrollTo({ top: box.value.scrollHeight, behavior: 'smooth' })
  }
}

async function saveProfile() {
  try {
    const me = await put<any>('/users/me/profile', { ...profile })
    completeness.value = Number(me.profileCompleteness ?? 100)
    const cached = JSON.parse(localStorage.getItem('hf-user') || '{}')
    cached.profileCompleteness = completeness.value
    localStorage.setItem('hf-user', JSON.stringify(cached))
    showProfileDrawer.value = false
    ElMessage.success('偏好已更新')
  } catch {
    ElMessage.error('保存失败')
  }
}

async function loadHistory() {
  try {
    const rows = await get<any[]>('/ai/chat')
    if (rows && rows.length > 0) {
      messages.value = rows.map((row, idx) => ({
        id: `hist_${idx}_${Date.now()}`,
        role: row.role === 'user' ? 'user' : 'assistant',
        content: String(row.content ?? ''),
        source: String(row.content ?? '').startsWith('模型暂不可用') ? 'fallback' : 'ai',
        time: formatTime(),
      }))
      currentSession.value.messages = [...messages.value]
      if (messages.value.length > 0) {
        const firstUser = messages.value.find((m) => m.role === 'user')
        if (firstUser) {
          currentSession.value.title = firstUser.content.slice(0, 16) + (firstUser.content.length > 16 ? '...' : '')
        }
      }
    }
  } catch {
    // 忽略加载错误
  }
  await scrollDown()
}

async function createNewChat() {
  if (sending.value) stopGeneration()
  try {
    await del('/ai/chat')
  } catch {}
  const newId = `session_${Date.now()}`
  const newSession: ChatSession = {
    id: newId,
    title: '新对话',
    updatedAt: '刚刚',
    messages: [],
  }
  sessions.value.unshift(newSession)
  currentSessionId.value = newId
  messages.value = []
  await nextTick()
  inputRef.value?.focus()
}

function stopGeneration() {
  if (abortCtrl) {
    abortCtrl.abort()
    abortCtrl = null
  }
  sending.value = false
}

function applyEvent(raw: string, turn: Turn) {
  const data = raw
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
    .join('\n')
  if (!data) return
  try {
    const ev = JSON.parse(data) as { type?: string; text?: string; source?: string }
    if (ev.type === 'token' && ev.text) {
      turn.content += ev.text
    } else if (ev.type === 'thinking' && ev.text) {
      turn.thinking = (turn.thinking || '') + ev.text
    } else if (ev.type === 'done') {
      turn.source = ev.source === 'fallback' ? 'fallback' : 'ai'
    }
  } catch {}
}

async function ask(preset?: string) {
  const question = (preset ?? q.value).trim()
  if (!question || sending.value) return
  q.value = ''

  const userTurn: Turn = {
    id: `u_${Date.now()}`,
    role: 'user',
    content: question,
    time: formatTime(),
  }
  messages.value.push(userTurn)
  currentSession.value.messages.push(userTurn)

  if (currentSession.value.messages.length <= 2) {
    currentSession.value.title = question.slice(0, 16) + (question.length > 16 ? '...' : '')
  }

  const aiTurn: Turn = {
    id: `ai_${Date.now()}`,
    role: 'assistant',
    content: '',
    thinking: '',
    showThinking: true,
    source: 'ai',
    time: formatTime(),
  }
  messages.value.push(aiTurn)
  currentSession.value.messages.push(aiTurn)
  sending.value = true
  await scrollDown()

  abortCtrl = new AbortController()

  try {
    const token = localStorage.getItem('hf-token') || ''
    const res = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ question }),
      signal: abortCtrl.signal,
    })

    const type = res.headers.get('content-type') || ''
    if (!res.ok || !type.includes('text/event-stream') || !res.body) {
      aiTurn.content = '网络繁忙，已转入离线分析模式。'
      aiTurn.source = 'fallback'
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let cut = buf.indexOf('\n\n')
      while (cut >= 0) {
        applyEvent(buf.slice(0, cut), aiTurn)
        buf = buf.slice(cut + 2)
        cut = buf.indexOf('\n\n')
        await scrollDown()
      }
    }
    if (buf.trim()) applyEvent(buf, aiTurn)

    if (!aiTurn.content) {
      aiTurn.content = '暂时没有找到匹配的结果，换个问法试试？'
      aiTurn.source = 'fallback'
    }
  } catch (err: any) {
    if (err.name !== 'AbortError') {
      if (!aiTurn.content) {
        aiTurn.content = '网络异常，请稍后重试。'
        aiTurn.source = 'fallback'
      }
    }
  } finally {
    sending.value = false
    abortCtrl = null
    currentSession.value.updatedAt = formatTime()
    await scrollDown()
  }
}

function copyMessage(turn: Turn) {
  navigator.clipboard.writeText(turn.content).then(() => {
    copiedId.value = turn.id
    setTimeout(() => {
      copiedId.value = null
    }, 2000)
  })
}

function exportChat() {
  if (!messages.value.length) return
  const text = messages.value
    .map((m) => `[${m.time}] ${m.role === 'user' ? '我' : '顾问'}:\n${m.content}\n`)
    .join('\n---\n\n')
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `选房咨询_${new Date().toISOString().slice(0, 10)}.md`
  a.click()
  URL.revokeObjectURL(url)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    ask()
  }
}

onMounted(async () => {
  try {
    const me = await get<any>('/users/me')
    completeness.value = Number(me.profileCompleteness ?? 100)
    profile.budgetMin = me.budgetMin == null ? undefined : Number(me.budgetMin)
    profile.budgetMax = me.budgetMax == null ? undefined : Number(me.budgetMax)
    profile.familyStructure = me.familyStructure || 'FAMILY_3'
    profile.mustRooms = me.mustRooms == null ? 3 : Number(me.mustRooms)
    profile.preferOrientation = me.preferOrientation || 'S'
    await loadHistory()
  } catch {
    // 忽略
  }
})
</script>

<template>
  <div class="kimi-page">
    <!-- 顶部极简操作栏 -->
    <header class="kimi-topbar">
      <div class="topbar-left">
        <button type="button" class="topbar-icon-btn" title="新对话" @click="createNewChat">
          <svg viewBox="0 0 20 20" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
            <path d="M10 4v12M4 10h12" />
          </svg>
        </button>
      </div>
      <div class="topbar-center">
        <span class="topbar-model-label">好房子 · 选房顾问</span>
        <span class="topbar-model-tag">Qwen3.5-4B · 深度思考</span>
      </div>
      <div class="topbar-right">
        <button v-if="messages.length" type="button" class="topbar-text-btn" @click="exportChat">导出</button>
        <button type="button" class="topbar-text-btn" @click="showProfileDrawer = true">偏好设置</button>
      </div>
    </header>

    <!-- 对话主体 -->
    <div ref="box" class="kimi-body">
      <!-- 空状态：居中欢迎 -->
      <div v-if="!messages.length" class="welcome-wrap">
        <div class="welcome-inner">
          <div class="welcome-avatar">
            <svg viewBox="0 0 40 40" width="40" height="40" fill="none">
              <rect width="40" height="40" rx="12" fill="#155e75" />
              <path d="M12 28V16l8-5 8 5v12l-8 5-8-5z" fill="none" stroke="#fff" stroke-width="1.5" stroke-linejoin="round"/>
              <circle cx="20" cy="20" r="3" fill="#fff" opacity="0.9"/>
            </svg>
          </div>
          <h1 class="welcome-hi">你好，我是你的选房顾问</h1>
          <p class="welcome-sub">由通义千问 Qwen3.5-4B 原生多模态模型与国家住宅规范联合驱动，支持 256K 深度对话</p>

          <div class="suggest-grid">
            <button
              v-for="(c, i) in SCENARIO_CARDS"
              :key="i"
              type="button"
              class="suggest-card"
              @click="ask(c.prompt)"
            >
              <span class="suggest-emoji">{{ c.emoji }}</span>
              <span class="suggest-text">{{ c.title }}</span>
              <svg class="suggest-arrow" viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
                <path d="M6 4l4 4-4 4" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- 对话消息流 -->
      <div v-else class="msg-list">
        <div
          v-for="m in messages"
          :key="m.id"
          class="msg-row"
          :class="m.role"
        >
          <!-- AI 头像 -->
          <div v-if="m.role === 'assistant'" class="msg-avatar">
            <svg viewBox="0 0 28 28" width="28" height="28" fill="none">
              <rect width="28" height="28" rx="8" fill="#155e75" />
              <path d="M8 20V12l6-4 6 4v8l-6 4-6-4z" fill="none" stroke="#fff" stroke-width="1.2" stroke-linejoin="round"/>
              <circle cx="14" cy="14" r="2" fill="#fff" opacity="0.85"/>
            </svg>
          </div>

          <div class="msg-content-wrap">
            <div
              class="msg-bubble"
              :class="m.role"
            >
              <!-- 用户消息 -->
              <div v-if="m.role === 'user'" class="user-text">{{ m.content }}</div>

              <!-- AI 消息 -->
              <template v-else>
                <!-- 思考过程 (Thinking Mode) 展开/收起卡片 -->
                <div v-if="m.thinking" class="thinking-card">
                  <button
                    type="button"
                    class="thinking-header"
                    @click="m.showThinking = (m.showThinking === undefined ? false : !m.showThinking)"
                  >
                    <span class="thinking-sparkle">✦</span>
                    <span class="thinking-title">
                      {{ sending && m === messages[messages.length - 1] && !m.content ? '正在思考中...' : '已深度思考' }}
                    </span>
                    <svg
                      class="thinking-arrow"
                      :class="{ open: m.showThinking !== false }"
                      viewBox="0 0 16 16"
                      width="12"
                      height="12"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    >
                      <path d="M4 6l4 4 4-4" />
                    </svg>
                  </button>
                  <div v-show="m.showThinking !== false" class="thinking-body">
                    {{ m.thinking }}
                  </div>
                </div>

                <div
                  v-if="m.content"
                  class="md-body"
                  v-html="renderMarkdown(m.content)"
                />
                <span
                  v-if="sending && m === messages[messages.length - 1] && m.content"
                  class="cursor-blink"
                >|</span>
                <div
                  v-if="sending && m === messages[messages.length - 1] && !m.content && !m.thinking"
                  class="thinking"
                >
                  <span class="dot-1">·</span>
                  <span class="dot-2">·</span>
                  <span class="dot-3">·</span>
                  <span class="thinking-text">正在分析户型数据</span>
                </div>
              </template>
            </div>

            <!-- AI 消息操作 -->
            <div v-if="m.role === 'assistant' && m.content && !(sending && m === messages[messages.length - 1])" class="msg-actions">
              <button type="button" class="action-btn" @click="copyMessage(m)">
                {{ copiedId === m.id ? '已复制 ✓' : '复制' }}
              </button>
              <button
                type="button"
                class="action-btn"
                :disabled="sending"
                @click="ask(messages[messages.indexOf(m) - 1]?.content)"
              >
                重新回答
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部输入区 -->
    <footer class="kimi-footer">
      <div class="input-wrap">
        <div class="input-box" :class="{ active: sending }">
          <textarea
            ref="inputRef"
            v-model="q"
            rows="1"
            class="input-area"
            placeholder="描述你的选房需求..."
            :disabled="sending"
            @keydown="onKeydown"
            @input="($event.target as HTMLTextAreaElement).style.height = 'auto'; ($event.target as HTMLTextAreaElement).style.height = ($event.target as HTMLTextAreaElement).scrollHeight + 'px'"
          />
          <div class="input-actions">
            <button
              v-if="sending"
              type="button"
              class="stop-btn"
              @click="stopGeneration"
            >
              <svg viewBox="0 0 16 16" width="14" height="14" fill="currentColor">
                <rect x="3" y="3" width="10" height="10" rx="2" />
              </svg>
            </button>
            <button
              v-else
              type="button"
              class="send-btn"
              :disabled="!q.trim()"
              @click="ask()"
            >
              <svg viewBox="0 0 20 20" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 10l5-5m0 0l5 5m-5-5v12" />
              </svg>
            </button>
          </div>
        </div>
        <p class="footer-hint">基于规则引擎与国家住宅规范数据，结果仅供参考</p>
      </div>
    </footer>

    <!-- 偏好设置抽屉 -->
    <el-drawer
      v-model="showProfileDrawer"
      title="选房偏好"
      direction="rtl"
      size="380px"
    >
      <div class="pref-body">
        <p class="pref-desc">顾问会根据你的偏好，从在售户型中匹配最合适的方案。</p>

        <div class="pref-item">
          <label>预算范围（元）</label>
          <div class="budget-row">
            <el-input-number
              v-model="profile.budgetMin"
              :min="0"
              :step="50000"
              placeholder="最低"
              style="width: 48%"
            />
            <span class="budget-sep">—</span>
            <el-input-number
              v-model="profile.budgetMax"
              :min="0"
              :step="50000"
              placeholder="最高"
              style="width: 48%"
            />
          </div>
        </div>

        <div class="pref-item">
          <label>家庭结构</label>
          <el-select v-model="profile.familyStructure" style="width: 100%">
            <el-option label="单身自住" value="SINGLE" />
            <el-option label="二人世界" value="COUPLE" />
            <el-option label="三口之家" value="FAMILY_3" />
            <el-option label="三代同堂" value="THREE_GEN" />
          </el-select>
        </div>

        <div class="pref-item">
          <label>最少居室</label>
          <el-input-number v-model="profile.mustRooms" :min="1" :max="6" style="width: 100%" />
        </div>

        <div class="pref-item">
          <label>朝向偏好</label>
          <el-select v-model="profile.preferOrientation" style="width: 100%">
            <el-option label="正南" value="S" />
            <el-option label="南北通透" value="NS" />
            <el-option label="东南" value="SE" />
            <el-option label="西南" value="SW" />
          </el-select>
        </div>

        <el-button type="primary" size="large" style="width: 100%; margin-top: 16px" @click="saveProfile">
          保存
        </el-button>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
/* ─── Kimi-style: 温暖、简洁、单栏居中 ─── */

.kimi-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 72px);
  background: #fafafa;
  overflow: hidden;
}

/* ─── 顶栏：极简一行 ─── */
.kimi-topbar {
  height: 52px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
  flex-shrink: 0;
  z-index: 10;
}

.topbar-left, .topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topbar-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
}

.topbar-model-label {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  letter-spacing: 0.01em;
}

.topbar-model-tag {
  font-size: 11px;
  font-weight: 500;
  color: #0891b2;
  background: #f0fdfa;
  border: 1px solid #ccfbf1;
  padding: 1px 8px;
  border-radius: 9999px;
  letter-spacing: 0.02em;
}

.topbar-icon-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  color: #666;
  cursor: pointer;
  transition: all 0.15s;
}
.topbar-icon-btn:hover {
  background: #f5f5f5;
  color: #333;
  border-color: #ddd;
}

.topbar-text-btn {
  padding: 6px 12px;
  background: transparent;
  border: none;
  font-size: 13px;
  color: #888;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s;
}
.topbar-text-btn:hover {
  background: #f5f5f5;
  color: #333;
}

/* ─── 对话主体：居中滚动 ─── */
.kimi-body {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 160px;
}

/* 滚动条美化 */
.kimi-body::-webkit-scrollbar {
  width: 5px;
}
.kimi-body::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 3px;
}
.kimi-body::-webkit-scrollbar-track {
  background: transparent;
}

/* ─── 欢迎页：居中温暖 ─── */
.welcome-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  padding: 40px 24px;
}

.welcome-inner {
  max-width: 560px;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.welcome-avatar {
  margin-bottom: 20px;
}

.welcome-hi {
  font-size: 26px;
  font-weight: 700;
  color: #1a1a1a;
  margin: 0 0 10px;
  letter-spacing: -0.02em;
  line-height: 1.3;
}

.welcome-sub {
  font-size: 15px;
  color: #999;
  margin: 0 0 36px;
  line-height: 1.6;
  max-width: 420px;
}

.suggest-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  width: 100%;
}

.suggest-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
}
.suggest-card:hover {
  border-color: #d0d0d0;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  transform: translateY(-1px);
}

.suggest-emoji {
  font-size: 20px;
  flex-shrink: 0;
}

.suggest-text {
  flex: 1;
  font-size: 14px;
  color: #444;
  font-weight: 500;
}

.suggest-arrow {
  color: #ccc;
  flex-shrink: 0;
  transition: transform 0.2s;
}
.suggest-card:hover .suggest-arrow {
  color: #999;
  transform: translateX(2px);
}

/* ─── 消息流 ─── */
.msg-list {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 24px 20px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.msg-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.msg-row.user {
  justify-content: flex-end;
}

.msg-row.assistant {
  justify-content: flex-start;
}

.msg-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.msg-content-wrap {
  max-width: 80%;
  display: flex;
  flex-direction: column;
}

.msg-row.user .msg-content-wrap {
  align-items: flex-end;
}

/* ─── 消息气泡 ─── */
.msg-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.msg-bubble.user {
  background: #155e75;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-bubble.assistant {
  background: #fff;
  color: #333;
  border: 1px solid #f0f0f0;
  border-bottom-left-radius: 4px;
}

.user-text {
  white-space: pre-wrap;
}

/* ─── Markdown 渲染 ─── */
.md-body {
  word-break: break-word;
}

.md-body :deep(p) {
  margin: 0 0 8px;
}
.md-body :deep(p:last-child) {
  margin-bottom: 0;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  color: #1a1a1a;
  font-weight: 600;
  margin: 14px 0 6px;
  line-height: 1.3;
}
.md-body :deep(h1) { font-size: 18px; }
.md-body :deep(h2) { font-size: 16px; }
.md-body :deep(h3) { font-size: 15px; }

.md-body :deep(strong) {
  color: #1a1a1a;
  font-weight: 600;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  padding-left: 18px;
  margin: 6px 0;
}

.md-body :deep(li) {
  margin: 3px 0;
}

.md-body :deep(blockquote) {
  border-left: 3px solid #155e75;
  background: #f8fafb;
  padding: 8px 14px;
  margin: 8px 0;
  border-radius: 0 8px 8px 0;
  color: #555;
}

.md-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
}

.md-body :deep(th),
.md-body :deep(td) {
  padding: 7px 10px;
  border: 1px solid #eee;
  text-align: left;
}

.md-body :deep(th) {
  background: #f9f9f9;
  font-weight: 600;
}

.md-body :deep(code) {
  background: #f5f5f5;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 13px;
  color: #c0392b;
}

/* ─── 流式光标 & 思考态 ─── */
.cursor-blink {
  display: inline;
  color: #155e75;
  font-weight: 700;
  animation: blink-kimi 0.9s infinite;
}

@keyframes blink-kimi {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ─── 思考过程 (Thinking Mode) 展开/折叠面板 ─── */
.thinking-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-bottom: 12px;
  overflow: hidden;
  transition: all 0.2s ease;
}

.thinking-header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 12px;
  color: #64748b;
  text-align: left;
  transition: background 0.15s;
}
.thinking-header:hover {
  background: #f1f5f9;
}

.thinking-sparkle {
  font-size: 11px;
  color: #0891b2;
}

.thinking-title {
  flex: 1;
  font-weight: 600;
  color: #475569;
}

.thinking-arrow {
  color: #94a3b8;
  transition: transform 0.2s ease;
}
.thinking-arrow.open {
  transform: rotate(180deg);
}

.thinking-body {
  padding: 10px 14px 12px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
  border-top: 1px solid #f1f5f9;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  max-height: 260px;
  overflow-y: auto;
  background: #ffffff;
}

.thinking {
  display: flex;
  align-items: center;
  gap: 3px;
  color: #aaa;
  font-size: 14px;
  padding: 4px 0;
}

.thinking span {
  display: inline-block;
}

.dot-1, .dot-2, .dot-3 {
  font-size: 22px;
  font-weight: 700;
  animation: dot-bounce 1.2s infinite;
}
.dot-2 { animation-delay: 0.2s; }
.dot-3 { animation-delay: 0.4s; }

@keyframes dot-bounce {
  0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-3px); }
}

.thinking-text {
  margin-left: 6px;
  font-size: 13px;
  color: #bbb;
}

/* ─── 消息操作 ─── */
.msg-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.15s;
}

.msg-row:hover .msg-actions {
  opacity: 1;
}

.action-btn {
  padding: 3px 10px;
  background: transparent;
  border: none;
  font-size: 12px;
  color: #bbb;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.15s;
}
.action-btn:hover:not(:disabled) {
  background: #f5f5f5;
  color: #666;
}
.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ─── 底部输入区 ─── */
.kimi-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 0 24px 20px;
  background: linear-gradient(to top, #fafafa 85%, transparent 100%);
  pointer-events: none;
}

.input-wrap {
  max-width: 720px;
  margin: 0 auto;
  pointer-events: auto;
}

.input-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 16px;
  padding: 10px 12px 10px 18px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  transition: all 0.2s;
}

.input-box:focus-within {
  border-color: #155e75;
  box-shadow: 0 4px 20px rgba(21, 94, 117, 0.1);
}

.input-area {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  line-height: 1.5;
  color: #333;
  resize: none;
  font-family: inherit;
  max-height: 120px;
  min-height: 22px;
}
.input-area::placeholder {
  color: #bbb;
}

.input-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.send-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #155e75;
  color: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.send-btn:hover:not(:disabled) {
  background: #0e7490;
}
.send-btn:disabled {
  background: #e0e0e0;
  color: #aaa;
  cursor: not-allowed;
}

.stop-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ef4444;
  color: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
}
.stop-btn:hover {
  background: #dc2626;
}

.footer-hint {
  text-align: center;
  font-size: 12px;
  color: #ccc;
  margin: 8px 0 0;
}

/* ─── 偏好抽屉 ─── */
.pref-body {
  padding: 0 4px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.pref-desc {
  font-size: 13px;
  color: #999;
  line-height: 1.5;
  margin: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.pref-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pref-item label {
  font-size: 13px;
  font-weight: 600;
  color: #444;
}

.budget-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.budget-sep {
  color: #ccc;
  font-weight: 500;
}

/* ─── 响应式 ─── */
@media (max-width: 640px) {
  .suggest-grid {
    grid-template-columns: 1fr;
  }
  .msg-list {
    padding: 16px 16px 20px;
  }
  .msg-content-wrap {
    max-width: 88%;
  }
  .welcome-hi {
    font-size: 22px;
  }
}
</style>
