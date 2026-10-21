<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, User, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { userLogout } from '@/api/user'
import { avatar } from '@/utils/img'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const keyword = ref('')
const userMenuVisible = ref(false)

const navs = [
  { label: '首页', path: '/index' },
  { label: '发售日历', path: '/dropList' },
  { label: '潮流店铺', path: '/storeList' },
  { label: 'AI 助手', path: '/aiAssistant' }
]

const isActive = (p) => {
  if (p === '/index') return route.path === '/index' || route.path === '/'
  if (p === '/storeList') return route.path.startsWith('/store')
  if (p === '/dropList') return route.path.startsWith('/drop')
  if (p === '/aiAssistant') return route.path.startsWith('/ai')
  return route.path === p
}

const isLogin = computed(() => !!userStore.token)
const nickName = computed(() => userStore.userInfo?.nickName || '用户')

const onSearch = () => {
  const kw = keyword.value.trim()
  if (kw) {
    router.push({ path: '/storeList', query: { name: kw } })
    keyword.value = ''
  }
}

const goUserHome = () => router.push('/InfoHtml')
const logout = async () => {
  await userLogout().catch(() => {})
  userStore.clearToken()
  userStore.resetUserInfo()
  userMenuVisible.value = false
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <div class="app-header-inner">
      <!-- Logo -->
      <div class="logo" @click="router.push('/index')">
        <span class="logo-name">集趣</span>
        <span class="logo-sub">潮流社区</span>
      </div>

      <!-- 导航 -->
      <nav class="nav">
        <router-link
          v-for="n in navs"
          :key="n.path"
          :to="n.path"
          class="nav-item"
          :class="{ active: isActive(n.path) }"
        >{{ n.label }}</router-link>
      </nav>

      <!-- 右侧 -->
      <div class="header-right">
        <div class="search-box">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
            v-model="keyword"
            class="search-input"
            placeholder="搜索潮流店铺…"
            @keyup.enter="onSearch"
          />
        </div>

        <template v-if="isLogin">
          <el-dropdown trigger="click" @visible-change="(v) => (userMenuVisible = v)">
            <div class="user-entry">
              <img class="user-avatar" :src="avatar(userStore.userInfo?.id || 'me', 96)" alt="" />
              <span class="user-name">{{ nickName }}</span>
              <el-icon :class="{ 'arrow-open': userMenuVisible }" class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goUserHome">我的主页</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <button class="jq-btn login-btn" @click="router.push('/login')">
            <el-icon><User /></el-icon>
            登录
          </button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: var(--z-navbar);
  background: rgba(15, 15, 18, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border);
}

.app-header-inner {
  max-width: var(--container-max);
  margin: 0 auto;
  height: var(--navbar-height);
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 0 20px;
}

.logo {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
  cursor: pointer;
  flex-shrink: 0;
}
.logo-name {
  font-size: 24px;
  font-weight: var(--font-black);
  letter-spacing: 1px;
  background: var(--accent-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.logo-sub {
  font-size: 10px;
  letter-spacing: 4px;
  color: var(--text-muted);
}

.nav {
  display: flex;
  gap: 8px;
  flex: 1;
}
.nav-item {
  padding: 8px 16px;
  border-radius: var(--radius-full);
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 500;
  transition: all 0.2s;
}
.nav-item:hover {
  color: var(--text-primary);
  background: var(--card);
}
.nav-item.active {
  color: var(--accent-light);
  background: rgba(255, 77, 0, 0.12);
  font-weight: 700;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-full);
  padding: 0 14px;
  height: 38px;
  width: 240px;
  transition: border-color 0.2s;
}
.search-box:focus-within {
  border-color: var(--accent);
}
.search-icon {
  color: var(--text-muted);
  font-size: 16px;
}
.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
}
.search-input::placeholder {
  color: var(--text-muted);
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-full);
  transition: background 0.2s;
}
.user-entry:hover {
  background: var(--card);
}
.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--border);
}
.user-name {
  font-size: 14px;
  color: var(--text-primary);
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-arrow {
  color: var(--text-muted);
  font-size: 12px;
  transition: transform 0.2s;
}
.arrow-open {
  transform: rotate(180deg);
}

.login-btn {
  height: 38px;
  padding: 0 22px;
  font-size: 14px;
}

@media (max-width: 768px) {
  .nav {
    display: none;
  }
  .search-box {
    width: 130px;
  }
  .app-header-inner {
    gap: 12px;
    padding: 0 12px;
  }
}
</style>
