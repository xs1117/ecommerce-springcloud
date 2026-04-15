<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchBannerDetail, fetchHomeData, fetchNoticeDetail, fetchPromotionDetail } from '../services/home';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const errorMessage = ref('');
const detail = ref({});

const TYPE_ALIASES = {
  banner: 'banner',
  banners: 'banner',
  notice: 'notice',
  notices: 'notice',
  promotion: 'promotion',
  promotions: 'promotion'
};

const contentType = computed(() => {
  const raw = String(route.params.type || '').trim().toLowerCase();
  return TYPE_ALIASES[raw] || raw;
});
const contentId = computed(() => {
  return String(route.params.id || '').trim();
});
const typeLabel = computed(() => {
  if (contentType.value === 'banner') {
    return '活动详情';
  }
  if (contentType.value === 'notice') {
    return '公告详情';
  }
  if (contentType.value === 'promotion') {
    return '促销详情';
  }
  return '详情';
});

const loadDetail = async () => {
  detail.value = {};
  if (!contentId.value) {
    errorMessage.value = '内容不存在';
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  const detailLoaders = {
    banner: fetchBannerDetail,
    notice: fetchNoticeDetail,
    promotion: fetchPromotionDetail
  };

  try {
    const loader = detailLoaders[contentType.value];
    if (!loader) {
      errorMessage.value = '内容类型不支持';
      return;
    }
    detail.value = await loader(contentId.value);
  } catch (error) {
    // Backward compatible fallback: if detail endpoints are not deployed yet,
    // use /api/home list payload to locate the item.
    try {
      const homeData = await fetchHomeData();
      const sectionMap = {
        banner: homeData?.banners || [],
        notice: homeData?.notices || [],
        promotion: homeData?.promotions || []
      };
      const list = sectionMap[contentType.value] || [];
      const fallback = list.find((item) => String(item?.id || '') === contentId.value);
      if (fallback) {
        detail.value = fallback;
        return;
      }
    } catch {
      // Ignore fallback errors and use primary error message below.
    }
    errorMessage.value = error?.response?.data?.message || '加载详情失败';
  } finally {
    loading.value = false;
  }
};

const goBack = () => router.push('/home');

watch([contentType, contentId], loadDetail, { immediate: true });
</script>

<template>
  <div class="detail-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <h1>{{ typeLabel }}</h1>
        <button class="btn btn-outline" @click="goBack">返回首页</button>
      </div>
    </header>

    <main class="container content-main">
      <section v-if="loading" class="card state-panel">
        <div class="spinner"></div>
        <p>加载中...</p>
      </section>
      <section v-else-if="errorMessage" class="card state-panel">
        <p class="error">{{ errorMessage }}</p>
      </section>
      <section v-else class="card detail-panel">
        <span class="badge badge-primary">{{ typeLabel }}</span>
        <h2>{{ detail.title || '未命名内容' }}</h2>
        <p class="subtitle">{{ detail.subtitle || detail.content }}</p>
        <p class="detail-text">{{ detail.detail || detail.content || '暂无更多说明' }}</p>

        <div v-if="Array.isArray(detail.rules) && detail.rules.length" class="rules">
          <h3>活动规则</h3>
          <ul>
            <li v-for="rule in detail.rules" :key="rule">{{ rule }}</li>
          </ul>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.detail-page { min-height: 100vh; background: var(--gradient-hero); }
.content-main { padding: 24px 0 40px; }
.detail-panel, .state-panel { padding: 20px; }
.subtitle { color: var(--color-gray-500); }
.detail-text { margin-top: 12px; line-height: 1.8; }
.rules { margin-top: 16px; }
.rules h3 { margin-bottom: 10px; }
.rules ul { margin: 0; padding-left: 20px; }
.error { color: #b91c1c; }
</style>



