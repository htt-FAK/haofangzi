<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import { del, get, put } from '../api/http'
import AppIcon from '../components/AppIcon.vue'

/**
 * 肇庆好房子 · 智能选房顾问（参考 GitHub 顶级 AI 对话应用重构）
 * 特性：
 *  - 现代化双栏布局（左侧会话历史/画像看板 + 右侧沉浸式对话大厅）
 *  - 完整真实 Markdown 渲染（标题、加粗、列表、表格、引用块）
 *  - 通义千问 Qwen-2.5 真实在线流式打字效果与打断控制（AbortController）
 *  - 场景化卡片式推荐提问（一键发起）
 *  - 消息一键复制、重新生成、会话导出
 *  - 买家置业画像侧拉抽屉无感编辑
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
    icon: '👨‍👩‍👧‍👦',
    title: '三代同堂置业选房',
    desc: '预算 160 万内，想要 3-4 房，重视通风与双卫',
    prompt: '三代同住，预算 160 万内，想要 3-4 房，重视通风与双卫，推荐哪套户型？',
  },
  {
    icon: '☀️',
    title: '星湖高采光通透户型',
    desc: '端州区星湖附近，窗地比和采光日照最好的户型',
    prompt: '端州区星湖附近，哪套户型窗地比和采光日照最好？',
  },
  {
    icon: '📐',
    title: '高得房率与实用空间',
    desc: '注重得房率和收纳空间，走道少、异形少的户型有哪些？',
    prompt: '注重得房率和收纳空间，走道少、异形少的户型有哪些？',
  },
  {
    icon: '💰',
    title: '刚需首套高性价比',
    desc: '预算 100 万左右，推荐两房一卫、居住舒适度最高的户型',
    prompt: '预算 100 万左右，推荐两房一卫、居住舒适度最高的户型',
  },
]

const QUICK_CHIPS = [
  '星湖澜庭 89㎡ 户型优缺点分析',
  '西江云上临江大户型采光如何？',
  '端州区均价 9500 左右高分房源',
  '哪些户型实现了全明厨卫？',
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
    ElMessage.success('置业偏好画像已同步更新')
  } catch {
    ElMessage.error('保存画像失败')
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
    title: '新选房咨询',
    updatedAt: '刚刚',
    messages: [],
  }
  sessions.value.unshift(newSession)
  currentSessionId.value = newId
  messages.value = []
  ElMessage.success('已开启新一轮智能选房对话')
  await nextTick()
  inputRef.value?.focus()
}

function switchSession(sess: ChatSession) {
  if (sending.value) stopGeneration()
  currentSessionId.value = sess.id
  messages.value = [...sess.messages]
  scrollDown()
}

function deleteSession(e: Event, id: string) {
  e.stopPropagation()
  if (sessions.value.length <= 1) {
    createNewChat()
    return
  }
  sessions.value = sessions.value.filter((s) => s.id !== id)
  if (currentSessionId.value === id) {
    currentSessionId.value = sessions.value[0].id
    messages.value = [...sessions.value[0].messages]
  }
}

async function resetCurrentChat() {
  if (sending.value) stopGeneration()
  try {
    await del('/ai/chat')
  } catch {}
  messages.value = []
  currentSession.value.messages = []
  currentSession.value.title = '新选房咨询'
  ElMessage.success('已清空当前对话记录')
}

function stopGeneration() {
  if (abortCtrl) {
    abortCtrl.abort()
    abortCtrl = null
  }
  sending.value = false
  ElMessage.info('已停止生成')
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
      aiTurn.content = '顾问服务网络繁忙，已转入离线规则引擎为您分析。'
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
      aiTurn.content = '抱歉，暂时没有匹配到该户型的评分依据，请稍后换个方式咨询。'
      aiTurn.source = 'fallback'
    }
  } catch (err: any) {
    if (err.name !== 'AbortError') {
      if (!aiTurn.content) {
        aiTurn.content = '网络通信异常，请检查后端模型服务状态。'
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
    ElMessage.success('已复制到剪贴板')
    setTimeout(() => {
      copiedId.value = null
    }, 2000)
  })
}

function exportChat() {
  if (!messages.value.length) {
    ElMessage.warning('当前无对话内容可导出')
    return
  }
  const text = messages.value
    .map((m) => `[${m.time}] ${m.role === 'user' ? '购房者' : 'AI顾问'}:\n${m.content}\n`)
    .join('\n---\n\n')
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `肇庆好房子_选房顾问咨询记录_${new Date().toISOString().slice(0, 10)}.md`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('对话记录已导出为 Markdown 文件')
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
  <div class="ai-studio-container">
    <!-- 左侧会话侧栏 -->
    <aside class="ai-sidebar">
      <div class="sidebar-header">
        <div class="sidebar-brand">
          <div class="brand-avatar">✦</div>
          <div>
            <div class="brand-title">好房子 · 智囊</div>
            <div class="brand-tag">通义千问 Qwen-2.5</div>
          </div>
        </div>
        <button type="button" class="new-chat-btn" @click="createNewChat">
          <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M8 3v10M3 8h10" />
          </svg>
          开启新对话
        </button>
      </div>

      <!-- 会话历史列表 -->
      <div class="sessions-scroll">
        <div class="sessions-sec-title">近期选房会话</div>
        <div class="session-list">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === currentSessionId }"
            @click="switchSession(s)"
          >
            <div class="session-icon">💬</div>
            <div class="session-meta">
              <span class="session-title">{{ s.title }}</span>
              <span class="session-time">{{ s.updatedAt }}</span>
            </div>
            <button
              type="button"
              class="del-session-btn"
              title="删除此会话"
              @click="deleteSession($event, s.id)"
            >
              ×
            </button>
          </div>
        </div>
      </div>

      <!-- 底部置业偏好看板卡片 -->
      <div class="profile-dock-card">
        <div class="profile-dock-header">
          <span class="dock-badge">👤 买家置业画像</span>
          <button type="button" class="dock-edit-btn" @click="showProfileDrawer = true">
            修改
          </button>
        </div>
        <div class="profile-dock-content">
          <div class="dock-stat">
            <span class="label">预算区间</span>
            <span class="val hf-num">
              {{ profile.budgetMin ? (profile.budgetMin / 10000).toFixed(0) : '0' }} -
              {{ profile.budgetMax ? (profile.budgetMax / 10000).toFixed(0) : '不限' }} 万
            </span>
          </div>
          <div class="dock-stat">
            <span class="label">居室/朝向</span>
            <span class="val">{{ profile.mustRooms }}房 · {{ profile.preferOrientation }}向</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右侧沉浸式主对话区 -->
    <main class="ai-main-chat">
      <!-- 顶部玻璃拟态 Header -->
      <header class="chat-topbar">
        <div class="topbar-left">
          <div class="online-indicator">
            <span class="pulse-dot"></span>
          </div>
          <div class="topbar-info">
            <h2 class="topbar-title">智能置业顾问 · 专业版</h2>
            <p class="topbar-subtitle">已接入真实国家《住宅项目规范》与外置 JSON DSL 规则引擎</p>
          </div>
        </div>

        <div class="topbar-actions">
          <button
            type="button"
            class="top-btn"
            title="清空当前消息"
            :disabled="sending || !messages.length"
            @click="resetCurrentChat"
          >
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M3 5h10M6 5V3h4v2M5 5v8a1 1 0 0 0 1 1h4a1 1 0 0 0 1-1V5" />
            </svg>
            清空
          </button>
          <button
            type="button"
            class="top-btn"
            title="导出对话记录"
            :disabled="!messages.length"
            @click="exportChat"
          >
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8">
              <path d="M8 2v9M4 8l4 4 4-4M2 13h12" />
            </svg>
            导出
          </button>
          <button
            type="button"
            class="top-btn accent-btn"
            @click="showProfileDrawer = true"
          >
            ⚙️ 画像偏好
          </button>
        </div>
      </header>

      <!-- 消息流容器 -->
      <div ref="box" class="messages-container">
        <!-- 空状态欢迎界面（GitHub 风格卡片网格） -->
        <div v-if="!messages.length" class="welcome-container">
          <div class="welcome-hero">
            <div class="welcome-spark">✦</div>
            <h1 class="welcome-title">您好，我是您的星湖好房子专属选房顾问</h1>
            <p class="welcome-desc">
              为您深度解析户型采光日照、动线布局、得房率与多方案横向对比。请点击下方常见诉求，或直接在底部输入咨询：
            </p>
          </div>

          <div class="scenarios-grid">
            <div
              v-for="(c, i) in SCENARIO_CARDS"
              :key="i"
              class="scenario-card"
              @click="ask(c.prompt)"
            >
              <div class="sc-icon">{{ c.icon }}</div>
              <div class="sc-content">
                <h4 class="sc-title">{{ c.title }}</h4>
                <p class="sc-desc">{{ c.desc }}</p>
              </div>
              <div class="sc-arrow">→</div>
            </div>
          </div>
        </div>

        <!-- 真实对话消息流 -->
        <div v-else class="message-stream">
          <div
            v-for="m in messages"
            :key="m.id"
            class="message-row"
            :class="m.role === 'user' ? 'row-user' : 'row-assistant'"
          >
            <!-- 顾问头像 -->
            <div v-if="m.role === 'assistant'" class="avatar-box ai-avatar-box">
              <span class="avatar-icon">🏛️</span>
            </div>

            <div class="bubble-envelope">
              <!-- 顾问消息头部 meta -->
              <div v-if="m.role === 'assistant'" class="msg-header-meta">
                <span class="role-name">置业专家 AI</span>
                <span
                  class="source-badge"
                  :class="m.source === 'fallback' ? 'badge-fallback' : 'badge-real-ai'"
                >
                  <span class="badge-dot"></span>
                  {{ m.source === 'fallback' ? '规则引擎离线模板' : '通义千问 Qwen-2.5 流式在线' }}
                </span>
                <span class="msg-time">{{ m.time }}</span>
              </div>

              <!-- 消息气泡正文 -->
              <div
                class="bubble-card"
                :class="m.role === 'user' ? 'bubble-user' : 'bubble-assistant'"
              >
                <!-- 用户提问（纯文本带换行） -->
                <div v-if="m.role === 'user'" class="user-text">
                  {{ m.content }}
                </div>

                <!-- AI 回答（Markdown 富文本渲染） -->
                <div v-else class="assistant-markdown-body">
                  <div
                    v-if="m.content"
                    class="markdown-content"
                    v-html="renderMarkdown(m.content)"
                  />
                  <!-- 流式打字中光标 -->
                  <span
                    v-if="sending && m === messages[messages.length - 1]"
                    class="typewriter-cursor"
                  >▍</span>
                  <div
                    v-if="sending && m === messages[messages.length - 1] && !m.content"
                    class="ai-thinking-state"
                  >
                    <span class="spinner-dot"></span>
                    <span>正在检索户型几何参数并调取规则引擎分析中…</span>
                  </div>
                </div>

                <!-- 操作栏（仅针对 AI 消息且有内容时） -->
                <div v-if="m.role === 'assistant' && m.content" class="msg-tools-row">
                  <button
                    type="button"
                    class="tool-btn"
                    title="复制回复"
                    @click="copyMessage(m)"
                  >
                    <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8">
                      <rect x="5" y="5" width="8" height="8" rx="1.5" />
                      <path d="M3 11V3h8" />
                    </svg>
                    <span>{{ copiedId === m.id ? '已复制' : '复制' }}</span>
                  </button>
                  <button
                    type="button"
                    class="tool-btn"
                    title="重新生成"
                    :disabled="sending"
                    @click="ask(messages[messages.indexOf(m) - 1]?.content)"
                  >
                    <svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8">
                      <path d="M2 8a6 6 0 1 1 1.76 4.24M2 8V4m0 4h4" />
                    </svg>
                    <span>重新回答</span>
                  </button>
                </div>
              </div>

              <!-- 用户端的时间戳 -->
              <div v-if="m.role === 'user'" class="user-meta-row">
                <span class="msg-time">{{ m.time }}</span>
              </div>
            </div>

            <!-- 用户头像 -->
            <div v-if="m.role === 'user'" class="avatar-box user-avatar-box">
              <AppIcon name="user" :size="16" />
            </div>
          </div>
        </div>
      </div>

      <!-- 底部悬浮输入 Dock -->
      <footer class="composer-dock-container">
        <div class="composer-dock-inner">
          <!-- 快捷提问药丸滑动栏 -->
          <div class="chips-bar">
            <span class="chips-label">💡 猜您想问：</span>
            <div class="chips-scroll">
              <button
                v-for="(chip, i) in QUICK_CHIPS"
                :key="i"
                type="button"
                class="chip-pill"
                :disabled="sending"
                @click="ask(chip)"
              >
                {{ chip }}
              </button>
            </div>
          </div>

          <!-- 输入框卡片容器 -->
          <div class="input-card" :class="{ focused: sending }">
            <textarea
              ref="inputRef"
              v-model="q"
              rows="2"
              class="dock-textarea"
              placeholder="输入您关心的选房需求，如：预算、家庭人口、朝向或想了解的户型... (Enter 发送，Shift+Enter 换行)"
              :disabled="sending"
              @keydown="onKeydown"
            />

            <!-- 底部工具控制行 -->
            <div class="input-controls-row">
              <div class="control-left">
                <span class="model-badge">
                  <span class="model-spark">✨</span>
                  <span>Qwen-2.5 在线</span>
                </span>
                <span class="hint-text">按 Enter 发送 / Shift+Enter 换行</span>
              </div>

              <div class="control-right">
                <!-- 停止生成按钮 -->
                <button
                  v-if="sending"
                  type="button"
                  class="stop-btn"
                  @click="stopGeneration"
                >
                  <span class="stop-icon">■</span>
                  <span>停止生成</span>
                </button>

                <!-- 发送按钮 -->
                <button
                  v-else
                  type="button"
                  class="send-btn"
                  :disabled="!q.trim()"
                  @click="ask()"
                >
                  <span>发送咨询</span>
                  <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2L7 9m7-7l-5 12-2-5-5-2 12-5z" />
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <div class="compliance-footer">
            <span>🛡️ 规则引擎与国家《住宅项目规范》联合驱动 · 数据客观溯源 · 不编造投资与贷款承诺</span>
          </div>
        </div>
      </footer>
    </main>

    <!-- 买家置业画像侧拉抽屉 (Drawer) -->
    <el-drawer
      v-model="showProfileDrawer"
      title="买家置业偏好画像设置"
      direction="rtl"
      size="400px"
      custom-class="profile-drawer-custom"
    >
      <div class="drawer-body">
        <p class="drawer-desc">
          AI 顾问将根据您的预算、家庭结构与偏好指标，从 20 套户型中匹配打分最优的方案。
        </p>

        <div class="drawer-form-item">
          <label>预算区间 (元)</label>
          <div class="budget-dual-row">
            <el-input-number
              v-model="profile.budgetMin"
              :min="0"
              :step="50000"
              placeholder="最低"
              style="width: 48%"
            />
            <span class="sep">-</span>
            <el-input-number
              v-model="profile.budgetMax"
              :min="0"
              :step="50000"
              placeholder="最高"
              style="width: 48%"
            />
          </div>
        </div>

        <div class="drawer-form-item">
          <label>家庭常住结构</label>
          <el-select v-model="profile.familyStructure" style="width: 100%">
            <el-option label="单身自住 (SINGLE)" value="SINGLE" />
            <el-option label="新婚两口之家 (COUPLE)" value="COUPLE" />
            <el-option label="三口之家 (带学龄儿童) (FAMILY_3)" value="FAMILY_3" />
            <el-option label="三代同堂 (有老人同住) (THREE_GEN)" value="THREE_GEN" />
          </el-select>
        </div>

        <div class="drawer-form-item">
          <label>期望最少居室数</label>
          <el-input-number v-model="profile.mustRooms" :min="1" :max="6" style="width: 100%" />
        </div>

        <div class="drawer-form-item">
          <label>首选采光主朝向</label>
          <el-select v-model="profile.preferOrientation" style="width: 100%">
            <el-option label="正南朝向 (S)" value="S" />
            <el-option label="南北通透 (NS)" value="NS" />
            <el-option label="东南朝向 (SE)" value="SE" />
            <el-option label="西南朝向 (SW)" value="SW" />
          </el-select>
        </div>

        <div class="drawer-actions">
          <el-button type="primary" size="large" class="w-full" @click="saveProfile">
            保存并立即生效
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
/* 整个 AI 交互 Studio 全屏工作台 */
.ai-studio-container {
  display: flex;
  height: calc(100vh - 72px);
  background: var(--hf-bg);
  overflow: hidden;
  position: relative;
  font-family: inherit;
}

