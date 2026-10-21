<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, ChatDotRound, Location } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { getUser, getUserInfo } from '@/api/user'
import {
  getBlogsOfUser,
  isFollowed,
  follow,
  getCommonFollows
} from '@/api/follow'
import { imgFirst, avatar } from '@/utils/img'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 数据定义
const user = ref({})
const loginUser = ref({})
const activeName = ref('1')
const info = ref({})
const blogs = ref([])
const followed = ref(false) // 是否关注了
const commonFollows = ref([]) // 共同关注

// 统一取用户 id：param 优先，兼容旧 query
const targetId = () => route.params.id || route.query.id

// 生命周期钩子
onMounted(() => {
  queryUser()
  queryLoginUser()
})

// 方法定义
const queryBlogs = () => {
  getBlogsOfUser(user.value.id, 1)
    .then(({ data }) => {
      blogs.value = (data || []).map((b) => ({
        ...b,
        img: imgFirst(b.images, `blog-${b.id}`, 400, 300)
      }))
    })
    .catch(() => {
      ElMessage.error('获取用户笔记失败')
    })
}

const queryLoginUser = () => {
  getUser()
    .then(({ data }) => {
      loginUser.value = data
    })
    .catch(() => {
      ElMessage.error('获取登录用户信息失败')
    })
}

const queryUser = () => {
  const id = targetId()
  getUser(id)
    .then(({ data }) => {
      user.value = data
      queryUserInfo()
      queryBlogs()
      isFollowed(id)
        .then(({ data }) => {
          followed.value = data
        })
        .catch(() => {
          ElMessage.error('获取关注状态失败')
        })
    })
    .catch(() => {
      ElMessage.error('获取用户信息失败')
    })
}

const goBack = () => {
  router.back()
}

const queryUserInfo = () => {
  getUserInfo(user.value.id)
    .then(({ data }) => {
      if (!data) return
      info.value = data
      userStore.setUserInfo(data)
    })
    .catch(() => {
      ElMessage.error('获取用户详情失败')
    })
}

const queryCommonFollow = () => {
  getCommonFollows(user.value.id)
    .then(({ data }) => {
      commonFollows.value = (data || []).map((u) => ({
        ...u,
        avatarSrc: avatar(u.id, 96)
      }))
    })
    .catch(() => {
      ElMessage.error('获取共同关注失败')
    })
}

const handleFollow = () => {
  follow(user.value.id, !followed.value)
    .then(() => {
      ElMessage.success(followed.value ? '已取消关注' : '已关注')
      followed.value = !followed.value
    })
    .catch(() => {
      ElMessage.error('操作失败')
    })
}

const handleClick = (tab) => {
  if (tab.props.name === '2') {
    queryCommonFollow()
  }
}

const toOtherInfo = (id) => {
  router.push(`/InfoOther/${id}`)
}
</script>

<template>
  <div class="info-other">
    <div class="crumb">
      <span class="crumb-back" @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</span>
      <span class="crumb-sep">/</span>
      <span class="crumb-cur">TA 的主页</span>
    </div>

    <!-- 资料卡 -->
    <section class="profile-card">
      <div class="profile-top">
        <img class="profile-avatar" :src="avatar(user.id || 'me', 200)" alt="" />
        <div class="profile-main">
          <div class="profile-name">{{ user.nickName }}</div>
          <div class="profile-meta">
            <span v-if="info.city"><el-icon><Location /></el-icon> {{ info.city }}</span>
            <span v-else><el-icon><Location /></el-icon> 未知城市</span>
          </div>
        </div>
        <button
          class="jq-btn follow-btn"
          :class="{ 'is-followed': followed }"
          @click="handleFollow"
        >{{ followed ? '已关注' : '关注 TA' }}</button>
      </div>
      <div class="profile-intro">
        <span v-if="info.introduce">{{ info.introduce }}</span>
        <span v-else>这个人很懒，什么都没有留下</span>
      </div>
    </section>

    <!-- Tab 内容 -->
    <section class="content-card">
      <el-tabs v-model="activeName" @tab-click="handleClick">
        <el-tab-pane label="笔记" name="1">
          <div class="note-grid">
            <div class="note-card" v-for="b in blogs" :key="b.id" @click="router.push(`/blogDetail/${b.id}`)">
              <div class="note-img"><img :src="b.img" alt="" /></div>
              <div class="note-body">
                <div class="note-title" v-html="b.title"></div>
                <div class="note-meta">
                  <span class="note-like">
                    <svg viewBox="0 0 1024 1024" width="13" height="13" fill="#ff4d00"><path d="M505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z"/></svg>
                    {{ b.liked }}
                  </span>
                  <span class="note-comment"><el-icon><ChatDotRound /></el-icon> {{ b.comments }}</span>
                </div>
              </div>
            </div>
          </div>
          <div v-if="!blogs.length" class="tab-empty">还没有发布笔记</div>
        </el-tab-pane>

        <el-tab-pane label="共同关注" name="2">
          <div class="common-tip">你们都关注了：</div>
          <div class="common-list">
            <div class="common-item" v-for="u in commonFollows" :key="u.id">
              <img class="common-avatar" :src="u.avatarSrc" alt="" />
              <span class="common-name">{{ u.nickName }}</span>
              <button class="jq-btn jq-btn-outline common-btn" @click="toOtherInfo(u.id)">去主页</button>
            </div>
            <div v-if="!commonFollows.length" class="tab-empty">暂无共同关注</div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.info-other {
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

.profile-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 28px;
  margin-bottom: 24px;
}
.profile-top {
  display: flex;
  align-items: center;
  gap: 20px;
}
.profile-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid var(--border);
  flex-shrink: 0;
}
.profile-main {
  flex: 1;
}
.profile-name {
  font-size: 22px;
  font-weight: var(--font-black);
  color: var(--text-primary);
}
.profile-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 14px;
  margin-top: 6px;
}
.follow-btn {
  height: 40px;
  padding: 0 28px;
  font-size: 14px;
}
.follow-btn.is-followed {
  background: transparent;
  border: 1px solid var(--border);
  color: var(--text-secondary);
  box-shadow: none;
}
.profile-intro {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 14px;
}

.content-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 8px 24px 24px;
}
.note-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  padding-top: 8px;
}
.note-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s;
  background: var(--bg-secondary);
}
.note-card:hover {
  transform: translateY(-4px);
  border-color: var(--accent);
  box-shadow: var(--shadow-md);
}
.note-img {
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
}
.note-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.note-body {
  padding: 12px 14px;
}
.note-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  height: 42px;
  overflow: hidden;
  margin-bottom: 8px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.note-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--text-muted);
  font-size: 12px;
}
.note-like,
.note-comment {
  display: flex;
  align-items: center;
  gap: 4px;
}

.common-tip {
  font-size: 13px;
  color: var(--text-muted);
  padding: 12px 0 8px;
}
.common-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 8px;
  border-radius: var(--radius-md);
  transition: background 0.2s;
}
.common-item:hover {
  background: var(--card-hover);
}
.common-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
}
.common-name {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.common-btn {
  height: 32px;
  padding: 0 18px;
  font-size: 12px;
}
.tab-empty {
  text-align: center;
  padding: 60px 0;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .note-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
