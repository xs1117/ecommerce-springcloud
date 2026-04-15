<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api, toFileUrl, uploadImage } from '../services/api';
import { clearAuthStorage, setCurrentUserId } from '../services/auth';

const router = useRouter();

const loading = ref(false);
const message = ref('');
const error = ref('');

const profile = ref({
  id: 0,
  username: '',
  nickname: '',
  avatarUrl: '',
  role: '',
  points: 0,
  memberLevel: ''
});

const profileForm = ref({
  nickname: '',
  avatarUrl: ''
});

const passwordForm = ref({
  currentPassword: '',
  newPassword: ''
});

const loadProfile = async () => {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await api.get('/api/user/account/me');
    const avatarUrl = toFileUrl(data.avatarUrl);
    profile.value = data;
    profileForm.value.nickname = data.nickname || '';
    profileForm.value.avatarUrl = avatarUrl;
    setCurrentUserId(data || {});
  } catch (e) {
    error.value = e?.response?.data?.message || '加载账号信息失败';
  } finally {
    loading.value = false;
  }
};

const saveProfile = async () => {
  message.value = '';
  error.value = '';
  try {
    const { data } = await api.put('/api/user/account/profile', {
      nickname: profileForm.value.nickname,
      avatarUrl: profileForm.value.avatarUrl
    });
    profile.value = {
      ...data,
      avatarUrl: toFileUrl(data.avatarUrl)
    };
    profileForm.value.avatarUrl = toFileUrl(data.avatarUrl);
    localStorage.setItem('nickname', data.nickname || data.username);
    message.value = '资料已更新';
  } catch (e) {
    error.value = e?.response?.data?.message || '更新资料失败';
  }
};

const changePassword = async () => {
  message.value = '';
  error.value = '';
  try {
    await api.post('/api/user/account/password', {
      currentPassword: passwordForm.value.currentPassword,
      newPassword: passwordForm.value.newPassword
    });
    passwordForm.value = { currentPassword: '', newPassword: '' };
    message.value = '密码修改成功';
  } catch (e) {
    error.value = e?.response?.data?.message || '修改密码失败';
  }
};

const onAvatarSelect = async (event) => {
  const file = event.target.files?.[0];
  if (!file) {
    return;
  }
  try {
    const upload = await uploadImage('/api/user/account/upload/avatar', file);
    profileForm.value.avatarUrl = upload.url;
    message.value = '头像上传成功，请点击保存资料';
  } catch (e) {
    error.value = e?.response?.data?.message || '头像上传失败';
  } finally {
    event.target.value = '';
  }
};

const goHome = () => router.push('/home');
const goOrders = () => router.push('/orders');
const logout = () => {
  clearAuthStorage();
  router.push('/login');
};

onMounted(loadProfile);
</script>

<template>
  <div class="account-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <div class="brand">
          <div class="brand-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>
          <div class="brand-text">
            <strong>账号中心</strong>
            <span>管理个人信息和账户设置</span>
          </div>
        </div>
        <div class="nav-actions">
          <button class="btn btn-outline" @click="goHome">返回首页</button>
          <button class="btn btn-outline" @click="goOrders">我的订单</button>
          <button class="btn btn-ghost" @click="logout">退出登录</button>
        </div>
      </div>
    </header>

    <main class="container main-content">
      <section v-if="loading" class="card loading-state">
        <div class="spinner"></div>
        <p>加载账号信息中...</p>
      </section>

      <section v-if="message" class="card success-panel fade-in">
        <div class="success-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
        </div>
        {{ message }}
      </section>

      <section v-if="error" class="card error-panel fade-in">
        <div class="error-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        </div>
        {{ error }}
      </section>

      <section class="card profile-section fade-in">
        <div class="section-header">
          <div>
            <span class="section-label">资料</span>
            <h2 class="section-title">基本资料</h2>
          </div>
        </div>

        <div class="profile-grid">
          <div class="avatar-section">
            <div class="avatar-wrapper">
              <img 
                v-if="profileForm.avatarUrl" 
                :src="profileForm.avatarUrl" 
                class="avatar" 
                alt="avatar" 
              />
              <div v-else class="avatar avatar-placeholder">
                {{ profile.nickname?.[0] || profile.username?.[0] || 'U' }}
              </div>
              <div class="avatar-overlay">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"></path>
                  <circle cx="12" cy="13" r="4"></circle>
                </svg>
              </div>
            </div>
            <label class="btn btn-outline upload-btn">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <polyline points="17 8 12 3 7 8"></polyline>
                <line x1="12" y1="3" x2="12" y2="15"></line>
              </svg>
              上传头像
              <input type="file" accept="image/*" @change="onAvatarSelect" hidden />
            </label>
          </div>
          
          <div class="form-section">
            <div class="form-row">
              <div class="form-item">
                <label>用户名</label>
                <input :value="profile.username" disabled class="form-input" />
              </div>
              <div class="form-item">
                <label>昵称</label>
                <input v-model="profileForm.nickname" placeholder="请输入昵称" class="form-input" />
              </div>
            </div>
            <div class="form-row">
              <div class="form-item">
                <label>角色</label>
                <div class="role-badge">
                  <span class="badge" :class="profile.role === 'ADMIN' ? 'badge-danger' : 'badge-primary'">
                    {{ profile.role === 'ADMIN' ? '管理员' : '用户' }}
                  </span>
                </div>
              </div>
              <div class="form-item">
                <label>会员信息</label>
                <div class="member-info">
                  <span class="points">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                    </svg>
                    {{ profile.points }} 积分
                  </span>
                  <span class="badge badge-accent">{{ profile.memberLevel }} 会员</span>
                </div>
              </div>
            </div>
            <button class="btn btn-primary btn-lg" @click="saveProfile">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path>
                <polyline points="17 21 17 13 7 13 7 21"></polyline>
                <polyline points="7 3 7 8 15 8"></polyline>
              </svg>
              保存资料
            </button>
          </div>
        </div>
      </section>

      <section class="card password-section fade-in">
        <div class="section-header">
          <div>
            <span class="section-label">安全</span>
            <h2 class="section-title">修改密码</h2>
          </div>
        </div>

        <div class="password-form">
          <div class="form-item">
            <label>当前密码</label>
            <input v-model="passwordForm.currentPassword" type="password" placeholder="请输入当前密码" class="form-input" />
          </div>
          <div class="form-item">
            <label>新密码</label>
            <input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码（至少6位）" class="form-input" />
          </div>
          <button class="btn btn-accent btn-lg" @click="changePassword">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
            修改密码
          </button>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.account-page {
  min-height: 100vh;
  background: var(--color-rose-50);
}

