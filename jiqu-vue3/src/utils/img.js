// 图片资源统一解析：所有业务图片替换为 picsum.photos 种子图（稳定、可缓存）
const PICSUM = 'https://picsum.photos/seed'

const picsum = (seed, w = 600, h = 600) =>
  `${PICSUM}/${encodeURIComponent(String(seed))}/${w}/${h}`

// 单图
export const img = (seed, w = 600, h = 600) => picsum(seed, w, h)

// 多图：raw 为逗号串或数组，空给一张兜底；第 i 张 seed=`${seed}-${i}` 保证各不相同且稳定
export const imgs = (raw, seed, w = 600, h = 600) => {
  const arr = Array.isArray(raw) ? raw : String(raw || '').split(',').filter(Boolean)
  if (!arr.length) return [picsum(seed, w, h)]
  return arr.map((_, i) => picsum(`${seed}-${i}`, w, h))
}

export const imgFirst = (raw, seed, w = 600, h = 600) => imgs(raw, seed, w, h)[0]

// 头像独立 seed，避免与内容图混淆
export const avatar = (userId, s = 200) => picsum(`avatar-${userId}`, s, s)
