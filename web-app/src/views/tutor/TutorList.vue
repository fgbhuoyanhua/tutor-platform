<template>
  <div>
    <el-card class="filter-card">
      <el-form inline>
        <el-form-item label="科目">
          <el-select v-model="query.subjectId" placeholder="全部科目" clearable style="width: 140px" @change="load(1)">
            <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" placeholder="老师姓名/介绍" clearable style="width: 200px" @keyup.enter="load(1)" @clear="load(1)" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load(1)">查询</el-button>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="onlyFav" @change="onFavFilterChange">只看我的收藏</el-checkbox>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="t in list" :key="t.id" :span="8" class="tutor-col">
        <el-card shadow="hover" class="tutor-card">
          <el-icon class="fav-icon" :class="{ fav: favIds.has(t.id) }" @click.stop="toggleFav(t)">
            <StarFilled />
          </el-icon>
          <div class="tutor-head">
            <el-avatar :size="48" :src="t.avatar || undefined">
              {{ (t.tutorName || '?')[0] }}
            </el-avatar>
            <div class="tutor-meta">
              <div class="tutor-name link" @click="openDetail(t)">{{ t.tutorName }}</div>
              <div class="tutor-subject">
                <el-tag size="small" type="success">{{ t.subjectName }}</el-tag>
                <span class="grade">{{ t.grade }}</span>
              </div>
            </div>
          </div>
          <p class="introduce">{{ t.introduce || '暂无简介' }}</p>
          <div class="tutor-foot">
            <span class="price">{{ formatPrice(t.price) }}<em>/小时</em></span>
            <span class="rating">★ {{ t.rating }}</span>
          </div>
          <el-button type="primary" size="small" class="order-btn" @click="openOrder(t)">
            预约 TA
          </el-button>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!loading && list.length === 0" description="暂无符合条件的家教" />
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

    <el-dialog v-model="orderVisible" title="预约家教" width="460px">
      <el-form label-width="80px">
        <el-form-item label="家教">
          <span>{{ currentTutor?.tutorName }}（{{ currentTutor?.subjectName }}，{{ formatPrice(currentTutor?.price ?? 0) }}/小时）</span>
        </el-form-item>
        <el-form-item label="预约日期" required>
          <el-date-picker
            v-model="orderForm.appointDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :disabled-date="disablePast"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="时间段" required>
          <el-select v-model="orderForm.timeSlot" placeholder="选择时间段" style="width: 100%">
            <el-option v-for="s in TIME_SLOTS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitOrder">提交预约</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="`${detailTutor?.tutorName ?? ''} · 老师详情`" size="420px">
      <div v-if="detailTutor" class="detail-wrap">
        <div class="detail-head">
          <el-avatar :size="56" :src="detailTutor.avatar || undefined">
            {{ (detailTutor.tutorName || '?')[0] }}
          </el-avatar>
          <div>
            <div class="detail-name">{{ detailTutor.tutorName }}</div>
            <el-tag size="small" type="success">{{ detailTutor.subjectName }}</el-tag>
            <span class="grade">{{ detailTutor.grade }}</span>
          </div>
        </div>
        <div class="detail-stats">
          <div class="stat"><b>{{ formatPrice(detailTutor.price) }}<em>/小时</em></b><span>课时单价</span></div>
          <div class="stat"><b>★ {{ detailTutor.rating }}</b><span>综合评分</span></div>
          <div class="stat"><b>{{ detailTutor.evaluateCount ?? 0 }}</b><span>条评价</span></div>
          <div class="stat"><b>{{ detailTutor.finishedOrderCount ?? 0 }}</b><span>次授课</span></div>
        </div>
        <h4 class="detail-sec">个人简介</h4>
        <p class="detail-intro">{{ detailTutor.introduce || '暂无简介' }}</p>
        <h4 class="detail-sec">学生评价（{{ evaluationTotal }} 条）</h4>
        <div v-loading="evLoading">
          <div v-if="evaluations.length === 0 && !evLoading" class="ev-empty">还没有学生评价，预约后即可成为 TA 的第一位学生</div>
          <div v-for="ev in evaluations" :key="ev.id" class="ev-item">
            <div class="ev-top">
              <span class="ev-user">{{ ev.studentName }}</span>
              <el-rate :model-value="ev.score" disabled readonly size="12" />
            </div>
            <p class="ev-content">{{ ev.content }}</p>
            <span class="ev-time">{{ formatDateTime(ev.createTime) }}</span>
          </div>
        </div>
        <div class="detail-actions">
          <el-button @click="goChat">发消息</el-button>
          <el-button type="primary" @click="fromDetailOrder">预约 TA</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { StarFilled } from '@element-plus/icons-vue';
import {
  pageTutors,
  listSubjects,
  createOrder,
  listTutorEvaluations,
  addFavorite,
  removeFavorite,
  listFavorites,
  TIME_SLOTS,
  formatPrice,
  formatDateTime,
  type TutorVO,
  type EvaluationVO,
  type SubjectEntity,
} from '@tutor-platform/frontend-common';
import { useUserStore } from '@/stores/user';

const userStore = useUserStore();
const router = useRouter();
const loading = ref(false);
const list = ref<TutorVO[]>([]);
const total = ref(0);
const subjects = ref<SubjectEntity[]>([]);
const query = reactive({ page: 1, size: 9, subjectId: undefined as number | undefined, keyword: '' });

const onlyFav = ref(false);
const favIds = ref<Set<number>>(new Set());

