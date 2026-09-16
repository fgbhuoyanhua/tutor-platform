<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
:root {
  --primary: #3b82f6;
  --primary-light: #60a5fa;
  --primary-dark: #2563eb;
  --accent: #06b6d4;
  --success: #10b981;
  --warning: #f59e0b;
  --danger: #ef4444;
  --bg: #f8fafc;
  --card: #ffffff;
  --text: #0f172a;
  --text-secondary: #64748b;
  --text-muted: #94a3b8;
  --border: #e2e8f0;
  --radius: 12px;
  --shadow-sm: 0 1px 3px rgba(0,0,0,0.05);
  --shadow-md: 0 4px 12px rgba(0,0,0,0.08);
  --shadow-lg: 0 8px 24px rgba(0,0,0,0.12);

  /* Element Plus 主题覆盖 */
  --el-color-primary: #3b82f6;
  --el-color-primary-light-3: #60a5fa;
  --el-color-primary-light-5: #93c5fd;
  --el-color-primary-light-7: #bfdbfe;
  --el-color-primary-light-8: #dbeafe;
  --el-color-primary-light-9: #eff6ff;
  --el-color-primary-dark-2: #2563eb;
  --el-border-radius-base: 8px;
  --el-border-radius-small: 6px;
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

/* 按钮 */
.el-button {
  border-radius: 8px;
  font-weight: 500;
}
.el-button--primary {
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
  border: none;
  box-shadow: 0 4px 12px rgba(59,130,246,0.25);
}
.el-button--primary:hover {
  background: linear-gradient(135deg, #2563eb, #0891b2);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(59,130,246,0.35);
}
.el-button--primary:active {
  transform: translateY(0);
}

/* 卡片 */
.el-card {
  border-radius: var(--radius);
  border: none;
  box-shadow: var(--shadow-sm);
}

/* 输入框 */
.el-input__wrapper,
.el-textarea__inner {
  border-radius: 8px;
  box-shadow: 0 0 0 1px var(--border) inset;
}
.el-input__wrapper.is-focus,
.el-textarea__inner:focus {
  box-shadow: 0 0 0 2px var(--primary) inset !important;
}

/* 表格 */
.el-table {
  border-radius: var(--radius);
  overflow: hidden;
}
.el-table th.el-table__cell {
  background: #f8fafc;
  color: var(--text-secondary);
  font-weight: 600;
}
.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell {
  background: #fafbfd;
}

/* 菜单 */
.el-menu {
  border-right: none !important;
}

/* 标签 */
.el-tag {
  border-radius: 6px;
  font-weight: 500;
}

/* 弹窗 */
.el-dialog {
  border-radius: 16px;
}
.el-dialog__header {
  border-bottom: 1px solid var(--border);
  padding-bottom: 16px;
}

/* 分页 */
.el-pagination {
  margin-top: 16px;
}

/* 滚动条 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}
::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
</style>
