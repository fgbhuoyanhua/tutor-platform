<template>
  <div>
    <el-card>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="roleTag(row.role)">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.role !== ROLE_ADMIN"
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              size="small"
              @click="toggle(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <span v-else class="noop">—</span>
          </template>
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
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  pageUsers,
  changeUserStatus,
  ROLE_ADMIN,
  ROLE_LABELS,
  formatDateTime,
  type UserEntity,
} from '@tutor-platform/frontend-common';

const loading = ref(false);
const list = ref<UserEntity[]>([]);
const total = ref(0);
const query = reactive({ page: 1, size: 10 });

function roleLabel(role: number): string {
  return ROLE_LABELS[role] ?? '未知';
}
function roleTag(role: number): 'primary' | 'warning' | 'danger' {
  if (role === ROLE_ADMIN) return 'danger';
  if (role === 2) return 'warning';
  return 'primary';
}

onMounted(() => load(1));

async function load(page: number) {
  loading.value = true;
  try {
    query.page = page;
    const res = await pageUsers({ page: query.page, size: query.size });
    list.value = res.records;
    total.value = res.total;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function toggle(row: UserEntity) {
  const next = row.status === 1 ? 0 : 1;
  try {
    await ElMessageBox.confirm(
      next === 0 ? `确认禁用用户「${row.username}」？` : `确认启用用户「${row.username}」？`,
      '提示',
      { type: 'warning' }
    );
    await changeUserStatus(row.id, next);
    ElMessage.success('操作成功');
    await load(query.page);
  } catch (e) {
    if (e === 'cancel' || (e as Error)?.name === 'CanceledError') return;
    ElMessage.error((e as Error).message);
  }
}
</script>

<style scoped>
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.noop {
  color: #c0c4cc;
}
</style>
