<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, toFileUrl, uploadImage } from '../services/api';
import { parseImageUrls, serializeImageUrls } from '../services/images';
import { queryMerchantOrderStats, queryMerchantOrders, shipMerchantOrder } from '../services/order';
import { openAfterSaleConversation } from '../services/chat';

const router = useRouter();
const route = useRoute();
const username = localStorage.getItem('nickname') || localStorage.getItem('username') || '商家';
const role = localStorage.getItem('role') || 'USER';

const loading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const activeTab = ref('products');
const merchantStatus = ref('UNKNOWN');

const store = ref({
  id: null,
  title: '',
  category: '',
  city: '',
  slogan: '',
  storeIntro: '',
  storeImageUrl: '',
  rating: 0,
  productCount: 0,
  followers: 0
});

const products = ref([]);
const orders = ref([]);
const stats = ref({
  totalProducts: 0,
  totalOrders: 0,
  totalRevenue: 0,
  pendingOrders: 0
});

const productForm = reactive({
  title: '',
  subtitle: '',
  description: '',
  price: 0,
  stock: 0,
  category: '',
  tag: '',
  imageUrls: []
});

const editingProductId = ref(null);
const showProductModal = ref(false);
const uploadingStoreImage = ref(false);
const uploadingProductImage = ref(false);
const canManageMerchant = computed(() => role === 'USER' && merchantStatus.value === 'APPROVED');
const categoryOptions = [
  '手机数码',
  '电脑办公',
  '家电家居',
  '服饰鞋包',
  '美妆个护',
  '食品生鲜',
  '母婴玩具',
  '运动户外',
  '图书文创',
  '汽车用品',
  '宠物生活',
  '家装建材'
];
const storeCategoryOptions = ['综合店铺', ...categoryOptions];

const formatCurrency = (value) => {
  const num = Number(value) || 0;
  return `¥${num.toFixed(2)}`;
};

const loadMerchantData = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const statusRes = await api.get('/api/merchant/applications/status').catch(() => ({ data: { status: 'UNKNOWN' } }));
    merchantStatus.value = statusRes.data?.status || 'UNKNOWN';
    if (merchantStatus.value !== 'APPROVED') {
      store.value = {
        id: null,
        title: '',
        category: '',
        city: '',
        slogan: '',
        storeIntro: '',
        storeImageUrl: '',
        rating: 0,
        productCount: 0,
        followers: 0
      };
      products.value = [];
      orders.value = [];
      stats.value = {
        totalProducts: 0,
        totalOrders: 0,
        totalRevenue: 0,
        pendingOrders: 0
      };
      return;
    }

    const { data: stores } = await api.get('/api/merchant/stores/me');
    const currentStore = Array.isArray(stores) && stores.length ? stores[0] : null;
    if (currentStore) {
      store.value = {
        id: currentStore.id,
        title: currentStore.storeName || '',
        category: currentStore.mainCategory || '',
        city: currentStore.city || '',
        slogan: currentStore.tags || '',
        storeIntro: currentStore.storeIntro || '',
        storeImageUrl: toFileUrl(currentStore.storeImageUrl || ''),
        rating: currentStore.rating || 0,
        productCount: currentStore.productCount || 0,
        followers: currentStore.followers || 0
      };
      const productsRes = await api.get(`/api/merchant/stores/${currentStore.id}/products`);
      products.value = (productsRes.data || []).map((item) => ({
        ...item,
          imageUrls: parseImageUrls(item.imageUrls || item.imageUrl).map((url) => toFileUrl(url)),
          imageUrl: toFileUrl(parseImageUrls(item.imageUrls || item.imageUrl)[0] || item.imageUrl || '')
      }));
      orders.value = (await queryMerchantOrders({ storeId: currentStore.id, limit: 50 })).map((item) => ({
        ...item,
        productImageUrl: toFileUrl(item.productImageUrl)
      }));
      const orderStats = await queryMerchantOrderStats(currentStore.id);
      stats.value = {
        totalProducts: products.value.length,
        totalOrders: Number(orderStats.totalOrders || 0),
        totalRevenue: Number(orderStats.totalRevenue || 0),
        pendingOrders: Number(orderStats.pendingOrders || 0)
      };
      localStorage.setItem(`ecommerce:order-seen:merchant:${currentStore.id}`, String(Date.now()));
    } else {
      store.value = {
        id: null,
        title: '',
        category: '',
        city: '',
        slogan: '',
        storeIntro: '',
        storeImageUrl: '',
        rating: 0,
        productCount: 0,
        followers: 0
      };
      products.value = [];
      orders.value = [];
      stats.value = {
        totalProducts: 0,
        totalOrders: 0,
        totalRevenue: 0,
        pendingOrders: 0
      };
    }
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '加载数据失败';
  } finally {
    loading.value = false;
  }
};