.navbar {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  background: var(--gradient-glass);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(225, 29, 72, 0.1);
  box-shadow: var(--shadow-sm);
}

.navbar-inner {
  min-height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  background: var(--gradient-primary);
  color: #fff;
  display: grid;
  place-items: center;
  box-shadow: var(--shadow-md);
}

.brand-text strong {
  display: block;
  font-size: 18px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.brand-text span {
  font-size: 13px;
  color: var(--color-gray-500);
}

.nav-actions {
  display: flex;
  gap: 12px;
}

.main-content {
  padding: 24px 0 48px;
  display: grid;
  gap: 20px;
}

.loading-state {
  padding: 80px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
}

.success-panel,
.error-panel {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  font-weight: 500;
}

.error-panel {
  border-color: var(--color-danger);
  background: linear-gradient(135deg, #FEF2F2 0%, #FEE2E2 100%);
  color: var(--color-danger);
}

.success-panel {
  border-color: var(--color-success);
  background: linear-gradient(135deg, #ECFDF5 0%, #D1FAE5 100%);
  color: var(--color-success);
}

.error-icon,
.success-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  display: grid;
  place-items: center;
  background: currentColor;
  color: #fff;
}

.profile-section,
.password-section {
  padding: 32px;
}

.section-header {
  margin-bottom: 28px;
}

.profile-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 48px;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.avatar-wrapper {
  position: relative;
  cursor: pointer;
}

.avatar {
  width: 200px;
  height: 200px;
  object-fit: cover;
  border-radius: var(--radius-2xl);
  border: 4px solid var(--color-surface);
  box-shadow: var(--shadow-xl);
}

.avatar-placeholder {
  display: grid;
  place-items: center;
  background: var(--gradient-primary);
  color: #fff;
  font-size: 64px;
  font-family: var(--font-display);
  font-weight: 800;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  border-radius: var(--radius-2xl);
  display: grid;
  place-items: center;
  opacity: 0;
  transition: opacity var(--transition-base);
  color: #fff;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.upload-btn {
  width: 100%;
  max-width: 200px;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-item label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.form-input {
  width: 100%;
  height: 52px;
  padding: 0 16px;
  border: 2px solid var(--color-gray-200);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  font-size: 15px;
  transition: all var(--transition-base);
}

.form-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px rgba(225, 29, 72, 0.1);
}

.form-input:disabled {
  background: var(--color-gray-50);
  color: var(--color-gray-500);
}

.role-badge {
  height: 52px;
  display: flex;
  align-items: center;
}

.member-info {
  height: 52px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.points {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.points svg {
  color: var(--color-warning);
}

.password-form {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  max-width: 800px;
}

.password-form .form-item:last-of-type {
  grid-column: span 2;
}

.password-form .btn {
  grid-column: span 2;
  max-width: 300px;
  justify-self: end;
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .avatar {
    width: 150px;
    height: 150px;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .password-form {
    grid-template-columns: 1fr;
  }

  .password-form .form-item:last-of-type,
  .password-form .btn {
    grid-column: span 1;
  }
}
</style>
