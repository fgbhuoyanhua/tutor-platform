<template>
  <el-tabs v-model="tab" type="border-card">
    <el-tab-pane label="家教信息审核" name="tutor">
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
    </el-tab-pane>

    <el-tab-pane :label="`资格认证（${certList.length}）`" name="cert">
      <el-table :data="certList" v-loading="certLoading" stripe>
        <el-table-column prop="tutorName" label="老师" width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="school" label="院校" width="150" show-overflow-tooltip />
        <el-table-column prop="major" label="专业" width="150" show-overflow-tooltip />
        <el-table-column prop="gradeLevel" label="年级" width="80" />
        <el-table-column label="证明类型" width="170">
          <template #default="{ row }">{{ row.certTypeLabel || certTypeLabel(row.certType) }}</template>
        </el-table-column>
        <el-table-column prop="certNo" label="报告编号" width="150" show-overflow-tooltip />
        <el-table-column label="提交时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="材料" width="100">
          <template #default="{ row }">
            <el-button v-if="row.certUrl" link type="primary" size="small" @click="viewCert(row)">查看</el-button>
            <span v-else class="muted">未上传</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="auditCertRow(row, true)">通过</el-button>
            <el-button link type="danger" size="small" @click="auditCertRow(row, false)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!certLoading && certList.length === 0" description="暂无待审核的资格认证" />
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  pendingTutors,
  auditTutor,
  formatPrice,
  formatDateTime,
  pendingCerts,
  auditCert,
  getBlob,
  certTypeLabel,
  type TutorEntity,
  type TutorCertVO,
} from '@tutor-platform/frontend-common';

const tab = ref('tutor');
const loading = ref(false);
const list = ref<TutorEntity[]>([]);
const certLoading = ref(false);
const certList = ref<TutorCertVO[]>([]);

onMounted(() => {
  load();
  loadCerts();
});

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

async function loadCerts() {
  certLoading.value = true;
  try {
    certList.value = await pendingCerts();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    certLoading.value = false;
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

/** 查看证明材料：接口需要 Bearer Token，先取 Blob 再在新窗口打开 */
async function viewCert(row: TutorCertVO) {
  if (!row.certUrl) return;
  try {
    const blob = await getBlob(row.certUrl);
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

async function auditCertRow(row: TutorCertVO, pass: boolean) {
  try {
    if (pass) {
      await ElMessageBox.confirm(
        `确认该老师（${row.tutorName || '未实名'}）的资格认证通过？通过后主页展示已认证标识与院校信息。`,
        '认证通过',
        { type: 'warning' }
      );
      await auditCert(row.id, true);
    } else {
      const { value } = await ElMessageBox.prompt('请填写驳回理由，老师会看到这条说明', '认证驳回', {
        inputPlaceholder: '如：学生证信息不清晰，请重新拍照上传',
        inputValidator: (v: string) => (v && v.trim().length > 0) || '驳回理由不能为空',
      });
      await auditCert(row.id, false, value);
    }
    ElMessage.success('操作成功');
    await loadCerts();
  } catch (e) {
    if (e === 'cancel' || (e as Error)?.name === 'CanceledError') return;
    ElMessage.error((e as Error).message);
  }
}
</script>

<style scoped>
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
