<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElRate } from 'element-plus'
import { ArrowLeft, ArrowRight, ChatSquare, Location } from '@element-plus/icons-vue'
import {
  getBlogById,
  getStoreById,
  getBlogLikes,
  likeBlog,
  isFollowed,
  follow
} from '@/api/blog/blog'
import { getUser } from '@/api/user'
import { formatTime } from '@/utils/format'
import { imgs, imgFirst, avatar } from '@/utils/img'

const route = useRoute()
const router = useRouter()

// 数据定义
const blog = ref({})
const store = ref({})
const likes = ref([])
const user = ref({})
const followed = ref(false)
const score = ref(4.5)
// 轮播图相关
const swiper = ref(null)
const _width = ref(0)
const items = ref([])
const active = ref(0)
const start = ref({ x: 0, y: 0 })
const move = ref({ x: 0, y: 0 })
const sensitivity = ref(60)
const resistance = ref(0.3)
const isMoving = ref(false)

// 初始化数据
const initData = async () => {
  const id = route.params.id
  try {
    const blogData = await getBlogById(id)
    if (blogData && blogData.data) {
      const raw = blogData.data
      blog.value = {
        ...raw,
        images: imgs(raw.images, `blog-${raw.id}`, 1200, 700),
        authorAvatar: avatar(raw.userId, 96),
        storeImage: imgFirst(raw.storeImage, `store-${raw.storeId}`, 200, 200)
      }
    }

    await nextTick()
    initSwiper()

    await Promise.all([
      queryStoreById(blogData.data.storeId),
      queryLikeList(id),
      queryLoginUser()
    ])
  } catch (error) {
    console.error('获取博客详情失败', error)
    ElMessage.error('获取博客详情失败')
  }
}
onMounted(() => {
  initData()
})

// 查询潮流店铺信息
const queryStoreById = async (storeId) => {
  try {
    const { data } = await getStoreById(storeId)
    if (data) {
      store.value = { ...data, image: imgFirst(data.images, `store-${data.id}`, 200, 200) }
    }
  } catch (error) {
    console.error('获取潮流店铺信息失败', error)
    ElMessage.error('获取潮流店铺信息失败')
  }
}

// 查询点赞列表
const queryLikeList = async (id) => {
  try {
    const { data } = await getBlogLikes(id)
    likes.value = (data || []).map((u) => ({ ...u, icon: avatar(u.id, 96) }))
  } catch (error) {
    console.error('获取点赞列表失败', error)
    ElMessage.error('获取点赞列表失败')
  }
}

// 点赞功能
const addLike = async () => {
  try {
    await likeBlog(blog.value.id)
    const { data } = await getBlogById(blog.value.id)
    blog.value = { ...data, images: imgs(data.images, `blog-${data.id}`, 1200, 700) }
    await queryLikeList(blog.value.id)
  } catch (error) {
    console.error('点赞失败', error)
    ElMessage.error('点赞失败')
  }
}

// 查询登录用户
const queryLoginUser = async () => {
  try {
    const { data } = await getUser()
    user.value = data
    if (user.value.id !== blog.value.userId) {
      await checkFollowed()
    }
  } catch (error) {
    console.error('获取用户信息失败', error)
  }
}

// 检查是否关注
const checkFollowed = async () => {
  try {
    const { data } = await isFollowed(blog.value.userId)
    followed.value = data
  } catch (error) {
    console.error('获取关注状态失败', error)
    ElMessage.error('获取关注状态失败')
  }
}

