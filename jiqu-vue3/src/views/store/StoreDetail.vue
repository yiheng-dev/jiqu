<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, ArrowRight, Location, Clock, Timer } from '@element-plus/icons-vue'
import { ElLoading } from 'element-plus'
import { getStoreById } from '@/api/store'
import { getDropList, issueDropAccessToken, limitedDrop, getDropOrderId, getDropOrderIdByDropId, cancelDropOrder, subscribeDrop, unsubscribeDrop, getSubscribeStatusBatch } from '@/api/drop'
import { useUserStore } from '@/stores'
import { imgs, imgFirst, avatar } from '@/utils/img'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 响应式数据
const store = ref({})
const drops = ref([])
const dropInProgress = ref(false)
// 已购买状态映射：dropId -> boolean
const purchasedMap = ref({})
// 订阅状态映射：dropId -> 0|1|2 （0 未订阅/已取消；1 已订阅；2 自动发券成功）
const subscribeStatusMap = ref({})

const isPurchased = (dropId) => {
  return !!purchasedMap.value[String(dropId)]
}

// 查询所有限量发售的已购买状态（登录用户）
const refreshPurchaseStatus = async () => {
  try {
    if (!userStore.token) return
    const list = drops.value || []
    await Promise.all(
      list.map(async (v) => {
        if (!v?.id) return
        try {
          const res = await getDropOrderIdByDropId(String(v.id))
          purchasedMap.value[String(v.id)] = !!res?.data
        } catch (e) {
          // 忽略单项错误，保持既有状态
        }
      })
    )
  } catch (e) {
    // 忽略总体错误
  }
}

// 批量查询订阅状态（登录用户）
const refreshSubscribeStatusBatch = async () => {
  try {
    if (!userStore.token) return
    const ids = (drops.value || []).map((v) => v.id).filter(Boolean)
    if (!ids.length) return
    const res = await getSubscribeStatusBatch(ids)
    const arr = Array.isArray(res?.data) ? res.data : []
    const map = {}
    for (const item of arr) {
      if (!item) continue
      const vid = String(item.dropId)
      const st = Number(item.subscribeStatus)
      map[vid] = Number.isFinite(st) ? st : 0
      if (st === 2) {
        purchasedMap.value[vid] = true
      }
    }
    subscribeStatusMap.value = { ...subscribeStatusMap.value, ...map }
  } catch (e) {
    // 静默失败
  }
}

const getSubscribeCode = (dropId) => {
  return Number(subscribeStatusMap.value[String(dropId)] ?? 0)
}
const isSubscribed = (dropId) => getSubscribeCode(dropId) === 1
const isSubscribeSuccess = (dropId) => getSubscribeCode(dropId) === 2

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const pollDropOrder = async (orderId, { delay = 800, timeoutMs = 11000 } = {}) => {
  const end = Date.now() + timeoutMs
  while (Date.now() < end) {
    try {
      const { data } = await getDropOrderId(String(orderId))
      if (data) {
        ElMessage.success('发售成功')
        return data
      }
    } catch (e) {
      // 短暂异常，继续重试
    }
    const remaining = end - Date.now()
    await sleep(Math.min(delay, Math.max(0, remaining)))
  }
  ElMessage.warning('确认订单超时，请稍后在订单页查看')
  return null
}

// 获取潮流店铺详情
const queryStoreById = async (storeId) => {
  try {
    const { data } = await getStoreById(storeId)
    store.value = { ...data, imageList: imgs(data.images, `store-${data.id}`, 600, 400) }
  } catch (error) {
    console.error(error)
    ElMessage.error('获取潮流店铺详情失败')
  }
}

// 获取限量发售列表（保持后端字段类型为字符串）
const queryDrop = async (storeId) => {
  try {
    const { data } = await getDropList(storeId)
    drops.value = data || []
    await refreshPurchaseStatus()
    await refreshSubscribeStatusBatch()
  } catch (error) {
    console.error(error)
    ElMessage.error('获取限量发售列表失败')
  }
}

// 格式化时间
const formatTime = (v) => {
  const b = new Date(v.beginTime)
  const e = new Date(v.endTime)
  return (
    b.getMonth() +
    1 +
    '月' +
    b.getDate() +
    '日 ' +
    b.getHours() +
    ':' +
    formatMinutes(b.getMinutes()) +
    ' ~ ' +
    e.getHours() +
    ':' +
    formatMinutes(e.getMinutes())
  )
}

// 格式化分钟
const formatMinutes = (m) => {
  if (m < 10) m = '0' + m
  return m
}

// 格式化价格，兼容字符串或数字
const formatPrice = (price) => {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : '0.00'
}

