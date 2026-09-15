<template>
  <div>
    <el-card>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userId" label="老师ID" width="90" />
        <el-table-column prop="subjectId" label="科目ID" width="90" />
        <el-table-column prop="grade" label="辅导学段" width="140" />
        <el-table-column label="时薪" width="110">
          <template #default="{ row }">{{ formatPrice(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="introduce" label="简介" show-overflow-tooltip />
        <el-table-column label="发布时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="audit(row, true)">通过</el-button>
            <el-button link type="danger" size="small" @click="audit(row, false)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无待审核的家教信息" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  pendingTutors,
  auditTutor,
  formatPrice,
  formatDateTime,
  type TutorEntity,
} from '@tutor-platform/frontend-common';

const loading = ref(false);
const list = ref<TutorEntity[]>([]);

onMounted(() => load());

async function load() {
  loading.value = true;
  try {
    list.value = await pendingTutors();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function audit(row: TutorEntity, pass: boolean) {
  try {
    await ElMessageBox.confirm(
      pass ? '确认通过该家教信息并上架？' : '确认驳回该家教信息？',
      pass ? '通过审核' : '驳回',
      { type: 'warning' }
    );
    await auditTutor(row.id, pass);
    ElMessage.success('操作成功');
    await load();
  } catch (e) {
    if (e === 'cancel' || (e as Error)?.name === 'CanceledError') return;
    ElMessage.error((e as Error).message);
  }
}
</script>
