<template>
  <div>
    <el-row :gutter="16" v-loading="loading">
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
    <el-card class="chart-card">
      <h4>订单完成率</h4>
      <el-progress
        :percentage="finishRate"
        :stroke-width="18"
        :format="() => `${finishRate}%`"
        color="#67c23a"
      />
      <p class="chart-tip">已完成 {{ stats.finishedOrderCount ?? 0 }} / 总订单 {{ stats.orderCount ?? 0 }}</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getStats, type StatsVO } from '@tutor-platform/frontend-common';

const loading = ref(false);
const stats = ref<StatsVO>({ userCount: 0, tutorCount: 0, orderCount: 0, finishedOrderCount: 0 });

const finishRate = computed(() => {
  if (!stats.value.orderCount) return 0;
  return Math.round((stats.value.finishedOrderCount / stats.value.orderCount) * 100);
});

onMounted(async () => {
  loading.value = true;
  try {
    stats.value = await getStats();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.stat-card {
  text-align: center;
  padding: 12px 0;
}
.stat-num {
  font-size: 32px;
  font-weight: 700;
  color: #409eff;
}
.stat-label {
  color: #909399;
  margin-top: 6px;
}
.chart-card {
  margin-top: 16px;
}
.chart-tip {
  color: #909399;
  font-size: 13px;
}
</style>
