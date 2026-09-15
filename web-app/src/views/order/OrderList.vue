<template>
  <div>
    <el-card>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column label="对方" width="130">
          <template #default="{ row }">
            {{ userStore.isStudent ? row.tutorName : row.studentName }}
          </template>
        </el-table-column>
        <el-table-column prop="subjectName" label="科目" width="90" />
        <el-table-column label="预约时间" width="160">
          <template #default="{ row }">{{ row.appointDate }} {{ row.timeSlot }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">{{ formatPrice(row.totalPrice) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="tagType(row.status)" size="small">{{ orderStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <template v-if="userStore.isStudent">
              <el-button v-if="row.status === 0" link type="warning" size="small" @click="doAction(row, 'cancel')">取消</el-button>
              <el-button v-if="row.status === 1" link type="primary" size="small" @click="openReschedule(row)">申请调课</el-button>
              <el-button v-if="row.status === 2" link type="success" size="small" @click="doAction(row, 'finish')">确认完成</el-button>
            </template>
            <template v-if="userStore.isTutor">
              <el-button v-if="row.status === 0" link type="success" size="small" @click="doAction(row, 'confirm')">接单</el-button>
              <el-button v-if="row.status === 0" link type="danger" size="small" @click="doAction(row, 'reject')">拒绝</el-button>
              <template v-if="row.status === 1">
                <el-button link type="success" size="small" @click="doAction(row, 'rsAccept')">同意调课</el-button>
                <el-button link type="warning" size="small" @click="doAction(row, 'rsReject')">拒绝调课</el-button>
              </template>
            </template>
            <span v-if="!canOperate(row)" class="noop">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无订单" />
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

    <el-dialog v-model="rsVisible" title="申请调课" width="420px">
      <el-form label-width="80px">
        <el-form-item label="原时间">
          {{ currentOrder?.appointDate }} {{ currentOrder?.timeSlot }}
        </el-form-item>
        <el-form-item label="新日期" required>
          <el-date-picker v-model="rsForm.newDate" type="date" value-format="YYYY-MM-DD" :disabled-date="disablePast" style="width:100%" />
        </el-form-item>
        <el-form-item label="新时段" required>
          <el-select v-model="rsForm.newSlot" placeholder="选择时间段" style="width:100%">
            <el-option v-for="s in TIME_SLOTS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rsVisible = false">取消</el-button>
        <el-button type="primary" :loading="rsSubmitting" @click="submitReschedule">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  pageOrders,
  confirmOrder,
  rejectOrder,
  cancelOrder,
  finishOrder,
  rescheduleRequest,
  rescheduleAccept,
  rescheduleReject,
  TIME_SLOTS,
  orderStatusLabel,
  formatPrice,
  formatDateTime,
  type OrderVO,
} from '@tutor-platform/frontend-common';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const loading = ref(false);
const list = ref<OrderVO[]>([]);
const total = ref(0);
const query = reactive({ page: 1, size: 10 });

const rsVisible = ref(false);
const rsSubmitting = ref(false);
const currentOrder = ref<OrderVO | null>(null);
const rsForm = reactive({ newDate: '', newSlot: '' });

const ACTIONS: Record<string, { title: string; text: string; fn: (id: number) => Promise<void> }> = {
  confirm: { title: '接单', text: '确认接单后不可撤销，确定接单吗？', fn: confirmOrder },
  reject: { title: '拒绝', text: '确定拒绝该预约吗？', fn: rejectOrder },
  cancel: { title: '取消', text: '确定取消该预约吗？', fn: cancelOrder },
  finish: { title: '确认完成', text: '确认已完成授课？', fn: finishOrder },
  rsAccept: { title: '同意调课', text: '同意后订单时间将改为学生申请的新时间，确认？', fn: rescheduleAccept },
  rsReject: { title: '拒绝调课', text: '拒绝后仍按原时间上课，确认拒绝？', fn: rescheduleReject },
};

function tagType(status: number): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  if (status === 3) return 'success';
  if (status === 0) return 'warning';
  if (status === 4 || status === 5) return 'info';
  return 'primary';
}

function canOperate(row: OrderVO): boolean {
  if (userStore.isStudent) return row.status === 0 || row.status === 1 || row.status === 2;
  if (userStore.isTutor) return row.status === 0 || row.status === 1;
  return false;
}

function disablePast(date: Date): boolean {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return date.getTime() < today.getTime();
}

function openReschedule(row: OrderVO) {
  currentOrder.value = row;
  rsForm.newDate = '';
  rsForm.newSlot = '';
  rsVisible.value = true;
}

async function submitReschedule() {
  if (!currentOrder.value || !rsForm.newDate || !rsForm.newSlot) {
    ElMessage.warning('请选择新日期和时段');
    return;
  }
  rsSubmitting.value = true;
  try {
    await rescheduleRequest(currentOrder.value.id, rsForm.newDate, rsForm.newSlot);
    ElMessage.success('调课申请已提交，等待老师处理');
    rsVisible.value = false;
    await load(query.page);
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    rsSubmitting.value = false;
  }
}

onMounted(() => load(1));

async function load(page: number) {
  loading.value = true;
  try {
    query.page = page;
    const res = await pageOrders({ page: query.page, size: query.size });
    list.value = res.records;
    total.value = res.total;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function doAction(row: OrderVO, action: string) {
  const cfg = ACTIONS[action];
  if (!cfg) return;
  try {
    await ElMessageBox.confirm(cfg.text, cfg.title, { type: 'warning' });
    await cfg.fn(row.id);
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
