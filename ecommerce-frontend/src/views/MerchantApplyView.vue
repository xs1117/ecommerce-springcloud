<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../services/api';

const router = useRouter();
const loadingStatus = ref(false);
const submitting = ref(false);
const status = ref('UNKNOWN');
const message = ref('');
const error = ref('');

const form = ref({
  shopName: '',
  businessScope: '',
  contactPhone: ''
});

const loadStatus = async () => {
  loadingStatus.value = true;
  error.value = '';
  try {
    const { data } = await api.get('/api/merchant/applications/status');
    status.value = data?.status || 'NONE';
  } catch (e) {
    status.value = 'UNKNOWN';
    error.value = e?.response?.data?.message || '获取申请状态失败';
  } finally {
    loadingStatus.value = false;
  }
};

const submitApply = async () => {
  if (!form.value.shopName.trim()) {
    error.value = '店铺名称不能为空';
    return;
  }
  submitting.value = true;
  error.value = '';
  message.value = '';
  try {
    await api.post('/api/merchant/applications/apply', {
      shopName: form.value.shopName,
      businessScope: form.value.businessScope,
      contactPhone: form.value.contactPhone
    });
    message.value = '申请提交成功，请等待管理员审核';
    form.value = { shopName: '', businessScope: '', contactPhone: '' };
    await loadStatus();
  } catch (e) {
    error.value = e?.response?.data?.message || '申请提交失败';
  } finally {
    submitting.value = false;
  }
};

const goHome = () => router.push('/home');

onMounted(loadStatus);
</script>

<template>
  <div class="apply-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <div class="brand">
          <div class="brand-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
              <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
            </svg>
          </div>
          <div class="brand-text">
            <strong>商家入驻</strong>
            <span>申请成为平台商家</span>
          </div>
        </div>
        <button class="btn btn-outline" @click="goHome">返回首页</button>
      </div>
    </header>

    <main class="container main-content">
      <section class="status-card fade-in">
        <div class="status-icon" :class="{
          'status-approved': status === 'APPROVED',
          'status-pending': status === 'PENDING',
          'status-rejected': status === 'REJECTED'
        }">
          <svg v-if="status === 'APPROVED'" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
            <polyline points="22 4 12 14.01 9 11.01"></polyline>
          </svg>
          <svg v-else-if="status === 'PENDING'" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="12 6 12 12 16 14"></polyline>
          </svg>
          <svg v-else-if="status === 'REJECTED'" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="15" y1="9" x2="9" y2="15"></line>
            <line x1="9" y1="9" x2="15" y2="15"></line>
          </svg>
          <svg v-else width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
          </svg>
        </div>
        <div class="status-content">
          <span class="status-label">当前状态</span>
          <h2 v-if="loadingStatus">加载中...</h2>
          <h2 v-else-if="status === 'APPROVED'" class="status-approved">审核通过</h2>
          <h2 v-else-if="status === 'PENDING'" class="status-pending">审核中</h2>
          <h2 v-else-if="status === 'REJECTED'" class="status-rejected">已驳回</h2>
          <h2 v-else>未申请</h2>
          <p v-if="status === 'APPROVED'">恭喜！您已通过审核，可以前往商家中心开店上架商品。</p>
          <p v-else-if="status === 'PENDING'">您的申请正在审核中，请耐心等待管理员处理。</p>
          <p v-else-if="status === 'REJECTED'">申请被驳回，您可以修改信息后重新提交申请。</p>
          <p v-else>填写以下信息申请成为平台商家。</p>
        </div>
        <button v-if="status === 'APPROVED'" class="btn btn-primary btn-lg" @click="router.push('/merchant/center')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
          </svg>
          进入商家中心
        </button>
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

      <section v-if="status !== 'APPROVED' && status !== 'PENDING'" class="card form-section fade-in">
        <div class="section-header">
          <div>
            <span class="section-label">申请</span>
            <h2 class="section-title">填写入驻信息</h2>
          </div>
        </div>

        <div class="apply-form">
          <div class="form-item">
            <label>店铺名称 <span class="required">*</span></label>
            <input v-model="form.shopName" placeholder="请输入店铺名称" class="form-input" />
          </div>
          <div class="form-item">
            <label>经营范围</label>
            <input v-model="form.businessScope" placeholder="例如：数码配件、家居日用、服装鞋帽" class="form-input" />
          </div>
          <div class="form-item">
            <label>联系方式</label>
            <input v-model="form.contactPhone" placeholder="手机号或座机" class="form-input" />
          </div>
          <div class="form-actions">
            <button class="btn btn-primary btn-lg" :disabled="submitting" @click="submitApply">
              <span v-if="submitting" class="spinner"></span>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="22" y1="2" x2="11" y2="13"></line>
                <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
              </svg>
              {{ submitting ? '提交中...' : '提交申请' }}
            </button>
          </div>
        </div>
      </section>

      <section class="card benefits-section fade-in">
        <div class="section-header">
          <div>
            <span class="section-label">优势</span>
            <h2 class="section-title">成为商家的好处</h2>
          </div>
        </div>

        <div class="benefits-grid">
          <div class="benefit-item">
            <div class="benefit-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
                <line x1="3" y1="6" x2="21" y2="6"></line>
                <path d="M16 10a4 4 0 0 1-8 0"></path>
              </svg>
            </div>
            <strong>商品管理</strong>
            <p>轻松上架和管理您的商品</p>
          </div>
          <div class="benefit-item">
            <div class="benefit-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="1" x2="12" y2="23"></line>
                <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
              </svg>
            </div>
            <strong>收益结算</strong>
            <p>清晰的订单和收益数据</p>
          </div>
          <div class="benefit-item">
            <div class="benefit-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="9" cy="7" r="4"></circle>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
              </svg>
            </div>
            <strong>客户触达</strong>
            <p>直接触达平台用户群体</p>
          </div>
          <div class="benefit-item">
            <div class="benefit-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 20V10"></path>
                <path d="M18 20V4"></path>
                <path d="M6 20v-4"></path>
              </svg>
            </div>
            <strong>数据分析</strong>
            <p>详细的店铺运营数据</p>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.apply-page {
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

.main-content {
  padding: 24px 0 48px;
  display: grid;
  gap: 20px;
}

.status-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 32px;
  background: var(--color-surface);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
  border: 2px solid var(--color-gray-100);
}

