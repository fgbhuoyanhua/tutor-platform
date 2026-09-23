<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon income-icon">¥</div>
          <div class="stat-info">
            <div class="stat-label">累计收入</div>
            <div class="stat-num">¥{{ Number(dash.totalIncome).toFixed(2) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon order-icon">📋</div>
          <div class="stat-info">
            <div class="stat-label">累计订单</div>
            <div class="stat-num">{{ dash.totalOrders }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon done-icon">✓</div>
          <div class="stat-info">
            <div class="stat-label">已完成</div>
            <div class="stat-num">{{ dash.completedOrders }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon pending-icon">⏳</div>
          <div class="stat-info">
            <div class="stat-label">待确认</div>
            <div class="stat-num">{{ dash.pendingOrders }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="14">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="chart-title">近6个月收入趋势</span>
          </template>
          <div ref="trendRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="chart-title">订单状态分布</span>
          </template>
          <div ref="pieRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :span="24">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="chart-title">学科收入分布</span>
          </template>
          <div ref="barRef" class="chart chart-wide"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { getTutorDashboard, type TutorDashboardVO } from '@tutor-platform/frontend-common';

const dash = ref<TutorDashboardVO>({
  totalIncome: 0,
  totalOrders: 0,
  completedOrders: 0,
  pendingOrders: 0,
  incomeTrend: [],
  subjectIncome: [],
  statusDistribution: [],
});

const trendRef = ref<HTMLElement>();
const pieRef = ref<HTMLElement>();
const barRef = ref<HTMLElement>();
let trendChart: echarts.ECharts | null = null;
let pieChart: echarts.ECharts | null = null;
let barChart: echarts.ECharts | null = null;

const COLORS = ['#3b82f6', '#06b6d4', '#8b5cf6', '#f59e0b', '#10b981', '#ef4444'];

function initCharts() {
  if (trendRef.value) {
    trendChart = echarts.init(trendRef.value);
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        data: dash.value.incomeTrend.map((t) => t.month),
        axisLine: { lineStyle: { color: '#ddd' } },
        axisLabel: { color: '#666' },
      },
      yAxis: {
        type: 'value',
        name: '收入(元)',
        axisLabel: { color: '#666' },
        splitLine: { lineStyle: { color: '#f0f0f0' } },
      },
      series: [
        {
          name: '收入',
          type: 'line',
          smooth: true,
          data: dash.value.incomeTrend.map((t) => Number(t.totalIncome)),
          itemStyle: { color: '#3b82f6' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(59,130,246,0.3)' },
              { offset: 1, color: 'rgba(59,130,246,0.02)' },
            ]),
          },
          lineStyle: { width: 3 },
          symbol: 'circle',
          symbolSize: 8,
        },
      ],
    });
  }

  if (pieRef.value) {
    pieChart = echarts.init(pieRef.value);
    pieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', right: 10, top: 'center', textStyle: { fontSize: 12 } },
      series: [
        {
          type: 'pie',
          radius: ['40%', '70%'],
          center: ['40%', '50%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          data: dash.value.statusDistribution.map((s, i) => ({
            name: s.statusName,
            value: s.count,
            itemStyle: { color: COLORS[i % COLORS.length] },
          })),
        },
      ],
    });
  }

  if (barRef.value) {
    barChart = echarts.init(barRef.value);
    barChart.setOption({
      tooltip: { trigger: 'axis', formatter: (p: any) => `${p[0].name}<br/>收入: ¥${p[0].value}<br/>订单: ${p[0].data.orderCount}` },
      grid: { left: 50, right: 20, top: 30, bottom: 30 },
      xAxis: {
        type: 'category',
        data: dash.value.subjectIncome.map((s) => s.subjectName),
        axisLabel: { color: '#666' },
      },
      yAxis: {
        type: 'value',
        name: '收入(元)',
        axisLabel: { color: '#666' },
        splitLine: { lineStyle: { color: '#f0f0f0' } },
      },
      series: [
        {
          type: 'bar',
          barWidth: '40%',
          data: dash.value.subjectIncome.map((s) => ({
            value: Number(s.income),
            orderCount: s.orderCount,
          })),
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#06b6d4' },
              { offset: 1, color: '#3b82f6' },
            ]),
          },
        },
      ],
    });
  }
}

function handleResize() {
  trendChart?.resize();
  pieChart?.resize();
  barChart?.resize();
}

onMounted(async () => {
  try {
    dash.value = await getTutorDashboard();
    await nextTick();
    initCharts();
    window.addEventListener('resize', handleResize);
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  trendChart?.dispose();
  pieChart?.dispose();
  barChart?.dispose();
});
</script>

<style scoped>
.dashboard {
  padding: 0;
}
.stat-card {
  border-radius: 12px;
  border: none;
}
.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}
.income-icon { background: linear-gradient(135deg, #3b82f6, #06b6d4); }
.order-icon { background: linear-gradient(135deg, #8b5cf6, #6366f1); font-size: 20px; }
.done-icon { background: linear-gradient(135deg, #10b981, #059669); }
.pending-icon { background: linear-gradient(135deg, #f59e0b, #f97316); font-size: 20px; }
.stat-label { color: #909399; font-size: 13px; }
.stat-num { font-size: 26px; font-weight: 700; color: #1f2d3d; margin-top: 4px; }
.chart-row { margin-top: 16px; }
.chart-card { border-radius: 12px; border: none; }
.chart-title { font-weight: 600; font-size: 15px; color: #1f2d3d; }
.chart { height: 300px; }
.chart-wide { height: 280px; }
</style>
