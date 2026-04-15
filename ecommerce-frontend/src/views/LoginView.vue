<script setup>
import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../services/api';
import { setCurrentUserId } from '../services/auth';

const router = useRouter();
const loading = ref(false);
const errorMessage = ref('');
const mode = ref('login');
const loginRole = ref('USER');

const form = reactive({
  username: 'user',
  nickname: '普通会员',
  password: '123456'
});

const title = computed(() => (mode.value === 'login' ? '登录' : '注册'));
const hint = computed(() => {
  if (mode.value === 'register') return '注册仅开放用户账号，注册后会自动进入用户体系。';
  return loginRole.value === 'ADMIN'
    ? '管理员入口：admin / 123456'
    : '用户入口：user / 123456';
});

const resolveSubmitErrorMessage = (error) => {
  const backendMessage = error?.response?.data?.message
    || error?.response?.data?.error
    || error?.response?.data?.detail;
  if (backendMessage) {
    if (backendMessage.includes('账号不存在')) return '账号不存在';
    if (backendMessage.includes('密码不正确')) return '密码不正确';
    if (backendMessage.includes('账号或密码错误')) return '账号不存在或密码不正确';
    return backendMessage;
  }
  if (mode.value === 'login' && [401, 403].includes(error?.response?.status)) {
    return '账号不存在或密码不正确';
  }
  if (mode.value === 'login') {
    return '账号不存在或密码不正确';
  }
  return '服务暂不可用，请稍后重试';
};

const submit = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const url = mode.value === 'register'
      ? '/api/user/auth/register'
      : loginRole.value === 'ADMIN'
        ? '/api/admin/auth/login'
        : '/api/user/auth/login';

    const payload = mode.value === 'register'
      ? {
          username: form.username,
          nickname: form.nickname,
          password: form.password
        }
      : {
          username: form.username,
          password: form.password
        };

    const { data } = await api.post(url, payload);
    localStorage.setItem('token', data.token);
    localStorage.setItem('username', data.username);
    localStorage.setItem('nickname', data.nickname || data.username);
    localStorage.setItem('role', data.role || loginRole.value);
    localStorage.setItem('points', String(data.points ?? 0));
    localStorage.setItem('memberLevel', data.memberLevel || 'BRONZE');
    setCurrentUserId(data || {});
    router.push('/home');
  } catch (error) {
    errorMessage.value = resolveSubmitErrorMessage(error);
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-shape bg-shape-1"></div>
      <div class="bg-shape bg-shape-2"></div>
      <div class="bg-shape bg-shape-3"></div>
      <div class="bg-grid"></div>
    </div>
    
    <div class="login-container">
      <div class="login-left">
        <div class="brand-section fade-in-up">
          <div class="brand-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
              <line x1="3" y1="6" x2="21" y2="6"></line>
              <path d="M16 10a4 4 0 0 1-8 0"></path>
            </svg>
          </div>
          <h1>ShopMall</h1>
          <p>发现好物，享受购物乐趣</p>
        </div>

        <div class="features-section">
          <div class="feature-item slide-in-right" style="animation-delay: 0.1s;">
            <div class="feature-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"></path>
              </svg>
            </div>
            <div class="feature-content">
              <strong>海量商品</strong>
              <p>精选优质商品，满足您的所有需求</p>
            </div>
          </div>
          <div class="feature-item slide-in-right" style="animation-delay: 0.2s;">
            <div class="feature-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
              </svg>
            </div>
            <div class="feature-content">
              <strong>安全保障</strong>
              <p>多重安全防护，购物无忧</p>
            </div>
          </div>
          <div class="feature-item slide-in-right" style="animation-delay: 0.3s;">
            <div class="feature-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <polyline points="12 6 12 12 16 14"></polyline>
              </svg>
            </div>
            <div class="feature-content">
              <strong>极速配送</strong>
              <p>快速物流，准时送达</p>
            </div>
          </div>
        </div>
      </div>

      <div class="login-right">
        <div class="login-card card fade-in">
          <div class="card-header">
            <div class="tabs">
              <button 
                class="tab" 
                :class="{ active: mode === 'login' }" 
                @click="mode = 'login'"
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path>
                  <polyline points="10 17 15 12 10 7"></polyline>
                  <line x1="15" y1="12" x2="3" y2="12"></line>
                </svg>
                登录
              </button>
              <button 
                class="tab" 
                :class="{ active: mode === 'register' }" 
                @click="mode = 'register'"
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle cx="8.5" cy="7" r="4"></circle>
                  <line x1="20" y1="8" x2="20" y2="14"></line>
                  <line x1="23" y1="11" x2="17" y2="11"></line>
                </svg>
                注册
              </button>
            </div>
          </div>

          <div class="card-body">
            <h2 class="form-title">{{ title }}商城账号</h2>
            <p class="form-hint">{{ hint }}</p>

            <div v-if="mode === 'login'" class="role-switch">
              <label class="radio-label" :class="{ active: loginRole === 'USER' }">
                <input v-model="loginRole" type="radio" value="USER" />
                <span class="radio-custom">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                    <circle cx="12" cy="7" r="4"></circle>
                  </svg>
                </span>
                <span class="radio-text">用户登录</span>
              </label>
              <label class="radio-label" :class="{ active: loginRole === 'ADMIN' }">
                <input v-model="loginRole" type="radio" value="ADMIN" />
                <span class="radio-custom">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"></path>
                  </svg>
                </span>
                <span class="radio-text">管理员登录</span>
              </label>
            </div>

            <form @submit.prevent="submit" class="login-form">
              <div class="form-group">
                <label class="form-label">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                    <circle cx="12" cy="7" r="4"></circle>
                  </svg>
                  用户名
                </label>
                <input 
                  v-model="form.username" 
                  class="form-input"
                  placeholder="请输入用户名"
                  required
                />
              </div>

              <div v-if="mode === 'register'" class="form-group">
                <label class="form-label">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                    <circle cx="12" cy="7" r="4"></circle>
                  </svg>
                  昵称
                </label>
                <input 
                  v-model="form.nickname" 
                  class="form-input"
                  placeholder="请输入昵称"
                  required
                />
              </div>

              <div class="form-group">
                <label class="form-label">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                  </svg>
                  密码
                </label>
                <input 
                  v-model="form.password" 
                  type="password" 
                  class="form-input"
                  placeholder="请输入密码"
                  required
                />
              </div>

              <button 
                type="submit" 
                class="btn btn-primary btn-lg btn-block"
                :disabled="loading"
              >
                <span v-if="loading" class="spinner"></span>
                <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path>
                  <polyline points="10 17 15 12 10 7"></polyline>
                  <line x1="15" y1="12" x2="3" y2="12"></line>
                </svg>
                {{ loading ? '处理中...' : title }}
              </button>

              <p v-if="errorMessage" class="error-message">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="8" x2="12" y2="12"></line>
                  <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
                {{ errorMessage }}
              </p>
            </form>
          </div>

          <div class="card-footer">
            <p>使用本平台即表示同意我们的服务条款和隐私政策</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  position: relative;
  overflow: hidden;
  background: var(--color-rose-50);
}

