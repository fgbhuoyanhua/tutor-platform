<template>
  <el-card v-loading="loading">
    <template #header>
      <span>学生给我的评价</span>
      <span v-if="list.length" class="summary">共 {{ list.length }} 条 · 平均分 {{ avgScore }}</span>
    </template>
    <el-empty v-if="!loading && list.length === 0" description="还没有学生评价" />
    <div v-for="ev in list" :key="ev.id" class="eval-item">
      <div class="eval-head">
        <el-avatar :size="32">{{ ev.studentName?.[0] || '学' }}</el-avatar>
        <div class="who">
          <div class="name">{{ ev.studentName }}</div>
          <div class="time">{{ formatDateTime(ev.createTime) }}</div>
        </div>
        <el-rate :model-value="ev.score" disabled />
      </div>
      <div class="content">{{ ev.content || '这个学生很优秀' }}</div>
      <div v-if="ev.reply" class="reply">
        <b>老师回复：</b>{{ ev.reply }}
      </div>
      <el-button v-if="!ev.reply" size="small" type="primary" link @click="openReply(ev)">回复评价</el-button>
    </div>
  </el-card>

  <el-dialog v-model="replyVisible" title="回复评价" width="460px">
    <el-input v-model="replyText" type="textarea" :rows="4" placeholder="请输入回复内容..." />
    <template #footer>
      <el-button @click="replyVisible = false">取消</el-button>
      <el-button type="primary" @click="submitReply">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listTutorEvaluations, replyEvaluation, formatDateTime, type EvaluationVO } from '@tutor-platform/frontend-common';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const loading = ref(false);
const list = ref<EvaluationVO[]>([]);
const replyVisible = ref(false);
const replyText = ref('');
const replyId = ref(0);

const avgScore = computed(() => {
  if (!list.value.length) return '-';
  const sum = list.value.reduce((s, e) => s + e.score, 0);
  return (sum / list.value.length).toFixed(1);
});

async function loadList() {
  loading.value = true;
  try {
    const res = await listTutorEvaluations(userStore.user!.userId, 1, 50);
    list.value = res.records;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

onMounted(() => loadList());

function openReply(ev: EvaluationVO) {
  replyId.value = ev.id;
  replyText.value = '';
  replyVisible.value = true;
}

async function submitReply() {
  if (!replyText.value.trim()) {
    ElMessage.warning('请输入回复内容');
    return;
  }
  try {
    await replyEvaluation(replyId.value, replyText.value);
    ElMessage.success('回复成功');
    replyVisible.value = false;
    await loadList();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}
</script>

<style scoped>
.summary {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
.eval-item {
  padding: 14px 0;
  border-bottom: 1px solid #ebeef5;
}
.eval-item:last-child {
  border-bottom: none;
}
.eval-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.who {
  flex: 1;
}
.name {
  font-weight: 600;
  font-size: 14px;
}
.time {
  color: #909399;
  font-size: 12px;
}
.content {
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}
.reply {
  margin-top: 8px;
  padding: 8px 12px;
  background: #f0f9eb;
  border-radius: 6px;
  font-size: 13px;
  color: #67c23a;
}
</style>