/* ───────────────────────── 左侧侧边栏 ───────────────────────── */
.ai-sidebar {
  width: 280px;
  background: #ffffff;
  border-right: 1px solid var(--hf-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.02);
}

.sidebar-header {
  padding: 18px 16px 14px;
  border-bottom: 1px solid var(--hf-border);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-avatar {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: linear-gradient(135deg, #155e75, #0891b2);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 800;
  box-shadow: 0 2px 6px rgba(21, 94, 117, 0.25);
}

.brand-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--hf-ink);
  line-height: 1.2;
}

.brand-tag {
  font-size: 11px;
  color: #0891b2;
  font-weight: 600;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 9px 14px;
  background: #155e75;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--hf-dur);
  box-shadow: 0 2px 6px rgba(21, 94, 117, 0.2);
}
.new-chat-btn:hover {
  background: #0e7490;
  transform: translateY(-1px);
}

.sessions-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 12px 10px;
}

.sessions-sec-title {
  font-size: 11px;
  font-weight: 700;
  color: var(--hf-text-3);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 4px 8px 8px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: all var(--hf-dur);
  border: 1px solid transparent;
  position: relative;
}

.session-item:hover {
  background: #f1f5f9;
}

.session-item.active {
  background: #f0fdfa;
  border-color: #99f6e4;
}

