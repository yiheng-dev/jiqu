import request from '@/utils/request'
//   getBlogById,
// getStoreById,
//   getBlogLikes,
//   likeBlog,
//   isFollowed,
//   follow
export const getBlogById = (id) => request.get(`/blog/${id}`)
export const getStoreById = (id) => request.get(`/store/${id}`)
export const getBlogLikes = (id) => request.get(`/blog/likes/${id}`)
export const likeBlog = (id) => request.put(`/blog/like/${id}`)
export const isFollowed = (id) => request.get(`/follow/or/not/${id}`)
export const follow = (id, followed) =>
  request.put(`/follow/${id}/${followed}`)
