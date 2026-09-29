<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { del, get, put } from '../api/http'
import AppIcon from '../components/AppIcon.vue'

/**
 * 顾问多轮对话。历史存在服务端，回答用 fetch 读 SSE，不走 axios。
 * 画像完整度不到 60% 时先保存画像。分数仍只来自规则引擎。
 */
type Turn = { role: 'user' | 'assistant'; content: string; source?: 'ai' | 'fallback' }

const q = ref('')
const sending = ref(false)
const messages = ref<Turn[]>([])
const completeness = ref(100)
const box = ref<HTMLElement | null>(null)
const profile = reactive({
  budgetMin: undefined as number | undefined,
  budgetMax: undefined as number | undefined,
  familyStructure: '',
  mustRooms: undefined as number | undefined,
  preferOrientation: '',
})

const EXAMPLES = [
  '三代同住，预算 160 万内，想要 3-4 房，重视通风与双卫',
  '年轻两口之家，注重采光通透与居家办公安静环境',
  '家有老人同住，希望动静分区明确、走道面积少、得房率高',
]

async function saveProfile() {
  const me = await put<any>('/users/me/profile', { ...profile })
  completeness.value = Number(me.profileCompleteness ?? 0)
  const cached = JSON.parse(localStorage.getItem('hf-user') || '{}')
  cached.profileCompleteness = completeness.value
  localStorage.setItem('hf-user', JSON.stringify(cached))
  ElMessage.success('购房者偏好画像已保存')
}

async function loadHistory() {
  const rows = await get<any[]>('/ai/chat')
  messages.value = (rows ?? []).map((row) => ({
    role: row.role === 'user' ? 'user' : 'assistant',
    content: String(row.content ?? ''),
    source: String(row.content ?? '').startsWith('模型暂不可用') ? 'fallback' : 'ai',
  }))
  await scrollDown()
}

async function resetChat() {
  await del('/ai/chat')
  messages.value = []
  ElMessage.success('已开启新一轮选房咨询对话')
}

async function scrollDown() {
  await nextTick()
  if (box.value) box.value.scrollTop = box.value.scrollHeight
}

function applyEvent(raw: string, turn: Turn) {
  const data = raw
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
    .join('\n')
  if (!data) return
  const ev = JSON.parse(data) as { type?: string; text?: string; source?: string }
  if (ev.type === 'token' && ev.text) {
    turn.content += ev.text
  } else if (ev.type === 'done') {
    turn.source = ev.source === 'fallback' ? 'fallback' : 'ai'
  }
}

async function ask(preset?: string) {
  const question = (preset ?? q.value).trim()
  if (!question || sending.value || completeness.value < 60) return
  q.value = ''
  messages.value.push({ role: 'user', content: question })
  const turn: Turn = { role: 'assistant', content: '', source: 'ai' }
  messages.value.push(turn)
  sending.value = true
  await scrollDown()
  try {
    const token = localStorage.getItem('hf-token') || ''
    const res = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ question }),
    })
    const type = res.headers.get('content-type') || ''
    if (!res.ok || !type.includes('text/event-stream') || !res.body) {
      turn.content = '顾问暂时连不上，请稍后再试。'
      turn.source = 'fallback'
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
        applyEvent(buf.slice(0, cut), turn)
        buf = buf.slice(cut + 2)
        cut = buf.indexOf('\n\n')
        await scrollDown()
      }
    }
    if (buf.trim()) applyEvent(buf, turn)
    if (!turn.content) {
      turn.content = '没有收到回复。'
      turn.source = 'fallback'
    }
  } catch {
    if (!turn.content) {
      turn.content = '顾问暂时连不上，请稍后再试。'
      turn.source = 'fallback'
    }
  } finally {
    sending.value = false
    await scrollDown()
  }
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
    completeness.value = Number(me.profileCompleteness ?? 0)
    profile.budgetMin = me.budgetMin == null ? undefined : Number(me.budgetMin)
    profile.budgetMax = me.budgetMax == null ? undefined : Number(me.budgetMax)
    profile.familyStructure = me.familyStructure || ''
    profile.mustRooms = me.mustRooms == null ? undefined : Number(me.mustRooms)
    profile.preferOrientation = me.preferOrientation || ''
    if (completeness.value >= 60) await loadHistory()
  } catch { /* 未登录由路由拦住 */ }
})
</script>