.login-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.5;
}

.bg-shape-1 {
  width: 600px;
  height: 600px;
  background: var(--color-rose-200);
  top: -200px;
  right: -100px;
  animation: float 12s ease-in-out infinite;
}

.bg-shape-2 {
  width: 500px;
  height: 500px;
  background: var(--color-rose-300);
  bottom: -150px;
  left: -100px;
  animation: float 15s ease-in-out infinite reverse;
}

.bg-shape-3 {
  width: 400px;
  height: 400px;
  background: rgba(37, 99, 235, 0.15);
  top: 50%;
  left: 30%;
  transform: translateY(-50%);
  animation: pulse 8s ease-in-out infinite;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image: 
    linear-gradient(rgba(225, 29, 72, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(225, 29, 72, 0.03) 1px, transparent 1px);
  background-size: 40px 40px;
}

.login-container {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 480px;
  gap: 80px;
  padding: 60px 40px;
  position: relative;
  z-index: 1;
  align-items: center;
}

.login-left {
  display: flex;
  flex-direction: column;
  gap: 60px;
}

.brand-section {
  text-align: left;
}

.brand-icon {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-xl);
  background: var(--gradient-primary);
  color: #fff;
  display: grid;
  place-items: center;
  box-shadow: var(--shadow-xl), 0 8px 32px rgba(225, 29, 72, 0.4);
  margin-bottom: 24px;
}

.brand-section h1 {
  margin: 0 0 12px;
  font-size: 48px;
  font-family: var(--font-display);
  font-weight: 800;
  color: var(--color-gray-900);
}