.session-icon {
  font-size: 13px;
}

.session-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.session-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--hf-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-item.active .session-title {
  font-weight: 600;
  color: #155e75;
}

.session-time {
  font-size: 11px;
  color: var(--hf-text-3);
}

.del-session-btn {
  opacity: 0;
  background: transparent;
  border: none;
  font-size: 16px;
  color: var(--hf-text-3);
  cursor: pointer;
  padding: 0 4px;
  transition: opacity var(--hf-dur);
}
.session-item:hover .del-session-btn {
  opacity: 1;
}
.del-session-btn:hover {
  color: #ef4444;
}

/* 侧栏底部买家看板 */
.profile-dock-card {
  margin: 10px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid var(--hf-border);
  border-radius: 8px;
}

.profile-dock-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.dock-badge {
  font-size: 11px;
  font-weight: 700;
  color: var(--hf-text-2);
}

.dock-edit-btn {
  background: transparent;
  border: none;
  font-size: 11px;
  color: #0891b2;
  cursor: pointer;
  font-weight: 600;
}
.dock-edit-btn:hover {
  text-decoration: underline;
}

.profile-dock-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dock-stat {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}

.dock-stat .label {
  color: var(--hf-text-3);
}

.dock-stat .val {
  color: var(--hf-ink);
  font-weight: 600;
}

