import request from '@/utils/request'
export const getDropList = (storeId) => request.get('/drop/list/' + storeId)

// 发售日历：进行中 + 即将发售
export const getDropCalendar = () => request.get('/drop/calendar')

// 查询单个发售详情（含所属店铺 id，日历接口不返回 storeId）
export const getDropById = (dropId) =>
  request.post('/drop/get', {
    dropId: String(dropId)
  })

// 获取发售访问令牌
export const issueDropAccessToken = (id) =>
  request.get('/drop-order/drop/token/' + id)

// 携带令牌进行发售下单
export const limitedDrop = (id, accessToken) =>
  request.post(`/drop-order/drop/${id}`,
    null,
    { params: { accessToken } }
  )
// 轮询查询发售订单是否生成
export const getDropOrderId = (orderId) =>
  request.post('/drop-order/get/drop-order-id', {
    orderId: String(orderId)
  })

// 进入页面或发售成功后，查询用户是否已购买该发售
export const getDropOrderIdByDropId = (dropId) =>
  request.post('/drop-order/get/drop-order-id/by/drop-id', {
    dropId: String(dropId)
  })

// 取消已领取的优惠券
export const cancelDropOrder = (dropId) =>
  request.post('/drop-order/cancel', {
    dropId: String(dropId)
  })

// 订阅到券提醒（加入自动发券队列）
export const subscribeDrop = (dropId) =>
  request.post('/drop/subscribe', {
    dropId: String(dropId)
  })

// 取消订阅（从队列移除）
export const unsubscribeDrop = (dropId) =>
  request.post('/drop/unsubscribe', {
    dropId: String(dropId)
  })

// 查询当前用户订阅状态（单个券）：0 未订阅或已取消；1 已订阅；2 自动发券成功
export const getSubscribeStatus = (dropId) =>
  request.post('/drop/get/subscribe/status', {
    dropId: String(dropId)
  })

// 批量查询订阅状态（页面初始化使用）
export const getSubscribeStatusBatch = (dropIdList) =>
  request.post('/drop/get/subscribe/status/batch', {
    dropIdList: (dropIdList || []).map((id) => String(id))
  })
