<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="title">大学生家教服务预约平台</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="onSubmit">
          登 录
        </el-button>
        <div class="links">
          <el-link type="primary" @click="$router.push('/register')">还没有账号？去注册</el-link>
        </div>
        <el-alert class="demo-tip" type="info" :closable="false">
          <p>演示账号（密码均为 123456）：</p>
          <p>学生 student01 ｜ 老师 tutor01 ｜ 管理员 admin</p>
        </el-alert>
      </el-form>
    </el-card>
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
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1a1d2e 0%, #4f6ef7 50%, #6b8afd 100%);
  position: relative;
  overflow: hidden;
}
.auth-page::before {
  content: '';
  position: absolute;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255,255,255,0.08) 0%, transparent 70%);
  top: -200px;
  right: -200px;
}
.auth-page::after {
  content: '';
  position: absolute;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255,255,255,0.06) 0%, transparent 70%);
  bottom: -100px;
  left: -100px;
}
.auth-card {
  width: 420px;
  padding: 40px 36px;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  position: relative;
  z-index: 1;
  backdrop-filter: blur(10px);
}
.title {
  text-align: center;
  margin: 0 0 32px;
  color: #1a1b1c;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}
.submit {
  width: 100%;
  margin-top: 12px;
  border-radius: 10px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
}
.links {
  text-align: center;
  margin-top: 16px;
}
.demo-tip {
  margin-top: 24px;
  border-radius: 8px;
}
.demo-tip p {
  margin: 2px 0;
  font-size: 12px;
}
</style>
