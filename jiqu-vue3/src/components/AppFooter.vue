<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
const links = [
  { label: '关于集趣', path: '/' },
  { label: '限量发售规则', path: '/dropList' },
  { label: '帮助中心', path: '/' },
  { label: '联系我们', path: '/' }
]
</script>

<template>
  <footer class="app-footer">
    <div class="footer-line"></div>
    <div class="footer-inner">
      <div class="footer-brand">
        <span class="footer-logo">集趣</span>
        <span class="footer-slogan">发现潮流 · 抢到限量 · 晒出态度</span>
      </div>
      <div class="footer-links">
        <span
          v-for="l in links"
          :key="l.label"
          class="footer-link"
          @click="router.push(l.path)"
        >{{ l.label }}</span>
      </div>
      <div class="footer-copy">© 2025 集趣·潮流社区 · All rights reserved</div>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  background: var(--bg-secondary);
  border-top: none;
}
.footer-line {
  height: 2px;
  background: var(--accent-gradient);
}
.footer-inner {
  max-width: var(--container-max);
  margin: 0 auto;
  padding: 32px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.footer-logo {
  font-size: 20px;
  font-weight: var(--font-black);
  background: var(--accent-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.footer-slogan {
  font-size: 13px;
  color: var(--text-muted);
}
.footer-links {
  display: flex;
  gap: 24px;
}
.footer-link {
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: color 0.2s;
}
.footer-link:hover {
  color: var(--accent-light);
}
.footer-copy {
  font-size: 12px;
  color: var(--text-muted);
}
</style>