const orderVisible = ref(false);
const submitting = ref(false);
const orderForm = reactive({ tutorId: 0, subjectId: 0, appointDate: '', timeSlot: '' });
const currentTutor = ref<TutorVO | null>(null);

const detailVisible = ref(false);
const detailTutor = ref<TutorVO | null>(null);
const evaluations = ref<EvaluationVO[]>([]);
const evaluationTotal = ref(0);
const evLoading = ref(false);

function openDetail(t: TutorVO) {
  detailTutor.value = t;
  detailVisible.value = true;
  evaluationTotal.value = t.evaluateCount ?? 0;
  loadEvaluations(t.userId);
}

async function loadEvaluations(tutorUserId: number) {
  evLoading.value = true;
  try {
    const res = await listTutorEvaluations(tutorUserId, 1, 20);
    evaluations.value = res.records;
    evaluationTotal.value = res.total;
  } catch {
    evaluations.value = [];
  } finally {
    evLoading.value = false;
  }
}

function fromDetailOrder() {
  if (!detailTutor.value) return;
  detailVisible.value = false;
  openOrder(detailTutor.value);
}

function goChat() {
  if (!detailTutor.value) return;
  detailVisible.value = false;
  router.push({ path: '/messages', query: { uid: detailTutor.value.userId, name: detailTutor.value.tutorName } });
}

function disablePast(date: Date): boolean {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return date.getTime() < today.getTime();
}

onMounted(async () => {
  try {
    subjects.value = await listSubjects();
  } catch {
    /* 科目接口失败不阻断列表 */
  }
  await Promise.all([load(1), loadFavIds()]);
});

async function load(page: number) {
  loading.value = true;
  try {
    if (onlyFav.value) {
      list.value = await listFavorites();
      total.value = list.value.length;
      return;
    }
    query.page = page;
    const res = await pageTutors({
      page: query.page,
      size: query.size,
      subjectId: query.subjectId,
      keyword: query.keyword || undefined,
    });
    list.value = res.records;
    total.value = res.total;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function loadFavIds() {
  if (!userStore.isLoggedIn) return;
  try {
    const favs = await listFavorites();
    favIds.value = new Set(favs.map((t) => t.id));
  } catch { /* 未登录不影响 */ }
}

function onFavFilterChange() {
  total.value = 0;
  load(1);
}

async function toggleFav(t: TutorVO) {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录');
    return;
  }
  try {
    if (favIds.value.has(t.id)) {
      await removeFavorite(t.id);
      favIds.value.delete(t.id);
      ElMessage.success('已取消收藏');
      if (onlyFav.value) {
        list.value = list.value.filter((x) => x.id !== t.id);
        total.value = list.value.length;
      }
    } else {
      await addFavorite(t.id);
      favIds.value.add(t.id);
      ElMessage.success('已收藏');
    }
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

function openOrder(t: TutorVO) {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录');
    return;
  }
  currentTutor.value = t;
  orderForm.tutorId = t.id;
  orderForm.subjectId = t.subjectId;
  orderForm.appointDate = '';
  orderForm.timeSlot = '';
  orderVisible.value = true;
}

async function submitOrder() {
  if (!orderForm.appointDate || !orderForm.timeSlot) {
    ElMessage.warning('请选择日期与时间段');
    return;
  }
  submitting.value = true;
  try {
    await createOrder({ ...orderForm });
    ElMessage.success('预约提交成功，等待老师接单');
    orderVisible.value = false;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}
.tutor-col {
  margin-bottom: 16px;
}
.tutor-card {
  height: 220px;
  position: relative;
}
.fav-icon {
  position: absolute;
  top: 12px;
  right: 12px;
  font-size: 20px;
  color: #c0c4cc;
  cursor: pointer;
  z-index: 1;
}
.fav-icon.fav {
  color: #f56c6c;
}
.tutor-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tutor-name {
  font-size: 16px;
  font-weight: 600;
}
.tutor-name.link {
  cursor: pointer;
}
.tutor-name.link:hover {
  color: #409eff;
}
.detail-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}
.detail-name {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 4px;
}
.detail-stats {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}
.detail-stats .stat {
  flex: 1;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 10px 6px;
  text-align: center;
}
.stat b {
  display: block;
  font-size: 15px;
  color: #1a1b1c;
}
.stat b em {
  font-style: normal;
  font-size: 11px;
  color: #909399;
  font-weight: 400;
}
.stat span {
  font-size: 12px;
  color: #909399;
}
.detail-sec {
  margin: 16px 0 8px;
  font-size: 14px;
}
.detail-intro {
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.ev-item {
  border-bottom: 1px solid #ebeef5;
  padding: 10px 0;
}
.ev-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ev-user {
  font-size: 13px;
  font-weight: 600;
}
.ev-content {
  margin: 6px 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
.ev-time {
  font-size: 12px;
  color: #909399;
}
.ev-empty {
  color: #909399;
  font-size: 13px;
  padding: 12px 0;
}
.detail-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.detail-actions .el-button {
  flex: 1;
}
.tutor-subject {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.grade {
  color: #909399;
  font-size: 12px;
}
.introduce {
  color: #606266;
  font-size: 13px;
  height: 40px;
  line-height: 20px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.tutor-foot {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.price {
  color: #f56c6c;
  font-size: 20px;
  font-weight: 700;
}
.price em {
  font-style: normal;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.rating {
  color: #e6a23c;
}
.order-btn {
  position: absolute;
  right: 16px;
  bottom: 14px;
}
.pager {
  margin-top: 8px;
  justify-content: flex-end;
}
</style>
