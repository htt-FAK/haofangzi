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
      <div class="intro-wrap">
        <div class="hf-kicker">
          🏛️ 肇庆 · 好房子 · 在线选房与户型智能评估
        </div>
        <h1 class="intro-title">先把户型看清楚，<br /><span class="highlight">再谈好不好。</span></h1>
        <p class="intro-desc">
          基于国家标准与外置规则引擎，采光、通风、动线、实用等 7 大维度可解释量化评分。2D 图纸、3D 空间仿真与选房矩阵一站式可查。
        </p>

        <div class="feature-pills">
          <span class="f-pill">📐 真实 CAD 图纸比例标尺</span>
          <span class="f-pill">📊 规则引擎透明可溯源</span>
          <span class="f-pill">⏱️ 10 分钟意向锁房</span>
        </div>

        <div class="blueprint-art" aria-hidden="true">
          <div class="art-badge">典型户型几何构件预览</div>
          <PlanThumb :plan="samplePlan" />
        </div>
      </div>
    </aside>

    <div class="form-side">
      <div class="form-card">
        <div class="form-header">
          <h2>{{ mode === 'login' ? '账户登录' : '注册新账号' }}</h2>
          <p class="sub">课程设计演示系统 · 种子数据开箱即用</p>
        </div>

        <div class="segment-tabs" role="tablist">
          <button type="button" :class="{ on: mode === 'login' }" @click="mode = 'login'">登录账号</button>
          <button type="button" :class="{ on: mode === 'register' }" @click="mode = 'register'">注册账号</button>
        </div>

        <el-form label-position="top" class="main-form" @submit.prevent="submit">
          <el-form-item label="登录账号 / 手机号">
            <el-input v-model="form.phone" size="large" maxlength="32" placeholder="手机号，或管理员账号 admin" />
          </el-form-item>

          <template v-if="mode === 'login'">
            <div class="sms-toggle">
              <el-checkbox v-model="smsMode" size="default">使用短信验证码登录</el-checkbox>
            </div>

            <el-form-item v-if="!smsMode" label="登录密码">
              <el-input v-model="form.password" type="password" size="large" show-password placeholder="≥8 位，含字母与数字" />
            </el-form-item>

            <el-form-item v-if="!smsMode" label="图形验证码">
              <div class="captcha-row">
                <el-input v-model="form.captcha" size="large" placeholder="计算结果" />
                <button type="button" class="captcha-btn" title="点击刷新" @click="loadCaptcha">
                  <img v-if="captchaImg" :src="captchaImg" alt="验证码" />
                  <span v-else>载入中…</span>
                </button>
              </div>
            </el-form-item>

            <template v-if="smsMode">
              <el-form-item label="短信验证码">
                <div class="captcha-row">
                  <el-input v-model="smsCode" size="large" maxlength="6" placeholder="6 位验证码" />
                  <el-button size="large" :disabled="lockSeconds > 0" class="send-sms-btn" @click="sendCode">
                    {{ lockSeconds > 0 ? `${lockSeconds}s` : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
            </template>
          </template>

          <template v-else>
            <el-form-item label="用户昵称（可选）">
              <el-input v-model="form.nickname" size="large" maxlength="12" placeholder="如何称呼您" />
            </el-form-item>
            <el-form-item label="设置密码">
              <el-input v-model="form.password" type="password" size="large" show-password placeholder="≥8 位，含字母与数字" />
            </el-form-item>
            <el-form-item label="图形验证码">
              <div class="captcha-row">
                <el-input v-model="form.captcha" size="large" placeholder="计算结果" />
                <button type="button" class="captcha-btn" title="点击刷新" @click="loadCaptcha">
                  <img v-if="captchaImg" :src="captchaImg" alt="验证码" />
                  <span v-else>载入中…</span>
                </button>
              </div>
            </el-form-item>
          </template>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            :disabled="lockSeconds > 0"
            @click="submit"
          >
            {{ mode === 'login' ? '立即登录' : '提交注册' }}
          </el-button>

          <el-button
            v-if="mode === 'login'"
            class="demo-login-btn"
            size="large"
            :loading="loading"
            @click="demoLogin"
          >
            🚀 购房者演示账号一键登录
          </el-button>

          <p v-if="lockSeconds > 0" class="lock-hint">账号已锁定，约 {{ lockSeconds }} 秒后可再试</p>
          <div class="login-footer-hint">
            <span>演示账号说明：</span>
            <span>购房者：<code>13800000001</code> / 验证码 <code>123456</code></span>
            <span>管理员：<code>admin</code> / 密码 <code>Test@123</code></span>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
}

.intro {
  padding: 64px 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background-color: var(--hf-canvas);
  background-image: radial-gradient(rgba(21, 94, 117, 0.12) 1px, transparent 1px);
  background-size: 24px 24px;
  border-right: 1px solid var(--hf-border);
}

.intro-wrap {
  max-width: 520px;
}

.intro-title {
  font-size: clamp(34px, 4.2vw, 48px);
  letter-spacing: -0.04em;
  margin: 14px 0 16px;
  line-height: 1.15;
  color: var(--hf-ink);
}

.highlight {
  background: linear-gradient(135deg, var(--hf-primary) 0%, #0284c7 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.intro-desc {
  font-size: 16px;
  color: var(--hf-text-2);
  line-height: 1.7;
  margin-bottom: 24px;
}

.feature-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 32px;
}

.f-pill {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--hf-text-2);
  background: #ffffff;
  padding: 5px 12px;
  border-radius: 9999px;
  border: 1px solid var(--hf-border);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.blueprint-art {
  position: relative;
  max-width: 440px;
  aspect-ratio: 16 / 11;
  border-radius: var(--hf-radius-l);
  overflow: hidden;
  border: 1px solid var(--hf-border);
  box-shadow: var(--hf-shadow-lg);
}

.art-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  font-size: 11px;
  font-weight: 700;
  color: var(--hf-primary);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid var(--hf-border);
}