const shipOrder = async (orderNo) => {
  if (!store.value.id || !orderNo) {
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await shipMerchantOrder({ orderNo, storeId: store.value.id });
    successMessage.value = '发货成功';
    await loadMerchantData();
    activeTab.value = 'orders';
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '发货失败';
  } finally {
    loading.value = false;
  }
};

const merchantOrderStatusText = (value) => {
  const map = {
    TO_SHIP: '待发货',
    TO_RECEIVE: '待收货',
    FINISHED: '已完成',
    AFTER_SALE: '售后中',
    WAIT_PAY: '待支付',
    CLOSED: '已关闭'
  };
  return map[value] || value;
};

const openOrderDetail = (orderNo) => {
  if (!orderNo) {
    return;
  }
  router.push(`/orders/${orderNo}?from=merchant-orders`);
};

const openAfterSaleChat = async (orderNo) => {
  if (!orderNo || !store.value.id) {
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  try {
    const conversation = await openAfterSaleConversation({
      orderNo,
      storeId: store.value.id
    });
    if (conversation?.id) {
      router.push(`/chat?conversationId=${conversation.id}`);
    }
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '打开售后会话失败';
  } finally {
    loading.value = false;
  }
};

const openProductModal = (product = null) => {
  if (product) {
    editingProductId.value = product.id;
    Object.assign(productForm, {
      title: product.title || '',
      subtitle: product.subtitle || '',
      description: product.description || '',
      price: product.price || 0,
      stock: product.stock || 0,
      category: product.category || '',
      tag: product.tag || product.tags || '',
      imageUrls: parseImageUrls(product.imageUrls || product.imageUrl).map((url) => toFileUrl(url))
    });
  } else {
    editingProductId.value = null;
    Object.assign(productForm, {
      title: '',
      subtitle: '',
      description: '',
      price: 0,
      stock: 0,
      category: '',
      tag: '',
      imageUrls: []
    });
  }
  showProductModal.value = true;
};

const closeProductModal = () => {
  showProductModal.value = false;
  editingProductId.value = null;
};

const saveProduct = async () => {
  if (!store.value.id) {
    errorMessage.value = '请先创建店铺';
    activeTab.value = 'store';
    return;
  }
  if (!productForm.title.trim()) {
    errorMessage.value = '商品名称不能为空';
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  const payload = {
    storeId: store.value.id,
    title: productForm.title,
    description: productForm.description || productForm.subtitle,
    imageUrl: serializeImageUrls(productForm.imageUrls),
    category: productForm.category,
    tags: productForm.tag,
    price: Number(productForm.price),
    stock: Number(productForm.stock)
  };
  try {
    if (editingProductId.value) {
      await api.put(`/api/merchant/products/${editingProductId.value}`, {
        title: payload.title,
        description: payload.description,
        imageUrl: payload.imageUrl,
        category: payload.category,
        tags: payload.tags,
        price: payload.price,
        stock: payload.stock
      });
      successMessage.value = '商品更新成功';
    } else {
      await api.post('/api/merchant/products', payload);
      successMessage.value = '商品创建成功';
    }
    closeProductModal();
    await loadMerchantData();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '保存商品失败';
  } finally {
    loading.value = false;
  }
};

const deleteProduct = async (id) => {
  if (!confirm('确定要删除这个商品吗？')) return;
  loading.value = true;
  errorMessage.value = '';
  try {
    await api.delete(`/api/merchant/products/${id}`);
    successMessage.value = '商品删除成功';
    await loadMerchantData();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '删除商品失败';
  } finally {
    loading.value = false;
  }
};

const saveStoreSettings = async () => {
  loading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    if (!store.value.title.trim()) {
      errorMessage.value = '店铺名称不能为空';
      return;
    }
    const payload = {
      storeName: store.value.title,
      storeIntro: store.value.storeIntro,
      storeImageUrl: store.value.storeImageUrl || '',
      mainCategory: store.value.category,
      tags: store.value.slogan
    };
    if (store.value.id) {
      await api.put(`/api/merchant/stores/${store.value.id}`, payload);
      successMessage.value = '店铺设置已保存';
    } else {
      await api.post('/api/merchant/stores', payload);
      successMessage.value = '店铺创建成功';
    }
    await loadMerchantData();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || error?.message || '保存店铺失败';
  } finally {
    loading.value = false;
  }
};

const onStoreImageSelect = async (event) => {
  const file = event.target.files?.[0];
  if (!file) {
    return;
  }
  uploadingStoreImage.value = true;
  errorMessage.value = '';
  try {
    const upload = await uploadImage('/api/merchant/upload/store', file);
    store.value.storeImageUrl = upload.url;
    successMessage.value = '店铺图片上传成功';
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '店铺图片上传失败';
  } finally {
    uploadingStoreImage.value = false;
    event.target.value = '';
  }
};

const onProductImageSelect = async (event) => {
  const files = Array.from(event.target.files || []);
  if (!files.length) {
    return;
  }
  uploadingProductImage.value = true;
  errorMessage.value = '';
  try {
    const uploads = await Promise.all(files.map((file) => uploadImage('/api/merchant/upload/product', file)));
    productForm.imageUrls = [
      ...productForm.imageUrls,
      ...uploads.map((item) => item.url).filter(Boolean)
    ];
    successMessage.value = '商品图片上传成功';
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '商品图片上传失败';
  } finally {
    uploadingProductImage.value = false;
    event.target.value = '';
  }
};

const setProductCover = (index) => {
  if (index <= 0 || index >= productForm.imageUrls.length) {
    return;
  }
  const next = [...productForm.imageUrls];
  const [picked] = next.splice(index, 1);
  next.unshift(picked);
  productForm.imageUrls = next;
};

const removeProductImage = (index) => {
  productForm.imageUrls = productForm.imageUrls.filter((_, current) => current !== index);
};

const goHome = () => router.push('/home');
const goChat = () => router.push('/chat?mode=merchant');
const logout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  localStorage.removeItem('nickname');
  localStorage.removeItem('role');
  router.push('/login');
};

