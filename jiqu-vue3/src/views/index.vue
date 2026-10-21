<script setup>
import { ref, onMounted } from 'vue'
import router from '@/router'
import { getStoreCategoryList } from '@/api/store'
import { indexQueryHotBlogsScroll, indexAddLike, indexQueryBlogById } from '@/api/user'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { imgFirst, avatar } from '@/utils/img'
import {
  Timer,
  Present,
  Star,
  ShoppingBag,
  CollectionTag,
  Headset,
  MapLocation,
  Watch,
  ChatDotRound,
  ArrowRight,
  Lightning,
  TrendCharts
} from '@element-plus/icons-vue'

const blogs = ref([])
const current = ref(1)

// 潮流品类矩阵：优先从后端 tb_shop_type 加载（带分类id），失败时回退静态分类
const defaultCategories = [
  { id: 8, name: '限量发售', icon: Timer },
  { id: 1, name: '潮玩手办', icon: Present },
  { id: 2, name: '潮流球鞋', icon: Star },
  { id: 3, name: '潮流服饰', icon: ShoppingBag },
  { id: 4, name: '收藏卡牌', icon: CollectionTag },
  { id: 5, name: '数码潮品', icon: Headset },
  { id: 6, name: '线下潮店', icon: MapLocation },
  { id: 7, name: '潮流配饰', icon: Watch }
]
const categories = ref(defaultCategories)

const loadCategories = async () => {
  try {
    const { data } = await getStoreCategoryList()
    if (data && data.length) {
      const iconMap = {}
      defaultCategories.forEach((c) => (iconMap[c.name] = c.icon))
      categories.value = data.map((c) => ({
        id: c.id,
        name: c.name,
        icon: iconMap[c.name] || Timer
      }))
    }
  } catch (error) {
    console.log('获取店铺分类失败，使用静态分类:', error)
  }
}

// 获取首页精选晒单数据
const queryHotBlogsScroll = async () => {
  try {
    const { data } = await indexQueryHotBlogsScroll(current.value)
    if (!data || !data.length) return
    data.forEach((b) => {
      b.img = imgFirst(b.images, `blog-${b.id}`, 600, 700)
      b.userAvatar = avatar(b.userId, 96)
    })
    blogs.value = blogs.value.concat(data)
  } catch (error) {
    console.log('获取精选晒单失败:', error)
  }
}

onMounted(() => {
  loadCategories()
  queryHotBlogsScroll()
})

// 滚动到底部加载更多
useInfiniteScroll(async () => {
  current.value++
  await queryHotBlogsScroll()
})

// 添加点赞
const addLike = async (blog) => {
  try {
    await indexAddLike(blog.id)
    await queryBlogById(blog)
  } catch (error) {
    console.log(error)
  }
}

// 根据id查询博客
const queryBlogById = async (blog) => {
  try {
    const { data } = await indexQueryBlogById(blog.id)
    blog.liked = data.liked
    blog.isLike = data.isLike
  } catch (error) {
    console.log(error)
    blog.liked++
  }
}

const toStoreList = (type, name) => {
  if (name === '限量发售') {
    router.push('/dropList')
    return
  }
  router.push({ path: '/storeList', query: { type, name } })
}
const toDropList = () => router.push('/dropList')
const toAiAssistant = () => router.push('/aiAssistant')
const toBlogDetail = (b) => router.push(`/blogDetail/${b.id}`)
const toInfo = () => router.push('/InfoHtml')
</script>