.status-icon {
  width: 80px;
  height: 80px;
  border-radius: var(--radius-xl);
  display: grid;
  place-items: center;
  flex-shrink: 0;
  background: var(--color-gray-100);
  color: var(--color-gray-500);
}

.status-icon.status-approved {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(52, 211, 153, 0.1) 100%);
  color: var(--color-success);
}

.status-icon.status-pending {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, rgba(251, 191, 36, 0.1) 100%);
  color: var(--color-warning);
}

.status-icon.status-rejected {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.1) 0%, rgba(248, 113, 113, 0.1) 100%);
  color: var(--color-danger);
}

.status-content {
  flex: 1;
}

.status-label {
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-gray-500);
  font-weight: 600;
}

.status-content h2 {
  margin: 8px 0;
  font-size: 28px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.status-content h2.status-approved { color: var(--color-success); }
.status-content h2.status-pending { color: var(--color-warning); }
.status-content h2.status-rejected { color: var(--color-danger); }

.status-content p {
  margin: 0;
  color: var(--color-gray-600);
  font-size: 15px;
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

.form-section,
.benefits-section {
  padding: 32px;
}

.section-header {
  margin-bottom: 28px;
}

.apply-form {
  display: grid;
  gap: 20px;
  max-width: 600px;
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

.required {
  color: var(--color-danger);
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

.form-actions {
  padding-top: 8px;
}

.benefits-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.benefit-item {
  padding: 24px;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  border: 2px solid var(--color-gray-100);
  text-align: center;
  transition: all var(--transition-base);
}

.benefit-item:hover {
  border-color: var(--color-primary);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.benefit-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgba(225, 29, 72, 0.1) 0%, rgba(251, 113, 133, 0.1) 100%);
  display: grid;
  place-items: center;
  margin: 0 auto 16px;
  color: var(--color-primary);
}

.benefit-item strong {
  display: block;
  margin-bottom: 8px;
  font-size: 16px;
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-gray-900);
}

.benefit-item p {
  margin: 0;
  font-size: 14px;
  color: var(--color-gray-500);
}

@media (max-width: 1024px) {
  .benefits-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .status-card {
    flex-direction: column;
    text-align: center;
  }

  .benefits-grid {
    grid-template-columns: 1fr;
  }
}
</style>
