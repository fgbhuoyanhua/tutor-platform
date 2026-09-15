<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="cal-header">
        <span>我的授课日历（{{ weekStart }} ~ {{ weekEnd }}）</span>
        <div>
          <el-button size="small" @click="shiftWeek(-1)">上一周</el-button>
          <el-button size="small" @click="shiftWeek(0)">本周</el-button>
          <el-button size="small" @click="shiftWeek(1)">下一周</el-button>
        </div>
      </div>
    </template>
    <div class="calendar">
      <div v-for="(day, idx) in days" :key="idx" class="day-col">
        <div class="day-head" :class="{ today: isToday(day.date) }">
          <div class="week">{{ day.week }}</div>
          <div class="date">{{ day.date.slice(5) }}</div>
        </div>
        <div class="slots">
          <div v-if="day.lessons.length === 0" class="empty-slot">—</div>
          <div
            v-for="l in day.lessons"
            :key="l.id"
            class="lesson"
            :class="statusClass(l.status)"
          >
            <div class="l-time">{{ l.timeSlot }}</div>
            <div class="l-name">{{ l.studentName }} · {{ l.subjectName }}</div>
          </div>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { pageOrders, type OrderVO } from '@tutor-platform/frontend-common';

const loading = ref(false);
const allOrders = ref<OrderVO[]>([]);
const cursor = ref(startOfWeek(new Date()));

const WEEK_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function startOfWeek(d: Date): Date {
  const r = new Date(d);
  const day = r.getDay() || 7; // 周一为一周开始
  r.setDate(r.getDate() - day + 1);
  r.setHours(0, 0, 0, 0);
  return r;
}

function fmt(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

const days = computed(() => {
  const result: { date: string; week: string; lessons: OrderVO[] }[] = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(cursor.value);
    d.setDate(d.getDate() + i);
    const key = fmt(d);
    const lessons = allOrders.value.filter(
      (o) => o.appointDate === key && (o.status === 1 || o.status === 2 || o.status === 3)
    );
    lessons.sort((a, b) => (a.timeSlot < b.timeSlot ? -1 : 1));
    result.push({ date: key, week: WEEK_NAMES[d.getDay()], lessons });
  }
  return result;
});

const weekStart = computed(() => fmt(cursor.value));
const weekEnd = computed(() => {
  const d = new Date(cursor.value);
  d.setDate(d.getDate() + 6);
  return fmt(d);
});

function isToday(dateStr: string): boolean {
  return dateStr === fmt(new Date());
}

function statusClass(s: number): string {
  if (s === 2) return 'teaching';
  if (s === 3) return 'finished';
  return 'booked';
}

function shiftWeek(dir: number) {
  if (dir === 0) {
    cursor.value = startOfWeek(new Date());
  } else {
    const d = new Date(cursor.value);
    d.setDate(d.getDate() + dir * 7);
    cursor.value = d;
  }
}

onMounted(async () => {
  loading.value = true;
  try {
    // 老师端一次拉足够多订单用于日历展示
    const res = await pageOrders({ page: 1, size: 200 });
    allOrders.value = res.records;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.cal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.calendar {
  display: flex;
  gap: 8px;
}
.day-col {
  flex: 1;
  min-width: 0;
}
.day-head {
  text-align: center;
  padding: 8px 0;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 8px;
}
.day-head.today {
  background: #ecf5ff;
}
.week {
  font-size: 12px;
  color: #909399;
}
.date {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.slots {
  min-height: 180px;
}
.empty-slot {
  text-align: center;
  color: #c0c4cc;
  padding: 20px 0;
}
.lesson {
  padding: 8px;
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #fff;
}
.lesson.booked {
  background: #409eff;
}
.lesson.teaching {
  background: #e6a23c;
}
.lesson.finished {
  background: #67c23a;
}
.l-time {
  font-weight: 600;
}
.l-name {
  margin-top: 2px;
  opacity: 0.95;
}
</style>
