<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>管理员操作日志</span>
          <el-button size="small" @click="load(1)">刷新</el-button>
        </div>
      </template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="adminName" label="操作人" width="120" />
        <el-table-column label="操作类型" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="actionTag(row.action)">
              {{ row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作对象" width="130">
          <template #default="{ row }">
            <span v-if="row.targetType">
              {{ targetLabel(row.targetType) }} #{{ row.targetId }}
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 0"
        class="pager"
        background
        layout="prev, pager, next, total"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  pageAdminLogs,
  formatDateTime,
  type AdminLogEntity,
} from '@tutor-platform/frontend-common';

const loading = ref(false);
const list = ref<AdminLogEntity[]>([]);
const total = ref(0);
const query = reactive({ page: 1, size: 10 });

function actionTag(action: string): 'primary' | 'warning' | 'danger' | 'success' {
  if (action.includes('审核')) return 'warning';
  if (action.includes('状态')) return 'danger';
  return 'primary';
}

function targetLabel(type: string): string {
  if (type === 'tutor') return '家教';
  if (type === 'user') return '用户';
  return type;
}

onMounted(() => load(1));

async function load(page: number) {
  loading.value = true;
  try {
    query.page = page;
    const res = await pageAdminLogs({ page: query.page, size: query.size });
    list.value = res.records;
    total.value = res.total;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
