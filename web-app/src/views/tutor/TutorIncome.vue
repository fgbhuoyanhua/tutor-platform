<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-label">本月收入</div>
          <div class="stat-num">¥{{ currentMonth.income }}</div>
          <div class="stat-sub">{{ currentMonth.orderCount }} 节已完成课程</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-label">累计收入</div>
          <div class="stat-num">¥{{ totalIncome }}</div>
          <div class="stat-sub">近6个月合计</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-label">累计课时</div>
          <div class="stat-num">{{ totalOrders }} 节</div>
          <div class="stat-sub">近6个月已完成</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="chart-card" shadow="never">
      <template #header>近6个月收入趋势</template>
      <div v-if="trend.length === 0" class="empty">暂无已完成课程收入数据</div>
      <div v-else class="chart">
        <div v-for="item in trend" :key="item.month" class="bar-col">
          <div class="bar-value">¥{{ item.totalIncome }}</div>
          <div class="bar-wrap">
            <div class="bar" :style="{ height: barHeight(item.totalIncome) }"></div>
          </div>
          <div class="bar-label">{{ item.month }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getIncomeTrend, type IncomeVO } from '@tutor-platform/frontend-common';

const trend = ref<IncomeVO[]>([]);
const loading = ref(false);

const currentMonth = computed(() => {
  const now = new Date();
  const key = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  const found = trend.value.find((t) => t.month === key);
  return {
    income: found ? Number(found.totalIncome).toFixed(2) : '0.00',
    orderCount: found ? found.orderCount : 0,
  };
});

const totalIncome = computed(() =>
  trend.value.reduce((s, t) => s + Number(t.totalIncome), 0).toFixed(2)
);
const totalOrders = computed(() => trend.value.reduce((s, t) => s + t.orderCount, 0));
const maxIncome = computed(() =>
  trend.value.length ? Math.max(...trend.value.map((t) => Number(t.totalIncome)), 1) : 1
);

function barHeight(v: number): string {
  const h = Math.max(4, (Number(v) / maxIncome.value) * 160);
  return `${h}px`;
}

onMounted(async () => {
  loading.value = true;
  try {
    trend.value = await getIncomeTrend();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.stat-label {
  color: #909399;
  font-size: 13px;
}
.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
  margin: 6px 0;
}
.stat-sub {
  color: #909399;
  font-size: 12px;
}
.chart-card {
  margin-top: 16px;
}
.chart {
  display: flex;
  align-items: flex-end;
  gap: 24px;
  height: 240px;
  padding: 12px 8px 0;
}
.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.bar-value {
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
.bar-wrap {
  flex: 1;
  display: flex;
  align-items: flex-end;
}
.bar {
  width: 36px;
  background: linear-gradient(180deg, #79bbff, #409eff);
  border-radius: 4px 4px 0 0;
  min-height: 4px;
}
.bar-label {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}
.empty {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}
</style>