/* ───────────────────────── 右侧主对话区 ───────────────────────── */
.ai-main-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  background: var(--hf-bg);
  background-image: radial-gradient(#cbd5e1 1px, transparent 1px);
  background-size: 24px 24px;
}

/* 顶栏 */
.chat-topbar {
  height: 56px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--hf-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  z-index: 10;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.02);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.online-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.2);
  animation: pulseDot 2s infinite;
}

@keyframes pulseDot {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
}

.topbar-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--hf-ink);
  margin: 0;
}

.topbar-subtitle {
  font-size: 11px;
  color: var(--hf-text-3);
  margin: 0;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.top-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--hf-text-2);
  cursor: pointer;
  transition: all var(--hf-dur);
}
.top-btn:hover:not(:disabled) {
  background: #f8fafc;
  color: var(--hf-ink);
  border-color: var(--hf-border-strong);
}
.top-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.accent-btn {
  background: #f0fdfa;
  border-color: #99f6e4;
  color: #0f766e;
}
.accent-btn:hover {
  background: #ccfbf1 !important;
}

/* ───────────────────────── 消息列表容器 ───────────────────────── */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px 20px 180px;
}

/* 空状态欢迎界面 */
.welcome-container {
  max-width: 820px;
  margin: 40px auto 0;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.welcome-hero {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.welcome-spark {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #155e75, #0ea5e9);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: bold;
  box-shadow: 0 4px 14px rgba(21, 94, 117, 0.25);
  margin-bottom: 6px;
}

.welcome-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--hf-ink);
  letter-spacing: -0.02em;
}

