<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchStoreDetail, formatCurrency } from '../services/home';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const errorMessage = ref('');
const detail = ref({
  store: null,
  products: []
});

const storeId = computed(() => Number(route.params.id));

const loadDetail = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    detail.value = await fetchStoreDetail(storeId.value);
  } catch (error) {
    detail.value = {
      store: null,
      products: []
    };
    errorMessage.value = error?.response?.data?.message || '加载店铺详情失败';
  } finally {
    loading.value = false;
  }
};

const goHome = () => router.push('/home');
const goSearch = () => router.push('/search');
const openProduct = (id) => router.push(`/product/${id}`);
const goChat = () => {
  if (!detail.value.store?.id) {
    return;
  }
  router.push({
    path: '/chat',
    query: {
      storeId: detail.value.store.id,
      source: 'store-detail'
    }
  });
};

const chatAboutProduct = (item) => {
  if (!detail.value.store?.id || !item?.id) {
    return;
  }
  router.push({
    path: '/chat',
    query: {
      storeId: detail.value.store.id,
      productId: item.id,
      source: 'store-detail-product'
    }
  });
};

watch(storeId, loadDetail, { immediate: true });
</script>

<template>
  <div class="store-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <button class="btn btn-outline" @click="goHome">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
            <polyline points="9 22 9 12 15 12 15 22"></polyline>
          </svg>
          返回首页
        </button>
        <button class="btn btn-primary" @click="goSearch">继续搜索</button>
      </div>
    </header>

    <main class="container main-content">
      <section v-if="loading" class="card loading-state">
        <div class="spinner"></div>
        <p>加载店铺详情中...</p>
      </section>
      
      <section v-else-if="errorMessage" class="card error-state">
        <div class="error-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        </div>
        <strong>店铺详情加载失败</strong>
        <p>{{ errorMessage }}</p>
      </section>
      
      <template v-else>
        <section v-if="detail.store" class="store-hero fade-in">
          <div class="store-banner">
            <img v-if="detail.store.storeImageUrl" :src="detail.store.storeImageUrl" class="cover-image" alt="store" />
            <div class="banner-overlay">
              <div class="store-badge">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                </svg>
                {{ detail.store.tag || '品牌店铺' }}
              </div>
            </div>
          </div>
          
          <div class="store-profile">
            <div class="store-avatar">
              <img v-if="detail.store.storeImageUrl" :src="detail.store.storeImageUrl" class="cover-image" alt="store-avatar" />
              <span v-else class="avatar-badge">{{ detail.store.category?.charAt(0) || '店' }}</span>
            </div>
            <div class="store-info">
              <span class="store-category">{{ detail.store.category }}</span>
              <h1>{{ detail.store.title }}</h1>
              <p class="store-slogan">{{ detail.store.slogan || detail.store.storeIntro }}</p>
              <div class="store-stats">
                <div class="stat-item">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                  </svg>
                  <strong>{{ detail.store.rating }}</strong>
                  <span>评分</span>
                </div>
                <div class="stat-item">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
                    <line x1="3" y1="6" x2="21" y2="6"></line>
                    <path d="M16 10a4 4 0 0 1-8 0"></path>
                  </svg>
                  <strong>{{ detail.store.productCount }}</strong>
                  <span>商品</span>
                </div>
                <div class="stat-item">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                    <circle cx="9" cy="7" r="4"></circle>
                    <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                    <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                  </svg>
                  <strong>{{ detail.store.followers }}</strong>
                  <span>关注</span>
                </div>
                <div class="stat-item">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                    <circle cx="12" cy="10" r="3"></circle>
                  </svg>
                  <strong>{{ detail.store.city }}</strong>
                  <span>所在地</span>
                </div>
              </div>
              <div class="store-actions">
                <button class="btn btn-primary" @click="goChat">联系客服</button>
              </div>
            </div>
          </div>
        </section>

        <section class="card intro-card fade-in" v-if="detail.store?.storeIntro">
          <div class="section-header">
            <span class="section-label">简介</span>
            <h2 class="section-title">店铺介绍</h2>
          </div>
          <p class="intro-text">{{ detail.store.storeIntro }}</p>
        </section>

        <section class="card products-card fade-in">
          <div class="section-header">
            <div>
              <span class="section-label">商品</span>
              <h2 class="section-title">店内全部商品</h2>
            </div>
            <span class="product-count">共 {{ detail.products.length }} 款</span>
          </div>
          
          <div v-if="detail.products.length" class="products-grid">
            <article
              v-for="item in detail.products"
              :key="item.id"
              class="product-card hover-lift"
              @click="openProduct(item.id)"
            >
              <div class="product-image">
                <img v-if="item.imageUrl" :src="item.imageUrl" class="cover-image" alt="product" />
                <span class="badge badge-primary">{{ item.tag || '商品' }}</span>
                <div class="product-overlay">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"></circle>
                    <path d="m21 21-4.35-4.35"></path>
                  </svg>
                </div>
              </div>
              <div class="product-body">
                <h3>{{ item.title }}</h3>
                <p>{{ item.subtitle }}</p>
                <div class="product-meta">
                  <strong class="price">{{ formatCurrency(item.price) }}</strong>
                  <span class="sales">销量 {{ item.salesCount }}</span>
                </div>
                <div class="product-actions">
                  <button class="btn btn-sm btn-outline" @click.stop="chatAboutProduct(item)">咨询商品</button>
                </div>
              </div>
            </article>
          </div>
          <p v-else class="empty-text">该店铺暂无商品。</p>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.store-page {
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
  gap: 12px;
}

