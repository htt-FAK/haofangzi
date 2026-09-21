<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { post } from '../api/http'

/**
 * 登录 / 注册（spec 001 US-01、AC-01/02/06）。
 * 令牌存 localStorage 只是课程演示口径（真实项目应使用 httpOnly Cookie，报告 §7 待确认项）。
 */
const route = useRoute()
const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const form = reactive({ phone: '', password: '', captchaId: '', captcha: '', nickname: '' })
const smsMode = ref(false)
const smsCode = ref('')
const lockSeconds = ref(0)

async function submit() {
  loading.value = true
  try {
    if (mode.value === 'register') {
      await post('/auth/register', { ...form })
      ElMessage.success('注册成功，请登录')
      mode.value = 'login'
      return
    }
    const body = smsMode.value
      ? { phone: form.phone, smsCode: smsCode.value }
      : { phone: form.phone, password: form.password, captchaId: form.captchaId, captcha: form.captcha }
    const data = await post<{ token: string; user: { nickname: string; profileCompleteness: number } }>('/auth/login', body)
    localStorage.setItem('hf-token', data.token)
    localStorage.setItem('hf-user', JSON.stringify(data.user))
    if (data.user.profileCompleteness < 60) ElMessage.warning('画像完整度不足 60%，建议先完善（AI 顾问将不可用）')
    router.replace(String(route.query.redirect || '/'))
  } catch (e: any) {
    if (e?.code === 40305) lockSeconds.value = e?.data?.lockSeconds ?? e?.detail?.lockSeconds ?? 900
  } finally {
    loading.value = false
  }
}

async function sendCode() {
  await post('/auth/code', { phone: form.phone })
  ElMessage.success('验证码已发送（演示环境固定 123456）')
}
</script>

<template>
  <div class="page">
    <el-card class="box" shadow="never">
      <h2>肇庆好房子 · 在线选房与户型智能评估</h2>
      <p class="sub">课程设计演示系统，数据为模拟样例</p>

      <el-radio-group v-model="mode" size="small" class="seg">
        <el-radio-button value="login">登录</el-radio-button>
        <el-radio-button value="register">注册</el-radio-button>
      </el-radio-group>

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="手机号">
          <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号" />
        </el-form-item>

        <template v-if="mode === 'login'">
          <el-checkbox v-model="smsMode" size="small">使用短信验证码登录</el-checkbox>
          <el-form-item v-if="!smsMode" label="口令">
            <el-input v-model="form.password" type="password" show-password placeholder="≥8 位，含字母与数字" />
          </el-form-item>
          <el-form-item v-if="!smsMode" label="图形验证码">
            <div class="cap">
              <el-input v-model="form.captcha" style="width: 120px" placeholder="结果" />
              <el-button size="small" @click="() => {}">看图作答（T-004）</el-button>
            </div>
          </el-form-item>
          <template v-if="smsMode">
            <el-form-item label="短信验证码">
              <div class="cap">
                <el-input v-model="smsCode" style="width: 140px" maxlength="6" />
                <el-button size="small" :disabled="lockSeconds > 0" @click="sendCode">
                  {{ lockSeconds > 0 ? `${lockSeconds}s` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
          </template>
        </template>

        <el-form-item v-else label="昵称（可选）">
          <el-input v-model="form.nickname" maxlength="8" />
        </el-form-item>
        <el-form-item v-else label="口令">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>

        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">
          {{ mode === 'login' ? '登录' : '注册' }}
        </el-button>
        <p class="hint">演示账号：13800000001 / Test@123　管理员：admin / Test@123</p>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f6f8fb; }
.box { width: 380px; }
h2 { font-size: 18px; margin: 0 0 4px; }
.sub { color: #888; font-size: 12px; margin: 0 0 12px; }
.seg { margin-bottom: 12px; }
.cap { display: flex; gap: 8px; align-items: center; }
.hint { color: #999; font-size: 12px; margin: 10px 0 0; }
</style>