.welcome-desc {
  font-size: 14px;
  color: var(--hf-text-2);
  max-width: 600px;
  line-height: 1.6;
}

.scenarios-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.scenario-card {
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: 12px;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
}
.scenario-card:hover {
  border-color: #0891b2;
  box-shadow: 0 6px 16px rgba(8, 145, 178, 0.12);
  transform: translateY(-2px);
}

.sc-icon {
  font-size: 26px;
  flex-shrink: 0;
}

.sc-content {
  flex: 1;
}

.sc-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--hf-ink);
  margin: 0 0 4px;
}

.sc-desc {
  font-size: 12px;
  color: var(--hf-text-3);
  margin: 0;
  line-height: 1.4;
}

.sc-arrow {
  color: var(--hf-text-3);
  font-size: 16px;
  transition: transform 0.2s;
}
.scenario-card:hover .sc-arrow {
  color: #0891b2;
  transform: translateX(4px);
}

/* 消息流 */
.message-stream {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.row-user {
  justify-content: flex-end;
}

.row-assistant {
  justify-content: flex-start;
}

.avatar-box {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai-avatar-box {
  background: linear-gradient(135deg, #0e7490, #155e75);
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(14, 116, 144, 0.2);
}

.user-avatar-box {
  background: #334155;
  color: #ffffff;
}

.bubble-envelope {
  max-width: 82%;
  display: flex;
  flex-direction: column;
}

.msg-header-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  padding-left: 2px;
}

.role-name {
  font-size: 12px;
  font-weight: 700;
  color: var(--hf-ink);
}

.source-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: 9999px;
}

.badge-real-ai {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.badge-fallback {
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #cbd5e1;
}

.badge-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.msg-time {
  font-size: 11px;
  color: var(--hf-text-3);
}

.bubble-card {
  padding: 14px 18px;
  border-radius: 14px;
  position: relative;
  font-size: 14px;
  line-height: 1.7;
}

.bubble-user {
  background: #155e75;
  color: #ffffff;
  border-top-right-radius: 3px;
  box-shadow: 0 3px 10px rgba(21, 94, 117, 0.25);
}

.user-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.user-meta-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
  padding-right: 4px;
}

.bubble-assistant {
  background: #ffffff;
  color: #1e293b;
  border: 1px solid var(--hf-border);
  border-top-left-radius: 3px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
}

/* Markdown 排版深度美化 */
.assistant-markdown-body {
  word-break: break-word;
}

.markdown-content :deep(p) {
  margin: 0 0 10px;
}
.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  color: #0f172a;
  font-weight: 700;
  margin: 16px 0 8px;
  line-height: 1.3;
}

