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
          <el-card class="quick-card" shadow="hover" @click="$router.push('/orders')">
            <div class="quick-icon"><el-icon size="32"><List /></el-icon></div>
            <div class="quick-title">我的订单</div>
            <div class="quick-desc">查看预约进度，确认完成授课</div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <template v-if="userStore.isTutor">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/my-tutor')">
            <div class="quick-icon"><el-icon size="32"><User /></el-icon></div>
            <div class="quick-title">我的家教信息</div>
            <div class="quick-desc">发布/编辑授课科目、价格与介绍</div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="quick-card" shadow="hover" @click="$router.push('/orders')">
            <div class="quick-icon"><el-icon size="32"><List /></el-icon></div>
            <div class="quick-title">收到的预约</div>
            <div class="quick-desc">接单、拒绝学生发来的预约</div>
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
import { Search, List, User, Checked, UserFilled } from '@element-plus/icons-vue';
import { getStats, ROLE_LABELS, type StatsVO } from '@tutor-platform/frontend-common';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const stats = ref<Partial<StatsVO>>({});

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

onMounted(async () => {
  if (userStore.isAdmin) {
    try {
      stats.value = await getStats();
    } catch (e) {
      ElMessage.error((e as Error).message);
    }
  }
});
</script>

<style scoped>
.welcome-card {
  margin-bottom: 16px;
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
}
.quick-icon {
  color: #409eff;
  margin-bottom: 10px;
}
.quick-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}
.quick-desc {
  color: #909399;
  font-size: 13px;
}
.stat-card {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}
.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
}
.stat-label {
  color: #909399;
  margin-top: 4px;
}
</style>
