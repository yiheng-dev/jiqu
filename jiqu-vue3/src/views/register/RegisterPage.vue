<script setup>
import { ref } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores'
import router from '@/router'
import { userGetCode, userLogin } from '@/api/user'

const disabled = ref(false) // 发送短信按钮
const codeBtnMsg = ref('发送验证码') // 发送短信按钮提示
const formRef = ref()
const userStore = useUserStore()

const form = ref({
  phone: '13686869696',
  code: '',
  radio: ''
})

// 修改为异步请求
const sendCode = async () => {
  try {
    await formRef.value.validateField('phone')
    const res = await userGetCode(form.value.phone)
    form.value.code = res.data

    disabled.value = true
    codeBtnMsg.value = '60s后重发'
    let time = 60
    const timer = setInterval(() => {
      time--
      codeBtnMsg.value = time + 's后重发'
      if (time === 0) {
        clearInterval(timer)
        codeBtnMsg.value = '发送验证码'
        disabled.value = false
      }
    }, 1000)
  } catch (error) {
    console.error('验证码发送失败:', error)
    disabled.value = false
    codeBtnMsg.value = '发送验证码'
  }
}

const login = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!form.value.radio) {
    ElMessage.warning('请先阅读并同意《用户服务协议》和《隐私政策》')
    return
  }
  try {
    const res = await userLogin(form.value)
    if (res.success && res.data) {
      userStore.setToken(res.data)
      ElMessage.success('登录成功')
      router.push('/index')
    } else {
      ElMessage.error(res.errorMsg || '登录失败')
    }
  } catch (error) {
    console.error('登录失败:', error)
    ElMessage.error('登录失败：' + (error.message || '未知错误'))
  }
}

const goBack = () => {
  window.history.back()
}

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '手机号格式不正确',
      trigger: 'blur'
    }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-glow a"></div>
    <div class="auth-glow b"></div>

    <div class="auth-card">
      <div class="auth-back" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
      </div>

      <div class="auth-logo">
        <span class="auth-logo-name">集趣</span>
        <span class="auth-logo-sub">潮流社区</span>
      </div>
      <div class="auth-title">手机号码快捷登录</div>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="0" class="auth-form" @submit.prevent>
        <el-form-item prop="phone">
          <div class="phone-row">
            <el-input
              placeholder="请输入手机号"
              v-model="form.phone"
              size="large"
              class="phone-input"
            />
            <el-button
              class="code-btn"
              :disabled="disabled"
              @click="sendCode"
              size="large"
            >{{ codeBtnMsg }}</el-button>
          </div>
        </el-form-item>

        <el-form-item prop="code">
          <el-input placeholder="请输入验证码" v-model="form.code" size="large" />
        </el-form-item>

        <div class="auth-tip">未注册的手机号码验证后自动创建账户</div>

        <button type="button" class="jq-btn auth-submit" @click="login">登 录</button>

        <div class="auth-switch">
          已有账号？
          <router-link to="/login" class="auth-switch-link">密码登录</router-link>
        </div>

        <div class="auth-agree">
          <el-checkbox v-model="form.radio" :true-value="1" :false-value="''" />
          <span class="auth-agree-text">
            我已阅读并同意
            <a href="javascript:void(0)">《集趣·潮流社区用户服务协议》</a>、
            <a href="javascript:void(0)">《隐私政策》</a>
          </span>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle at 20% 20%, #1a1a20, #0f0f12 70%);
  position: relative;
  overflow: hidden;
  padding: 20px;
}
.auth-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
}
.auth-glow.a {
  width: 400px;
  height: 400px;
  background: rgba(255, 77, 0, 0.25);
  top: -100px;
  left: -80px;
}
.auth-glow.b {
  width: 360px;
  height: 360px;
  background: rgba(255, 138, 61, 0.18);
  bottom: -120px;
  right: -60px;
}

.auth-card {
  width: 440px;
  max-width: 100%;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 40px 40px 32px;
  position: relative;
  box-shadow: var(--shadow-lg);
}
.auth-back {
  position: absolute;
  top: 18px;
  left: 18px;
  font-size: 20px;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.2s;
}
.auth-back:hover {
  color: var(--text-primary);
}
.auth-logo {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 20px;
}
.auth-logo-name {
  font-size: 34px;
  font-weight: var(--font-black);
  background: var(--accent-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.auth-logo-sub {
  font-size: 11px;
  letter-spacing: 3px;
  color: var(--text-muted);
}
.auth-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 24px;
}
.auth-form :deep(.el-input__wrapper) {
  background: var(--bg-secondary);
  border-radius: 10px;
}
.phone-row {
  display: flex;
  gap: 12px;
  width: 100%;
}
.phone-input {
  flex: 1;
}
.code-btn {
  flex-shrink: 0;
  background: var(--card-hover);
  color: var(--accent-light);
  border-color: var(--accent);
}
.code-btn:disabled {
  opacity: 0.5;
}
.auth-tip {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 20px;
}
.auth-submit {
  width: 100%;
  height: 46px;
  font-size: 16px;
  letter-spacing: 4px;
}
.auth-switch {
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
  margin-top: 18px;
}
.auth-switch-link {
  color: var(--accent-light);
  font-weight: 600;
}
.auth-agree {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 16px;
}
.auth-agree-text {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.6;
}
</style>
