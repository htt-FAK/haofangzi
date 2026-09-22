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
  '三代同住，预算 160 万内，想要 3-4 房，重视通风',
  '两口之家，注重采光与居家办公安静',
  '有老人同住，希望动静分区明确、少走道',
]

async function saveProfile() {
  const me = await put<any>('/users/me/profile', { ...profile })
  completeness.value = Number(me.profileCompleteness ?? 0)
  const cached = JSON.parse(localStorage.getItem('hf-user') || '{}')
  cached.profileCompleteness = completeness.value
  localStorage.setItem('hf-user', JSON.stringify(cached))
  ElMessage.success('画像已保存')
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
  ElMessage.success('已开始新对话')
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
  <div class="ai">
    <header class="intro">
      <div>
        <p class="hf-kicker">顾问</p>
        <h1>AI 选房顾问</h1>
        <p class="hf-lead">可以连续追问。我会记住你这个账号说过的话。分数只引用规则引擎，不编贷款、税费和资格。</p>
      </div>
      <el-button v-if="completeness >= 60" :disabled="sending || !messages.length" @click="resetChat">新对话</el-button>
    </header>

    <section v-if="completeness < 60" class="ask">
      <p class="hf-lead">画像完整度 {{ completeness }}%，未到 60% 时不能使用顾问。填完预算、家庭、居室和朝向即可。</p>
      <div class="profile">
        <el-input-number v-model="profile.budgetMin" :min="0" :step="10000" placeholder="预算下限" />
        <el-input-number v-model="profile.budgetMax" :min="0" :step="10000" placeholder="预算上限" />
        <el-input v-model="profile.familyStructure" placeholder="家庭结构，如 FAMILY_3" />
        <el-input-number v-model="profile.mustRooms" :min="1" :max="6" placeholder="最少居室" />
        <el-input v-model="profile.preferOrientation" placeholder="偏好朝向，如 S" />
        <el-button type="primary" @click="saveProfile">保存画像</el-button>
      </div>
    </section>

    <template v-else>
      <div ref="box" class="thread">
        <p v-if="!messages.length" class="empty">从下面选一句，或直接输入。刷新后这段对话还在。</p>
        <div
          v-for="(m, i) in messages"
          :key="i"
          class="row"
          :class="m.role === 'user' ? 'mine' : 'theirs'"
        >
          <span class="avatar" :class="m.role === 'user' ? 'q' : 'a'">
            <AppIcon :name="m.role === 'user' ? 'user' : 'ai'" :size="15" />
          </span>
          <div class="bubble">
            <div v-if="m.role === 'assistant'" class="who">
              顾问
              <el-tag v-if="m.source === 'fallback'" size="small" type="info">模板</el-tag>
            </div>
            <p class="txt">{{ m.content || (sending && i === messages.length - 1 ? '…' : '') }}</p>
          </div>
        </div>
      </div>

      <div class="composer">
        <div class="examples">
          <button v-for="e in EXAMPLES" :key="e" type="button" class="ex-chip" :disabled="sending" @click="ask(e)">{{ e }}</button>
        </div>
        <div class="bar">
          <el-input
            v-model="q"
            type="textarea"
            :rows="2"
            maxlength="800"
            resize="none"
            placeholder="接着问，例如：刚才说的那套朝向怎么样"
            @keydown="onKeydown"
          />
          <el-button type="primary" :loading="sending" :disabled="!q.trim()" @click="ask()">发送</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ai { max-width: 760px; margin: 0 auto; display: flex; flex-direction: column; min-height: calc(100vh - 120px); }
.intro { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 16px; }
.intro h1 { font-size: 28px; letter-spacing: -0.03em; margin: 8px 0 10px; }
.ask {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 14px 0 18px;
  padding: 16px;
  background: var(--hf-surface);
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-m);
}
.profile { display: flex; flex-wrap: wrap; gap: 8px; }
.thread {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 8px 4px 16px;
  min-height: 280px;
}
.empty { color: var(--hf-text-3); font-size: 14px; margin: 48px auto; }
.row { display: flex; gap: 10px; align-items: flex-start; max-width: 86%; }
.row.mine { align-self: flex-end; flex-direction: row-reverse; }
.row.theirs { align-self: flex-start; }
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex: none;
}
.avatar.q { background: var(--hf-primary-soft); color: var(--hf-primary-strong); }
.avatar.a { background: var(--hf-ink); color: #fff; }
.bubble {
  padding: 10px 14px;
  border-radius: 16px;
  border: 1px solid var(--hf-border);
}
.mine .bubble { background: var(--hf-primary-softer); border-color: var(--hf-primary-soft); border-bottom-right-radius: 4px; }
.theirs .bubble { background: var(--hf-surface); box-shadow: var(--hf-shadow-sm); border-bottom-left-radius: 4px; }
.who { font-size: 12px; color: var(--hf-text-3); display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.txt { white-space: pre-wrap; font-size: 14px; line-height: 1.75; color: var(--hf-text); min-height: 1.2em; }
.composer {
  position: sticky;
  bottom: 0;
  padding-top: 8px;
  background: linear-gradient(to top, var(--hf-bg, #fff) 70%, transparent);
}
.examples { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.ex-chip {
  border: 1px dashed var(--hf-border-strong);
  background: var(--hf-primary-softer);
  color: var(--hf-text-2);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  max-width: 100%;
}
.ex-chip:disabled { opacity: 0.5; cursor: default; }
.bar { display: flex; gap: 10px; align-items: flex-end; }
.bar .el-button { height: 40px; }
</style>
