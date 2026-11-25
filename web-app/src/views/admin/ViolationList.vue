<template>
  <div>
    <el-card class="filter-card">
      <el-form inline>
        <el-form-item label="老师">
          <el-select v-model="tutorId" placeholder="全部老师" clearable style="width: 200px" @change="load">
            <el-option
              v-for="t in tutors"
              :key="t.userId"
              :label="`${t.tutorName}（${t.subjectName}）`"
              :value="t.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
        <el-form-item>
          <span class="summary">
            共 {{ list.length }} 条 · 累计扣分 {{ totalDeduct }} 分 · 涉及 {{ tutorCount }} 位老师
          </span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="tutorName" label="老师" width="110" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.type === 1 ? 'danger' : 'warning'">{{ row.typeLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="扣分" width="80">
          <template #default="{ row }">
            <span class="deduct">-{{ row.deduct }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" show-overflow-tooltip />
        <el-table-column label="关联单据" width="170">
          <template #default="{ row }">
            <span class="muted">{{ refLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="处理人" width="110" />
        <el-table-column label="记录时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无违规记录" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  adminViolations,
  formatDateTime,
  pageTutors,
  type TutorVO,
  type TutorViolationVO,
} from '@tutor-platform/frontend-common';

const list = ref<TutorViolationVO[]>([]);
const loading = ref(false);
const tutors = ref<TutorVO[]>([]);
const tutorId = ref<number | undefined>(undefined);

const totalDeduct = computed(() => list.value.reduce((s, v) => s + (v.deduct ?? 0), 0));
const tutorCount = computed(() => new Set(list.value.map((v) => v.tutorId)).size);

function refLabel(row: TutorViolationVO): string {
  if (!row.refType) return '—';
  return `${row.refType === 'complaint' ? '投诉' : '订单'} #${row.refId ?? '—'}`;
}

onMounted(async () => {
  try {
    const res = await pageTutors({ page: 1, size: 100 });
    tutors.value = res.records;
  } catch {
    /* 下拉失败不阻断列表 */
  }
  await load();
});

async function load() {
  loading.value = true;
  try {
    list.value = await adminViolations(tutorId.value, 200);
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

function reset() {
  tutorId.value = undefined;
  load();
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
  border-radius: var(--radius);
}
.summary {
  color: var(--text-secondary);
  font-size: 13px;
}
.deduct {
  color: #f56c6c;
  font-weight: 600;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
