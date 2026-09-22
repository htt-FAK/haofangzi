<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { get, post } from '../api/http'
import PlanThumb from '../components/PlanThumb.vue'
import type { PlanBox } from '../components/PlanThumb.vue'

/**
 * 登录 / 注册（spec 001 US-01、AC-01/02/06）。
 * 令牌存 localStorage 只是课程演示口径（真实项目应使用 httpOnly Cookie）。
 */
const route = useRoute()
const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const form = reactive({ phone: '', password: '', captchaId: '', captcha: '', nickname: '' })
const smsMode = ref(false)
const smsCode = ref('')
const lockSeconds = ref(0)
const captchaImg = ref('')
const samplePlan = ref<PlanBox[]>([])

async function loadCaptcha() {
  const d = await get<{ captchaId: string; imageBase64: string }>('/auth/captcha')
  form.captchaId = d.captchaId
  form.captcha = ''
  captchaImg.value = d.imageBase64
}

type LoginResult = { token: string; refreshToken?: string; user: { nickname: string; profileCompleteness: number; role: string } }

function enter(data: LoginResult) {
  localStorage.setItem('hf-token', data.token)
  if (data.refreshToken) localStorage.setItem('hf-refresh', data.refreshToken)
  localStorage.setItem('hf-user', JSON.stringify(data.user))
  if (data.user.profileCompleteness < 60) ElMessage.warning('画像完整度不足 60%，建议先完善（AI 顾问将不可用）')
  router.replace(String(route.query.redirect || '/'))
}

async function submit() {
  loading.value = true
  try {
    if (mode.value === 'register') {
      await post('/auth/register', { ...form })
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
      await loadCaptcha()
      return
    }
    const body = smsMode.value
      ? { phone: form.phone, smsCode: smsCode.value }
      : { phone: form.phone, password: form.password, captchaId: form.captchaId, captcha: form.captcha }
    enter(await post<LoginResult>('/auth/login', body))
  } catch (e: any) {
    if (e?.code === 40305) lockSeconds.value = Number(e?.data?.lockSeconds ?? e?.data?.detail?.lockSeconds ?? 900)
    if (e?.code === 40011) await loadCaptcha()
  } finally {
    loading.value = false
  }
}

/** 演示账号免图形码：走已有短信通道，验证码固定 123456。 */
async function demoLogin() {
  loading.value = true
  try {
    const phone = '13800000001'
    await post('/auth/code', { phone })
    enter(await post<LoginResult>('/auth/login', { phone, smsCode: '123456' }))
  } finally {
    loading.value = false
  }
}

async function sendCode() {
  await post('/auth/code', { phone: form.phone })
  ElMessage.success('验证码已发送（演示环境固定 123456）')
}

onMounted(async () => {
  loadCaptcha()
  try {
    const p = await get<{ records: { plan?: PlanBox[] }[] }>('/house-types', { size: 1 })
    samplePlan.value = p.records?.[0]?.plan ?? []
  } catch {
    samplePlan.value = []
  }
})
</script>

