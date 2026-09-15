<template>
  <el-card v-loading="loading">
    <template #header>
      <span>学生给我的评价</span>
      <span v-if="list.length" class="summary">共 {{ list.length }} 条 · 平均分 {{ avgScore }}</span>
    </template>
    <el-empty v-if="!loading && list.length === 0" description="还没有学生评价" />
    <div v-for="ev in list" :key="ev.id" class="eval-item">
      <div class="eval-head">
        <el-avatar :size="32">{{ ev.studentName?.[0] || '学' }}</el-avatar>
        <div class="who">
          <div class="name">{{ ev.studentName }}</div>
          <div class="time">{{ formatDateTime(ev.createTime) }}</div>
        </div>
        <el-rate :model-value="ev.score" disabled />
      </div>
      <div class="content">{{ ev.content || '这个学生很优秀' }}</div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listTutorEvaluations, formatDateTime, type EvaluationVO } from '@tutor-platform/frontend-common';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const loading = ref(false);
const list = ref<EvaluationVO[]>([]);

const avgScore = computed(() => {
  if (!list.value.length) return '-';
  const sum = list.value.reduce((s, e) => s + e.score, 0);
  return (sum / list.value.length).toFixed(1);
});

onMounted(async () => {
  loading.value = true;
  try {
    const res = await listTutorEvaluations(userStore.user!.userId, 1, 50);
    list.value = res.records;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.summary {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
.eval-item {
  padding: 14px 0;
  border-bottom: 1px solid #ebeef5;
}
.eval-item:last-child {
  border-bottom: none;
}
.eval-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.who {
  flex: 1;
}
.name {
  font-weight: 600;
  font-size: 14px;
}
.time {
  color: #909399;
  font-size: 12px;
}
.content {
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}
</style>