// 关注/取消关注
const handleFollow = async () => {
  try {
    await follow(blog.value.userId, !followed.value)
    ElMessage.success(followed.value ? '已取消关注' : '已关注')
    followed.value = !followed.value
  } catch (error) {
    console.error('关注/取消关注失败', error)
    ElMessage.error('操作失败')
  }
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 跳转到用户信息页
const toOtherInfo = () => {
  if (blog.value.userId === user.value.id) {
    router.push('/InfoHtml')
  } else {
    router.push(`/InfoOther/${blog.value.userId}`)
  }
}

// 轮播图相关方法（保留原有触摸手势逻辑）
const initSwiper = () => {
  const container = swiper.value
  items.value = container.querySelectorAll('.swiper-item')
  updateItemWidth()
  setTransform()
  setTransition('none')
}

const updateItemWidth = () => {
  _width.value = swiper.value.offsetWidth || document.documentElement.offsetWidth
}

const setTransform = (offset = 0) => {
  items.value.forEach((item, i) => {
    const distance = (i - active.value) * _width.value + offset
    const transform = `translate3d(${distance}px, 0, 0)`
    item.style.webkitTransform = transform
    item.style.transform = transform
  })
}

const setTransition = (duration = 300) => {
  const transition = typeof duration === 'number' ? `${duration}ms` : duration
  items.value.forEach((item) => {
    item.style.webkitTransition = transition
    item.style.transition = transition
  })
}

const moveStart = (e) => {
  start.value.x = e.touches[0].pageX
  start.value.y = e.touches[0].pageY
  setTransition('none')
}

const moving = (e) => {
  e.preventDefault()
  e.stopPropagation()
  let distanceX = e.touches[0].pageX - start.value.x
  let distanceY = e.touches[0].pageY - start.value.y

  if (Math.abs(distanceX) > Math.abs(distanceY)) {
    isMoving.value = true
    move.value.x = start.value.x + distanceX
    move.value.y = start.value.y + distanceY

    if (
      (active.value === 0 && distanceX > 0) ||
      (active.value === items.value.length - 1 && distanceX < 0)
    ) {
      distanceX = distanceX * resistance.value
    }
    setTransform(distanceX)
  }
}

const moveEnd = (e) => {
  if (isMoving.value) {
    e.preventDefault()
    e.stopPropagation()
    const distance = move.value.x - start.value.x

    if (Math.abs(distance) > sensitivity.value) {
      if (distance < 0) {
        next()
      } else {
        prev()
      }
    } else {
      back()
    }
    reset()
    isMoving.value = false
  }
}

const next = () => {
  go(active.value + 1)
}

const prev = () => {
  go(active.value - 1)
}

const reset = () => {
  start.value.x = 0
  start.value.y = 0
  move.value.x = 0
  move.value.y = 0
}

const back = () => {
  setTransition()
  setTransform()
}

const go = (index) => {
  active.value = index
  if (active.value < 0) {
    active.value = 0
  } else if (active.value > items.value.length - 1) {
    active.value = items.value.length - 1
  }
  setTransition()
  setTransform()
}
</script>

<template>
  <div class="blog-detail">
    <div class="crumb">
      <span class="crumb-back" @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</span>
      <span class="crumb-sep">/</span>
      <span class="crumb-cur">晒单详情</span>
    </div>

    <!-- 轮播图 -->
    <div class="swiper-wrap">
      <div
        class="blog-info-box"
        ref="swiper"
        @touchstart="moveStart"
        @touchmove="moving"
        @touchend="moveEnd"
      >
        <div class="swiper-item" v-for="(img, i) in blog.images" :key="i">
          <img :src="img" alt="" />
        </div>
      </div>
      <button class="swiper-arrow left" @click="prev"><el-icon><ArrowLeft /></el-icon></button>
      <button class="swiper-arrow right" @click="next"><el-icon><ArrowRight /></el-icon></button>
      <div class="swiper-dots" v-if="blog.images?.length > 1">
        <span
          v-for="(_, i) in blog.images"
          :key="i"
          class="dot"
          :class="{ active: i === active }"
          @click="go(i)"
        ></span>
      </div>
    </div>

    <!-- 作者 -->
    <div class="basic">
      <div class="basic-icon" @click="toOtherInfo">
        <img :src="blog.authorAvatar" alt="" />
      </div>
      <div class="basic-info">
        <div class="name">{{ blog.name }}</div>
        <span class="time">{{ formatTime(new Date(blog.createTime)) }}</span>
      </div>
      <div class="follow-wrap" v-show="!user || user.id !== blog.userId">
        <button
          class="jq-btn follow-btn"
          :class="{ 'is-followed': followed }"
          @click="handleFollow"
        >{{ followed ? '取消关注' : '关注' }}</button>
      </div>
    </div>

    <!-- 正文 -->
    <div class="blog-text" v-html="blog.content"></div>

    <!-- 关联店铺 -->
    <div class="store-basic" v-if="store.name">
      <div class="store-icon">
        <img :src="store.image" alt="" />
      </div>
      <div class="store-info">
        <div class="store-name">{{ store.name }}</div>
        <div class="store-meta">
          <el-rate :model-value="Number(store.score) / 10" disabled></el-rate>
          <span class="store-avg">￥{{ store.avgPrice }}/人</span>
        </div>
        <div class="store-addr"><el-icon><Location /></el-icon> {{ store.address }}</div>
      </div>
    </div>

    <!-- 点赞 -->
    <div class="zan-box">
      <div class="zan-count">
        <svg
          viewBox="0 0 1024 1024"
          width="20"
          height="20"
          :fill="blog.isLike ? '#ff4d00' : '#71717a'"
        >
          <path
            d="M160 944c0 8.8-7.2 16-16 16h-32c-26.5 0-48-21.5-48-48V528c0-26.5 21.5-48 48-48h32c8.8 0 16 7.2 16 16v448zM96 416c-53 0-96 43-96 96v416c0 53 43 96 96 96h96c17.7 0 32-14.3 32-32V448c0-17.7-14.3-32-32-32H96zM505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z"
          ></path>
        </svg>
        <span>{{ blog.liked }}人点赞</span>
      </div>
      <div class="zan-list">
        <div class="user-icon-mini" v-for="u in likes" :key="u.id">
          <img :src="u.icon" alt="" />
        </div>
      </div>
    </div>

    <!-- 操作栏 -->
    <div class="foot">
      <button class="jq-btn like-btn" :class="{ 'is-liked': blog.isLike }" @click="addLike">
        <svg viewBox="0 0 1024 1024" width="16" height="16" :fill="blog.isLike ? '#fff' : 'currentColor'">
          <path
            d="M160 944c0 8.8-7.2 16-16 16h-32c-26.5 0-48-21.5-48-48V528c0-26.5 21.5-48 48-48h32c8.8 0 16 7.2 16 16v448zM96 416c-53 0-96 43-96 96v416c0 53 43 96 96 96h96c17.7 0 32-14.3 32-32V448c0-17.7-14.3-32-32-32H96zM505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z"
          ></path>
        </svg>
        {{ blog.isLike ? '已点赞' : '点赞' }} · {{ blog.liked }}
      </button>
      <button class="jq-btn jq-btn-outline comment-btn" disabled>
        <el-icon><ChatSquare /></el-icon>
        评论
      </button>
    </div>
  </div>
</template>

<style scoped>
.blog-detail {
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

/* 轮播 */
.swiper-wrap {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--border);
  margin-bottom: 16px;
}
.blog-info-box {
  position: relative;
  width: 100%;
  height: min(520px, 60vh);
  overflow: hidden;
}
.swiper-item {
  position: absolute;
  width: 100%;
  height: 100%;
  transition: transform 0.3s;
}
.swiper-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.swiper-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: rgba(15, 15, 18, 0.6);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  backdrop-filter: blur(6px);
  transition: all 0.2s;
}
.swiper-arrow:hover {
  background: var(--accent);
  border-color: transparent;
}
.swiper-arrow.left {
  left: 16px;
}
.swiper-arrow.right {
  right: 16px;
}
.swiper-dots {
  position: absolute;
  bottom: 14px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
}
.swiper-dots .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  transition: all 0.2s;
}
.swiper-dots .dot.active {
  width: 22px;
  border-radius: 4px;
  background: var(--accent);
}

