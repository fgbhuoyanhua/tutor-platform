<template>
  <div>
    <el-card>
      <el-tabs v-model="statusTab" @tab-change="load">
        <el-tab-pane label="待处理" name="0" />
        <el-tab-pane label="已成立" name="1" />
        <el-tab-pane label="已驳回" name="2" />
        <el-tab-pane label="全部" name="all" />
      </el-tabs>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="complainantName" label="投诉人" width="100" />
        <el-table-column prop="tutorName" label="被投诉老师" width="110" />
        <el-table-column prop="typeLabel" label="类型" width="100" />
        <el-table-column prop="content" label="投诉说明" show-overflow-tooltip />
        <el-table-column label="关联订单" width="190">
          <template #default="{ row }">
            <span class="muted">{{ row.orderNo || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="扣分" width="80">
          <template #default="{ row }">
            <span :class="{ deduct: row.deductScore > 0 }">{{ row.deductScore || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="success" size="small" @click="openHandle(row, true)">成立</el-button>
              <el-button link type="danger" size="small" @click="openHandle(row, false)">驳回</el-button>
            </template>
            <span v-else class="muted">{{ row.handleNote || '已处理' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无投诉记录" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="accept ? '判定投诉成立' : '驳回投诉'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="被投诉人">
          <span>{{ current?.tutorName }}（投诉人：{{ current?.complainantName }}）</span>
        </el-form-item>
        <el-form-item label="投诉内容">
          <span class="content">{{ current?.content }}</span>
        </el-form-item>
        <el-form-item v-if="accept" label="扣减信用分">
          <el-input-number v-model="deduct" :min="1" :max="50" />
          <div class="tip">投诉成立会写一条违规记录，信用分低于 60 时老师被限制接单</div>
        </el-form-item>
        <el-form-item :label="accept ? '处理说明' : '驳回理由'" :required="!accept">
          <el-input
            v-model="note"
            type="textarea"
            :rows="3"
            :maxlength="200"
            show-word-limit
            :placeholder="accept ? '可不填（如：核实聊天记录与订单）' : '必填，会同步给学生'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmHandle">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  adminComplaints,
  handleComplaint,
  formatDateTime,
  type ComplaintVO,
} from '@tutor-platform/frontend-common';

const statusTab = ref('0');
const list = ref<ComplaintVO[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const submitting = ref(false);
const accept = ref(true);
const deduct = ref(10);
const note = ref('');
const current = ref<ComplaintVO | null>(null);

function statusTag(status: number): 'warning' | 'success' | 'info' {
  if (status === 1) return 'success';
  if (status === 2) return 'info';
  return 'warning';
}

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const status = statusTab.value === 'all' ? undefined : Number(statusTab.value);
    list.value = await adminComplaints(status);
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

function openHandle(row: ComplaintVO, pass: boolean) {
  current.value = row;
  accept.value = pass;
  deduct.value = 10;
  note.value = '';
  dialogVisible.value = true;
}

async function confirmHandle() {
  if (!current.value) return;
  if (!accept.value && !note.value.trim()) {
    ElMessage.warning('驳回投诉需填写理由');
    return;
  }
  if (accept.value) {
    try {
      await ElMessageBox.confirm(
        `确认投诉成立？将对「${current.value.tutorName}」扣减 ${deduct.value} 信用分并记 1 次违规`,
        '确认扣分',
        { type: 'warning' }
      );
    } catch {
      return;
    }
  }
  submitting.value = true;
  try {
    await handleComplaint(current.value.id, accept.value, accept.value ? deduct.value : undefined, note.value.trim() || undefined);
    ElMessage.success('处理成功');
    dialogVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.muted {
  color: #909399;
  font-size: 12px;
}
.deduct {
  color: #f56c6c;
  font-weight: 600;
}
.content {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
.tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
</style>