onMounted(async () => {
  if (route.query.tab === 'orders') {
    activeTab.value = 'orders';
  }
  await loadMerchantData();
});
</script>

<template>
  <div class="merchant-page">
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
            <strong>商家中心</strong>
            <span>{{ store.title || '我的店铺' }}</span>
          </div>
        </div>
        <div class="nav-actions">
          <button class="btn btn-outline" @click="goHome">返回首页</button>
          <button class="btn btn-outline" @click="goChat">客服会话</button>
          <button class="btn btn-ghost" @click="logout">退出登录</button>
        </div>
      </div>
    </header>

    <main class="container main-content">
      <section v-if="merchantStatus !== 'APPROVED'" class="card error-panel">
        {{ merchantStatus === 'PENDING' ? '商家申请审核中，暂时不能管理店铺和商品。' : '你还不是已审核商家，请先完成商家入驻申请。' }}
        <button v-if="merchantStatus !== 'PENDING'" class="btn btn-sm btn-outline" @click="router.push('/merchant/apply')">去申请</button>
      </section>

      <template v-if="canManageMerchant">
      <section class="stats-grid fade-in">
        <div class="stat-card stat-card-primary">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
              <line x1="3" y1="6" x2="21" y2="6"></line>
              <path d="M16 10a4 4 0 0 1-8 0"></path>
            </svg>
          </div>
          <div class="stat-content">
            <strong>{{ stats.totalProducts }}</strong>
            <span>商品总数</span>
          </div>
        </div>
        <div class="stat-card stat-card-accent">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
              <line x1="3" y1="6" x2="21" y2="6"></line>
              <path d="M16 10a4 4 0 0 1-8 0"></path>
            </svg>
          </div>
          <div class="stat-content">
            <strong>{{ stats.totalOrders }}</strong>
            <span>订单总数</span>
          </div>
        </div>
        <div class="stat-card stat-card-success">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="1" x2="12" y2="23"></line>
              <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
            </svg>
          </div>
          <div class="stat-content">
            <strong>{{ formatCurrency(stats.totalRevenue) }}</strong>
            <span>总收入</span>
          </div>
        </div>
        <div class="stat-card stat-card-warning">
          <div class="stat-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
          </div>
          <div class="stat-content">
            <strong>{{ stats.pendingOrders }}</strong>
            <span>待处理订单</span>
          </div>
        </div>
      </section>

      <section v-if="errorMessage" class="card error-panel">
        <div class="error-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        </div>
        {{ errorMessage }}
      </section>

      <section v-if="successMessage" class="card success-panel">
        <div class="success-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg>
        </div>
        {{ successMessage }}
      </section>

      <section class="card tab-panel">
        <button :class="{ active: activeTab === 'products' }" @click="activeTab = 'products'">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
            <line x1="3" y1="6" x2="21" y2="6"></line>
            <path d="M16 10a4 4 0 0 1-8 0"></path>
          </svg>
          商品管理
        </button>
        <button :class="{ active: activeTab === 'orders' }" @click="activeTab = 'orders'">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
            <polyline points="14 2 14 8 20 8"></polyline>
            <line x1="16" y1="13" x2="8" y2="13"></line>
            <line x1="16" y1="17" x2="8" y2="17"></line>
            <polyline points="10 9 9 9 8 9"></polyline>
          </svg>
          订单管理
        </button>
        <button :class="{ active: activeTab === 'store' }" @click="activeTab = 'store'">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
          </svg>
          店铺设置
        </button>
      </section>

      <section v-if="activeTab === 'products'" class="card content-panel fade-in">
        <div class="panel-header">
          <div>
            <span class="section-label">商品</span>
            <h2 class="section-title">商品管理</h2>
          </div>
          <button class="btn btn-primary" @click="openProductModal()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            添加商品
          </button>
        </div>

        <div v-if="products.length" class="product-table">
          <div class="table-header">
            <span class="col-name">商品信息</span>
            <span class="col-price">价格</span>
            <span class="col-stock">库存</span>
            <span class="col-sales">销量</span>
            <span class="col-actions">操作</span>
          </div>
          <div v-for="item in products" :key="item.id" class="table-row">
            <div class="col-name">
              <img v-if="item.imageUrl" :src="item.imageUrl" class="product-thumb" alt="product" />
              <div v-else class="product-thumb"></div>
              <div class="product-info">
                <strong>{{ item.title }}</strong>
                <span>{{ item.category }}</span>
              </div>
            </div>
            <div class="col-price">
              <strong>{{ formatCurrency(item.price) }}</strong>
            </div>
            <div class="col-stock">
              <span :class="{ 'low-stock': item.stock < 10 }">{{ item.stock }}</span>
            </div>
            <div class="col-sales">{{ item.salesCount }}</div>
            <div class="col-actions">
              <button class="btn btn-sm btn-outline" @click="openProductModal(item)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="deleteProduct(item.id)">删除</button>
            </div>
          </div>
        </div>
        <p v-else class="empty-text">暂无商品，点击上方按钮添加。</p>
      </section>

      <section v-if="activeTab === 'orders'" class="card content-panel fade-in">
        <div class="panel-header">
          <div>
            <span class="section-label">订单</span>
            <h2 class="section-title">订单管理</h2>
          </div>
        </div>

        <div v-if="orders.length" class="order-list">
          <div v-for="order in orders" :key="order.orderNo" class="order-card" @click="openOrderDetail(order.orderNo)">
            <div class="order-header">
              <span class="order-id">订单号: {{ order.orderNo }}</span>
              <span class="badge" :class="{
                'badge-warning': order.status === 'TO_SHIP',
                'badge-success': order.status === 'FINISHED',
                'badge-danger': order.status === 'CLOSED',
                'badge-info': order.status === 'TO_RECEIVE',
                'badge-default': order.status === 'AFTER_SALE'
              }">{{ merchantOrderStatusText(order.status) }}</span>
            </div>
            <div class="order-body">
              <div class="order-product">
                <div class="order-product-main">
                  <img v-if="order.productImageUrl" :src="order.productImageUrl" class="order-product-image" alt="product" />
                  <div>
                    <strong>{{ Number(order.itemCount || 1) > 1 ? `多件商品（含${order.productTitle || '商品'}）` : (order.productTitle || '商品') }}</strong>
                    <span>共 {{ order.quantity || 0 }} 件（{{ order.itemCount || 1 }} 种）</span>
                  </div>
                </div>
              </div>
              <div class="order-meta">
                <span>买家ID: {{ order.buyerUserId }}</span>
                <span>{{ order.createdAt }}</span>
              </div>
            </div>
            <div class="order-footer">
              <strong class="order-total">{{ formatCurrency(order.payAmount) }}</strong>
              <button v-if="order.status === 'TO_SHIP'" class="btn btn-sm btn-primary" :disabled="loading" @click.stop="shipOrder(order.orderNo)">确认发货</button>
              <button v-if="order.status === 'AFTER_SALE'" class="btn btn-sm btn-outline" :disabled="loading" @click.stop="openAfterSaleChat(order.orderNo)">售后沟通</button>
            </div>
          </div>
        </div>
        <p v-else class="empty-text">暂无订单。</p>
      </section>

      <section v-if="activeTab === 'store'" class="card content-panel fade-in">
        <div class="panel-header">
          <div>
            <span class="section-label">设置</span>
            <h2 class="section-title">店铺设置</h2>
          </div>
        </div>

        <div class="store-settings">
          <div class="setting-item">
            <label>店铺名称</label>
            <input v-model="store.title" class="form-input" placeholder="请输入店铺名称" />
          </div>
          <div class="setting-item">
            <label>店铺分类</label>
            <select v-model="store.category" class="form-input">
              <option value="">请选择店铺分类</option>
              <option v-for="item in storeCategoryOptions" :key="item" :value="item">{{ item }}</option>
            </select>
          </div>
          <div class="setting-item">
            <label>所在城市</label>
            <input v-model="store.city" class="form-input" placeholder="请输入所在城市" />
          </div>
          <div class="setting-item">
            <label>店铺标语</label>
            <input v-model="store.slogan" class="form-input" placeholder="请输入店铺标语" />
          </div>
          <div class="setting-item full-width">
            <label>店铺介绍</label>
            <textarea v-model="store.storeIntro" class="form-textarea" rows="4" placeholder="请输入店铺介绍"></textarea>
          </div>
          <div class="setting-item full-width">
            <label>店铺图片</label>
            <div class="image-upload-row">
              <img v-if="store.storeImageUrl" :src="store.storeImageUrl" class="store-preview" alt="store" />
              <input type="file" accept="image/*" @change="onStoreImageSelect" />
              <span v-if="uploadingStoreImage">上传中...</span>
            </div>
          </div>
          <button class="btn btn-primary btn-lg" :disabled="loading" @click="saveStoreSettings">保存设置</button>
        </div>
      </section>
      </template>
    </main>

    <div v-if="showProductModal" class="modal-overlay" @click.self="closeProductModal">
      <div class="modal-content card">
        <div class="modal-header">
          <h3>{{ editingProductId ? '编辑商品' : '添加商品' }}</h3>
          <button class="modal-close" @click="closeProductModal">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-item">
            <label>商品名称</label>
            <input v-model="productForm.title" class="form-input" placeholder="请输入商品名称" />
          </div>
          <div class="form-item">
            <label>副标题</label>
            <input v-model="productForm.subtitle" class="form-input" placeholder="请输入副标题" />
          </div>
          <div class="form-row">
            <div class="form-item">
              <label>价格</label>
              <input v-model.number="productForm.price" type="number" class="form-input" placeholder="0.00" />
            </div>
            <div class="form-item">
              <label>库存</label>
              <input v-model.number="productForm.stock" type="number" class="form-input" placeholder="0" />
            </div>
          </div>
          <div class="form-item">
            <label>分类</label>
            <select v-model="productForm.category" class="form-input">
              <option value="">请选择商品分类</option>
              <option v-for="item in categoryOptions" :key="item" :value="item">{{ item }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>标签</label>
            <input v-model="productForm.tag" class="form-input" placeholder="如：新品、热销" />
          </div>
          <div class="form-item">
            <label>商品图片</label>
            <div class="image-upload-row">
              <img v-if="productForm.imageUrls.length" :src="productForm.imageUrls[0]" class="store-preview" alt="product" />
              <input type="file" accept="image/*" multiple @change="onProductImageSelect" />
              <span v-if="uploadingProductImage">上传中...</span>
            </div>
            <div v-if="productForm.imageUrls.length" class="image-gallery-strip">
              <div
                v-for="(url, index) in productForm.imageUrls"
                :key="`${url}-${index}`"
                class="image-gallery-item"
                :class="{ active: index === 0 }"
              >
                <img :src="url" class="image-gallery-thumb" alt="product" @click="setProductCover(index)" />
                <div class="image-gallery-actions">
                  <button type="button" class="btn btn-xs btn-outline" @click="setProductCover(index)">首图</button>
                  <button type="button" class="btn btn-xs btn-danger" @click="removeProductImage(index)">删除</button>
                </div>
              </div>
            </div>
          </div>
          <div class="form-item">
            <label>商品描述</label>
            <textarea v-model="productForm.description" class="form-textarea" rows="3" placeholder="请输入商品描述"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="closeProductModal">取消</button>
          <button class="btn btn-primary" @click="saveProduct" :disabled="loading">
            <span v-if="loading" class="spinner"></span>
            {{ loading ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.merchant-page {
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

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  padding: 24px;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  border: 2px solid var(--color-gray-100);
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all var(--transition-base);
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: grid;
  place-items: center;
}

.stat-card-primary .stat-icon {
  background: linear-gradient(135deg, rgba(225, 29, 72, 0.1) 0%, rgba(251, 113, 133, 0.1) 100%);
  color: var(--color-primary);
}

.stat-card-accent .stat-icon {
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.1) 0%, rgba(59, 130, 246, 0.1) 100%);
  color: var(--color-accent);
}

.stat-card-success .stat-icon {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(52, 211, 153, 0.1) 100%);
  color: var(--color-success);
}

