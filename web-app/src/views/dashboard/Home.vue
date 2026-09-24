<template>
  <div class="home">
    <el-card class="welcome-card">
      <h2>你好，{{ userStore.user?.realName || userStore.user?.username }}！</h2>
      <p class="sub">
        <el-tag :type="roleTagType" size="small">{{ roleLabel }}</el-tag>
        <span class="tip">{{ roleTip }}</span>
      </p>
    </el-card>

    <template v-if="userStore.isStudent">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/tutors')">
            <div class="quick-icon"><el-icon size="32"><Search /></el-icon></div>
            <div class="quick-title">找家教</div>
            <div class="quick-desc">浏览家教信息，在线预约上课时间</div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/student/calendar')">
            <div class="quick-icon"><el-icon size="32"><Calendar /></el-icon></div>
            <div class="quick-title">我的课程表</div>
            <div class="quick-desc">查看本周课程安排，一目了然</div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <template v-if="userStore.isTutor">
      <!-- 快捷入口 -->
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/my-tutor')">
            <div class="quick-icon"><el-icon size="28"><User /></el-icon></div>
            <div class="quick-title">家教信息</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/tutor/calendar')">
            <div class="quick-icon"><el-icon size="28"><Calendar /></el-icon></div>
            <div class="quick-title">授课日历</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/orders')">
            <div class="quick-icon"><el-icon size="28"><List /></el-icon></div>
            <div class="quick-title">收到的预约</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/tutor/income')">
            <div class="quick-icon"><el-icon size="28"><Money /></el-icon></div>
            <div class="quick-title">收入统计</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 今日课程 + 待处理预约 -->
      <el-row :gutter="16" class="panel-row">
        <el-col :span="14">
          <el-card shadow="never" class="panel-card">
            <template #header>
              <div class="panel-head">
                <span class="panel-title">📅 今日课程</span>
                <el-tag size="small" type="primary">{{ todayLessons.length }} 节</el-tag>
              </div>
            </template>
            <div v-if="todayLessons.length === 0" class="panel-empty">今天没有课程安排</div>
            <div v-for="l in todayLessons" :key="l.id" class="lesson-item">
              <div class="lesson-time">{{ l.timeSlot }}</div>
              <div class="lesson-info">
                <div class="lesson-name">{{ l.studentName }} · {{ l.subjectName }}</div>
                <el-tag size="small" :type="lessonTagType(l.status)">{{ statusLabel(l.status) }}</el-tag>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="never" class="panel-card">
            <template #header>
              <div class="panel-head">
                <span class="panel-title">⏳ 待处理预约</span>
                <el-tag size="small" type="warning">{{ pendingOrders.length }} 条</el-tag>
              </div>
            </template>
            <div v-if="pendingOrders.length === 0" class="panel-empty">暂无待处理预约</div>
            <div v-for="o in pendingOrders" :key="o.id" class="pending-item">
              <div class="pending-info">
                <div class="pending-name">{{ o.studentName }} · {{ o.subjectName }}</div>
                <div class="pending-date">{{ o.appointDate }} {{ o.timeSlot }}</div>
              </div>
              <el-button size="small" type="primary" @click="$router.push('/orders')">处理</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <template v-if="userStore.isAdmin">
      <el-row :gutter="16" class="stat-row">
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-num">{{ stats.userCount ?? '-' }}</div>
            <div class="stat-label">注册用户</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-num">{{ stats.tutorCount ?? '-' }}</div>
            <div class="stat-label">家教信息</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-num">{{ stats.orderCount ?? '-' }}</div>
            <div class="stat-label">总订单</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-num">{{ stats.finishedOrderCount ?? '-' }}</div>
            <div class="stat-label">已完成订单</div>
          </el-card>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/admin/tutor-audit')">
            <div class="quick-icon"><el-icon size="32"><Checked /></el-icon></div>
            <div class="quick-title">家教审核</div>
            <div class="quick-desc">审核老师发布的待上架信息</div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/admin/users')">
            <div class="quick-icon"><el-icon size="32"><UserFilled /></el-icon></div>
            <div class="quick-title">用户管理</div>
            <div class="quick-desc">查看用户列表，禁用违规账号</div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Search, List, User, Checked, UserFilled, Calendar, Money } from '@element-plus/icons-vue';
