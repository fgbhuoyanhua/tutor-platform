<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
:root {
  --primary: #4f6ef7;
  --primary-light: #6b8afd;
  --primary-dark: #3a55d4;
  --success: #67c23a;
  --warning: #e6a23c;
  --danger: #f56c6c;
  --bg: #f0f2f5;
  --card: #ffffff;
  --text: #1a1b1c;
  --text-secondary: #6b7280;
  --text-muted: #9ca3af;
  --border: #e5e7eb;
  --radius: 12px;
  --shadow-sm: 0 1px 3px rgba(0,0,0,0.05);
  --shadow-md: 0 4px 12px rgba(0,0,0,0.08);
  --shadow-lg: 0 8px 24px rgba(0,0,0,0.12);
}

html,
body,
#app {
  height: 100%;
  margin: 0;
  padding: 0;
  background: var(--bg);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* Element Plus 主题覆盖 */
.el-button--primary {
  --el-button-bg-color: var(--primary);
  --el-button-border-color: var(--primary);
  --el-button-hover-bg-color: var(--primary-light);
  --el-button-hover-border-color: var(--primary-light);
  --el-button-active-bg-color: var(--primary-dark);
  --el-button-active-border-color: var(--primary-dark);
}

.el-card {
  border-radius: var(--radius);
  border: none;
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.25s ease;
}

.el-card:hover {
  box-shadow: var(--shadow-md);
}

.el-menu {
  border-right: none !important;
}

/* 滚动条美化 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}
::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}
</style>
