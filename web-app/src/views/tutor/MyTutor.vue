<template>
  <div>
    <el-card style="margin-bottom: 16px">
      <div class="head">
        <h3>资格认证</h3>
        <el-tag :type="certTag(cert.certStatus)" size="small">{{ certStatusLabel(cert.certStatus) }}</el-tag>
      </div>

      <!-- 已认证：只读展示 -->
      <div v-if="cert.certStatus === 2">
        <p>院校：{{ cert.school }}　专业：{{ cert.major }}　年级：{{ cert.gradeLevel }}</p>
        <p v-if="cert.reviewTime" class="hint">认证通过时间：{{ formatDateTime(cert.reviewTime) }}</p>
      </div>

      <!-- 待审：提示 -->
      <el-alert
        v-else-if="cert.certStatus === 1"
        type="warning"
        :closable="false"
        title="认证材料已提交，等待管理员审核，审核结果会通过站内消息通知你"
      />

      <!-- 未提交或已驳回：表单 -->
      <div v-else>
        <el-alert
          v-if="cert.certStatus === 3"
          type="error"
          :closable="false"
          :title="'上次认证未通过：' + (cert.reviewNote || '材料不清晰') + '，请核对后重新提交'"
          style="margin-bottom: 12px"
        />
        <el-form :model="certForm" label-width="100px" style="max-width: 560px">
          <el-form-item label="就读院校" required>
            <el-input v-model="certForm.school" placeholder="如：苏州大学" maxlength="100" />
          </el-form-item>
          <el-form-item label="专业" required>
            <el-input v-model="certForm.major" placeholder="如：计算机科学与技术" maxlength="100" />
          </el-form-item>
          <el-form-item label="年级" required>
            <el-select v-model="certForm.gradeLevel" placeholder="选择年级" style="width: 100%">
              <el-option v-for="g in grades" :key="g" :label="g" :value="g" />
            </el-select>
          </el-form-item>
          <el-form-item label="证明类型" required>
            <el-select v-model="certForm.certType" style="width: 100%">
              <el-option v-for="t in certTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="certForm.certType === 2" label="报告编号" required>
            <el-input v-model="certForm.certNo" placeholder="学信网在线验证报告编号" maxlength="64" />
          </el-form-item>
          <el-form-item label="证明材料">
            <el-upload :show-file-list="false" :http-request="doUpload" accept=".jpg,.jpeg,.png,.pdf">
              <el-button :loading="uploading">选择文件（jpg/png/pdf，不超过5MB）</el-button>
            </el-upload>
            <span v-if="certForm.certUrl" class="uploaded">已上传，可提交</span>
          </el-form-item>
          <el-form-item label="补充说明">
            <el-input v-model="certForm.note" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="certSaving" @click="submitCertForm">提交认证</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <el-card>
      <div class="head">
        <h3>我的家教信息</h3>
        <el-button type="primary" @click="openEdit(null)">发布家教信息</el-button>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="subjectName" label="科目" width="100" />
        <el-table-column prop="grade" label="辅导学段" width="140" />
        <el-table-column label="价格" width="110">
          <template #default="{ row }">{{ formatPrice(row.price) }}/小时</template>
        </el-table-column>
        <el-table-column prop="introduce" label="简介" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ tutorStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="还没有发布家教信息，点击右上角发布" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑家教信息' : '发布家教信息'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="科目" prop="subjectId">
          <el-select v-model="form.subjectId" placeholder="选择科目" style="width: 100%">
            <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="辅导学段" prop="grade">
          <el-input v-model="form.grade" placeholder="如：初中/高中、小学" />
        </el-form-item>
        <el-form-item label="时薪(元)" prop="price">
          <el-input-number v-model="form.price" :min="1" :max="2000" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="简介" prop="introduce">
          <el-input v-model="form.introduce" type="textarea" :rows="4" maxlength="300" show-word-limit placeholder="介绍教学经验、擅长科目、可授课时间等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import {
  pageTutors,
  publishTutor,
  updateTutor,
  listSubjects,
  tutorStatusLabel,
  formatPrice,
  formatDateTime,
  getMyCert,
  submitCert,
  uploadCertFile,
  certStatusLabel,
  type TutorVO,
  type TutorCertVO,
  type SubjectEntity,
} from '@tutor-platform/frontend-common';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const loading = ref(false);
const list = ref<TutorVO[]>([]);
const subjects = ref<SubjectEntity[]>([]);
const dialogVisible = ref(false);
const saving = ref(false);
const editing = ref<TutorVO | null>(null);
const formRef = ref<FormInstance>();