.markdown-content :deep(strong) {
  color: #0f172a;
  font-weight: 700;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}

.markdown-content :deep(li) {
  margin: 4px 0;
}

.markdown-content :deep(blockquote) {
  border-left: 3px solid #0891b2;
  background: #f0fdfa;
  padding: 8px 14px;
  margin: 10px 0;
  border-radius: 0 8px 8px 0;
  color: #134e4a;
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 8px 12px;
  border: 1px solid #e2e8f0;
  text-align: left;
}

.markdown-content :deep(th) {
  background: #f8fafc;
  font-weight: 700;
}

.typewriter-cursor {
  display: inline-block;
  color: #0891b2;
  font-weight: 900;
  animation: blink 0.8s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.ai-thinking-state {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--hf-text-2);
  padding: 6px 0;
}

.spinner-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #0891b2;
  animation: thinkingPulse 1s infinite alternate;
}

@keyframes thinkingPulse {
  from { transform: scale(0.8); opacity: 0.5; }
  to { transform: scale(1.3); opacity: 1; }
}

/* 消息底部工具条 */
.msg-tools-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  border: none;
  font-size: 11px;
  color: var(--hf-text-3);
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: all var(--hf-dur);
}
.tool-btn:hover:not(:disabled) {
  background: #f1f5f9;
  color: var(--hf-ink);
}
.tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ───────────────────────── 底部悬浮输入 Dock ───────────────────────── */
.composer-dock-container {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 0 20px 16px;
  background: linear-gradient(to top, rgba(248, 250, 252, 0.95) 80%, rgba(248, 250, 252, 0) 100%);
  pointer-events: none;
}

