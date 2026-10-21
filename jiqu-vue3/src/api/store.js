import request from '@/utils/request'

export const getStoreCategoryList = () => request.get('/store-category/list')

export const getStoreList = (params) => request.get('/store/of/type', { params })
export const getStoreById = (id) => request.get('/store/' + id)
