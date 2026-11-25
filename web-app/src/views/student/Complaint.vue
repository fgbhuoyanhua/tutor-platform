<template>
  <div>
    <el-tabs v-model="tab" type="border-card">
      <!-- 我的投诉 -->
      <el-tab-pane :label="`我的投诉（${mine.length}）`" name="mine">
        <el-table :data="mine" v-loading="mineLoading" stripe>
          <el-table-column prop="tutorName" label="被投诉老师" width="110" />
          <el-table-column prop="typeLabel" label="类型" width="110" />
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
          <el-table-column label="证据" width="90">
            <template #default="{ row }">
              <el-button v-if="row.evidenceUrl" link type="primary" size="small" @click="previewEvidence(row.evidenceUrl)">
                查看
              </el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="处理说明" width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.handleNote || '—' }}</template>
          </el-table-column>
          <el-table-column label="提交时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!mineLoading && mine.length === 0" description="你还没有提交过投诉" />
      </el-tab-pane>

      <!-- 提交投诉 -->
      <el-tab-pane label="提交投诉" name="submit">
        <el-form :model="form" label-width="100px" style="max-width: 620px">
          <el-form-item label="被投诉老师" required>
            <el-select v-model="form.tutorId" placeholder="选择老师" style="width: 260px">
              <el-option
                v-for="t in tutors"
                :key="t.userId"
                :label="`${t.tutorName}（${t.subjectName}）`"
                :value="t.userId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="投诉类型" required>
            <el-radio-group v-model="form.type">
              <el-radio v-for="(label, key) in COMPLAINT_TYPE_LABELS" :key="key" :value="Number(key)">
                {{ label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="关联订单">
            <el-select v-model="form.orderId" placeholder="可不选" clearable style="width: 320px">
              <el-option
                v-for="o in myOrders"
                :key="o.id"
                :label="`${o.orderNo} · ${o.tutorName} · ${o.appointDate} ${o.timeSlot}`"
                :value="o.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="投诉说明" required>
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="4"
              :maxlength="500"
              show-word-limit
              placeholder="请描述发生了什么，便于管理员核实"
            />
          </el-form-item>
          <el-form-item label="证据材料">
            <div class="evidence-row">
              <el-upload :show-file-list="false" :http-request="doUpload" accept=".jpg,.jpeg,.png,.pdf">
                <el-button :loading="uploading">上传截图/凭证（jpg/png/pdf，≤5MB）</el-button>
              </el-upload>
              <span v-if="form.evidenceUrl" class="uploaded">
                已上传
                <el-button link type="primary" size="small" @click="previewEvidence(form.evidenceUrl)">预览</el-button>
                <el-button link type="danger" size="small" @click="form.evidenceUrl = null">移除</el-button>
              </span>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">提交投诉</el-button>
            <span class="hint">同一老师同一订单同时只能有一条待处理投诉</span>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  COMPLAINT_TYPE_LABELS,
  formatDateTime,
  myComplaints,
  pageTutors,
  submitComplaint,
  pageOrders,
  uploadComplaintEvidence,
  getBlob,
  type ComplaintVO,
  type OrderVO,
  type TutorVO,
} from '@tutor-platform/frontend-common';

const tab = ref('mine');
const mine = ref<ComplaintVO[]>([]);
const mineLoading = ref(false);
const tutors = ref<TutorVO[]>([]);
const myOrders = ref<OrderVO[]>([]);
const submitting = ref(false);
const uploading = ref(false);

const form = reactive({
  tutorId: undefined as number | undefined,
  type: 1,
  orderId: undefined as number | undefined,
  content: '',
  evidenceUrl: null as string | null,
});

function statusTag(status: number): 'warning' | 'success' | 'info' {
  if (status === 1) return 'success';
  if (status === 2) return 'info';
  return 'warning';
}

onMounted(async () => {
  await Promise.all([loadMine(), loadOptions()]);
});

async function loadMine() {
  mineLoading.value = true;
  try {
    mine.value = await myComplaints();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    mineLoading.value = false;
  }
}

async function loadOptions() {
  try {
    const res = await pageTutors({ page: 1, size: 50 });
    tutors.value = res.records;
    const orders = await pageOrders({ page: 1, size: 50 });
    myOrders.value = orders.records;
  } catch {
    /* 下拉数据失败不阻断提交 */
  }
}

async function submit() {
  if (!form.tutorId) {
    ElMessage.warning('请选择被投诉老师');
    return;
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写投诉说明');
    return;
  }
  submitting.value = true;
  try {
    await submitComplaint({
      tutorId: form.tutorId,
      type: form.type,
      content: form.content.trim(),
      orderId: form.orderId ?? null,
      evidenceUrl: form.evidenceUrl,
    });
    ElMessage.success('投诉已提交，管理员会尽快处理');
    form.content = '';
    form.orderId = undefined;
    form.evidenceUrl = null;
    await loadMine();
    tab.value = 'mine';
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    submitting.value = false;
  }
}

async function doUpload(options: { file: File }) {
  uploading.value = true;
  try {
    form.evidenceUrl = await uploadComplaintEvidence(options.file);
    ElMessage.success('证据上传成功');
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    uploading.value = false;
  }
}

/** 证据是私有资源，必须带 token 取二进制再本地打开 */
async function previewEvidence(url: string) {
  try {
    const blob = await getBlob(url);
    const objUrl = URL.createObjectURL(blob);
    window.open(objUrl, '_blank');
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
</script>

<style scoped>
.muted {
  color: #909399;
  font-size: 12px;
}
.hint {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.evidence-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.uploaded {
  color: #67c23a;
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