.brand-section p {
  margin: 0;
  color: var(--color-gray-600);
  font-size: 18px;
}

.features-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  background: var(--gradient-glass);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-base);
}

.feature-item:hover {
  transform: translateX(8px);
  box-shadow: var(--shadow-md);
}

.feature-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-lg);
  background: var(--color-rose-100);
  display: grid;
  place-items: center;
  color: var(--color-primary);
  flex-shrink: 0;
}

.feature-content strong {
  display: block;
  font-size: 16px;
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-gray-900);
  margin-bottom: 4px;
}

.feature-content p {
  margin: 0;
  font-size: 14px;
  color: var(--color-gray-500);
}

.login-right {
  display: flex;
  justify-content: center;
}

.login-card {
  width: 100%;
  max-width: 440px;
  background: var(--color-surface);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-2xl);
  border: 1px solid rgba(225, 29, 72, 0.08);
  overflow: hidden;
}

.card-header {
  padding: 24px 32px 0;
}

.tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  background: var(--color-gray-100);
  padding: 6px;
  border-radius: var(--radius-xl);
}

.tab {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--color-gray-500);
  font-weight: 600;
  font-size: 15px;
  transition: all var(--transition-base);
}

.tab:hover {
  color: var(--color-gray-700);
}

.tab.active {
  background: var(--color-surface);
  color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.card-body {
  padding: 32px;
}

.form-title {
  margin: 0 0 8px;
  font-size: 28px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.form-hint {
  margin: 0 0 28px;
  color: var(--color-gray-500);
  font-size: 14px;
}

.role-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 24px;
  padding: 16px;
  background: var(--color-rose-50);
  border-radius: var(--radius-xl);
  border: 2px solid var(--color-rose-100);
}

.radio-label {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-lg);
  cursor: pointer;
  font-size: 14px;
  color: var(--color-gray-600);
  transition: all var(--transition-base);
  border: 2px solid transparent;
}

.radio-label:hover {
  background: var(--color-surface);
}

.radio-label.active {
  background: var(--color-surface);
  color: var(--color-primary);
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.radio-label input[type="radio"] {
  display: none;
}

.radio-custom {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  background: var(--color-gray-100);
  display: grid;
  place-items: center;
  color: var(--color-gray-400);
  transition: all var(--transition-base);
}

.radio-label.active .radio-custom {
  background: var(--gradient-primary);
  color: #fff;
  box-shadow: var(--shadow-sm);
}

.radio-text {
  font-weight: 600;
}

.login-form {
  display: grid;
  gap: 20px;
}

.form-group {
  display: grid;
  gap: 10px;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.form-label svg {
  color: var(--color-primary);
}

.form-input {
  width: 100%;
  height: 52px;
  border-radius: var(--radius-lg);
  border: 2px solid var(--color-gray-200);
  background: var(--color-gray-50);
  font-size: 15px;
  transition: all var(--transition-base);
}

.form-input:focus {
  border-color: var(--color-primary);
  background: var(--color-surface);
  box-shadow: 0 0 0 4px rgba(225, 29, 72, 0.1);
}

.btn-block {
  width: 100%;
  height: 52px;
  margin-top: 8px;
  font-size: 16px;
}

.error-message {
  margin: 0;
  padding: 14px 16px;
  background: linear-gradient(135deg, #FEF2F2 0%, #FEE2E2 100%);
  border: 2px solid var(--color-danger);
  border-radius: var(--radius-lg);
  color: var(--color-danger);
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-footer {
  padding: 20px 32px;
  background: var(--color-gray-50);
  border-top: 1px solid var(--color-gray-100);
}

.card-footer p {
  margin: 0;
  font-size: 12px;
  color: var(--color-gray-500);
  text-align: center;
}

@media (max-width: 1024px) {
  .login-container {
    grid-template-columns: 1fr;
    gap: 40px;
    padding: 40px 24px;
  }

  .login-left {
    display: none;
  }
}

@media (max-width: 480px) {
  .login-card {
    border-radius: var(--radius-xl);
  }

  .card-header,
  .card-body {
    padding: 24px;
  }

  .form-title {
    font-size: 24px;
  }

  .role-switch {
    grid-template-columns: 1fr;
  }

  .tabs {
    padding: 4px;
  }

  .tab {
    padding: 12px;
    font-size: 14px;
  }
}
</style>
