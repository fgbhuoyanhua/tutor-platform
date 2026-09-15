<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2 class="title">注册账号</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名（登录用）" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" size="large" :prefix-icon="Avatar" />
        </el-form-item>
        <el-form-item prop="phone">
          <el-input v-model="form.phone" placeholder="手机号" size="large" :prefix-icon="Iphone" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码（至少6位）"
            size="large"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item prop="role">
          <el-radio-group v-model="form.role">
            <el-radio :value="ROLE_STUDENT">我是学生</el-radio>
            <el-radio :value="ROLE_TUTOR">我是老师</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="onSubmit">
          注 册
        </el-button>
        <div class="links">
          <el-link type="primary" @click="$router.push('/login')">已有账号？去登录</el-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { User, Lock, Avatar, Iphone } from '@element-plus/icons-vue';
import { ROLE_STUDENT, ROLE_TUTOR } from '@tutor-platform/frontend-common';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive({
  username: '',
  realName: '',
  phone: '',
  password: '',
  role: ROLE_STUDENT,
});

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度 3-20 位', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
};

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    await userStore.register({
      username: form.username,
      realName: form.realName,
      phone: form.phone,
      password: form.password,
      code: '123456',
      role: form.role,
    });
    ElMessage.success('注册成功，请登录');
    router.push('/login');
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
  background: linear-gradient(135deg, #1f2d3d 0%, #409eff 100%);
}
.auth-card {
  width: 400px;
  padding: 12px 8px;
}
.title {
  text-align: center;
  margin: 0 0 24px;
  color: #333;
}
.submit {
  width: 100%;
  margin-top: 8px;
}
.links {
  text-align: center;
  margin-top: 14px;
}
</style>
