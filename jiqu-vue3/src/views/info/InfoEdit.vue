<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { getUser, getUserInfo } from '@/api/user'
import { avatar } from '@/utils/img'

const router = useRouter()
const userStore = useUserStore()

// 数据定义
const user = ref({})
const info = ref({})

// 生命周期钩子
onMounted(() => {
  checkLogin()
})

// 方法定义
const checkLogin = () => {
  getUser()
    .then(({ data }) => {
      user.value = data
      getUserInfo(data.id)
        .then(({ data: userInfo }) => {
          if (userInfo) {
            info.value = userInfo
            userStore.setUserInfo(userInfo)
          }
        })
        .catch(() => {
          ElMessage.error('获取用户详情失败')
        })
    })
    .catch(() => {
      ElMessage.error('获取用户信息失败')
      router.push('/login')
    })
}

const goBack = () => {
  router.back()
}

const genderText = (g) => {
  const map = { 0: '男', 1: '女' }
  return map[g] ?? '选择'
}
</script>

<template>
  <div class="info-edit">
    <div class="crumb">
      <span class="crumb-back" @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</span>
      <span class="crumb-sep">/</span>
      <span class="crumb-cur">资料编辑</span>
    </div>

    <div class="edit-panel">
      <div class="panel-head">
        <span class="head-avatar">
          <img :src="avatar(user.id || 'me', 160)" alt="用户头像" />
        </span>
        <div>
          <div class="head-name">{{ user.nickName }}</div>
          <div class="head-tip">账号资料 · 只读展示</div>
        </div>
      </div>

      <div class="info-box">
        <div class="info-item">
          <span class="info-label">头像</span>
          <span class="info-value"><img class="mini-avatar" :src="avatar(user.id || 'me', 96)" alt="" /></span>
        </div>
        <div class="info-item">
          <span class="info-label">昵称</span>
          <span class="info-value">{{ user.nickName }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">个人介绍</span>
          <span class="info-value">{{ info.introduce || '介绍一下自己' }}</span>
        </div>
      </div>

      <div class="info-box">
        <div class="info-item">
          <span class="info-label">性别</span>
          <span class="info-value">{{ genderText(info.gender) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">城市</span>
          <span class="info-value">{{ info.city || '选择' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">生日</span>
          <span class="info-value">{{ info.birthday || '添加' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.info-edit {
  padding: 16px 0 48px;
}
.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-muted);
  margin-bottom: 16px;
}
.crumb-back {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 6px 12px;
  border-radius: var(--radius-full);
  transition: all 0.2s;
}
.crumb-back:hover {
  color: var(--accent-light);
  background: rgba(255, 77, 0, 0.1);
}
.crumb-sep {
  color: var(--border);
}

.edit-panel {
  max-width: 640px;
  margin: 0 auto;
}
.panel-head {
  display: flex;
  align-items: center;
  gap: 18px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 24px;
  margin-bottom: 20px;
}
.head-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid var(--border);
  flex-shrink: 0;
}
.head-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.head-name {
  font-size: 20px;
  font-weight: var(--font-black);
  color: var(--text-primary);
}
.head-tip {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.info-box {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-bottom: 20px;
}
.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
}
.info-item + .info-item {
  border-top: 1px solid var(--border);
}
.info-label {
  font-size: 15px;
  color: var(--text-secondary);
}
.info-value {
  font-size: 15px;
  color: var(--text-primary);
}
.mini-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}
</style>