<template>
  <div class="home">
    <!-- Hero 区 -->
    <section class="hero">
      <div class="hero-left">
        <div class="hero-badge"><Lightning /> 潮流好物 抢先发售</div>
        <h1 class="hero-title">发现潮流<br />抢到限量 · 晒出态度</h1>
        <p class="hero-desc">集趣潮流社区 · 限量发售日历 · AI 智能选品 · 社区晒单种草</p>

        <div class="hero-cards">
          <!-- AI 助手入口 -->
          <div class="hero-card ai" @click="toAiAssistant">
            <div class="hero-card-icon"><ChatDotRound /></div>
            <div class="hero-card-body">
              <div class="hero-card-title">潮流 AI 助手</div>
              <div class="hero-card-desc">自然语言找店、查发售、选好物</div>
            </div>
            <div class="hero-card-arrow"><ArrowRight /></div>
          </div>
          <!-- 发售日历入口 -->
          <div class="hero-card drop" @click="toDropList">
            <div class="hero-card-icon"><TrendCharts /></div>
            <div class="hero-card-body">
              <div class="hero-card-title">发售日历</div>
              <div class="hero-card-desc">进行中 · 即将发售 · 倒计时抢购</div>
            </div>
            <div class="hero-card-arrow"><ArrowRight /></div>
          </div>
        </div>
      </div>

      <div class="hero-right">
        <img class="hero-img" src="https://picsum.photos/seed/hero-home/560/420" alt="集趣潮流" />
        <div class="hero-glow"></div>
      </div>
    </section>

    <!-- 品类矩阵 -->
    <section class="section">
      <div class="section-head">
        <h2 class="section-title">潮流品类</h2>
        <span class="section-sub">CATEGORIES</span>
      </div>
      <div class="type-list">
        <div
          class="type-box"
          v-for="c in categories"
          :key="c.id"
          @click="toStoreList(c.id, c.name)"
        >
          <div class="type-view">
            <el-icon :size="26"><component :is="c.icon" /></el-icon>
          </div>
          <div class="type-text">{{ c.name }}</div>
        </div>
      </div>
    </section>

    <!-- 精选晒单 -->
    <section class="section">
      <div class="section-head">
        <h2 class="section-title">社区精选晒单</h2>
        <span class="section-sub">COMMUNITY</span>
        <span class="section-more" @click="toInfo">更多 ›</span>
      </div>
      <div class="blog-list">
        <div class="blog-box" v-for="b in blogs" :key="b.id" @click="toBlogDetail(b)">
          <div class="blog-img">
            <img :src="b.img" alt="" />
            <div class="blog-like" @click.stop="addLike(b)">
              <svg
                viewBox="0 0 1024 1024"
                class="like-icon"
                :class="{ liked: b.isLike }"
                width="16"
                height="16"
              >
                <path
                  d="M505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z"
                ></path>
              </svg>
              {{ b.liked }}
            </div>
          </div>
          <div class="blog-body">
            <div class="blog-title">{{ b.title }}</div>
            <div class="blog-foot">
              <div class="blog-user">
                <img :src="b.userAvatar" alt="" class="blog-user-avatar" />
                <span class="blog-user-name">{{ b.name }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="!blogs.length" class="blog-empty">加载中…</div>
    </section>
  </div>
</template>

<style scoped>
.home {
  padding: 24px 0 48px;
}

/* Hero */
.hero {
  display: flex;
  align-items: center;
  gap: 48px;
  padding: 40px 0 24px;
  position: relative;
}
.hero-left {
  flex: 1;
}
.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--accent-light);
  background: rgba(255, 77, 0, 0.12);
  border: 1px solid rgba(255, 77, 0, 0.3);
  padding: 5px 14px;
  border-radius: var(--radius-full);
  margin-bottom: 20px;
}
.hero-title {
  font-size: 42px;
  font-weight: var(--font-black);
  line-height: 1.15;
  margin: 0 0 12px;
  color: var(--text-primary);
}
.hero-desc {
  font-size: 15px;
  color: var(--text-secondary);
  margin-bottom: 28px;
}
.hero-cards {
  display: flex;
  gap: 16px;
}
.hero-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.25s;
  border: 1px solid var(--border);
}
.hero-card.ai {
  background: var(--accent-gradient);
  color: #fff;
  box-shadow: var(--shadow-glow);
}
.hero-card.drop {
  background: var(--card);
  color: var(--text-primary);
}
.hero-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}
.hero-card-icon {
  font-size: 28px;
  display: flex;
}
.hero-card-body {
  flex: 1;
}
.hero-card-title {
  font-size: 16px;
  font-weight: 700;
}
.hero-card-desc {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 3px;
}
.hero-card-arrow {
  font-size: 16px;
  opacity: 0.7;
}
.hero-right {
  flex: 0 0 460px;
  position: relative;
}
.hero-img {
  width: 100%;
  height: 360px;
  object-fit: cover;
  border-radius: 24px;
  border: 1px solid var(--border);
  box-shadow: var(--shadow-lg);
}
.hero-glow {
  position: absolute;
  inset: -20px;
  background: radial-gradient(circle at 70% 30%, rgba(255, 77, 0, 0.18), transparent 60%);
  z-index: -1;
}

/* Section */
.section {
  margin-top: 40px;
}
.section-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}
.section-title {
  font-size: 24px;
  font-weight: var(--font-black);
  color: var(--text-primary);
  margin: 0;
}
.section-sub {
  font-size: 12px;
  letter-spacing: 3px;
  color: var(--text-muted);
}
.section-more {
  margin-left: auto;
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
}
.section-more:hover {
  color: var(--accent-light);
}

/* 品类矩阵 */
.type-list {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 16px;
}
.type-box {
  text-align: center;
  cursor: pointer;
  padding: 16px 8px;
  border-radius: var(--radius-lg);
  background: var(--card);
  border: 1px solid var(--border);
  transition: all 0.2s;
}
.type-box:hover {
  transform: translateY(-4px);
  background: var(--card-hover);
  border-color: var(--accent);
}
.type-view {
  width: 56px;
  height: 56px;
  margin: 0 auto;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--accent-gradient);
  color: #fff;
  box-shadow: var(--shadow-glow);
}
.type-text {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  margin-top: 10px;
}

/* 精选晒单 */
.blog-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.blog-box {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s;
}
.blog-box:hover {
  transform: translateY(-4px);
  border-color: var(--border-hover);
  box-shadow: var(--shadow-md);
}
.blog-img {
  position: relative;
  width: 100%;
  aspect-ratio: 6 / 5;
  overflow: hidden;
}
.blog-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}
.blog-box:hover .blog-img img {
  transform: scale(1.05);
}
.blog-like {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  padding: 4px 12px;
  border-radius: var(--radius-full);
}
.blog-like:hover {
  background: var(--accent);
}
.like-icon {
  fill: #fff;
}
.like-icon.liked {
  fill: #ff4d00;
}
.blog-body {
  padding: 14px 16px 16px;
}
.blog-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.5;
  height: 44px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.blog-foot {
  display: flex;
  align-items: center;
  margin-top: 12px;
}
.blog-user {
  display: flex;
  align-items: center;
  gap: 8px;
}
.blog-user-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}
.blog-user-name {
  font-size: 13px;
  color: var(--text-secondary);
}
.blog-empty {
  text-align: center;
  padding: 60px 0;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .hero {
    flex-direction: column;
    padding: 24px 0 8px;
  }
  .hero-right {
    flex-basis: auto;
    width: 100%;
  }
  .hero-img {
    height: 200px;
  }
  .hero-title {
    font-size: 30px;
  }
  .hero-cards {
    flex-direction: column;
  }
  .type-list {
    grid-template-columns: repeat(4, 1fr);
    gap: 10px;
  }
  .blog-list {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
  .home {
    padding: 12px 0 32px;
  }
}
</style>
