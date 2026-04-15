<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../services/api';

const router = useRouter();
const loading = ref(false);
const loadingStores = ref(false);
const reviewingId = ref(0);
const updatingStoreId = ref(0);
const errorMessage = ref('');
const items = ref([]);
const stores = ref([]);
const storeKeyword = ref('');
const couponTemplates = ref([]);
const notices = ref([]);
const couponKeyword = ref('');
const couponStatusFilter = ref('ALL');
const couponPage = ref(1);
const noticeKeyword = ref('');
const noticeStatusFilter = ref('ALL');
const noticePage = ref(1);
const pageSize = 6;
const savingCoupon = ref(false);
const savingNotice = ref(false);
const editingCouponId = ref(0);
const editingNoticeId = ref(0);
const couponEditForm = ref({
  name: '',
  pointsCost: 100,
  threshold: 100,
  discountAmount: 10,
  description: '',
  status: 1
});
const noticeEditForm = ref({
  title: '',
  content: '',
  sortNo: 50,
  status: 1
});
const couponForm = ref({
  name: '',
  pointsCost: 100,
  threshold: 100,
  discountAmount: 10,
  description: ''
});
const noticeForm = ref({
  title: '',
  content: '',
  sortNo: 50
});

const loadPending = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const { data } = await api.get('/api/admin/merchant/applications', {
      params: { status: 'PENDING' }
    });
    items.value = data || [];
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '加载待审核申请失败';
  } finally {
    loading.value = false;
  }
};

const review = async (id, approved) => {
  reviewingId.value = id;
  errorMessage.value = '';
  try {
    await api.post(`/api/admin/merchant/applications/${id}/review`, {
      approved,
      comment: approved ? '审核通过' : '审核驳回'
    });
    await loadPending();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '审核失败';
  } finally {
    reviewingId.value = 0;
  }
};

const loadStores = async () => {
  loadingStores.value = true;
  errorMessage.value = '';
  try {
    const { data } = await api.get('/api/admin/merchant/stores', {
      params: {
        keyword: storeKeyword.value.trim() || undefined
      }
    });
    stores.value = data || [];
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '加载店铺列表失败';
  } finally {
    loadingStores.value = false;
  }
};

const openStoreProducts = async (store) => {
  router.push({
    path: `/admin/merchant/stores/${store.id}`,
    query: { storeName: store.storeName || '' }
  });
};

const updateStoreStatus = async (storeId, status) => {
  updatingStoreId.value = storeId;
  errorMessage.value = '';
  try {
    await api.post(`/api/admin/merchant/stores/${storeId}/status`, { status });
    await loadStores();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '更新店铺状态失败';
  } finally {
    updatingStoreId.value = 0;
  }
};

const goHome = () => router.push('/home');
const logout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  localStorage.removeItem('nickname');
  localStorage.removeItem('role');
  router.push('/login');
};

const loadCouponTemplates = async () => {
  try {
    const { data } = await api.get('/api/admin/coupon-templates');
    couponTemplates.value = data || [];
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '加载积分券模板失败';
  }
};

const createCouponTemplate = async () => {
  if (!couponForm.value.name?.trim()) {
    errorMessage.value = '请输入优惠券模板名称';
    return;
  }
  savingCoupon.value = true;
  errorMessage.value = '';
  try {
    await api.post('/api/admin/coupon-templates', {
      name: couponForm.value.name,
      pointsCost: Number(couponForm.value.pointsCost || 0),
      threshold: Number(couponForm.value.threshold || 0),
      discountAmount: Number(couponForm.value.discountAmount || 0),
      description: couponForm.value.description,
      status: 1
    });
    couponForm.value = { name: '', pointsCost: 100, threshold: 100, discountAmount: 10, description: '' };
    await loadCouponTemplates();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '创建积分券模板失败';
  } finally {
    savingCoupon.value = false;
  }
};

const updateCouponStatus = async (id, status) => {
  try {
    await api.put(`/api/admin/coupon-templates/${id}/status`, { status });
    await loadCouponTemplates();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '更新积分券状态失败';
  }
};

const deleteCouponTemplate = async (id) => {
  if (!id || !confirm('确认删除该优惠券模板？删除后无法恢复。')) {
    return;
  }
  try {
    await api.delete(`/api/admin/coupon-templates/${id}`);
    await loadCouponTemplates();
    editingCouponId.value = 0;
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '删除优惠券模板失败';
  }
};

