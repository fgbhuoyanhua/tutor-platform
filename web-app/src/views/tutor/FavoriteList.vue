<template>
  <div>
    <el-card class="header-card" shadow="never">
      <div class="page-head">
        <div>
          <h2 class="page-title">我的收藏</h2>
          <p class="page-sub">共收藏 {{ list.length }} 位老师</p>
        </div>
        <el-button type="primary" @click="$router.push('/tutors')">
          <el-icon><Search /></el-icon>
          去找家教
        </el-button>
      </div>
    </el-card>

    <el-row :gutter="16" v-loading="loading" class="tutor-grid">
      <el-col v-for="t in list" :key="t.id" :span="8" class="tutor-col">
        <el-card shadow="hover" class="tutor-card">
          <el-icon class="fav-icon fav" @click.stop="removeFav(t)">
            <StarFilled />
          </el-icon>
          <div class="tutor-head" @click="openDetail(t)">
            <el-avatar :size="48" :src="t.avatar || undefined">
              {{ (t.tutorName || '?')[0] }}
            </el-avatar>
            <div class="tutor-meta">
              <div class="tutor-name">{{ t.tutorName }}</div>
              <div class="tutor-subject">
                <el-tag size="small" type="success">{{ t.subjectName }}</el-tag>
                <span class="grade">擅长{{ t.grade }}</span>
              </div>
            </div>
          </div>
          <p class="introduce">{{ t.introduce || '暂无简介' }}</p>
          <div class="tutor-foot">
            <span class="price">{{ formatPrice(t.price) }}<em>/小时</em></span>
            <span class="rating">★ {{ t.rating }}</span>
          </div>
          <div class="action-row">
            <el-button type="primary" size="small" @click="openOrder(t)">预约 TA</el-button>
            <el-button size="small" @click="sendMsg(t)">发消息</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && list.length === 0" description="还没收藏老师，去发现好老师吧">
      <el-button type="primary" @click="$router.push('/tutors')">去找家教</el-button>
    </el-empty>

    <!-- 预约弹窗 -->
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

    <!-- 老师详情抽屉 -->
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
          <div class="stat"><b>{{ detailTutor.finishedOrderCount || 0 }}</b><span>授课次数</span></div>
        </div>
        <div class="detail-section">
          <div class="section-title">老师介绍</div>
          <p class="section-content">{{ detailTutor.introduce || '暂无介绍' }}</p>
        </div>
        <div class="detail-actions">
          <el-button type="primary" @click="openOrder(detailTutor)">立即预约</el-button>
          <el-button @click="sendMsg(detailTutor)">发消息</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { StarFilled, Search } from '@element-plus/icons-vue';
import {
  listFavorites,
  removeFavorite,
  createOrder,
  formatPrice,
  TIME_SLOTS,
  type TutorVO,
} from '@tutor-platform/frontend-common';

const router = useRouter();
const loading = ref(false);
const list = ref<TutorVO[]>([]);

const orderVisible = ref(false);
const submitting = ref(false);
const currentTutor = ref<TutorVO | null>(null);
const orderForm = reactive({ appointDate: '', timeSlot: '' });

const detailVisible = ref(false);
const detailTutor = ref<TutorVO | null>(null);

async function load() {
  loading.value = true;
  try {
    list.value = await listFavorites();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function removeFav(t: TutorVO) {
  try {
    await ElMessageBox.confirm(`确定取消收藏 ${t.tutorName} 吗？`, '取消收藏', { type: 'warning' });
    await removeFavorite(t.id);
    list.value = list.value.filter((x) => x.id !== t.id);
    ElMessage.success('已取消收藏');
  } catch (e) {
    if (e === 'cancel' || (e as Error)?.name === 'CanceledError') return;
    ElMessage.error((e as Error).message);
  }
}

function openDetail(t: TutorVO) {
  detailTutor.value = t;
  detailVisible.value = true;
}

function openOrder(t: TutorVO) {
  currentTutor.value = t;
  orderForm.appointDate = '';
  orderForm.timeSlot = '';
  orderVisible.value = true;
  detailVisible.value = false;
}

function sendMsg(t: TutorVO) {
  router.push({ path: '/messages', query: { uid: t.userId, name: t.tutorName } });
}

function disablePast(date: Date) {
  return date.getTime() < Date.now() - 86400000;
}

async function submitOrder() {
  if (!orderForm.appointDate || !orderForm.timeSlot) {
    ElMessage.warning('请选择日期和时间段');
    return;
  }
  if (!currentTutor.value) return;
  submitting.value = true;
  try {
    await createOrder({
      tutorId: currentTutor.value.id,
      appointDate: orderForm.appointDate,
      timeSlot: orderForm.timeSlot,
    });
    ElMessage.success('预约已提交，等待老师确认');
    orderVisible.value = false;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    submitting.value = false;
  }
}

onMounted(() => load());
</script>

<style scoped>
.header-card {
  margin-bottom: 16px;
  border: none;
  border-radius: 12px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.page-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
}
.page-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #94a3b8;
}
.tutor-grid {
  margin-top: 0;
}
.tutor-card {
  position: relative;
  border-radius: 12px;
  border: none;
  transition: transform 0.2s, box-shadow 0.2s;
  margin-bottom: 16px;
}
.tutor-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}
.fav-icon {
  position: absolute;
  top: 14px;
  right: 14px;
  font-size: 20px;
  cursor: pointer;
  color: #dcdfe6;
  transition: all 0.2s;
  z-index: 1;
}
.fav-icon.fav {
  color: #f59e0b;
}
.fav-icon:hover {
  transform: scale(1.2);
}
.tutor-head {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}
.tutor-meta {
  flex: 1;
}
.tutor-name {
  font-size: 17px;
  font-weight: 700;
  color: #1f2937;
}
.tutor-subject {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.grade {
  font-size: 12px;
  color: #909399;
}
.introduce {
  margin: 12px 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tutor-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}
.price {
  color: #ef4444;
  font-size: 20px;
  font-weight: 800;
}
.price em {
  font-size: 12px;
  font-weight: 400;
  color: #9ca3af;
  font-style: normal;
}
.rating {
  color: #f59e0b;
  font-size: 14px;
  font-weight: 600;
}
.action-row {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.action-row .el-button {
  flex: 1;
  border-radius: 8px;
}

/* 详情抽屉 */
.detail-wrap {
  padding: 0 4px;
}
.detail-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
}
.detail-name {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 6px;
}
.detail-stats {
  display: flex;
  background: linear-gradient(135deg, #f0f7ff, #ecfeff);
  border-radius: 12px;
  padding: 16px 0;
  margin-bottom: 20px;
}
.detail-stats .stat {
  flex: 1;
  text-align: center;
}
.detail-stats .stat b {
  display: block;
  font-size: 18px;
  color: #0f172a;
}
.detail-stats .stat b em {
  font-size: 12px;
  color: #9ca3af;
  font-style: normal;
}
.detail-stats .stat span {
  font-size: 12px;
  color: #64748b;
}
.detail-section {
  margin-bottom: 20px;
}
.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #374151;
  margin-bottom: 8px;
}
.section-content {
  color: #6b7280;
  font-size: 14px;
  line-height: 1.7;
  margin: 0;
}
.detail-actions {
  display: flex;
  gap: 10px;
}
.detail-actions .el-button {
  flex: 1;
  border-radius: 10px;
}
</style>