/* 作者 */
.basic {
  display: flex;
  align-items: center;
  padding: 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}
.basic-icon {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 12px;
  cursor: pointer;
  flex-shrink: 0;
}
.basic-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.basic-info {
  flex: 1;
}
.basic-info .name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.basic-info .time {
  font-size: 12px;
  color: var(--text-muted);
}
.follow-wrap {
  flex-shrink: 0;
}
.follow-btn {
  height: 36px;
  padding: 0 24px;
  font-size: 13px;
}
.follow-btn.is-followed {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  box-shadow: none;
}

/* 正文 */
.blog-text {
  padding: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  margin-top: 14px;
  color: var(--text-primary);
  font-size: 15px;
  line-height: 1.8;
}

/* 关联店铺 */
.store-basic {
  display: flex;
  align-items: center;
  padding: 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  margin-top: 14px;
}
.store-icon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-right: 14px;
  flex-shrink: 0;
}
.store-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.store-info {
  flex: 1;
}
.store-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}
.store-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}
.store-avg {
  font-size: 14px;
  color: var(--accent);
  font-weight: 600;
}
.store-addr {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 6px;
}

/* 点赞 */
.zan-box {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  margin-top: 14px;
}
.zan-count {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  flex-shrink: 0;
}
.zan-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.user-icon-mini {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
}
.user-icon-mini img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 操作栏 */
.foot {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}
.like-btn {
  height: 44px;
  padding: 0 28px;
  font-size: 14px;
}
.like-btn.is-liked {
  background: var(--danger);
}
.comment-btn {
  height: 44px;
  padding: 0 28px;
  font-size: 14px;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .blog-info-box {
    height: 260px;
  }
  .swiper-arrow {
    display: none;
  }
}
</style>