const startEditCoupon = (item) => {
  editingCouponId.value = Number(item?.id || 0);
  couponEditForm.value = {
    name: item?.name || '',
    pointsCost: Number(item?.pointsCost || 100),
    threshold: Number(item?.threshold || 100),
    discountAmount: Number(item?.discountAmount || 10),
    description: item?.description || '',
    status: Number(item?.status || 1)
  };
};

const cancelEditCoupon = () => {
  editingCouponId.value = 0;
};

const saveCouponEdit = async (id) => {
  if (!couponEditForm.value.name?.trim()) {
    errorMessage.value = '请输入优惠券模板名称';
    return;
  }
  savingCoupon.value = true;
  try {
    await api.put(`/api/admin/coupon-templates/${id}`, {
      ...couponEditForm.value,
      pointsCost: Number(couponEditForm.value.pointsCost || 0),
      threshold: Number(couponEditForm.value.threshold || 0),
      discountAmount: Number(couponEditForm.value.discountAmount || 0),
      status: Number(couponEditForm.value.status || 1)
    });
    editingCouponId.value = 0;
    await loadCouponTemplates();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '更新积分券模板失败';
  } finally {
    savingCoupon.value = false;
  }
};

const loadNotices = async () => {
  try {
    const { data } = await api.get('/api/admin/notices');
    notices.value = data || [];
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '加载公告失败';
  }
};

const createNotice = async () => {
  if (!noticeForm.value.title?.trim() || !noticeForm.value.content?.trim()) {
    errorMessage.value = '公告标题和内容不能为空';
    return;
  }
  savingNotice.value = true;
  errorMessage.value = '';
  try {
    await api.post('/api/admin/notices', {
      title: noticeForm.value.title,
      content: noticeForm.value.content,
      sortNo: Number(noticeForm.value.sortNo || 0),
      status: 1
    });
    noticeForm.value = { title: '', content: '', sortNo: 50 };
    await loadNotices();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '创建公告失败';
  } finally {
    savingNotice.value = false;
  }
};

const updateNoticeStatus = async (id, status) => {
  try {
    await api.put(`/api/admin/notices/${id}/status`, { status });
    await loadNotices();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '更新公告状态失败';
  }
};

const deleteNotice = async (id) => {
  if (!id || !confirm('确认删除该公告？删除后无法恢复。')) {
    return;
  }
  try {
    await api.delete(`/api/admin/notices/${id}`);
    await loadNotices();
    editingNoticeId.value = 0;
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '删除公告失败';
  }
};

const startEditNotice = (item) => {
  editingNoticeId.value = Number(item?.id || 0);
  noticeEditForm.value = {
    title: item?.title || '',
    content: item?.content || '',
    sortNo: Number(item?.sortNo || 0),
    status: Number(item?.status || 1)
  };
};

const cancelEditNotice = () => {
  editingNoticeId.value = 0;
};

const saveNoticeEdit = async (id) => {
  if (!noticeEditForm.value.title?.trim() || !noticeEditForm.value.content?.trim()) {
    errorMessage.value = '公告标题和内容不能为空';
    return;
  }
  savingNotice.value = true;
  try {
    await api.put(`/api/admin/notices/${id}`, {
      ...noticeEditForm.value,
      sortNo: Number(noticeEditForm.value.sortNo || 0),
      status: Number(noticeEditForm.value.status || 1)
    });
    editingNoticeId.value = 0;
    await loadNotices();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '更新公告失败';
  } finally {
    savingNotice.value = false;
  }
};

const filteredCouponTemplates = computed(() => {
  const keyword = couponKeyword.value.trim().toLowerCase();
  return couponTemplates.value.filter((item) => {
    const statusOk = couponStatusFilter.value === 'ALL' || String(item?.status) === couponStatusFilter.value;
    if (!statusOk) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return String(item?.name || '').toLowerCase().includes(keyword)
      || String(item?.description || '').toLowerCase().includes(keyword);
  });
});

const couponTotalPages = computed(() => Math.max(1, Math.ceil(filteredCouponTemplates.value.length / pageSize)));
const pagedCouponTemplates = computed(() => {
  const page = Math.min(couponPage.value, couponTotalPages.value);
  const from = (Math.max(1, page) - 1) * pageSize;
  return filteredCouponTemplates.value.slice(from, from + pageSize);
});