.stat-card-warning .stat-icon {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.1) 0%, rgba(251, 191, 36, 0.1) 100%);
  color: var(--color-warning);
}

.stat-content strong {
  display: block;
  font-size: 28px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.stat-content span {
  font-size: 13px;
  color: var(--color-gray-500);
}

.error-panel,
.success-panel {
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

.tab-panel {
  padding: 12px;
  display: flex;
  gap: 8px;
}

.tab-panel button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  border-radius: var(--radius-lg);
  border: 2px solid var(--color-gray-200);
  background: var(--color-surface);
  font-weight: 600;
  font-size: 14px;
  color: var(--color-gray-600);
  transition: all var(--transition-base);
}

.tab-panel button:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.tab-panel button.active {
  background: var(--gradient-primary);
  color: #fff;
  border-color: transparent;
  box-shadow: var(--shadow-md);
}

.content-panel {
  padding: 32px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
  gap: 16px;
}

.product-table {
  border: 2px solid var(--color-gray-100);
  border-radius: var(--radius-xl);
  overflow: hidden;
}

.table-header,
.table-row {
  display: grid;
  grid-template-columns: 1fr 120px 100px 100px 180px;
  gap: 16px;
  align-items: center;
  padding: 16px 20px;
}

.table-header {
  background: var(--color-gray-50);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-gray-500);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.table-row {
  border-top: 1px solid var(--color-gray-100);
  transition: background var(--transition-base);
}

.table-row:hover {
  background: var(--color-rose-50);
}

.col-name {
  display: flex;
  align-items: center;
  gap: 14px;
}

.product-thumb {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-gray-200), var(--color-gray-300));
  flex-shrink: 0;
}

