import { onMounted, onBeforeUnmount } from 'vue'
import { throttle } from '@/utils/scroll'

// 桌面端 window 滚动加载：距底部 threshold 触发 loadMore（防重：加载中不重复触发）
export function useInfiniteScroll(loadMore, { threshold = 80, interval = 200 } = {}) {
  let busy = false
  const onScroll = throttle(async () => {
    if (busy) return
    const doc = document.documentElement
    const scrollTop = doc.scrollTop || document.body.scrollTop
    const scrollHeight = doc.scrollHeight
    const clientHeight = doc.clientHeight
    if (scrollTop + clientHeight >= scrollHeight - threshold) {
      busy = true
      try {
        await loadMore()
      } finally {
        busy = false
      }
    }
  }, interval)

  onMounted(() => window.addEventListener('scroll', onScroll))
  onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
}