<template>
  <div class="page">
    <aside class="intro">
      <p class="hf-kicker">肇庆 · 好房子</p>
      <h1>先把户型看清楚，<br />再谈好不好。</h1>
      <p class="hf-lead">评分来自规则引擎，不是黑盒。2D 图纸、3D 空间、对比矩阵都在同一条路径里。</p>
      <div class="art" aria-hidden="true">
        <PlanThumb :plan="samplePlan" />
      </div>
    </aside>

    <div class="form-side">
      <div class="box">
        <h2>{{ mode === 'login' ? '登录' : '注册账号' }}</h2>
        <p class="sub">演示系统，数据为模拟样例</p>

        <div class="seg" role="tablist">
          <button type="button" :class="{ on: mode === 'login' }" @click="mode = 'login'">登录</button>
          <button type="button" :class="{ on: mode === 'register' }" @click="mode = 'register'">注册</button>
        </div>

        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model="form.phone" maxlength="32" placeholder="手机号，或演示账号 admin" />
          </el-form-item>

          <template v-if="mode === 'login'">
            <el-checkbox v-model="smsMode" size="small">使用短信验证码登录</el-checkbox>
            <el-form-item v-if="!smsMode" label="口令">
              <el-input v-model="form.password" type="password" show-password placeholder="≥8 位，含字母与数字" />
            </el-form-item>
            <el-form-item v-if="!smsMode" label="图形验证码">
              <div class="cap">
                <el-input v-model="form.captcha" placeholder="结果" />
                <button type="button" class="cap-img" title="点击刷新" @click="loadCaptcha">
                  <img v-if="captchaImg" :src="captchaImg" alt="验证码" />
                  <span v-else>加载验证码</span>
                </button>
              </div>
            </el-form-item>
            <template v-if="smsMode">
              <el-form-item label="短信验证码">
                <div class="cap">
                  <el-input v-model="smsCode" maxlength="6" />
                  <el-button :disabled="lockSeconds > 0" @click="sendCode">
                    {{ lockSeconds > 0 ? `${lockSeconds}s` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
            </template>
          </template>

          <template v-else>
            <el-form-item label="昵称（可选）">
              <el-input v-model="form.nickname" maxlength="8" />
            </el-form-item>
            <el-form-item label="口令">
              <el-input v-model="form.password" type="password" show-password />
            </el-form-item>
            <el-form-item label="图形验证码">
              <div class="cap">
                <el-input v-model="form.captcha" placeholder="结果" />
                <button type="button" class="cap-img" title="点击刷新" @click="loadCaptcha">
                  <img v-if="captchaImg" :src="captchaImg" alt="验证码" />
                  <span v-else>加载验证码</span>
                </button>
              </div>
            </el-form-item>
          </template>

          <el-button type="primary" size="large" style="width: 100%" :loading="loading" :disabled="lockSeconds > 0" @click="submit">
            {{ mode === 'login' ? '进入选房' : '注册' }}
          </el-button>
          <el-button v-if="mode === 'login'" class="demo" size="large" :loading="loading" @click="demoLogin">
            测试账号一键登录
          </el-button>
          <p v-if="lockSeconds > 0" class="hint">账号已锁定，约 {{ lockSeconds }} 秒后可再试</p>
          <p class="hint">一键登录使用购房者 13800000001。管理员仍用 admin / Test@123</p>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
}

.intro {
  padding: 64px 56px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: var(--hf-ink);
}
.intro h1 {
  font-size: clamp(32px, 4vw, 44px);
  letter-spacing: -0.04em;
  margin: 12px 0 14px;
  line-height: 1.2;
}
.art {
  margin-top: 36px;
  max-width: 420px;
  aspect-ratio: 16 / 11;
  border-radius: 18px;
  overflow: hidden;
  border: 1px solid var(--hf-border);
  box-shadow: var(--hf-shadow-md);
}

.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: var(--hf-surface);
  border-left: 1px solid var(--hf-border);
}
.box { width: 400px; max-width: 100%; }
h2 { font-size: 26px; letter-spacing: -0.03em; }
.sub { color: var(--hf-text-3); font-size: 14px; margin: 6px 0 22px; }

.seg {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 4px;
  margin-bottom: 18px;
  background: var(--hf-canvas);
  border-radius: 10px;
}
.seg button {
  border: 0;
  background: transparent;
  min-height: 40px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--hf-text-2);
  font-size: 14px;
}
.seg button.on { background: #fff; color: var(--hf-ink); font-weight: 600; box-shadow: var(--hf-shadow-sm); }

.cap { display: flex; gap: 8px; align-items: center; }
.cap-img {
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-s);
  padding: 0;
  height: 40px;
  width: 120px;
  flex: none;
  background: #fff;
  cursor: pointer;
  overflow: hidden;
}
.cap-img img { display: block; width: 120px; height: 40px; }
.demo { width: 100%; margin-top: 10px; }
.hint { color: var(--hf-text-3); font-size: 12px; margin: 12px 0 0; }

@media (max-width: 900px) {
  .page { grid-template-columns: 1fr; }
  .intro { display: none; }
  .form-side { border-left: 0; min-height: 100dvh; background: var(--hf-canvas); }
  .box {
    background: #fff;
    padding: 28px 22px;
    border-radius: var(--hf-radius-l);
    border: 1px solid var(--hf-border);
  }
}
</style>