const filteredNotices = computed(() => {
  const keyword = noticeKeyword.value.trim().toLowerCase();
  return notices.value.filter((item) => {
    const statusOk = noticeStatusFilter.value === 'ALL' || String(item?.status) === noticeStatusFilter.value;
    if (!statusOk) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return String(item?.title || '').toLowerCase().includes(keyword)
      || String(item?.content || '').toLowerCase().includes(keyword);
  });
});

const noticeTotalPages = computed(() => Math.max(1, Math.ceil(filteredNotices.value.length / pageSize)));
const pagedNotices = computed(() => {
  const page = Math.min(noticePage.value, noticeTotalPages.value);
  const from = (Math.max(1, page) - 1) * pageSize;
  return filteredNotices.value.slice(from, from + pageSize);
});

const resetCouponPage = () => {
  couponPage.value = 1;
};

const resetNoticePage = () => {
  noticePage.value = 1;
};

onMounted(async () => {
  await Promise.all([loadPending(), loadStores(), loadCouponTemplates(), loadNotices()]);
});
</script>

<template>
  <div class="admin-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <div class="brand">
          <div class="brand-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"></path>
            </svg>
          </div>
          <div class="brand-text">
            <strong>管理后台</strong>
            <span>商家入驻审核</span>
          </div>
        </div>
        <div class="nav-actions">
          <button class="btn btn-outline" @click="goHome">返回首页</button>
          <button class="btn btn-ghost" @click="logout">退出登录</button>
        </div>
      </div>
    </header>

    <main class="container main-content">
      <section class="stats-card fade-in">
        <div class="stat-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
          </svg>
        </div>
        <div class="stat-content">
          <strong>{{ items.length }}</strong>
          <span>待审核申请</span>
        </div>
        <div class="stat-indicator" :class="{ 'has-items': items.length > 0 }"></div>
      </section>

      <section v-if="errorMessage" class="card error-panel fade-in">
        <div class="error-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        </div>
        {{ errorMessage }}
      </section>

      <section v-if="loading" class="card loading-state">
        <div class="spinner"></div>
        <p>加载待审核申请中...</p>
      </section>

      <section v-else-if="items.length" class="card review-section fade-in">
        <div class="section-header">
          <div>
            <span class="section-label">审核</span>
            <h2 class="section-title">待审核商家申请</h2>
          </div>
        </div>

        <div class="review-list">
          <article v-for="item in items" :key="item.id" class="review-card">
            <div class="review-header">
              <span class="review-id">#{{ item.id }}</span>
              <span class="badge badge-warning">待审核</span>
            </div>
            <div class="review-body">
              <div class="review-main">
                <h3>{{ item.shopName }}</h3>
                <div class="review-meta">
                  <div class="meta-item">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                      <circle cx="12" cy="7" r="4"></circle>
                    </svg>
                    <span>{{ item.applicantUsername }}（ID: {{ item.applicantUserId }}）</span>
                  </div>
                  <div class="meta-item">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
                      <line x1="3" y1="6" x2="21" y2="6"></line>
                      <path d="M16 10a4 4 0 0 1-8 0"></path>
                    </svg>
                    <span>{{ item.businessScope || '未填写经营范围' }}</span>
                  </div>
                  <div class="meta-item">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path>
                    </svg>
                    <span>{{ item.contactPhone || '未填写联系方式' }}</span>
                  </div>
                </div>
              </div>
              <div class="review-actions">
                <button 
                  class="btn btn-success" 
                  :disabled="reviewingId === item.id" 
                  @click="review(item.id, true)"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"></polyline>
                  </svg>
                  通过
                </button>
                <button 
                  class="btn btn-danger" 
                  :disabled="reviewingId === item.id" 
                  @click="review(item.id, false)"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"></line>
                    <line x1="6" y1="6" x2="18" y2="18"></line>
                  </svg>
                  驳回
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-else class="card empty-state fade-in">
        <div class="empty-icon">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
            <polyline points="22 4 12 14.01 9 11.01"></polyline>
          </svg>
        </div>
        <h3>暂无待审核申请</h3>
        <p>所有商家申请都已处理完毕</p>
      </section>

      <section class="card review-section fade-in">
        <div class="section-header">
          <div>
            <span class="section-label">店铺管理</span>
            <h2 class="section-title">商家店铺封禁与解禁</h2>
          </div>
          <div class="review-actions">
            <input v-model="storeKeyword" class="search-input" placeholder="按店铺名/店主ID模糊搜索" @keyup.enter="loadStores" />
            <button class="btn btn-outline" @click="loadStores">搜索</button>
          </div>
        </div>
        <section v-if="loadingStores" class="loading-state" style="padding: 24px;">
          <div class="spinner"></div>
          <p>加载店铺列表中...</p>
        </section>
        <div v-else-if="stores.length" class="review-list">
          <article v-for="store in stores" :key="store.id" class="review-card">
            <div class="review-header">
              <span class="review-id">#{{ store.id }}</span>
              <span class="badge" :class="store.status === 'DISABLED' ? 'badge-danger' : 'badge-success'">{{ store.status }}</span>
            </div>
            <div class="review-body">
              <div class="review-main">
                <h3>{{ store.storeName }}</h3>
                <div class="review-meta">
                  <div class="meta-item"><span>店主ID：{{ store.ownerUserId }}</span></div>
                  <div class="meta-item"><span>主营：{{ store.mainCategory || '未设置' }}</span></div>
                </div>
              </div>
              <div class="review-actions">
                <button
                  v-if="store.status !== 'DISABLED'"
                  class="btn btn-danger"
                  :disabled="updatingStoreId === store.id"
                  @click="updateStoreStatus(store.id, 'DISABLED')"
                >封禁店铺</button>
                <button
                  v-else
                  class="btn btn-success"
                  :disabled="updatingStoreId === store.id"
                  @click="updateStoreStatus(store.id, 'ACTIVE')"
                >解除封禁</button>
                <button class="btn btn-outline" @click="openStoreProducts(store)">进入店铺</button>
              </div>
            </div>
          </article>
        </div>
        <section v-else class="empty-state" style="padding: 24px;">
          <p>暂无店铺数据</p>
        </section>
      </section>

      <section class="card review-section fade-in">
        <div class="section-header">
          <div>
            <span class="section-label">积分商城</span>
            <h2 class="section-title">优惠券模板上架管理</h2>
          </div>
        </div>
        <div class="review-actions" style="margin-bottom: 12px;">
          <input v-model="couponForm.name" class="search-input" placeholder="券名称，例如 满200减25券" />
          <input v-model.number="couponForm.pointsCost" class="search-input" type="number" min="1" placeholder="兑换积分" />
          <input v-model.number="couponForm.threshold" class="search-input" type="number" min="0" placeholder="使用门槛" />
          <input v-model.number="couponForm.discountAmount" class="search-input" type="number" min="1" placeholder="优惠金额" />
          <button class="btn btn-success" :disabled="savingCoupon" @click="createCouponTemplate">{{ savingCoupon ? '保存中...' : '新增模板' }}</button>
        </div>
        <textarea v-model="couponForm.description" class="search-input" style="width:100%;min-height:70px;margin-bottom:12px;" placeholder="模板说明"></textarea>
        <div class="review-actions" style="margin-bottom: 12px;">
          <input v-model="couponKeyword" class="search-input" placeholder="按名称/说明筛选" @input="resetCouponPage" />
          <select v-model="couponStatusFilter" class="search-input" @change="resetCouponPage">
            <option value="ALL">全部状态</option>
            <option value="1">已上架</option>
            <option value="0">已下架</option>
          </select>
        </div>
        <div v-if="filteredCouponTemplates.length" class="review-list">
          <article v-for="tpl in pagedCouponTemplates" :key="tpl.id" class="review-card">
            <div class="review-header">
              <span class="review-id">#{{ tpl.id }}</span>
              <span class="badge" :class="Number(tpl.status) === 1 ? 'badge-success' : 'badge-danger'">
                {{ Number(tpl.status) === 1 ? '已上架' : '已下架' }}
              </span>
            </div>
            <div class="review-body">
              <div class="review-main">
                <template v-if="editingCouponId === tpl.id">
                  <div class="review-actions" style="margin-bottom: 10px;">
                    <input v-model="couponEditForm.name" class="search-input" placeholder="券名称" />
                    <input v-model.number="couponEditForm.pointsCost" class="search-input" type="number" min="1" placeholder="兑换积分" />
                    <input v-model.number="couponEditForm.threshold" class="search-input" type="number" min="0" placeholder="使用门槛" />
                    <input v-model.number="couponEditForm.discountAmount" class="search-input" type="number" min="1" placeholder="优惠金额" />
                  </div>
                  <textarea v-model="couponEditForm.description" class="search-input" style="width:100%;min-height:64px;" placeholder="模板说明"></textarea>
                </template>
                <template v-else>
                  <h3>{{ tpl.name }}</h3>
                  <div class="review-meta">
                    <div class="meta-item"><span>积分：{{ tpl.pointsCost }}</span></div>
                    <div class="meta-item"><span>门槛：{{ tpl.threshold }}</span></div>
                    <div class="meta-item"><span>优惠：{{ tpl.discountAmount }}</span></div>
                  </div>
                  <div class="meta-item"><span>{{ tpl.description || '无说明' }}</span></div>
                </template>
              </div>
              <div class="review-actions">
                <template v-if="editingCouponId === tpl.id">
                  <button class="btn btn-success" :disabled="savingCoupon" @click="saveCouponEdit(tpl.id)">保存</button>
                  <button class="btn btn-outline" :disabled="savingCoupon" @click="cancelEditCoupon">取消</button>
                </template>
                <template v-else>
                  <button class="btn btn-outline" @click="startEditCoupon(tpl)">编辑</button>
                  <button v-if="Number(tpl.status) !== 1" class="btn btn-success" @click="updateCouponStatus(tpl.id, 1)">上架</button>
                  <button v-else class="btn btn-danger" @click="updateCouponStatus(tpl.id, 0)">下架</button>
                  <button class="btn btn-danger" @click="deleteCouponTemplate(tpl.id)">删除</button>
                </template>
              </div>
            </div>
          </article>
        </div>
        <div v-if="filteredCouponTemplates.length > pageSize" class="review-actions" style="margin-top: 12px; justify-content: flex-end;">
          <button class="btn btn-outline" :disabled="couponPage <= 1" @click="couponPage = couponPage - 1">上一页</button>
          <span class="review-id">第 {{ couponPage }} / {{ couponTotalPages }} 页</span>
          <button class="btn btn-outline" :disabled="couponPage >= couponTotalPages" @click="couponPage = couponPage + 1">下一页</button>
        </div>
      </section>

      <section class="card review-section fade-in">
        <div class="section-header">
          <div>
            <span class="section-label">首页公告</span>
            <h2 class="section-title">公告配置</h2>
          </div>
        </div>
        <div class="review-actions" style="margin-bottom: 12px;">
          <input v-model="noticeForm.title" class="search-input" placeholder="公告标题" />
          <input v-model.number="noticeForm.sortNo" class="search-input" type="number" placeholder="排序值" />
          <button class="btn btn-success" :disabled="savingNotice" @click="createNotice">{{ savingNotice ? '保存中...' : '新增公告' }}</button>
        </div>
        <textarea v-model="noticeForm.content" class="search-input" style="width:100%;min-height:70px;margin-bottom:12px;" placeholder="公告内容"></textarea>
        <div class="review-actions" style="margin-bottom: 12px;">
          <input v-model="noticeKeyword" class="search-input" placeholder="按标题/内容筛选" @input="resetNoticePage" />
          <select v-model="noticeStatusFilter" class="search-input" @change="resetNoticePage">
            <option value="ALL">全部状态</option>
            <option value="1">已发布</option>
            <option value="0">已停用</option>
          </select>
        </div>
        <div v-if="filteredNotices.length" class="review-list">
          <article v-for="notice in pagedNotices" :key="notice.id" class="review-card">
            <div class="review-header">
              <span class="review-id">#{{ notice.id }}</span>
              <span class="badge" :class="Number(notice.status) === 1 ? 'badge-success' : 'badge-danger'">
                {{ Number(notice.status) === 1 ? '已发布' : '已停用' }}
              </span>
            </div>
            <div class="review-body">
              <div class="review-main">
                <template v-if="editingNoticeId === notice.id">
                  <div class="review-actions" style="margin-bottom: 10px;">
                    <input v-model="noticeEditForm.title" class="search-input" placeholder="公告标题" />
                    <input v-model.number="noticeEditForm.sortNo" class="search-input" type="number" placeholder="排序值" />
                  </div>
                  <textarea v-model="noticeEditForm.content" class="search-input" style="width:100%;min-height:64px;" placeholder="公告内容"></textarea>
                </template>
                <template v-else>
                  <h3>{{ notice.title }}</h3>
                  <div class="review-meta">
                    <div class="meta-item"><span>{{ notice.content }}</span></div>
                  </div>
                </template>
              </div>
              <div class="review-actions">
                <template v-if="editingNoticeId === notice.id">
                  <button class="btn btn-success" :disabled="savingNotice" @click="saveNoticeEdit(notice.id)">保存</button>
                  <button class="btn btn-outline" :disabled="savingNotice" @click="cancelEditNotice">取消</button>
                </template>
                <template v-else>
                  <button class="btn btn-outline" @click="startEditNotice(notice)">编辑</button>
                  <button v-if="Number(notice.status) !== 1" class="btn btn-success" @click="updateNoticeStatus(notice.id, 1)">发布</button>
                  <button v-else class="btn btn-danger" @click="updateNoticeStatus(notice.id, 0)">停用</button>
                  <button class="btn btn-danger" @click="deleteNotice(notice.id)">删除</button>
                </template>
              </div>
            </div>
          </article>
        </div>
        <div v-if="filteredNotices.length > pageSize" class="review-actions" style="margin-top: 12px; justify-content: flex-end;">
          <button class="btn btn-outline" :disabled="noticePage <= 1" @click="noticePage = noticePage - 1">上一页</button>
          <span class="review-id">第 {{ noticePage }} / {{ noticeTotalPages }} 页</span>
          <button class="btn btn-outline" :disabled="noticePage >= noticeTotalPages" @click="noticePage = noticePage + 1">下一页</button>
        </div>
      </section>


    </main>
  </div>
