<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Search, Plus, Close } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores'
import {
  uploadBlogImage,
  deleteBlogImage,
  queryStoresByName,
  createBlog
} from '@/api/blog'

const router = useRouter()
const userStore = useUserStore()

// 数据定义
const fileInput = ref(null)
const fileList = ref([]) // 文件列表（存后端路径，删除/提交用）
const previews = ref({}) // 预览图：index -> objectURL 或 后端回显
const params = reactive({
  title: '',
  content: ''
})
const showDialog = ref(false)
const stores = ref([]) // 店铺信息
const storeName = ref('') // 店铺名称
const selectedStore = ref({}) // 选中的店铺

// 生命周期钩子
onMounted(() => {
  checkLogin()
  queryStores()
})

// 方法定义
const checkLogin = () => {
  const token = userStore.getToken()
  if (!token) {
    router.push('/login')
    return
  }
}

const queryStores = () => {
  queryStoresByName(storeName.value)
    .then((res) => {
      stores.value = res.data
    })
    .catch((err) => {
      ElMessage.error(err.message || '获取店铺列表失败')
    })
}

const selectStore = (store) => {
  selectedStore.value = store
  showDialog.value = false
}

const openFileDialog = () => {
  fileInput.value.click()
}

const fileSelected = (event) => {
  const file = event.target.files[0]
  if (!file) return

  const formData = new FormData()
  formData.append('file', file)

  uploadBlogImage(formData)
    .then((res) => {
      const path = '/imgs' + res.data
      fileList.value.push(path)
      // 预览用 objectURL（file 仍可访问）；若后续刷新，改走 picsum 回显
      previews.value[fileList.value.length - 1] = URL.createObjectURL(file)
      event.target.value = ''
    })
    .catch((err) => {
      ElMessage.error(err.message || '上传图片失败')
    })
}

const deletePic = (index) => {
  const imagePath = fileList.value[index]
  deleteBlogImage(imagePath)
    .then(() => {
      const objUrl = previews.value[index]
      if (objUrl && objUrl.startsWith('blob:')) URL.revokeObjectURL(objUrl)
      delete previews.value[index]
      fileList.value.splice(index, 1)
    })
    .catch((err) => {
      ElMessage.error(err.message || '删除图片失败')
    })
}

const goBack = () => {
  router.back()
}

const publishBlog = async () => {
  if (!params.title) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!params.content) {
    ElMessage.warning('请输入内容')
    return
  }
  if (fileList.value.length === 0) {
    ElMessage.warning('请上传图片')
    return
  }

  try {
    const blogData = {
      title: params.title,
      content: params.content,
      images: fileList.value.join(','),
      storeId: selectedStore.value ? selectedStore.value.id : null
    }

    await createBlog(blogData)
    ElMessage.success('发布成功')
    router.push('/InfoHtml')
  } catch (error) {
    console.error('发布失败', error)
    ElMessage.error('发布失败')
  }
}
</script>

