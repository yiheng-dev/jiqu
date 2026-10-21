import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/index'
    },
    {
      path: '/login',
      name: 'LoginPage',
      component: () => import('@/views/login/LoginPage.vue'),
      meta: { bare: true }
    },
    {
      path: '/register',
      name: 'RegisterPage',
      component: () => import('@/views/register/RegisterPage.vue'),
      meta: { bare: true }
    },
    {
      path: '/index',
      name: 'indexPage',
      component: () => import('@/views/index.vue')
    },
    {
      path: '/storeList',
      component: () => import('@/views/store/StoreList.vue')
    },
    {
      path: '/storeDetail/:id?',
      component: () => import('@/views/store/StoreDetail.vue')
    },
    {
      path: '/dropList',
      component: () => import('@/views/drops/DropList.vue')
    },
    {
      path: '/aiAssistant',
      component: () => import('@/views/ai/AiAssistant.vue')
    },
    {
      path: '/blogDetail/:id?', // 动态参数 `:id`
      component: () => import('@/views/blog/BlogDetail.vue')
    },
    {
      path: '/blogEdit',
      component: () => import('@/views/blog/BlogEdit.vue')
    },
    {
      path: '/InfoHtml',
      component: () => import('@/views/info/InfoHtml.vue')
    },
    {
      path: '/InfoEdit',
      component: () => import('@/views/info/InfoEdit.vue')
    },
    {
      path: '/InfoOther/:id?',
      component: () => import('@/views/info/InfoOther.vue')
    }
  ]
})

export default router
