<template>
  <div>
    <el-card class="head-card">
      <el-form inline>
        <el-form-item label="账期">
          <el-date-picker
            v-model="month"
            type="month"
            value-format="YYYY-MM"
            :clearable="false"
            style="width: 160px"
            @change="load"
          />
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading" @click="load">查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!bill || bill.items.length === 0" @click="exportCsv">
            导出 CSV
          </el-button>
        </el-form-item>
        <el-form-item>
          <span class="hint">金额取自订单快照，老师后续改价不会影响历史账单</span>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">已完成节数</div>
          <div class="stat-num">{{ bill?.finishedCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">已完成时长</div>
          <div class="stat-num">{{ hoursOf(bill?.finishedMinutes ?? 0) }} <em>小时</em></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">已完成金额</div>
          <div class="stat-num amount">¥{{ Number(bill?.totalAmount ?? 0).toFixed(2) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">未结算订单</div>
          <div class="stat-num">{{ bill?.pendingCount ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="table-card" shadow="never">
      <template #header>
        <span class="table-title">{{ month }} 课时明细（{{ bill?.items.length ?? 0 }} 笔）</span>
      </template>
      <el-table :data="bill?.items ?? []" stripe>
        <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
        <el-table-column prop="studentName" label="学生" width="100" />
        <el-table-column prop="subjectName" label="科目" width="100" />
        <el-table-column prop="appointDate" label="授课日期" width="120" />
        <el-table-column prop="timeSlot" label="时段" width="120" />
        <el-table-column label="时长" width="90">
          <template #default="{ row }">{{ row.durationMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="单价" width="110">
          <template #default="{ row }">{{ formatPrice(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">
            <span class="amount">{{ formatPrice(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && (bill?.items.length ?? 0) === 0" description="该月还没有课时记录" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getTutorBill, getToken, formatPrice, type BillVO } from '@tutor-platform/frontend-common';

const month = ref(new Date().toISOString().slice(0, 7));
const bill = ref<BillVO | null>(null);
const loading = ref(false);

function hoursOf(minutes: number): string {
  return (Math.round((minutes / 60) * 10) / 10).toFixed(1);
}

function statusTag(status: number): 'success' | 'primary' | 'info' | 'warning' | 'danger' {
  if (status === 3) return 'success';
  if (status === 1) return 'primary';
  if (status === 2) return 'info';
  if (status === 0) return 'warning';
  return 'danger';
}

onMounted(load);

async function load() {
  loading.value = true;
  try {
    bill.value = await getTutorBill(month.value);
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

/** 导出走带 Token 的原始请求（浏览器直接触发下载，后端返回 UTF-8 BOM 的 CSV） */
async function exportCsv() {
  try {
    const res = await fetch(`/api/tutor/bill/export?month=${encodeURIComponent(month.value)}`, {
      headers: { Authorization: `Bearer ${getToken()}` },
    });
    if (!res.ok) {
      throw new Error(`导出失败（HTTP ${res.status}）`);
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `课时账单-${month.value}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
    ElMessage.success('已导出 CSV');
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
</script>

<style scoped>
.head-card {
  margin-bottom: 16px;
  border-radius: var(--radius);
}
.hint {
  color: var(--text-muted);
  font-size: 12px;
}
.stat-card {
  border-radius: var(--radius);
  text-align: center;
}
.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}
.stat-num {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 800;
  color: var(--text);
}
.stat-num em {
  font-style: normal;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
}
.amount {
  color: var(--danger);
}
.table-card {
  margin-top: 16px;
  border-radius: var(--radius);
}
.table-title {
  font-weight: 600;
}
</style>
