<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getDropCalendar, getDropById } from '@/api/drop'
import router from '@/router'
import { Timer, Van, ArrowLeft, Clock } from '@element-plus/icons-vue'

const ongoing = ref([])
const upcoming = ref([])
const loading = ref(false)

// 日历接口不返回 storeId，逐个反查所属店铺 id，用于点击跳转店铺详情
const resolveStoreIds = async (list) => {
  await Promise.all(
    list.map(async (d) => {
      try {
        const res = await getDropById(d.dropId)
        if (res?.data?.storeId) d.storeId = res.data.storeId
      } catch (e) {
        console.log('解析发售店铺失败 dropId=', d.dropId)
      }
    })
  )
}

const loadCalendar = async () => {
  loading.value = true
  try {
    const { data } = await getDropCalendar()
    const list = data || []
    ongoing.value = list.filter((d) => d.status === 'ongoing')
    upcoming.value = list.filter((d) => d.status === 'upcoming')
    await Promise.all([resolveStoreIds(ongoing.value), resolveStoreIds(upcoming.value)])
  } catch (error) {
    console.log('获取发售日历失败:', error)
  } finally {
    loading.value = false
  }
}
onMounted(loadCalendar)

// 倒计时
const countdownMap = ref({})
let timer = null
const startCountdown = () => {
  const calc = () => {
    const now = Date.now()
    const map = {}
    ;[...ongoing.value, ...upcoming.value].forEach((d) => {
      const target = d.status === 'upcoming' ? new Date(d.beginTime).getTime() : new Date(d.endTime).getTime()
      let diff = Math.max(0, target - now)
      const days = Math.floor(diff / 86400000)
      const hours = Math.floor((diff % 86400000) / 3600000)
      const mins = Math.floor((diff % 3600000) / 60000)
      const secs = Math.floor((diff % 60000) / 1000)
      map[d.dropId] = days > 0
        ? `${days}天${hours}时${mins}分`
        : `${String(hours).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
    })
    countdownMap.value = map
  }
  calc()
  timer = setInterval(calc, 1000)
}
const stopCountdown = () => timer && clearInterval(timer)
onMounted(() => startCountdown())
onBeforeUnmount(stopCountdown)

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const goBack = () => router.back()
const toStoreDetail = (d) => {
  if (!d.storeId) {
    ElMessage.warning('该发售暂未关联店铺')
    return
  }
  router.push(`/storeDetail/${d.storeId}`)
}
</script>

<template>
  <div class="drop-page">
    <div class="page-head">
      <div class="head-title-row">
        <span class="head-back" @click="goBack"><el-icon><ArrowLeft /></el-icon></span>
        <h1 class="head-title">发售日历</h1>
        <span class="head-sub">DROP CALENDAR</span>
      </div>
      <p class="head-desc">限量发售，先到先得 · 订阅提醒，不错过每一次抢购</p>
    </div>

    <div class="drop-body" v-loading="loading">
      <!-- 进行中 -->
      <div class="drop-section" v-if="ongoing.length">
        <div class="section-head">
          <span class="dot ongoing"></span> 进行中
          <span class="section-count">{{ ongoing.length }} 款</span>
        </div>
        <div class="drop-grid">
          <div class="drop-card" v-for="d in ongoing" :key="d.dropId" @click="toStoreDetail(d)">
            <div class="drop-thumb">
              <img :src="`https://picsum.photos/seed/drop-${d.dropId}/400/400`" alt="" />
              <span class="drop-status-tag ongoing">抢购中</span>
            </div>
            <div class="drop-info">
              <div class="drop-title">{{ d.title || '限量发售' }}</div>
              <div class="drop-sub" v-if="d.subTitle">{{ d.subTitle }}</div>
              <div class="drop-time">
                <el-icon><Clock /></el-icon>
                <span>截止 {{ formatTime(d.endTime) }}</span>
              </div>
              <div class="drop-bottom">
                <span class="drop-count" v-if="d.stock != null">剩余 {{ d.stock }} 件</span>
                <span class="drop-countdown">{{ countdownMap[d.dropId] }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 即将发售 -->
      <div class="drop-section" v-if="upcoming.length">
        <div class="section-head">
          <span class="dot upcoming"></span> 即将发售
          <span class="section-count">{{ upcoming.length }} 款</span>
        </div>
        <div class="drop-grid">
          <div class="drop-card upcoming" v-for="d in upcoming" :key="d.dropId" @click="toStoreDetail(d)">
            <div class="drop-thumb">
              <img :src="`https://picsum.photos/seed/drop-${d.dropId}/400/400`" alt="" />
              <span class="drop-status-tag upcoming">待发售</span>
            </div>
            <div class="drop-info">
              <div class="drop-title">{{ d.title || '限量发售' }}</div>
              <div class="drop-sub" v-if="d.subTitle">{{ d.subTitle }}</div>
              <div class="drop-time">
                <el-icon><Clock /></el-icon>
                <span>{{ formatTime(d.beginTime) }} 开抢</span>
              </div>
              <div class="drop-bottom">
                <span class="drop-count" v-if="d.initStock != null">限量 {{ d.initStock }} 件</span>
                <span class="drop-countdown upcoming">倒计时 {{ countdownMap[d.dropId] }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <div class="drop-empty" v-if="!ongoing.length && !upcoming.length && !loading">
        <el-icon :size="44" color="#71717a"><Timer /></el-icon>
        <p>暂无可发售的限量发售</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.drop-page {
  padding: 24px 0 48px;
}
.page-head {
  margin-bottom: 24px;
}
.head-title-row {
  display: flex;
  align-items: center;
  gap: 14px;
}
.head-back {
  font-size: 20px;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  transition: color 0.2s;
}
.head-back:hover {
  color: var(--accent-light);
}
.head-title {
  font-size: 30px;
  font-weight: var(--font-black);
  color: var(--text-primary);
  margin: 0;
}
.head-sub {
  font-size: 12px;
  letter-spacing: 3px;
  color: var(--text-muted);
}
.head-desc {
  color: var(--text-secondary);
  margin-top: 6px;
}

.drop-section {
  margin-bottom: 32px;
}
.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 16px;
}
.section-count {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
  margin-left: auto;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot.ongoing {
  background: var(--accent);
  box-shadow: 0 0 0 4px rgba(255, 77, 0, 0.18);
}
.dot.upcoming {
  background: var(--text-secondary);
  box-shadow: 0 0 0 4px rgba(161, 161, 170, 0.15);
}

.drop-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}
.drop-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s;
}
.drop-card:hover {
  transform: translateY(-4px);
  border-color: var(--accent);
  box-shadow: var(--shadow-md);
}
.drop-thumb {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
}
.drop-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}
.drop-card:hover .drop-thumb img {
  transform: scale(1.05);
}
.drop-status-tag {
  position: absolute;
  top: 12px;
  left: 12px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  padding: 4px 14px;
  border-radius: var(--radius-full);
  backdrop-filter: blur(4px);
}
.drop-status-tag.ongoing {
  background: var(--accent);
}
.drop-status-tag.upcoming {
  background: rgba(20, 20, 25, 0.8);
  border: 1px solid var(--border-hover);
}
.drop-info {
  padding: 14px 16px 16px;
}
.drop-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.drop-sub {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.drop-time {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 10px;
}
.drop-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.drop-count {
  font-size: 12px;
  color: var(--text-secondary);
}
.drop-countdown {
  font-size: 14px;
  font-weight: 700;
  color: var(--accent);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.5px;
}
.drop-countdown.upcoming {
  color: var(--text-secondary);
}
.drop-empty {
  text-align: center;
  padding: 80px 0;
  color: var(--text-muted);
}
.drop-empty p {
  margin-top: 12px;
  font-size: 14px;
}

@media (max-width: 768px) {
  .drop-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
}
</style>
