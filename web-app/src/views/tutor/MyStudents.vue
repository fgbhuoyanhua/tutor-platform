<template>
  <el-card v-loading="loading">
    <template #header>我的学生（{{ list.length }} 人）</template>
    <el-empty v-if="!loading && list.length === 0" description="还没有上过课的学生" />
    <el-row :gutter="16">
      <el-col v-for="s in list" :key="s.studentId" :span="8">
        <el-card shadow="hover" class="student-card">
          <div class="head">
            <el-avatar :size="40">{{ s.studentName?.[0] || '学' }}</el-avatar>
            <div class="info">
              <div class="name">{{ s.studentName }}</div>
              <div class="last">最近上课：{{ s.lastDate || '—' }}</div>
            </div>
          </div>
          <div class="stats">
            <div class="stat">
              <div class="num">{{ s.orderCount }}</div>
              <div class="label">总预约</div>
            </div>
            <div class="stat">
              <div class="num">{{ s.finishedCount }}</div>
              <div class="label">已完成</div>
            </div>
            <div class="stat">
              <div class="num">{{ s.orderCount - s.finishedCount }}</div>
              <div class="label">待上</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getMyStudents, type MyStudentVO } from '@tutor-platform/frontend-common';

const loading = ref(false);
const list = ref<MyStudentVO[]>([]);

onMounted(async () => {
  loading.value = true;
  try {
    list.value = await getMyStudents();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.student-card {
  margin-bottom: 16px;
}
.head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.name {
  font-weight: 600;
  font-size: 15px;
}
.last {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.stats {
  display: flex;
  margin-top: 14px;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}
.stat {
  flex: 1;
  text-align: center;
}
.num {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
}
.label {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
</style>
