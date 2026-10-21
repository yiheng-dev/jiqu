import request from '@/utils/request'

// 上传图片
export function uploadBlogImage(data) {
  return request({
    url: '/upload/blog',
    method: 'post',
    data
  })
}

// 发布博客
export function publishBlog(data) {
  return request({
    url: '/blog',
    method: 'post',
    data
  })
}

// 查询附近的潮流店铺
export function queryNearbyStores(params) {
  return request({
    url: '/store/of/nearby',
    method: 'get',
    params
  })
}
// 查询潮流店铺: 根据名称 名称为空时 查询所有潮流店铺
export const queryStoresByName = (name) =>
  request.get('/store/of/name', { params: { name } })
// 创建博客
export const createBlog = (data) => request.post('/blog', data)
// 删除博客图片
export const deleteBlogImage = (name) =>
  request.delete('/upload/blog/delete', { params: { name } })
