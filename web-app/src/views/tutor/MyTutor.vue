<template>
  <div>
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
  type TutorVO,
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
</style>