.main-content {
  padding: 32px 0 48px;
  display: grid;
  gap: 24px;
}

.loading-state,
.error-state {
  padding: 100px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
}

.error-state {
  border-color: var(--color-danger);
  background: linear-gradient(135deg, #FEF2F2 0%, #FEE2E2 100%);
}

.error-icon {
  width: 80px;
  height: 80px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: var(--radius-full);
  display: grid;
  place-items: center;
  color: var(--color-danger);
}

.error-state strong {
  color: var(--color-danger);
  font-size: 20px;
}

.store-hero {
  background: var(--color-surface);
  border-radius: var(--radius-2xl);
  overflow: hidden;
  box-shadow: var(--shadow-xl);
}

.store-banner {
  height: 240px;
  background: linear-gradient(135deg, var(--color-success), #059669, #047857);
  position: relative;
  overflow: hidden;
}

.cover-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.banner-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0.3) 100%);
  display: grid;
  place-items: end start;
  padding: 24px;
}

.store-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: var(--radius-full);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-gray-800);
  box-shadow: var(--shadow-md);
}

.store-badge svg {
  color: var(--color-warning);
}

.store-profile {
  display: flex;
  gap: 32px;
  padding: 32px 40px;
}

.store-avatar {
  width: 140px;
  height: 140px;
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, var(--color-rose-100), var(--color-rose-200));
  display: grid;
  place-items: center;
  flex-shrink: 0;
  margin-top: -70px;
  border: 6px solid var(--color-surface);
  box-shadow: var(--shadow-lg);
  position: relative;
  overflow: hidden;
}

.avatar-badge {
  font-size: 48px;
  font-family: var(--font-display), system-ui, sans-serif;
  font-weight: 800;
  color: var(--color-primary);
}

.store-info {
  flex: 1;
  padding-top: 8px;
}

.store-category {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-success);
  font-weight: 700;
}

.store-info h1 {
  margin: 0 0 12px;
  font-size: 32px;
  font-family: var(--font-display), system-ui, sans-serif;
  font-weight: 800;
  color: var(--color-gray-900);
}

.store-slogan {
  margin: 0 0 24px;
  color: var(--color-gray-600);
  font-size: 16px;
  line-height: 1.6;
}

.store-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 32px;
}

.store-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-item svg {
  color: var(--color-primary);
}

.stat-item strong {
  font-size: 20px;
  font-family: var(--font-display), system-ui, sans-serif;
  font-weight: 700;
  color: var(--color-gray-900);
}

.stat-item span {
  font-size: 13px;
  color: var(--color-gray-500);
}

.intro-card,
.products-card {
  padding: 32px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
  gap: 16px;
}

.product-count {
  font-size: 14px;
  color: var(--color-gray-500);
  font-weight: 500;
}

.intro-text {
  margin: 0;
  color: var(--color-gray-700);
  line-height: 1.9;
  font-size: 15px;
}

.products-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.product-card {
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  overflow: hidden;
  cursor: pointer;
  border: 2px solid var(--color-gray-100);
  transition: all var(--transition-base);
}

.product-card:hover {
  border-color: var(--color-primary);
}

.product-image {
  height: 180px;
  background: linear-gradient(135deg, var(--color-gray-800), var(--color-primary));
  display: grid;
  place-items: end start;
  padding: 16px;
  position: relative;
  overflow: hidden;
}

.product-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: grid;
  place-items: center;
  opacity: 0;
  transition: opacity var(--transition-base);
  color: #fff;
}

.banner-overlay,
.store-badge,
.product-image .badge,
.product-overlay {
  z-index: 1;
}

.product-card:hover .product-overlay {
  opacity: 1;
}

.product-body {
  padding: 20px;
}

.product-body h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-family: var(--font-display), system-ui, sans-serif;
  font-weight: 600;
  color: var(--color-gray-900);
  line-height: 1.4;
}

.product-body p {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--color-gray-500);
}

.product-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--color-gray-100);
}

.product-actions {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.price {
  font-size: 20px;
  font-family: var(--font-display), system-ui, sans-serif;
  font-weight: 700;
  color: var(--color-primary);
}

.sales {
  font-size: 12px;
  color: var(--color-gray-400);
}

.empty-text {
  text-align: center;
  color: var(--color-gray-400);
  padding: 60px 0;
  font-size: 15px;
}

@media (max-width: 1200px) {
  .products-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .products-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .store-profile {
    flex-direction: column;
    align-items: center;
    text-align: center;
    padding: 24px;
  }

  .store-avatar {
    margin-top: -70px;
  }

  .store-stats {
    justify-content: center;
  }
}

@media (max-width: 600px) {
  .products-grid {
    grid-template-columns: 1fr;
  }

  .store-banner {
    height: 180px;
  }

  .store-info h1 {
    font-size: 24px;
  }

  .store-stats {
    gap: 20px;
  }
}
</style>