</template>

<style scoped>
.admin-page {
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

.stats-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px 32px;
  background: var(--color-surface);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-lg);
  border: 2px solid var(--color-gray-100);
  position: relative;
  overflow: hidden;
}

.stat-icon {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, rgba(251, 191, 36, 0.1) 100%);
  display: grid;
  place-items: center;
  color: var(--color-warning);
  flex-shrink: 0;
}

.stat-content strong {
  display: block;
  font-size: 36px;
  font-family: var(--font-display);
  font-weight: 800;
  color: var(--color-gray-900);
}

.stat-content span {
  font-size: 14px;
  color: var(--color-gray-500);
}

.stat-indicator {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  background: var(--color-gray-200);
}

.stat-indicator.has-items {
  background: var(--color-warning);
}

.error-panel {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  font-weight: 500;
  border-color: var(--color-danger);
  background: linear-gradient(135deg, #FEF2F2 0%, #FEE2E2 100%);
  color: var(--color-danger);
}

.error-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  display: grid;
  place-items: center;
  background: currentColor;
  color: #fff;
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

.review-section {
  padding: 32px;
}

.section-header {
  margin-bottom: 28px;
}

.review-list {
  display: grid;
  gap: 16px;
}

.review-card {
  padding: 24px;
  border-radius: var(--radius-xl);
  border: 2px solid var(--color-gray-100);
  background: var(--color-surface);
  transition: all var(--transition-base);
}

.review-card:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.review-id {
  font-size: 13px;
  font-family: monospace;
  color: var(--color-gray-500);
  background: var(--color-gray-100);
  padding: 4px 10px;
  border-radius: var(--radius-full);
}

.review-body {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
}

.review-main {
  flex: 1;
}

.review-main h3 {
  margin: 0 0 16px;
  font-size: 20px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.review-meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--color-gray-600);
}

.meta-item svg {
  color: var(--color-primary);
  flex-shrink: 0;
}

.review-actions {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.search-input {
  min-width: 220px;
  padding: 10px 12px;
  border: 1px solid var(--color-gray-300);
  border-radius: var(--radius-lg);
}


.empty-state {
  padding: 80px 24px;
  text-align: center;
}

.empty-icon {
  color: var(--color-success);
  margin-bottom: 16px;
}

.empty-state h3 {
  margin: 0 0 8px;
  font-size: 20px;
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-gray-900);
}

.empty-state p {
  margin: 0;
  color: var(--color-gray-500);
  font-size: 15px;
}

@media (max-width: 768px) {
  .review-body {
    flex-direction: column;
  }

  .review-actions {
    width: 100%;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid var(--color-gray-100);
  }

  .review-actions .btn {
    flex: 1;
  }
}
</style>