<template>
  <div class="blog-edit">
    <div class="page-head">
      <div class="head-title-row">
        <h1 class="head-title">发布笔记</h1>
        <span class="head-sub">SHARE YOUR STYLE</span>
      </div>
      <div class="head-actions">
        <button class="jq-btn jq-btn-outline" @click="goBack">取消</button>
        <button class="jq-btn publish-btn" @click="publishBlog">发 布</button>
      </div>
    </div>

    <div class="edit-layout">
      <!-- 左：上传区 -->
      <div class="edit-left">
        <div class="panel-title">图片 <span class="muted">（最多 9 张）</span></div>
        <input
          type="file"
          @change="fileSelected"
          name="file"
          ref="fileInput"
          style="display: none"
        />
        <div class="pic-grid">
          <div class="pic-box" v-for="(f, i) in fileList" :key="i">
            <img :src="previews[i]" alt="图片" />
            <div class="pic-delete" @click="deletePic(i)">
              <el-icon><Close /></el-icon>
            </div>
          </div>
          <div class="upload-btn" @click="openFileDialog">
            <el-icon :size="26"><Plus /></el-icon>
            <span>上传图片</span>
          </div>
        </div>
      </div>

      <!-- 右：表单 -->
      <div class="edit-right">
        <div class="panel-title">笔记信息</div>

        <input
          type="text"
          v-model="params.title"
          placeholder="输入标题"
          class="title-input"
        />

        <textarea
          v-model="params.content"
          placeholder="分享你的潮流体验..."
          class="content-input"
        ></textarea>

        <div class="store-picker" @click="showDialog = true">
          <div class="store-label">关联店铺</div>
          <div class="store-value" v-if="selectedStore.name">
            {{ selectedStore.name }}
            <span class="muted">{{ selectedStore.area }}</span>
          </div>
          <div class="store-value placeholder" v-else>
            去选择 <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="showDialog"
      title="选择店铺"
      width="560px"
      :show-close="true"
      :close-on-click-modal="true"
    >
      <div class="search-bar">
        <div class="search-input">
          <el-icon @click="queryStores"><Search /></el-icon>
          <el-input
            v-model="storeName"
            placeholder="搜索店铺名称"
            @keyup.enter="queryStores"
          />
        </div>
      </div>
      <div class="store-list">
        <div
          v-for="s in stores"
          :key="s.id"
          class="store-item"
          @click="selectStore(s)"
        >
          <div class="store-item-name">{{ s.name }}</div>
          <div class="store-item-area">{{ s.area }}</div>
        </div>
        <div v-if="!stores.length" class="store-empty">没有找到店铺</div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.blog-edit {
  padding: 24px 0 48px;
}
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
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
.head-actions {
  display: flex;
  gap: 12px;
}
.publish-btn {
  height: 42px;
  padding: 0 32px;
  font-size: 15px;
}

.edit-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  align-items: start;
}
.edit-left,
.edit-right {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 24px;
}
.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 16px;
}
.muted {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 400;
}

/* 图片上传 */
.pic-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.pic-box {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--border);
}
.pic-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.pic-delete {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.2s;
}
.pic-delete:hover {
  background: var(--danger);
}
.upload-btn {
  aspect-ratio: 1 / 1;
  border: 2px dashed var(--border);
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
  font-size: 13px;
}
.upload-btn:hover {
  border-color: var(--accent);
  color: var(--accent-light);
  background: rgba(255, 77, 0, 0.05);
}

/* 表单 */
.title-input {
  width: 100%;
  height: 48px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
  padding: 0 16px;
  outline: none;
  margin-bottom: 14px;
  box-sizing: border-box;
}
.title-input:focus {
  border-color: var(--accent);
}
.content-input {
  width: 100%;
  height: 200px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: 14px;
  padding: 14px 16px;
  outline: none;
  resize: vertical;
  box-sizing: border-box;
  font-family: inherit;
  line-height: 1.7;
}
.content-input:focus {
  border-color: var(--accent);
}
.content-input::placeholder,
.title-input::placeholder {
  color: var(--text-muted);
}

.store-picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  margin-top: 14px;
  cursor: pointer;
  transition: border-color 0.2s;
}
.store-picker:hover {
  border-color: var(--accent);
}
.store-label {
  font-size: 14px;
  color: var(--text-secondary);
}
.store-value {
  font-size: 14px;
  color: var(--text-primary);
}
.store-value.placeholder {
  color: var(--text-muted);
}
.store-value .muted {
  margin-left: 8px;
}

/* 店铺选择 dialog */
.search-bar {
  margin-bottom: 14px;
}
.search-input {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 0 12px;
  background: var(--bg-secondary);
}
.search-input .el-icon {
  color: var(--text-muted);
  cursor: pointer;
}
.store-list {
  max-height: 360px;
  overflow-y: auto;
}
.store-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background 0.2s;
}
.store-item:hover {
  background: var(--card-hover);
}
.store-item-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.store-item-area {
  font-size: 13px;
  color: var(--text-muted);
}
.store-empty {
  text-align: center;
  padding: 40px 0;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .edit-layout {
    grid-template-columns: 1fr;
  }
  .pic-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
