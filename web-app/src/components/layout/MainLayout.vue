<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">家教预约平台</div>
      <el-menu :default-active="activePath" router background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#409eff">
        <el-menu-item index="/home">
          <el-icon><HomeFilled /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isStudent" index="/tutors">
          <el-icon><Search /></el-icon>
          <span>找家教</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isTutor" index="/my-tutor">
          <el-icon><User /></el-icon>
          <span>我的家教信息</span>
        </el-menu-item>
        <template v-if="userStore.isTutor">
          <el-menu-item index="/tutor/calendar">
            <el-icon><Calendar /></el-icon>
            <span>授课日历</span>
          </el-menu-item>
          <el-menu-item index="/tutor/students">
            <el-icon><UserFilled /></el-icon>
            <span>我的学生</span>
          </el-menu-item>
          <el-menu-item index="/tutor/evaluations">
            <el-icon><Star /></el-icon>
            <span>我的评价</span>
          </el-menu-item>
          <el-menu-item index="/tutor/income">
            <el-icon><Money /></el-icon>
            <span>收入统计</span>
          </el-menu-item>
        </template>
        <el-menu-item index="/orders">
          <el-icon><List /></el-icon>
          <span>我的订单</span>
        </el-menu-item>
        <el-menu-item v-if="!userStore.isAdmin" index="/messages">
          <el-icon><ChatDotRound /></el-icon>
          <span>我的消息</span>
        </el-menu-item>
        <template v-if="userStore.isAdmin">
          <el-menu-item index="/admin/stats">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据统计</span>
          </el-menu-item>
          <el-menu-item index="/admin/tutor-audit">
            <el-icon><Checked /></el-icon>
            <span>家教审核</span>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><UserFilled /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/logs">
            <el-icon><Document /></el-icon>
            <span>操作日志</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ route.meta.title }}</span>
        <div class="header-right">
          <el-popover v-model:visible="notifyVisible" placement="bottom-end" :width="360" trigger="click">
            <template #reference>
              <el-badge :value="unread" :hidden="unread === 0" class="bell">
                <el-button circle>
                  <el-icon><Bell /></el-icon>
                </el-button>
              </el-badge>
            </template>
            <div class="notify-pop">
              <div class="notify-head">
                <span>通知中心</span>
                <el-button link type="primary" @click="readAll">全部已读</el-button>
              </div>
              <div v-if="notices.length === 0" class="notify-empty">暂无通知</div>
              <div v-for="n in notices" :key="n.id" class="notify-item" :class="{ unread: n.isRead === 0 }">
                <div class="notify-top">
                  <el-tag size="small" :type="notifyTagType(n.type)">{{ messageTypeLabel(n.type) }}</el-tag>
                  <span class="notify-time">{{ formatDateTime(n.createTime) }}</span>
                </div>
                <div class="notify-content">{{ n.content }}</div>
              </div>
            </div>
          </el-popover>
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.user?.realName || userStore.user?.username }}
              <el-tag size="small" :type="roleTagType" class="role-tag">{{ roleLabel }}</el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  HomeFilled,
  Search,
  User,
  List,
  DataAnalysis,
  Checked,
  UserFilled,
  Document,
  ChatDotRound,
  Bell,
  Calendar,
  Star,
  Money,
} from '@element-plus/icons-vue';
import {
  ROLE_LABELS,
  listMessages,
  unreadCount,
  markAllRead,
  messageTypeLabel,
  formatDateTime,
  type MessageEntity,
} from '@tutor-platform/frontend-common';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const activePath = computed(() => route.path);
const roleLabel = computed(() => ROLE_LABELS[userStore.role] ?? '访客');
const roleTagType = computed(() =>
  userStore.isAdmin ? 'danger' : userStore.isTutor ? 'warning' : 'primary'
);

const unread = ref(0);
const notices = ref<MessageEntity[]>([]);
const notifyVisible = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;

async function refreshUnread() {
  try {
    unread.value = await unreadCount();
  } catch { /* 忽略轮询失败 */ }
}

async function openNotices() {
  notices.value = await listMessages();
  await refreshUnread();
}

async function readAll() {
  await markAllRead();
  notices.value.forEach((n) => (n.isRead = 1));
  unread.value = 0;
}

watch(notifyVisible, (v) => {
  if (v) openNotices();
});

function notifyTagType(type: number): 'primary' | 'success' | 'warning' | 'info' {
  if (type === 1) return 'warning';
  if (type === 2) return 'primary';
  if (type === 3) return 'info';
  return 'success';
}

onMounted(() => {
  refreshUnread();
  timer = setInterval(refreshUnread, 30000);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.logout();
    router.push('/login');
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: linear-gradient(180deg, #1a1d2e 0%, #252a42 100%);
  box-shadow: 2px 0 12px rgba(0,0,0,0.1);
}
.logo {
  height: 64px;
  line-height: 64px;
  text-align: center;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1px;
  background: rgba(255,255,255,0.05);
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.aside :deep(.el-menu) {
  border-right: none;
  background: transparent;
}
.aside :deep(.el-menu-item) {
  color: #a0aec0;
  transition: all 0.2s ease;
  border-radius: 8px;
  margin: 4px 12px;
  height: 44px;
  line-height: 44px;
}
.aside :deep(.el-menu-item:hover) {
  background: rgba(79,110,247,0.15);
  color: #fff;
}
.aside :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #4f6ef7, #6b8afd);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(79,110,247,0.35);
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 24px;
  height: 60px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.bell :deep(.el-badge__content) {
  z-index: 1;
}
.notify-pop {
  max-height: 400px;
  overflow-y: auto;
}
.notify-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
}
.notify-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.notify-item.unread {
  background: #f7faff;
}
.notify-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
}
.notify-time {
  font-size: 11px;
  color: #909399;
}
.notify-content {
  font-size: 13px;
  color: #333;
  line-height: 1.5;
}
.notify-empty {
  color: #909399;
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
}
.page-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text);
}
.user-info {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #333;
  padding: 6px 12px;
  border-radius: 8px;
  transition: background 0.2s;
}
.user-info:hover {
  background: #f5f7fa;
}
.role-tag {
  margin-left: 4px;
}
.main {
  background: var(--bg);
  padding: 24px;
}
</style>