<template>
  <div class="ai-container">
    <!-- 标头 -->
    <header class="ai-header">
      <div class="ai-header-left">
        <div class="hf-kicker">
          <svg viewBox="0 0 16 16" width="12" height="12" fill="currentColor">
            <path d="M6 12.5a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 0-1h-3a.5.5 0 0 0-.5.5zm-4.354-4.646a.5.5 0 0 1 0-.708l3-3a.5.5 0 0 1 .708.708L2.707 7.5l2.647 2.646a.5.5 0 0 1-.708.708l-3-3zm12.708 0a.5.5 0 0 0 0-.708l-3-3a.5.5 0 0 0-.708.708L13.293 7.5l-2.647 2.646a.5.5 0 0 0 .708.708l3-3z"/>
          </svg>
          INTELLIGENT HOUSING ADVISOR · AI 置业顾问
        </div>
        <h1 class="ai-title">肇庆好房子 · 智能选房顾问</h1>
        <p class="ai-lead">
          多轮上下文连续咨询。严格引用后端规则引擎指标得分，不编造贷款、税费与虚假资格。
        </p>
      </div>

      <div v-if="completeness >= 60" class="ai-header-right">
        <el-button
          size="default"
          class="reset-btn"
          :disabled="sending || !messages.length"
          @click="resetChat"
        >
          <svg viewBox="0 0 16 16" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M2 8a6 6 0 1 1 1.76 4.24M2 8V4m0 4h4" />
          </svg>
          开启新对话
        </el-button>
      </div>
    </header>

    <!-- 画像未达标提示 -->
    <section v-if="completeness < 60" class="profile-gate-card">
      <div class="gate-header">
        <div class="gate-icon">👤</div>
        <div>
          <h3>完善置业偏好画像 (当前完整度 {{ completeness }}%)</h3>
          <p>系统要求画像完整度达到 60% 后才可激活 AI 顾问，以便为您推荐最匹配的户型方案。</p>
        </div>
      </div>

      <div class="profile-form-grid">
        <div class="form-item">
          <label>预算下限 (元)</label>
          <el-input-number v-model="profile.budgetMin" :min="0" :step="50000" placeholder="例如 800000" style="width: 100%" />
        </div>
        <div class="form-item">
          <label>预算上限 (元)</label>
          <el-input-number v-model="profile.budgetMax" :min="0" :step="50000" placeholder="例如 1600000" style="width: 100%" />
        </div>
        <div class="form-item">
          <label>家庭结构</label>
          <el-select v-model="profile.familyStructure" placeholder="请选择家庭结构" style="width: 100%">
            <el-option label="单身贵族" value="SINGLE" />
            <el-option label="新婚两口之家" value="COUPLE" />
            <el-option label="三口之家 (带学龄儿童)" value="FAMILY_3" />
            <el-option label="三代同堂 (有老人同住)" value="THREE_GEN" />
          </el-select>
        </div>
        <div class="form-item">
          <label>最少居室数</label>
          <el-input-number v-model="profile.mustRooms" :min="1" :max="6" placeholder="居室" style="width: 100%" />
        </div>
        <div class="form-item">
          <label>首选采光朝向</label>
          <el-select v-model="profile.preferOrientation" placeholder="请选择朝向" style="width: 100%">
            <el-option label="南北通透 (NS)" value="NS" />
            <el-option label="正南朝向 (S)" value="S" />
            <el-option label="东南朝向 (SE)" value="SE" />
            <el-option label="西南朝向 (SW)" value="SW" />
          </el-select>
        </div>
        <div class="form-action">
          <el-button type="primary" size="large" class="save-profile-btn" @click="saveProfile">
            保存画像并解锁 AI 顾问
          </el-button>
        </div>
      </div>
    </section>

    <!-- 正常对话界面 -->
    <template v-else>
      <div class="chat-card">
        <div ref="box" class="thread-box">
          <div v-if="!messages.length" class="empty-chat">
            <div class="empty-logo">🏛️</div>
            <h4>我是您的肇庆星湖置业顾问</h4>
            <p>您可以从下方推荐问题中选择，或直接输入您的购房需求、居住痛点或特定户型咨询：</p>
          </div>

          <div
            v-for="(m, i) in messages"
            :key="i"
            class="chat-row"
            :class="m.role === 'user' ? 'user-row' : 'assistant-row'"
          >
            <div class="chat-avatar" :class="m.role === 'user' ? 'u-avatar' : 'ai-avatar'">
              <AppIcon :name="m.role === 'user' ? 'user' : 'ai'" :size="16" />
            </div>

            <div class="chat-bubble-wrap">
              <div v-if="m.role === 'assistant'" class="bubble-meta">
                <span class="advisor-name">置业顾问 AI</span>
                <el-tag
                  v-if="m.source === 'fallback'"
                  size="small"
                  type="info"
                  effect="light"
                  class="source-tag"
                >
                  离线模板
                </el-tag>
                <el-tag
                  v-else
                  size="small"
                  type="primary"
                  effect="light"
                  class="source-tag"
                >
                  通义千问在线流式
                </el-tag>
              </div>

              <div class="chat-bubble">
                <p class="bubble-text">{{ m.content || (sending && i === messages.length - 1 ? '正在检索户型与规则分析…' : '') }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部输入与预设问题栏 -->
        <div class="composer-section">
          <div class="preset-examples">
            <span class="preset-label">快捷提问：</span>
            <button
              v-for="e in EXAMPLES"
              :key="e"
              type="button"
              class="example-pill"
              :disabled="sending"
              @click="ask(e)"
            >
              {{ e }}
            </button>
          </div>

          <div class="input-row">
            <el-input
              v-model="q"
              type="textarea"
              :rows="2"
              maxlength="800"
              resize="none"
              placeholder="请输入您的选房咨询，例如：肇庆星湖附近哪套户型通风采光最好？支持 Shift+Enter 换行"
              class="chat-input"
              @keydown="onKeydown"
            />
            <el-button
              type="primary"
              size="large"
              class="send-btn"
              :loading="sending"
              :disabled="!q.trim()"
              @click="ask()"
            >
              发送咨询
            </el-button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ai-container {
  max-width: 920px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Header */
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 24px;
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
  flex-wrap: wrap;
}

.ai-header-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 52em;
}