/* Form Side */
.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
  background: #ffffff;
}

.form-card {
  width: 420px;
  max-width: 100%;
}

.form-header h2 {
  font-size: 28px;
  font-weight: 800;
  color: var(--hf-ink);
  letter-spacing: -0.03em;
  margin: 0 0 6px;
}

.form-header .sub {
  color: var(--hf-text-3);
  font-size: 14px;
  margin: 0 0 24px;
}

.segment-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 4px;
  margin-bottom: 24px;
  background: var(--hf-canvas-subtle);
  border-radius: 9999px;
  border: 1px solid var(--hf-border);
}

.segment-tabs button {
  border: 0;
  background: transparent;
  height: 38px;
  border-radius: 9999px;
  cursor: pointer;
  color: var(--hf-text-2);
  font-size: 14px;
  font-weight: 600;
  transition: all var(--hf-dur) var(--hf-ease);
}

.segment-tabs button.on {
  background: #ffffff;
  color: var(--hf-primary);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
}

.main-form :deep(.el-form-item__label) {
  font-weight: 700;
  color: var(--hf-ink);
  font-size: 13px;
  margin-bottom: 4px;
}

.sms-toggle {
  margin-bottom: 14px;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.captcha-btn {
  border: 1px solid var(--hf-border);
  border-radius: var(--hf-radius-s);
  padding: 0;
  height: 42px;
  width: 120px;
  flex: none;
  background: #ffffff;
  cursor: pointer;
  overflow: hidden;
}

.captcha-btn img {
  display: block;
  width: 120px;
  height: 42px;
}

.send-sms-btn {
  height: 42px !important;
  border-radius: var(--hf-radius-s) !important;
}

.submit-btn {
  width: 100%;
  height: 46px !important;
  border-radius: 9999px !important;
  font-size: 15px !important;
  font-weight: 700 !important;
  margin-top: 8px;
}

.demo-login-btn {
  width: 100%;
  height: 44px !important;
  border-radius: 9999px !important;
  margin-top: 12px;
  margin-left: 0 !important;
  background: var(--hf-canvas-subtle) !important;
  border: 1px dashed var(--hf-primary) !important;
  color: var(--hf-primary) !important;
  font-weight: 700 !important;
}

.demo-login-btn:hover {
  background: var(--hf-primary-soft) !important;
}

.lock-hint {
  color: var(--hf-bad);
  font-size: 13px;
  margin-top: 12px;
  text-align: center;
}

.login-footer-hint {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px dashed var(--hf-border);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--hf-text-3);
}

.login-footer-hint code {
  color: var(--hf-primary);
  background: var(--hf-primary-soft);
  padding: 1px 4px;
  border-radius: 4px;
}

@media (max-width: 900px) {
  .page {
    grid-template-columns: 1fr;
  }
  .intro {
    display: none;
  }
  .form-side {
    padding: 32px 20px;
    background: var(--hf-canvas);
  }
  .form-card {
    background: #ffffff;
    padding: 32px 24px;
    border-radius: var(--hf-radius-l);
    border: 1px solid var(--hf-border);
    box-shadow: var(--hf-shadow-md);
  }
}
</style>
