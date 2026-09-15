import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import {
  login as apiLogin,
  register as apiRegister,
  getToken,
  setToken,
  clearToken,
  getUserJSON,
  setUserJSON,
  ROLE_STUDENT,
  ROLE_TUTOR,
  ROLE_ADMIN,
  type LoginVO,
  type RegisterDTO,
} from '@tutor-platform/frontend-common';

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken());
  const user = ref<LoginVO | null>(loadUser());

  function loadUser(): LoginVO | null {
    const raw = getUserJSON();
    if (!raw) return null;
    try {
      return JSON.parse(raw) as LoginVO;
    } catch {
      return null;
    }
  }

  const isLoggedIn = computed(() => !!token.value);
  const role = computed(() => user.value?.role ?? 0);
  const isStudent = computed(() => role.value === ROLE_STUDENT);
  const isTutor = computed(() => role.value === ROLE_TUTOR);
  const isAdmin = computed(() => role.value === ROLE_ADMIN);

  async function login(username: string, password: string): Promise<void> {
    const data = await apiLogin(username, password);
    token.value = data.token;
    user.value = data;
    setToken(data.token);
    setUserJSON(JSON.stringify(data));
  }

  async function register(dto: RegisterDTO): Promise<void> {
    await apiRegister(dto);
  }

  function logout(): void {
    token.value = null;
    user.value = null;
    clearToken();
  }

  return {
    token,
    user,
    isLoggedIn,
    role,
    isStudent,
    isTutor,
    isAdmin,
    login,
    register,
    logout,
  };
});
