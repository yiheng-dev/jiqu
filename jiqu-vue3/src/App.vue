<script setup>
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'

const route = useRoute()

// 挂载暗色主题 class（Element Plus 组件变量依赖 html.dark）
const html = document.documentElement
if (!html.classList.contains('dark')) {
  html.classList.add('dark')
}

const isBare = () => !!route.meta.bare
</script>

<template>
  <div class="app-shell">
    <AppHeader v-if="!isBare()" />
    <main class="app-main">
      <div class="app-container" :class="{ 'is-bare': isBare() }">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>
    <AppFooter v-if="!isBare()" />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
}
.app-main {
  flex: 1;
  width: 100%;
}
.app-container {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: 0 20px;
  flex: 1;
}
.app-container.is-bare {
  max-width: none;
  padding: 0;
}
</style>
