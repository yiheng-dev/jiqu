<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElTabs, ElTabPane } from 'element-plus'
import { Edit, ChatDotRound, SwitchButton, Location } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import { getUser, getUserBlog, getUserInfo, getFollowCount, userLogout } from '@/api/user'
import {
  indexQueryBlogsOfFollow,
  indexAddLike,
  indexQueryBlogById
} from '@/api/user'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { imgFirst, avatar } from '@/utils/img'

const router = useRouter()
const userStore = useUserStore()

// 数据定义
const user = ref({})
const info = ref({})
const blogs = ref([])
const blogs2 = ref([]) // 关注的人的博客
const followCount = ref(0)
const fansCount = ref(0)
const activeName = ref('1')
const params = reactive({
  minTime: 0, // 上一次拉取到的时间戳
  offset: 0 // 偏移量
})

// 生命周期钩子
onMounted(() => {
  queryUser()
})

// 方法定义
const queryUser = () => {
  getUser()
    .then(({ data }) => {
      user.value = data
      queryUserInfo()
      queryBlogs()
      queryFollowCount()
    })
    .catch(() => {
      ElMessage.error('获取用户信息失败')
      router.push('/login')
    })
}
const queryBlogs = () => {
  getUserBlog()
    .then(({ data }) => {
      blogs.value = (data || []).map((b) => ({
        ...b,
        img: imgFirst(b.images, `blog-${b.id}`, 400, 300),
        userAvatar: avatar(b.userId, 96)
      }))
    })
    .catch(() => {
      ElMessage.error('获取用户博客失败')
    })
}
const queryFollowCount = () => {
  getFollowCount()
    .then(({ data }) => {
      if (!data) return
      followCount.value = data.follow || 0
      fansCount.value = data.fans || 0
    })
    .catch(() => {
      ElMessage.error('获取关注数失败')
    })
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

const queryBlogsOfFollow = (clear) => {
  if (clear) {
    params.offset = 0
    params.minTime = new Date().getTime() + 1
  }
  const { offset: os } = params

  indexQueryBlogsOfFollow(params.minTime, os)
    .then(({ data }) => {
      if (!data) return
      const { list, ...newParams } = data
      list.forEach((b) => (b.img = imgFirst(b.images, `blog-${b.id}`, 600, 400)))
      list.forEach((b) => (b.userAvatar = avatar(b.userId, 96)))
      blogs2.value = clear ? list : blogs2.value.concat(list)
      Object.assign(params, newParams)
    })
    .catch(() => {
      ElMessage.error('获取关注博客失败')
    })
}

const goBack = () => {
  router.back()
}

const toEdit = () => {
  router.push('/InfoEdit')
}

const logout = async () => {
  await userLogout().catch(() => {})
  userStore.clearToken()
  userStore.resetUserInfo()
  router.push('/login')
}

const handleClick = (tab) => {
  if (tab.props.name === '4') {
    queryBlogsOfFollow(true)
  }
}

const addLike = (b) => {
  indexAddLike(b.id)
    .then(() => {
      queryBlogById(b)
    })
    .catch(() => {
      ElMessage.error('点赞失败')
      b.liked++
    })
}

const queryBlogById = (b) => {
  indexQueryBlogById(b.id)
    .then(({ data }) => {
      b.liked = data.liked
      b.isLike = data.isLike
    })
    .catch(() => {
      ElMessage.error('获取博客详情失败')
      b.liked++
    })
}

// 关注 tab 滚动加载（仅当切到关注 tab 时有效）
useInfiniteScroll(() => {
  if (activeName.value === '4') {
    queryBlogsOfFollow()
  }
})
</script>

<template>
  <div class="info-html">
    <!-- 资料卡 -->
    <section class="profile-card">
      <div class="profile-top">
        <img class="profile-avatar" :src="avatar(user.id || 'me', 200)" alt="用户头像" />
        <div class="profile-main">
          <div class="profile-name">{{ user.nickName }}</div>
          <div class="profile-meta">
            <span v-if="info.city"><el-icon><Location /></el-icon> {{ info.city }}</span>
            <span v-else><el-icon><Location /></el-icon> 未知城市</span>
          </div>
        </div>
        <div class="profile-actions">
          <button class="jq-btn jq-btn-outline" @click="toEdit"><el-icon><Edit /></el-icon> 编辑资料</button>
          <button class="jq-btn logout-btn" @click="logout"><el-icon><SwitchButton /></el-icon> 退出登录</button>
        </div>
      </div>

      <div class="profile-intro">
        <span v-if="info.introduce">{{ info.introduce }}</span>
        <span v-else>添加个人简介，让大家更好的认识你 <el-icon><Edit /></el-icon></span>
      </div>

      <div class="profile-stats">
        <div class="stat"><b>{{ blogs.length }}</b><span>笔记</span></div>
        <div class="stat"><b>{{ fansCount }}</b><span>粉丝</span></div>
        <div class="stat"><b>{{ followCount }}</b><span>关注</span></div>
      </div>
    </section>

    <!-- Tab 内容 -->
    <section class="content-card">
      <el-tabs v-model="activeName" @tab-click="handleClick">
        <el-tab-pane label="笔记" name="1">
          <div class="note-grid">
            <div class="note-card" v-for="b in blogs" :key="b.id" @click="router.push(`/blogDetail/${b.id}`)">
              <div class="note-img">
                <img :src="b.img" alt="博客图片" />
              </div>
              <div class="note-body">
                <div class="note-title">{{ b.title }}</div>
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

        <el-tab-pane label="晒单" name="2">
          <div class="tab-empty">暂无晒单</div>
        </el-tab-pane>

        <el-tab-pane :label="`粉丝(${fansCount})`" name="3">
          <div class="tab-empty">暂无粉丝</div>
        </el-tab-pane>

        <el-tab-pane :label="`关注(${followCount})`" name="4">
          <div class="follow-list">
            <div class="follow-card" v-for="b in blogs2" :key="b.id">
              <div class="follow-img" @click="router.push(`/blogDetail/${b.id}`)">
                <img :src="b.img" alt="博客图片" />
              </div>
              <div class="follow-title">{{ b.title }}</div>
              <div class="follow-foot">
                <div class="follow-user">
                  <img :src="b.userAvatar" alt="" class="follow-avatar" />
                  <span>{{ b.name }}</span>
                </div>
                <div class="follow-like" @click.stop="addLike(b)">
                  <svg viewBox="0 0 1024 1024" width="13" height="13" :fill="b.isLike ? '#ff4d00' : '#71717a'"><path d="M505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z"/></svg>
                  {{ b.liked }}
                </div>
              </div>
            </div>
          </div>
          <div v-if="!blogs2.length" class="tab-empty">还没有关注的动态</div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.info-html {
  padding: 24px 0 48px;
}

/* 资料卡 */
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
  width: 88px;
  height: 88px;
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
.profile-actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}
.logout-btn {
  background: transparent;
  border: 1px solid var(--danger);
  color: var(--danger);
  box-shadow: none;
}
.logout-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  filter: none;
}
.profile-intro {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.profile-stats {
  display: flex;
  gap: 40px;
  margin-top: 20px;
}
.stat {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.stat b {
  font-size: 22px;
  font-weight: var(--font-black);
  color: var(--accent);
}
.stat span {
  font-size: 13px;
  color: var(--text-muted);
}

/* Tab 内容 */
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
  height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 8px;
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

.follow-list {
  padding-top: 8px;
}
.follow-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-bottom: 16px;
  background: var(--bg-secondary);
}
.follow-img {
  width: 100%;
  height: 220px;
  overflow: hidden;
  cursor: pointer;
}
.follow-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.follow-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  padding: 12px 16px;
}
.follow-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 14px;
}
.follow-user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}
.follow-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  object-fit: cover;
}
.follow-like {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}
.follow-like:hover {
  color: var(--accent);
}
.tab-empty {
  text-align: center;
  padding: 60px 0;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .profile-actions {
    flex-direction: column;
  }
  .profile-top {
    flex-wrap: wrap;
  }
  .note-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
