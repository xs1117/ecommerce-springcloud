<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api, toFileUrl } from '../services/api';
import { resolveCoverImageUrl } from '../services/images';

const route = useRoute();
const router = useRouter();

const storeId = computed(() => Number(route.params.storeId || 0));
const storeName = computed(() => String(route.query.storeName || '').trim() || `店铺 #${storeId.value}`);

const loading = ref(false);
const forcingProductId = ref(0);
const errorMessage = ref('');
const productKeyword = ref('');
const products = ref([]);

const loadProducts = async () => {
  if (!storeId.value) {
    errorMessage.value = '店铺ID无效';
    products.value = [];
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  try {
    const { data } = await api.get(`/api/admin/merchant/stores/${storeId.value}/products`, {
      params: {
        keyword: productKeyword.value.trim() || undefined
      }
    });
    products.value = (data || []).map((item) => ({
      ...item,
      imageUrl: resolveCoverImageUrl(item.imageUrls || item.imageUrl) || toFileUrl(item.imageUrl)
    }));
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '加载店铺商品失败';
    products.value = [];
  } finally {
    loading.value = false;
  }
};

const forceOffShelf = async (productId) => {
  if (!productId) {
    return;
  }
  if (!window.confirm('确认强制下架该商品吗？')) {
    return;
  }
  forcingProductId.value = productId;
  errorMessage.value = '';
  try {
    await api.post(`/api/admin/merchant/stores/${storeId.value}/products/${productId}/off-shelf`);
    await loadProducts();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '强制下架失败';
  } finally {
    forcingProductId.value = 0;
  }
};

const openProductDetail = (productId) => {
  if (!productId) {
    return;
  }
  router.push(`/product/${productId}`);
};

const backToReview = () => router.push('/admin/merchant/review');

onMounted(loadProducts);
</script>

<template>
  <div class="admin-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <div>
          <strong>店铺商品管理</strong>
          <p class="sub-title">{{ storeName }}</p>
        </div>
        <div class="actions">
          <button class="btn btn-outline" @click="backToReview">返回店铺管理</button>
        </div>
      </div>
    </header>

    <main class="container main-content">
      <section class="card panel">
        <div class="panel-head">
          <h2>商品列表</h2>
          <div class="toolbar">
            <input v-model="productKeyword" class="search-input" placeholder="按商品名/ID模糊搜索" @keyup.enter="loadProducts" />
            <button class="btn btn-outline" @click="loadProducts">搜索</button>
          </div>
        </div>

        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

        <div v-if="loading" class="empty">加载中...</div>
        <div v-else-if="products.length" class="list">
          <article v-for="item in products" :key="item.id" class="row">
            <img v-if="item.imageUrl" :src="item.imageUrl" class="thumb" alt="product" />
            <div v-else class="thumb thumb-empty"></div>
            <div class="main">
              <strong class="clickable" @click="openProductDetail(item.id)">{{ item.title }}</strong>
              <p>ID: {{ item.id }} | 价格: ￥{{ Number(item.price || 0).toFixed(2) }} | 库存: {{ item.stock }} | 销量: {{ item.salesCount }}</p>
              <p>状态: {{ item.status }}</p>
            </div>
            <div class="ops">
              <button class="btn btn-outline" @click="openProductDetail(item.id)">查看详情</button>
              <button
                class="btn btn-danger"
                :disabled="item.status === 'OFF_SHELF' || forcingProductId === item.id"
                @click="forceOffShelf(item.id)"
              >{{ forcingProductId === item.id ? '下架中...' : '强制下架' }}</button>
            </div>
          </article>
        </div>
        <div v-else class="empty">该店铺暂无符合条件商品</div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100vh; background: var(--color-rose-50); }
.navbar { background: var(--gradient-glass); border-bottom: 1px solid rgba(225,29,72,.1); }
.navbar-inner { min-height: 72px; display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.sub-title { margin: 4px 0 0; color: var(--color-gray-500); font-size: 13px; }
.main-content { padding: 24px 0 40px; }
.panel { padding: 20px; }
.panel-head { display: flex; justify-content: space-between; gap: 10px; align-items: center; margin-bottom: 12px; }
.toolbar { display: flex; gap: 8px; }
.search-input { min-width: 260px; padding: 10px 12px; border: 1px solid var(--color-gray-300); border-radius: var(--radius-lg); }
.error { color: var(--color-danger); margin: 0 0 10px; }
.list { display: grid; gap: 12px; }
.row { display: grid; grid-template-columns: 92px 1fr auto; gap: 12px; border: 1px solid var(--color-gray-200); border-radius: var(--radius-lg); padding: 12px; background: #fff; }
.thumb { width: 92px; height: 92px; border-radius: 10px; object-fit: cover; border: 1px solid var(--color-gray-200); }
.thumb-empty { background: linear-gradient(135deg, var(--color-gray-200), var(--color-gray-300)); }
.main p { margin: 6px 0 0; color: var(--color-gray-600); font-size: 13px; }
.clickable { cursor: pointer; color: var(--color-primary); }
.ops { display: flex; flex-direction: column; gap: 8px; justify-content: center; }
.empty { padding: 32px; text-align: center; color: var(--color-gray-500); }
@media (max-width: 900px) {
  .panel-head, .toolbar { flex-direction: column; align-items: stretch; }
  .search-input { min-width: 0; }
  .row { grid-template-columns: 1fr; }
  .ops { flex-direction: row; }
}
</style>