.ai-title {
  font-size: clamp(24px, 3vw, 32px);
  font-weight: 800;
  color: var(--hf-ink);
  letter-spacing: -0.03em;
  margin: 0;
}

.ai-lead {
  font-size: 14.5px;
  color: var(--hf-text-2);
  line-height: 1.6;
}

.reset-btn {
  border-radius: 9999px !important;
}

/* Gate Card */
.profile-gate-card {
  padding: 28px;
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.gate-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.gate-icon {
  font-size: 32px;
  width: 56px;
  height: 56px;
  border-radius: 12px;
  background: var(--hf-primary-soft);
  display: grid;
  place-items: center;
}

.gate-header h3 {
  font-size: 18px;
  font-weight: 800;
  color: var(--hf-ink);
  margin: 0 0 4px;
}

.gate-header p {
  font-size: 13.5px;
  color: var(--hf-text-2);
}

.profile-form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--hf-border);
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item label {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--hf-text-2);
}

.form-action {
  grid-column: 1 / -1;
  margin-top: 8px;
}

.save-profile-btn {
  width: 100%;
  border-radius: var(--hf-radius-m) !important;
  font-weight: 700 !important;
}

/* Chat Card */
.chat-card {
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-l);
  box-shadow: var(--hf-shadow-sm);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 260px);
  min-height: 520px;
}

.thread-box {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  background-color: var(--hf-canvas);
  background-image: radial-gradient(rgba(148, 163, 184, 0.1) 1px, transparent 1px);
  background-size: 20px 20px;
}

.empty-chat {
  margin: auto;
  text-align: center;
  max-width: 440px;
  padding: 40px 20px;
}

.empty-logo {
  font-size: 40px;
  margin-bottom: 12px;
}

.empty-chat h4 {
  font-size: 18px;
  font-weight: 800;
  color: var(--hf-ink);
  margin-bottom: 8px;
}

.empty-chat p {
  font-size: 13.5px;
  color: var(--hf-text-2);
  line-height: 1.6;
}

/* Chat Rows */
.chat-row {
  display: flex;
  gap: 12px;
  max-width: 82%;
  align-items: flex-start;
}

.user-row {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.assistant-row {
  align-self: flex-start;
}

.chat-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex: none;
  box-shadow: var(--hf-shadow-xs);
}

.u-avatar {
  background: var(--hf-primary-soft);
  color: var(--hf-primary);
}

.ai-avatar {
  background: linear-gradient(135deg, var(--hf-primary) 0%, #0284c7 100%);
  color: #ffffff;
}

.chat-bubble-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.bubble-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--hf-text-3);
}

.advisor-name {
  font-weight: 700;
  color: var(--hf-text-2);
}

.source-tag {
  font-size: 10px !important;
  height: 18px !important;
  line-height: 18px !important;
  padding: 0 6px !important;
}

.chat-bubble {
  padding: 12px 18px;
  border-radius: 16px;
  box-shadow: var(--hf-shadow-xs);
  word-break: break-word;
}

.user-row .chat-bubble {
  background: var(--hf-primary);
  color: #ffffff;
  border-bottom-right-radius: 4px;
}

.assistant-row .chat-bubble {
  background: #ffffff;
  border: 1px solid var(--hf-border);
  border-bottom-left-radius: 4px;
  color: var(--hf-text);
}

.bubble-text {
  font-size: 14.5px;
  line-height: 1.7;
  white-space: pre-wrap;
}

/* Composer */
.composer-section {
  padding: 16px 20px;
  background: #ffffff;
  border-top: 1px solid var(--hf-border);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preset-examples {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.preset-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--hf-text-3);
  white-space: nowrap;
}

.example-pill {
  border: 1px dashed var(--hf-border-strong);
  background: var(--hf-canvas);
  color: var(--hf-text-2);
  border-radius: 9999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all var(--hf-dur);
  white-space: nowrap;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.example-pill:hover {
  background: var(--hf-primary-softer);
  color: var(--hf-primary);
  border-color: var(--hf-primary);
}

.example-pill:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input :deep(.el-textarea__inner) {
  border-radius: var(--hf-radius-m) !important;
  font-size: 14px;
  padding: 10px 14px;
}

.send-btn {
  height: 54px !important;
  border-radius: var(--hf-radius-m) !important;
  padding: 0 24px !important;
  font-weight: 700 !important;
}

@media (max-width: 640px) {
  .profile-form-grid {
    grid-template-columns: 1fr;
  }
  .chat-row {
    max-width: 92%;
  }
}
</style>
