import { createRouter, createWebHistory } from 'vue-router';
import { getToken, getUserJSON } from '@tutor-platform/frontend-common';

const ROLE_ADMIN = 3;

function currentRole(): number {
  const raw = getUserJSON();
  if (!raw) return 0;
  try {
    return (JSON.parse(raw) as { role?: number }).role ?? 0;
  } catch {
    return 0;
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/Login.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/auth/Register.vue'),
      meta: { public: true, title: '注册' },
    },
    {
      path: '/',
      component: () => import('@/components/layout/MainLayout.vue'),
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/dashboard/Home.vue'),
          meta: { title: '工作台' },
        },
        {
          path: 'tutors',
          name: 'tutors',
          component: () => import('@/views/tutor/TutorList.vue'),
          meta: { title: '找家教', roles: [1] },
        },
        {
          path: 'my-tutor',
          name: 'myTutor',
          component: () => import('@/views/tutor/MyTutor.vue'),
          meta: { title: '我的家教信息', roles: [2] },
        },
        {
          path: 'tutor/calendar',
          name: 'tutorCalendar',
          component: () => import('@/views/tutor/TutorCalendar.vue'),
          meta: { title: '授课日历', roles: [2] },
        },
        {
          path: 'tutor/students',
          name: 'tutorStudents',
          component: () => import('@/views/tutor/MyStudents.vue'),
          meta: { title: '我的学生', roles: [2] },
        },
        {
          path: 'tutor/evaluations',
          name: 'tutorEvals',
          component: () => import('@/views/tutor/MyEvaluations.vue'),
          meta: { title: '我的评价', roles: [2] },
        },
        {
          path: 'tutor/income',
          name: 'tutorIncome',
          component: () => import('@/views/tutor/TutorIncome.vue'),
          meta: { title: '收入统计', roles: [2] },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/order/OrderList.vue'),
          meta: { title: '我的订单', roles: [1, 2] },
        },
        {
          path: 'student/calendar',
          name: 'studentCalendar',
          component: () => import('@/views/student/StudentCalendar.vue'),
          meta: { title: '我的课程表', roles: [1] },
        },
        {
          path: 'messages',
          name: 'messages',
          component: () => import('@/views/message/MessageCenter.vue'),
          meta: { title: '我的消息', roles: [1, 2] },
        },
        {
          path: 'admin/stats',
          name: 'adminStats',
          component: () => import('@/views/admin/Stats.vue'),
          meta: { title: '数据统计', roles: [ROLE_ADMIN] },
        },
        {
          path: 'admin/tutor-audit',
          name: 'adminTutorAudit',
          component: () => import('@/views/admin/TutorAudit.vue'),
          meta: { title: '家教审核', roles: [ROLE_ADMIN] },
        },
        {
          path: 'admin/users',
          name: 'adminUsers',
          component: () => import('@/views/admin/UserManage.vue'),
          meta: { title: '用户管理', roles: [ROLE_ADMIN] },
        },
        {
          path: 'admin/logs',
          name: 'adminLogs',
          component: () => import('@/views/admin/AdminLog.vue'),
          meta: { title: '操作日志', roles: [ROLE_ADMIN] },
        },
      ],
    },
  ],
});

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title as string} - 家教预约平台` : '家教预约平台';
  if (to.meta.public) {
    return true;
  }
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  // 角色守卫：路由声明了允许角色而当前用户不匹配时，重定向回工作台
  const allowed = to.meta.roles as number[] | undefined;
  if (allowed && !allowed.includes(currentRole())) {
    return { path: '/home' };
  }
  return true;
});

export default router;