.composer-dock-inner {
  max-width: 860px;
  margin: 0 auto;
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 猜您想问滚动条 */
.chips-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.chips-label {
  font-size: 12px;
  color: var(--hf-text-3);
  font-weight: 600;
  white-space: nowrap;
}

.chips-scroll {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
}
.chips-scroll::-webkit-scrollbar {
  display: none;
}

.chip-pill {
  padding: 5px 12px;
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: 9999px;
  font-size: 12px;
  color: var(--hf-text-2);
  white-space: nowrap;
  cursor: pointer;
  transition: all var(--hf-dur);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}
.chip-pill:hover:not(:disabled) {
  background: #f0fdfa;
  border-color: #5eead4;
  color: #0f766e;
}

/* 输入框卡片 */
.input-card {
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid var(--hf-border-strong);
  border-radius: 14px;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.08);
  padding: 10px 14px 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: border-color var(--hf-dur), box-shadow var(--hf-dur);
}

.input-card:focus-within {
  border-color: #0891b2;
  box-shadow: 0 8px 26px rgba(8, 145, 178, 0.15);
}

.dock-textarea {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  line-height: 1.6;
  color: var(--hf-ink);
  resize: none;
  font-family: inherit;
  max-height: 140px;
}
.dock-textarea::placeholder {
  color: #94a3b8;
}

.input-controls-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.control-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: #f1f5f9;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--hf-text-2);
}

.model-spark {
  color: #0891b2;
}

.hint-text {
  font-size: 11px;
  color: var(--hf-text-3);
}

.control-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #155e75;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--hf-dur);
  box-shadow: 0 2px 6px rgba(21, 94, 117, 0.25);
}
.send-btn:hover:not(:disabled) {
  background: #0e7490;
  transform: translateY(-1px);
}
.send-btn:disabled {
  background: #cbd5e1;
  color: #94a3b8;
  cursor: not-allowed;
  box-shadow: none;
}

.stop-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #ef4444;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--hf-dur);
}
.stop-btn:hover {
  background: #dc2626;
}

.stop-icon {
  font-size: 10px;
}

.compliance-footer {
  text-align: center;
  font-size: 11px;
  color: var(--hf-text-3);
  padding-top: 2px;
}

/* ───────────────────────── 侧拉画像抽屉 ───────────────────────── */
.drawer-body {
  padding: 0 8px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.drawer-desc {
  font-size: 13px;
  color: var(--hf-text-2);
  line-height: 1.5;
  margin: 0;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--hf-border);
}

.drawer-form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.drawer-form-item label {
  font-size: 13px;
  font-weight: 600;
  color: var(--hf-ink);
}

.budget-dual-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sep {
  color: var(--hf-text-3);
  font-weight: bold;
}

.drawer-actions {
  margin-top: 12px;
}

.w-full {
  width: 100% !important;
}

/* 响应式适配 */
@media (max-width: 900px) {
  .ai-sidebar {
    display: none;
  }
  .scenarios-grid {
    grid-template-columns: 1fr;
  }
}
</style>