// ---------- 资格认证 ----------
const grades = ['大一', '大二', '大三', '大四', '研一', '研二', '研三'];
const certTypes = [
  { value: 1, label: '学生证' },
  { value: 2, label: '学信网在线验证报告' },
  { value: 3, label: '校园卡' },
  { value: 4, label: '校园邮箱' },
  { value: 5, label: '其他' },
];
const cert = ref<TutorCertVO>({
  id: 0, tutorId: 0, tutorName: null, school: null, major: null, gradeLevel: null,
  certType: null, certNo: null, certUrl: null, note: null, status: 0,
  reviewNote: null, reviewTime: null, createTime: '', certStatus: 0,
});
const certForm = reactive({
  school: '',
  major: '',
  gradeLevel: '',
  certType: 1,
  certNo: '',
  certUrl: '',
  note: '',
});
const uploading = ref(false);
const certSaving = ref(false);

function certTag(status?: number): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 2) return 'success';
  if (status === 1) return 'warning';
  if (status === 3) return 'danger';
  return 'info';
}

async function loadCert() {
  try {
    const res = await getMyCert();
    cert.value = res;
    if (res.certStatus === 3) {
      certForm.school = res.school || '';
      certForm.major = res.major || '';
      certForm.gradeLevel = res.gradeLevel || '';
      certForm.certType = res.certType || 1;
      certForm.certNo = res.certNo || '';
      certForm.note = res.note || '';
    }
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

async function doUpload(options: { file: File }) {
  uploading.value = true;
  try {
    certForm.certUrl = await uploadCertFile(options.file);
    ElMessage.success('上传成功');
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    uploading.value = false;
  }
}

async function submitCertForm() {
  if (!certForm.school.trim() || !certForm.major.trim() || !certForm.gradeLevel) {
    ElMessage.warning('请填写院校、专业和年级');
    return;
  }
  if (certForm.certType === 2 && !certForm.certNo.trim()) {
    ElMessage.warning('学信网报告需填写报告编号');
    return;
  }
  certSaving.value = true;
  try {
    await submitCert({
      school: certForm.school.trim(),
      major: certForm.major.trim(),
      gradeLevel: certForm.gradeLevel,
      certType: certForm.certType,
      certNo: certForm.certNo.trim() || undefined,
      certUrl: certForm.certUrl || undefined,
      note: certForm.note.trim() || undefined,
    });
    ElMessage.success('已提交，等待管理员审核');
    await loadCert();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    certSaving.value = false;
  }
}

const form = reactive({
  subjectId: undefined as number | undefined,
  grade: '',
  price: 100,
  introduce: '',
});

const rules: FormRules = {
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  grade: [{ required: true, message: '请填写辅导学段', trigger: 'blur' }],
  introduce: [{ required: true, message: '请填写简介', trigger: 'blur' }],
};

function statusTag(status: number): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 1) return 'success';
  if (status === 0) return 'warning';
  if (status === 2) return 'info';
  return 'danger';
}

onMounted(async () => {
  try {
    subjects.value = await listSubjects();
  } catch {
    /* 忽略 */
  }
  await load();
  await loadCert();
});

async function load() {
  loading.value = true;
  try {
    const res = await pageTutors({ page: 1, size: 100 });
    // 只看自己的
    list.value = res.records.filter((t) => t.userId === userStore.user?.userId);
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

function openEdit(row: TutorVO | null) {
  editing.value = row;
  if (row) {
    form.subjectId = row.subjectId;
    form.grade = row.grade || '';
    form.price = row.price;
    form.introduce = row.introduce || '';
  } else {
    form.subjectId = undefined;
    form.grade = '';
    form.price = 100;
    form.introduce = '';
  }
  dialogVisible.value = true;
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || form.subjectId === undefined) return;
  saving.value = true;
  try {
    if (editing.value) {
      await updateTutor(editing.value.id, { ...form, subjectId: form.subjectId });
      ElMessage.success('已保存，重新进入待审核');
    } else {
      await publishTutor({ ...form, subjectId: form.subjectId });
      ElMessage.success('发布成功，等待管理员审核');
    }
    dialogVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.head h3 {
  margin: 0;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.uploaded {
  margin-left: 12px;
  color: #67c23a;
  font-size: 13px;
}
</style>
