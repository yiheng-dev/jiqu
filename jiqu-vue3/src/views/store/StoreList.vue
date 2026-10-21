<template>
  <div class="store-list-page">
    <!-- 页头 -->
    <div class="page-head">
      <div class="head-title-row">
        <h1 class="head-title">{{ typeName || '潮流店铺' }}</h1>
        <span class="head-sub">{{ searchMode ? '搜索店铺' : 'CATEGORY STORES' }}</span>
      </div>
      <p class="head-desc" v-if="!searchMode">
        精选 {{ typeName || '全部分类' }} 潮流好店，发现你的最爱
      </p>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-select
        v-model="selectedTypeId"
        placeholder="选择分类"
        class="type-select"
        @change="onTypeChange"
      >
        <el-option
          v-for="t in types"
          :key="t.id"
          :label="t.name"
          :value="t.id"
        />
      </el-select>

      <div class="sort-group">
        <span class="sort-item" :class="{ active: params.sortBy === '' }" @click="sortAndQuery('')">距离</span>
        <span class="sort-item" :class="{ active: params.sortBy === 'comments' }" @click="sortAndQuery('comments')">人气</span>
        <span class="sort-item" :class="{ active: params.sortBy === 'score' }" @click="sortAndQuery('score')">评分</span>
      </div>
    </div>

    <!-- 店铺列表 -->
    <div class="store-grid" v-loading="loading">
      <div
        class="store-card"
        v-for="s in stores"
        :key="s.id"
        @click="toDetail(s.id)"
      >
        <div class="store-img">
          <img :src="s.img" alt="" loading="lazy" />
        </div>
        <div class="store-info">
          <div class="store-title">{{ s.name }}</div>
          <div class="store-meta">
            <el-rate
              :model-value="formatScore(s.score)"
              disabled
              :max="5"
              show-score
              score-template="{value}"
            />
            <span class="store-comments">{{ s.comments }} 条</span>
          </div>
          <div class="store-area-row">
            <span class="store-area">{{ s.area }}</span>
            <span class="store-distance" v-if="s.distanceText">{{ s.distanceText }}</span>
          </div>
          <div class="store-bottom">
            <span class="store-price">￥{{ s.avgPrice }}<i>/人</i></span>
            <span class="store-addr">{{ s.address }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!stores.length && !loading" class="store-empty">没有找到相关店铺</div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '@/utils/request'
import { getStoreList, getStoreCategoryList } from '@/api/store'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { imgFirst } from '@/utils/img'

const router = useRouter()
const route = useRoute()

const types = ref([])
const stores = ref([])
const typeName = ref('')
const loading = ref(false)
const searchMode = computed(() => !route.query.type)

const selectedTypeId = ref(0)
const params = ref({
  typeId: 0,
  current: 1,
  sortBy: '',
  x: 120.149993,
  y: 30.334229
})

const formatScore = (v) => {
  const n = Number(v)
  return Number.isFinite(n) && n > 0 ? n / 10 : 5
}

const fmtDistance = (d) => {
  const n = Number(d)
  if (!Number.isFinite(n) || n <= 0) return ''
  return n < 1000 ? n.toFixed(0) + 'm' : (n / 1000).toFixed(1) + 'km'
}

// 获取潮流店铺类型列表
const queryTypes = async () => {
  try {
    const { data } = await getStoreCategoryList()
    types.value = data
    if (data && data.length && !route.query.type) {
      selectedTypeId.value = data[0]?.id
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('获取潮流店铺类型失败')
  }
}

// 获取潮流店铺列表
const queryStores = async () => {
  loading.value = true
  try {
    let data = null
    if (searchMode.value) {
      const name = route.query.name || typeName.value || ''
      const res = await request.get('/store/of/name', {
        params: { name, current: params.value.current }
      })
      data = res?.data
    } else {
      const res = await getStoreList(params.value)
      data = res?.data
    }
    if (!data || !data.length) {
      loading.value = false
      return
    }
    const mapped = data.map((s) => ({
      ...s,
      img: imgFirst(s.images, `store-${s.id}`, 480, 360),
      distanceText: fmtDistance(s.distance)
    }))
    if (params.value.current === 1) {
      stores.value = mapped
    } else {
      stores.value = stores.value.concat(mapped)
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('获取潮流店铺列表失败')
  } finally {
    loading.value = false
  }
}

// 处理类型选择
const onTypeChange = (id) => {
  router.push({ path: '/storeList', query: { type: id, name: '' } })
}

// 排序和查询
const sortAndQuery = (sortBy) => {
  params.value.sortBy = sortBy
  params.value.current = 1
  stores.value = []
  queryStores()
}

// 跳转到潮流店铺详情
const toDetail = (id) => {
  router.push(`/storeDetail/${id}`)
}

// 滚动加载
useInfiniteScroll(() => {
  params.value.current++
  queryStores()
})

// 初始化
const initData = () => {
  params.value.typeId = Number(route.query.type) || 0
  selectedTypeId.value = params.value.typeId
  typeName.value = route.query.name || ''
  if (!route.query.type && !route.query.name) {
    // 无参数时默认第一个分类
    typeName.value = types.value[0]?.name || ''
    params.value.typeId = selectedTypeId.value || 0
  }
  stores.value = []
  params.value.current = 1
  queryStores()
}

// 监听路由参数变化
watch(
  () => route.query,
  () => {
    initData()
  }
)
onMounted(() => {
  queryTypes()
  initData()
})
</script>

<style scoped>
.store-list-page {
  padding: 24px 0 48px;
}
.page-head {
  margin-bottom: 20px;
}
.head-title-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
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

/* 筛选条 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 14px 20px;
  margin-bottom: 20px;
}
.type-select {
  width: 200px;
}
.sort-group {
  display: flex;
  gap: 8px;
  flex: 1;
}
.sort-item {
  padding: 8px 20px;
  border-radius: var(--radius-full);
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid var(--border);
}
.sort-item:hover {
  color: var(--text-primary);
  border-color: var(--border-hover);
}
.sort-item.active {
  color: #fff;
  background: var(--accent-gradient);
  border-color: transparent;
  font-weight: 600;
}

/* 店铺网格 */
.store-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  min-height: 200px;
}
.store-card {
  display: flex;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s;
}
.store-card:hover {
  transform: translateY(-4px);
  border-color: var(--accent);
  box-shadow: var(--shadow-md);
}
.store-img {
  width: 200px;
  flex-shrink: 0;
}
.store-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  min-height: 160px;
}
.store-info {
  flex: 1;
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.store-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}
.store-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}
.store-comments {
  font-size: 13px;
  color: var(--text-muted);
}
.store-area-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.store-area {
  font-size: 14px;
  color: var(--text-secondary);
}
.store-distance {
  font-size: 12px;
  color: var(--accent-light);
}
.store-bottom {
  margin-top: auto;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.store-price {
  font-size: 20px;
  font-weight: 700;
  color: var(--accent);
}
.store-price i {
  font-size: 12px;
  font-style: normal;
  color: var(--text-muted);
  font-weight: 400;
}
.store-addr {
  font-size: 12px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.store-empty {
  text-align: center;
  padding: 80px 0;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .store-grid {
    grid-template-columns: 1fr;
  }
  .store-img {
    width: 130px;
  }
}
</style>