// 格式化折扣，保留一位小数，例如 7.7折
const formatDiscount = (payValue, actualValue) => {
  const pay = Number(payValue)
  const actual = Number(actualValue)
  if (!Number.isFinite(pay) || !Number.isFinite(actual) || actual <= 0) {
    return '--'
  }
  return `${Number(((pay * 10) / actual).toFixed(1))}折`
}

// 统一评分：0-50 制转 0-5 制
const formatScore = (v) => {
  const n = Number(v)
  return Number.isFinite(n) && n > 0 ? n / 10 : 4.8
}

// 判断是否未开始
const isNotBegin = (v) => {
  return new Date(v.beginTime).getTime() > new Date().getTime()
}

// 判断是否已结束
const isEnd = (v) => {
  return new Date(v.endTime).getTime() < new Date().getTime()
}

// 发售流程（先获取令牌，再携带令牌下单）
const drop = async (v) => {
  if (!userStore.token) {
    ElMessage.error('请先登录')
    setTimeout(() => {
      router.push('/login')
    }, 200)
    return
  }

  if (isNotBegin(v)) {
    ElMessage.error('限量发售尚未开始！')
    return
  }

  if (isEnd(v)) {
    ElMessage.error('限量发售已经结束！')
    return
  }

  if (Number(v.stock) < 1) {
    ElMessage.error('库存不足，请刷新再试试！')
    return
  }

  if (isPurchased(v.id)) {
    ElMessage.error('您已购买该券，不能重复购买')
    return
  }

  let loading = null
  try {
    if (dropInProgress.value) {
      return
    }
    dropInProgress.value = true
    loading = ElLoading.service({
      fullscreen: true,
      lock: true,
      text: '正在确认订单，请稍等…',
      background: 'rgba(0,0,0,0.35)',
      customClass: 'drop-overlay'
    })
    // 1）先获取访问令牌
    const tokenRes = await issueDropAccessToken(v.id)
    if (!tokenRes?.success || !tokenRes?.data) {
      ElMessage.error(tokenRes?.errorMsg || '令牌获取失败，请稍后重试')
      return
    }
    const accessToken = String(tokenRes.data)
    // 2）携带令牌发起下单
    const res = await limitedDrop(v.id, accessToken)
    if (res && res.success) {
      const order = await pollDropOrder(String(res.data), { delay: 800, timeoutMs: 11000 })
      if (order) {
        try {
          // 重新拉取发售列表，同步最新库存（Kafka 异步扣减已落地）与已购状态
          await queryDrop(route.params.id)
        } catch (e) {
          // 忽略错误，不影响既有状态
        }
      }
    } else {
      ElMessage.error(res?.errorMsg || '发售失败')
      return
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('发售失败')
  } finally {
    dropInProgress.value = false
    if (loading) loading.close()
  }
}

// 取消已领取的限量发售
const cancelDrop = async (v) => {
  if (!userStore.token) {
    ElMessage.error('请先登录')
    setTimeout(() => {
      router.push('/login')
    }, 200)
    return
  }
  if (!v?.id) return
  try {
    const res = await cancelDropOrder(String(v.id))
    if (res?.success && String(res.data) === 'true') {
      ElMessage.success('已取消购买')
      purchasedMap.value[String(v.id)] = false
      await queryDrop(route.params.id)
    } else {
      ElMessage.error(res?.errorMsg || '取消失败')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('取消失败')
  }
}

// 订阅开售提醒
const subscribeToDrop = async (v) => {
  if (!userStore.token) {
    ElMessage.error('请先登录')
    setTimeout(() => router.push('/login'), 200)
    return
  }
  if (!v?.id) return
  try {
    const res = await subscribeDrop(String(v.id))
    if (res?.success) {
      ElMessage.success('已订阅开售提醒')
      subscribeStatusMap.value[String(v.id)] = 1
    } else {
      ElMessage.error(res?.errorMsg || '订阅失败')
    }
  } catch (e) {
    ElMessage.error('订阅失败')
  }
}

// 取消订阅开售提醒
const unsubscribeFromDrop = async (v) => {
  if (!userStore.token) {
    ElMessage.error('请先登录')
    setTimeout(() => router.push('/login'), 200)
    return
  }
  if (!v?.id) return
  try {
    const res = await unsubscribeDrop(String(v.id))
    if (res?.success) {
      ElMessage.success('已取消订阅')
      subscribeStatusMap.value[String(v.id)] = 0
    } else {
      ElMessage.error(res?.errorMsg || '取消订阅失败')
    }
  } catch (e) {
    ElMessage.error('取消订阅失败')
  }
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 过滤后的限量发售列表
const filteredDrops = computed(() => {
  return drops.value.filter((v) => !isEnd(v))
})

// 初始化
onMounted(() => {
  const storeId = route.params.id
  queryStoreById(storeId)
  queryDrop(storeId)
})
</script>

<template>
  <div class="store-detail-page">
    <!-- 面包屑 -->
    <div class="crumb">
      <span class="crumb-back" @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</span>
      <span class="crumb-sep">/</span>
      <span class="crumb-cur">店铺详情</span>
    </div>

    <!-- Hero -->
    <section class="store-hero">
      <div class="store-gallery">
        <div class="gallery-main">
          <img :src="store.imageList?.[0]" alt="" />
        </div>
        <div class="gallery-thumbs">
          <img
            v-for="(g, i) in store.imageList?.slice(1, 4)"
            :key="i"
            :src="g"
            alt=""
          />
        </div>
      </div>

      <div class="store-hero-info">
        <div class="store-title-row">
          <span class="store-badge">STORE</span>
          <h1 class="store-name">{{ store.name }}</h1>
        </div>

        <div class="store-meta-row">
          <el-rate :model-value="formatScore(store.score)" disabled :max="5" show-score score-template="{value}" />
          <span class="store-comments">{{ store.comments }} 条评论</span>
          <span class="store-rank">好评榜 · TOP</span>
        </div>

        <div class="store-address-row">
          <el-icon><Location /></el-icon>
          <span>{{ store.address }}</span>
        </div>
        <div class="store-open-row">
          <el-icon><Clock /></el-icon>
          <span>营业时间：{{ store.openHours }}</span>
        </div>

        <div class="store-tags">
          <span class="tag">潮流好店</span>
          <span class="tag">正品保障</span>
          <span class="tag">限时发售</span>
        </div>
      </div>
    </section>

    <!-- 限量发售 -->
    <section class="drop-section">
      <div class="section-head">
        <h2 class="section-title">限量发售</h2>
        <span class="section-sub">LIMITED DROPS</span>
      </div>

      <div class="drop-list">
        <div class="drop-card" v-for="v in filteredDrops" :key="v.id">
          <div class="drop-thumb">
            <img :src="`https://picsum.photos/seed/drop-${v.id}/400/400`" alt="" />
          </div>

          <div class="drop-body">
            <div class="drop-title">{{ v.title }}</div>
            <div class="drop-subtitle">{{ v.subTitle }}</div>

            <div class="drop-price-row">
              <span class="drop-price">￥{{ formatPrice(v.payValue) }}</span>
              <span class="drop-discount">{{ formatDiscount(v.payValue, v.actualValue) }}</span>
            </div>

            <div class="drop-time-row">
              <el-icon><Timer /></el-icon>
              <span>{{ formatTime(v) }}</span>
            </div>
          </div>

          <div class="drop-action">
            <template v-if="v.type">
              <button
                class="jq-btn drop-btn"
                :class="{ 'is-disabled': isNotBegin(v) || v.stock < 1 || isPurchased(v.id) }"
                :disabled="isNotBegin(v) || v.stock < 1 || isPurchased(v.id)"
                @click="drop(v)"
              >
                {{ isPurchased(v.id) ? '已购买' : isNotBegin(v) ? '未开始' : v.stock < 1 ? '已售罄' : '立即抢购' }}
              </button>
              <div class="drop-stock">剩余 <span>{{ v.stock }}</span> 件</div>

              <div v-if="isPurchased(v.id)" class="drop-status">
                <span class="purchased-tag">已购买</span>
                <span class="cancel-link" @click="cancelDrop(v)">取消购买</span>
              </div>

              <div v-else-if="Number(v.stock) < 1 && !isPurchased(v.id)" class="subscribe-box">
                <div v-if="isSubscribed(v.id)" class="subscribe-status">
                  <span class="subscribed-tag">已订阅开售提醒</span>
                  <span class="cancel-subscribe" @click="unsubscribeFromDrop(v)">取消订阅</span>
                </div>
                <div v-else class="subscribe-status">
                  <span class="subscribe-link" @click="subscribeToDrop(v)">开售提醒</span>
                </div>
              </div>
            </template>
            <button v-else class="jq-btn drop-btn is-disabled" disabled>普通券</button>
          </div>
        </div>
      </div>

      <div v-if="!filteredDrops.length" class="drop-empty">该店铺暂无限量发售</div>
    </section>

    <div class="copyright">© 2025 集趣·潮流社区</div>
  </div>
</template>

<style scoped>
.store-detail-page {
  padding: 16px 0 48px;
}

/* 面包屑 */
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

/* Hero */
.store-hero {
  display: flex;
  gap: 32px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 24px;
  margin-bottom: 32px;
}
.store-gallery {
  flex: 0 0 420px;
}
.gallery-main {
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-bottom: 12px;
}
.gallery-main img {
  width: 100%;
  height: 260px;
  object-fit: cover;
  display: block;
}
.gallery-thumbs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.gallery-thumbs img {
  width: 100%;
  height: 80px;
  object-fit: cover;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: transform 0.2s;
}
.gallery-thumbs img:hover {
  transform: scale(1.03);
}
.store-hero-info {
  flex: 1;
  padding: 8px 0;
}
.store-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.store-badge {
  font-size: 11px;
  letter-spacing: 2px;
  font-weight: 700;
  color: var(--accent);
  background: rgba(255, 77, 0, 0.12);
  padding: 4px 10px;
  border-radius: var(--radius-full);
}
.store-name {
  font-size: 28px;
  font-weight: var(--font-black);
  color: var(--text-primary);
  margin: 0;
}
.store-meta-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}
.store-comments {
  font-size: 14px;
  color: var(--text-secondary);
}
.store-rank {
  font-size: 12px;
  color: var(--warning);
  background: rgba(245, 158, 11, 0.12);
  padding: 3px 10px;
  border-radius: var(--radius-full);
}
.store-address-row,
.store-open-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  margin-bottom: 10px;
}
.store-tags {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}
.tag {
  font-size: 12px;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  padding: 5px 14px;
  border-radius: var(--radius-full);
}

/* 发售区 */
.drop-section {
  margin-bottom: 32px;
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

.drop-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}
.drop-card {
  display: flex;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 16px;
  gap: 16px;
  transition: all 0.25s;
}
.drop-card:hover {
  border-color: var(--border-hover);
  box-shadow: var(--shadow-md);
}
.drop-thumb {
  width: 120px;
  height: 120px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  overflow: hidden;
}
.drop-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.drop-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.drop-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 4px;
}
.drop-subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 10px;
}
.drop-price-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}
.drop-price {
  font-size: 24px;
  font-weight: var(--font-black);
  color: var(--accent);
}
.drop-discount {
  font-size: 12px;
  color: var(--accent-light);
  background: rgba(255, 77, 0, 0.12);
  padding: 2px 8px;
  border-radius: var(--radius-full);
}
.drop-time-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted);
}
.drop-action {
  width: 150px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-left: 1px solid var(--border);
  padding-left: 16px;
}
.drop-btn {
  width: 100%;
  height: 40px;
  font-size: 14px;
}
.drop-btn.is-disabled {
  opacity: 0.55;
  cursor: not-allowed;
  background: var(--card-hover);
  color: var(--text-muted);
  box-shadow: none;
}
.drop-stock {
  font-size: 12px;
  color: var(--text-muted);
}
.drop-stock span {
  color: var(--accent);
  font-weight: 700;
}
.drop-status {
  display: flex;
  align-items: center;
  gap: 8px;
}
.purchased-tag {
  font-size: 12px;
  color: var(--success);
  background: rgba(34, 197, 94, 0.12);
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-weight: 600;
}
.cancel-link {
  font-size: 12px;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  padding: 4px 12px;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all 0.2s;
}
.cancel-link:hover {
  color: var(--danger);
  border-color: var(--danger);
}
.subscribe-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.subscribed-tag {
  font-size: 12px;
  color: var(--warning);
  background: rgba(245, 158, 11, 0.12);
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-weight: 600;
}
.subscribe-link {
  font-size: 13px;
  color: #fff;
  background: var(--accent-gradient);
  padding: 8px 20px;
  border-radius: var(--radius-full);
  cursor: pointer;
  font-weight: 600;
}
.cancel-subscribe {
  font-size: 12px;
  color: var(--text-secondary);
  border: 1px solid var(--border);
  padding: 4px 12px;
  border-radius: var(--radius-full);
  cursor: pointer;
}
.cancel-subscribe:hover {
  color: var(--danger);
  border-color: var(--danger);
}
.drop-empty {
  text-align: center;
  padding: 60px 0;
  color: var(--text-muted);
}
.copyright {
  text-align: center;
  color: var(--text-muted);
  font-size: 12px;
  padding: 24px 0;
}

@media (max-width: 768px) {
  .store-hero {
    flex-direction: column;
  }
  .store-gallery {
    flex-basis: auto;
  }
  .drop-list {
    grid-template-columns: 1fr;
  }
  .drop-card {
    flex-direction: column;
  }
  .drop-action {
    width: 100%;
    border-left: none;
    border-top: 1px solid var(--border);
    padding-left: 0;
    padding-top: 12px;
  }
}
</style>