import { getStats, pageOrders, ROLE_LABELS, type StatsVO, type OrderVO } from '@tutor-platform/frontend-common';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const stats = ref<Partial<StatsVO>>({});
const allOrders = ref<OrderVO[]>([]);

const roleLabel = computed(() => ROLE_LABELS[userStore.role] ?? '访客');
const roleTagType = computed(() =>
  userStore.isAdmin ? 'danger' : userStore.isTutor ? 'warning' : 'primary'
);
const roleTip = computed(() => {
  if (userStore.isStudent) return '选择心仪的家教，提交预约，等待老师接单';
  if (userStore.isTutor) return '完善你的家教信息，接单学生预约';
  if (userStore.isAdmin) return '审核家教信息、管理平台用户、查看数据统计';
  return '';
});

function todayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const todayLessons = computed(() => {
  const today = todayStr();
  return allOrders.value
    .filter((o) => o.appointDate === today && (o.status === 1 || o.status === 2 || o.status === 3))
    .sort((a, b) => (a.timeSlot < b.timeSlot ? -1 : 1));
});

const pendingOrders = computed(() => {
  return allOrders.value.filter((o) => o.status === 0).slice(0, 5);
});

function statusLabel(s: number): string {
  if (s === 1) return '已预约';
  if (s === 2) return '授课中';
  if (s === 3) return '已完成';
  return '';
}

function lessonTagType(s: number): 'primary' | 'warning' | 'success' {
  if (s === 2) return 'warning';
  if (s === 3) return 'success';
  return 'primary';
}

onMounted(async () => {
  if (userStore.isAdmin) {
    try {
      stats.value = await getStats();
    } catch (e) {
      ElMessage.error((e as Error).message);
    }
  }
  if (userStore.isTutor) {
    try {
      const res = await pageOrders({ page: 1, size: 200 });
      allOrders.value = res.records;
    } catch (e) {
      ElMessage.error((e as Error).message);
    }
  }
});
</script>

<style scoped>
.home {
  padding: 0;
}
.welcome-card {
  margin-bottom: 16px;
  border: none;
  border-radius: 12px;
}
.welcome-card h2 {
  margin: 0 0 8px;
}
.sub {
  display: flex;
  align-items: center;
  gap: 10px;
}
.tip {
  color: #909399;
  font-size: 14px;
}
.quick-card {
  cursor: pointer;
  text-align: center;
  padding: 12px 0;
  margin-bottom: 16px;
  border: none;
  border-radius: 12px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.quick-card:hover {
  transform: translateY(-2px);
}
.quick-icon {
  color: #4f6ef7;
  margin-bottom: 10px;
}
.quick-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
}
.quick-desc {
  color: #909399;
  font-size: 12px;
}
.panel-row {
  margin-top: 0;
}
.panel-card {
  border: none;
  border-radius: 12px;
  margin-bottom: 16px;
}
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-title {
  font-weight: 600;
  font-size: 15px;
}
.panel-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 32px 0;
  font-size: 13px;
}
.lesson-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fa;
}
.lesson-item:last-child {
  border-bottom: none;
}
.lesson-time {
  font-size: 18px;
  font-weight: 700;
  color: #4f6ef7;
  min-width: 100px;
}
.lesson-info {
  flex: 1;
}
.lesson-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}
.pending-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fa;
}
.pending-item:last-child {
  border-bottom: none;
}
.pending-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}
.pending-date {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.stat-card {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
  border: none;
  border-radius: 12px;
}
.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #4f6ef7;
}
.stat-label {
  color: #909399;
  margin-top: 4px;
}
</style>
