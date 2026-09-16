<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <div class="brand-side">
      <div class="brand-bg">
        <div class="blob blob-1"></div>
        <div class="blob blob-2"></div>
        <div class="blob blob-3"></div>
      </div>
      <div class="brand-content">
        <div class="logo-row">
          <span class="logo-text">青蓝</span>
          <span class="logo-divider"></span>
          <span class="logo-sub">家教平台</span>
        </div>
        <h1 class="brand-title">
          连接每一份求知<br />
          <span class="highlight">点亮每一次成长</span>
        </h1>
        <p class="brand-sub">
          大学生以己之长授业解惑，求学者得良师伴行——在这里，知识传递有温度
        </p>
        <div class="brand-stats">
          <div class="stat">
            <b>2,000+</b>
            <span>大学生老师</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat">
            <b>50,000+</b>
            <span>成功课时</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat">
            <b>98%</b>
            <span>好评率</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-side">
      <div class="form-wrap">
        <div class="form-header">
          <h2 class="welcome">欢迎回来</h2>
          <p class="welcome-sub">登录你的账号继续</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @keyup.enter="onSubmit">
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              size="large"
              :prefix-icon="User"
              class="custom-input"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              show-password
              :prefix-icon="Lock"
              class="custom-input"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="onSubmit"
          >
            登 录
          </el-button>
        </el-form>

        <div class="divider">
          <span>或</span>
        </div>

        <div class="links">
          还没有账号？
          <el-link type="primary" @click="$router.push('/register')">立即注册</el-link>
        </div>

        <div class="demo-accounts">
          <div class="demo-title">演示账号</div>
          <div class="demo-list">
            <span class="demo-tag">学生 student01</span>
            <span class="demo-tag">老师 tutor01</span>
            <span class="demo-tag">管理员 admin</span>
          </div>
          <div class="demo-pwd">密码均为 123456</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { User, Lock } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive({ username: '', password: '' });
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    await userStore.login(form.username, form.password);
    ElMessage.success('登录成功');
    const redirect = (route.query.redirect as string) || '/home';
    router.push(redirect);
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: #f8fafc;
}

/* ===== 左侧品牌区 ===== */
.brand-side {
  flex: 1.1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #0f172a 0%, #1e3a5f 40%, #0e7490 100%);
  overflow: hidden;
}
.brand-bg {
  position: absolute;
  inset: 0;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
}
.blob-1 {
  width: 500px;
  height: 500px;
  background: #06b6d4;
  top: -150px;
  right: -100px;
  animation: float 8s ease-in-out infinite;
}
.blob-2 {
  width: 400px;
  height: 400px;
  background: #3b82f6;
  bottom: -100px;
  left: -80px;
  animation: float 10s ease-in-out infinite reverse;
}
.blob-3 {
  width: 300px;
  height: 300px;
  background: #8b5cf6;
  top: 50%;
  left: 40%;
  opacity: 0.3;
  animation: float 12s ease-in-out infinite;
}
@keyframes float {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(30px, -30px) scale(1.05); }
}

.brand-content {
  position: relative;
  z-index: 1;
  padding: 60px;
  max-width: 560px;
  animation: fadeUp 0.8s ease;
}
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}
.logo-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 60px;
}
.logo-text {
  font-size: 32px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 4px;
}
.logo-divider {
  width: 1px;
  height: 24px;
  background: rgba(255,255,255,0.2);
  margin: 0 4px;
  align-self: center;
}
.logo-sub {
  font-size: 16px;
  color: #22d3ee;
  letter-spacing: 2px;
}
.brand-title {
  font-size: 48px;
  font-weight: 800;
  color: #fff;
  line-height: 1.25;
  margin: 0 0 24px;
  letter-spacing: -1px;
}
.highlight {
  background: linear-gradient(135deg, #22d3ee, #60a5fa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.brand-sub {
  font-size: 17px;
  color: rgba(255,255,255,0.7);
  line-height: 1.7;
  margin: 0 0 56px;
  max-width: 420px;
}
.brand-stats {
  display: flex;
  align-items: center;
  gap: 32px;
}
.stat {
  display: flex;
  flex-direction: column;
}
.stat b {
  font-size: 28px;
  font-weight: 800;
  color: #fff;
}
.stat span {
  font-size: 13px;
  color: rgba(255,255,255,0.6);
  margin-top: 4px;
}
.stat-divider {
  width: 1px;
  height: 40px;
  background: rgba(255,255,255,0.15);
}

/* ===== 右侧表单区 ===== */
.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
}
.form-wrap {
  width: 100%;
  max-width: 400px;
  padding: 40px;
  animation: fadeUp 0.8s ease 0.2s backwards;
}
.form-header {
  margin-bottom: 40px;
}
.welcome {
  font-size: 28px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 8px;
  letter-spacing: -0.5px;
}
.welcome-sub {
  font-size: 15px;
  color: #64748b;
  margin: 0;
}

:deep(.custom-input .el-input__wrapper) {
  border-radius: 12px;
  padding: 4px 16px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
  transition: all 0.25s;
}
:deep(.custom-input .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #94a3b8 inset;
}
:deep(.custom-input .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #3b82f6 inset;
}
:deep(.custom-input .el-input__inner) {
  height: 44px;
  font-size: 15px;
}

.submit-btn {
  width: 100%;
  height: 48px;
  margin-top: 8px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 4px;
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
  box-shadow: 0 8px 20px rgba(59,130,246,0.35);
  transition: all 0.25s;
}
.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(59,130,246,0.45);
}
.submit-btn:active {
  transform: translateY(0);
}

.divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 28px 0;
  color: #94a3b8;
  font-size: 13px;
}
.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e2e8f0;
}

.links {
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

.demo-accounts {
  margin-top: 36px;
  padding: 20px;
  background: #f1f5f9;
  border-radius: 12px;
}
.demo-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 10px;
}
.demo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.demo-tag {
  padding: 4px 12px;
  background: #fff;
  border-radius: 6px;
  font-size: 12px;
  color: #3b82f6;
  font-weight: 500;
}
.demo-pwd {
  font-size: 12px;
  color: #94a3b8;
}

@media (max-width: 900px) {
  .brand-side { display: none; }
  .form-side { flex: 1; }
}
</style>