.product-info strong {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-gray-900);
  margin-bottom: 2px;
}

.product-info span {
  font-size: 12px;
  color: var(--color-gray-500);
}

.col-price strong {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-primary);
}

.col-stock span {
  font-weight: 600;
  color: var(--color-gray-700);
}

.low-stock {
  color: var(--color-danger) !important;
}

.col-sales {
  color: var(--color-gray-600);
}

.col-actions {
  display: flex;
  gap: 8px;
}

.order-list {
  display: grid;
  gap: 16px;
}

.order-card {
  padding: 20px;
  border: 2px solid var(--color-gray-100);
  border-radius: var(--radius-xl);
  transition: all var(--transition-base);
  cursor: pointer;
}

.order-card:hover {
  border-color: var(--color-primary);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.badge-warning { background: #fef3c7; color: #92400e; }
.badge-success { background: #dcfce7; color: #166534; }
.badge-danger { background: #fee2e2; color: #991b1b; }
.badge-info { background: #dbeafe; color: #1d4ed8; }
.badge-default { background: #f3f4f6; color: #374151; }

.order-id {
  font-size: 13px;
  color: var(--color-gray-500);
  font-family: monospace;
}

.order-body {
  margin-bottom: 16px;
}

.order-product {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.order-product-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.order-product-image {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid var(--color-gray-200);
}

.order-product strong {
  font-size: 15px;
  color: var(--color-gray-900);
}

.order-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--color-gray-500);
}

.order-footer {
  padding-top: 16px;
  border-top: 1px solid var(--color-gray-100);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.order-total {
  font-size: 18px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-primary);
}

.store-settings {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.setting-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.setting-item.full-width {
  grid-column: span 2;
}

.setting-item label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid var(--color-gray-200);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  font-size: 14px;
  transition: all var(--transition-base);
}

.form-input:focus,
.form-textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 4px rgba(225, 29, 72, 0.1);
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
}

.image-upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.image-gallery-strip {
  margin-top: 12px;
  padding: 12px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96) 0%, rgba(255, 241, 242, 0.96) 100%);
  border: 1px solid rgba(225, 29, 72, 0.12);
  box-shadow: var(--shadow-sm);
  display: flex;
  gap: 12px;
  overflow-x: auto;
}

.image-gallery-item {
  min-width: 120px;
  max-width: 120px;
  display: grid;
  gap: 8px;
  flex-shrink: 0;
}

.image-gallery-item.active .image-gallery-thumb {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(225, 29, 72, 0.35);
}

.image-gallery-thumb {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 12px;
  border: 2px solid rgba(225, 29, 72, 0.12);
  cursor: pointer;
  background: #fff;
  transition: all var(--transition-base);
}

.image-gallery-thumb:hover {
  border-color: var(--color-primary-light);
  transform: translateY(-1px);
}

.image-gallery-actions {
  display: flex;
  gap: 6px;
  justify-content: space-between;
}

.btn-xs {
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1;
  border-radius: 10px;
}

.store-preview {
  width: 88px;
  height: 88px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-gray-200);
  object-fit: cover;
}

.empty-text {
  text-align: center;
  color: var(--color-gray-400);
  padding: 60px 0;
  font-size: 15px;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  display: grid;
  place-items: center;
  z-index: var(--z-modal);
  padding: 24px;
}

.modal-content {
  width: 100%;
  max-width: 720px;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--color-surface);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-2xl);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid var(--color-gray-100);
}

.modal-header h3 {
  margin: 0;
  font-size: 20px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.modal-close {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-lg);
  display: grid;
  place-items: center;
  color: var(--color-gray-500);
  transition: all var(--transition-base);
}

.modal-close:hover {
  background: var(--color-gray-100);
  color: var(--color-gray-700);
}

.modal-body {
  padding: 24px;
  display: grid;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid var(--color-gray-100);
  background: var(--color-gray-50);
}

@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .table-header,
  .table-row {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .table-header {
    display: none;
  }

  .col-name {
    margin-bottom: 8px;
  }

  .col-actions {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--color-gray-100);
  }

  .store-settings {
    grid-template-columns: 1fr;
  }

  .setting-item.full-width {
    grid-column: span 1;
  }
}
</style>
