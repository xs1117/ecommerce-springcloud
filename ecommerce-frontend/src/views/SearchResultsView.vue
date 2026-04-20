<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  fetchHomeData,
  searchCatalog,
  formatCurrency,
  loadSearchHistory,
  saveSearchHistory,
  SEARCH_SORT_OPTIONS,
  SEARCH_TYPE_OPTIONS
} from '../services/home';

const route = useRoute();
const router = useRouter();

const username = localStorage.getItem('nickname') || localStorage.getItem('username') || '用户';
const searchKeyword = ref('');
const searchType = ref('all');
const searchSort = ref('relevance');
const currentPage = ref(1);
const pageSize = ref(24);
const loading = ref(false);
const errorMessage = ref('');
const speechRecognitionSupported = ref(false);
const speechListening = ref(false);
const speechTip = ref('');
const homeData = ref({
  hotKeywords: [],
  categories: []
});
const results = ref({
  items: [],
  products: [],
  stores: [],
  total: 0,
  productTotal: 0,
  storeTotal: 0
});
const historyList = ref([]);

const activeTypeLabel = computed(() => SEARCH_TYPE_OPTIONS.find((item) => item.value === searchType.value)?.label || '全部');
const selectedTotal = computed(() => Number(results.value.selectedTotal || 0));
const totalPages = computed(() => Math.max(1, Number(results.value.totalPages || 1)));
const pageNumbers = computed(() => {
  const total = totalPages.value;
  const page = currentPage.value;
  const start = Math.max(1, page - 2);
  const end = Math.min(total, start + 4);
  const fixedStart = Math.max(1, end - 4);
  return Array.from({ length: end - fixedStart + 1 }, (_, index) => fixedStart + index);
});
const visibleItems = computed(() => {
  if (searchType.value === 'product') {
    return results.value.products;
  }
  if (searchType.value === 'store') {
    return results.value.stores;
  }
  return results.value.items;
});

const updateRoute = () => {
  router.push({
    path: '/search',
    query: {
      keyword: searchKeyword.value.trim(),
      type: searchType.value,
      sort: searchSort.value,
      page: currentPage.value,
      size: pageSize.value
    }
  });
};

const openItem = (item) => {
  if (item.type === 'store') {
    router.push(`/store/${item.id}`);
    return;
  }
  router.push(`/product/${item.id}`);
};

const goHome = () => router.push('/home');

let searchSpeechRecognition = null;

const getSpeechRecognitionCtor = () => {
  if (typeof window === 'undefined') {
    return null;
  }
  return window.SpeechRecognition || window.webkitSpeechRecognition || null;
};

const mapSpeechErrorMessage = (errorCode) => {
  if (errorCode === 'not-allowed' || errorCode === 'service-not-allowed') {
    return '请先允许麦克风权限';
  }
  if (errorCode === 'audio-capture') {
    return '未检测到可用麦克风';
  }
  if (errorCode === 'network') {
    return '网络异常，语音识别失败';
  }
  return '语音识别失败，请重试';
};

const sanitizeSpeechKeyword = (value) => {
  return String(value || '')
    .replace(/[^\u4e00-\u9fa5a-zA-Z0-9\s]/g, '')
    .replace(/\s+/g, '');
};

const stopSpeechInput = () => {
  if (searchSpeechRecognition && speechListening.value) {
    searchSpeechRecognition.stop();
  }
};

const toggleSpeechInput = () => {
  if (speechListening.value) {
    stopSpeechInput();
    return;
  }
  const SpeechRecognition = getSpeechRecognitionCtor();
  if (!SpeechRecognition) {
    speechRecognitionSupported.value = false;
    speechTip.value = '当前浏览器不支持语音输入';
    return;
  }

  const recognition = new SpeechRecognition();
  recognition.lang = 'zh-CN';
  recognition.interimResults = true;
  recognition.maxAlternatives = 1;
  recognition.continuous = false;

  recognition.onstart = () => {
    speechListening.value = true;
    speechTip.value = '正在聆听，请说出关键词';
  };

  recognition.onresult = (event) => {
    let transcript = '';
    for (let index = event.resultIndex; index < event.results.length; index += 1) {
      transcript += event.results[index]?.[0]?.transcript || '';
    }
    const clean = sanitizeSpeechKeyword(transcript);
    if (clean) {
      searchKeyword.value = clean;
      const latestResult = event.results[event.results.length - 1];
      if (latestResult?.isFinal) {
        speechTip.value = '识别完成，已填入搜索框';
      }
    }
  };

  recognition.onerror = (event) => {
    speechTip.value = mapSpeechErrorMessage(event?.error);
  };

  recognition.onend = () => {
    speechListening.value = false;
    searchSpeechRecognition = null;
  };

  try {
    searchSpeechRecognition = recognition;
    recognition.start();
  } catch {
    speechListening.value = false;
    searchSpeechRecognition = null;
    speechTip.value = '语音识别启动失败，请重试';
  }
};

const loadSearch = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const keyword = String(route.query.keyword || '').trim();
    searchKeyword.value = keyword;
    searchType.value = ['all', 'product', 'store'].includes(String(route.query.type || 'all')) ? String(route.query.type || 'all') : 'all';
    searchSort.value = ['relevance', 'price-asc', 'price-desc', 'sales-desc', 'stock-desc', 'newest'].includes(String(route.query.sort || 'relevance'))
      ? String(route.query.sort || 'relevance')
      : 'relevance';
    currentPage.value = Math.max(Number(route.query.page || 1), 1);
    pageSize.value = Math.min(Math.max(Number(route.query.size || 24), 6), 48);
    historyList.value = loadSearchHistory(username);
    const data = await searchCatalog({
      keyword,
      type: searchType.value,
      sort: searchSort.value,
      page: currentPage.value,
      size: pageSize.value
    });
    results.value = data;
    currentPage.value = Math.max(1, Number(data.page || currentPage.value));
    pageSize.value = Math.max(1, Number(data.size || pageSize.value));
    if (keyword) {
      saveSearchHistory(username, keyword, searchType.value === 'store' ? 'store' : 'product');
      historyList.value = loadSearchHistory(username);
    }
  } catch (error) {
    results.value = {
      items: [],
      products: [],
      stores: [],
      total: 0,
      productTotal: 0,
      storeTotal: 0,
      selectedTotal: 0,
      totalPages: 1
    };
    errorMessage.value = error?.response?.data?.message || '搜索失败，请稍后重试';
  }

  try {
    homeData.value = await fetchHomeData();
  } catch {
    homeData.value = {
      hotKeywords: [],
      categories: []
    };
  } finally {
    loading.value = false;
  }
};

const submitSearch = (keyword = searchKeyword.value, type = searchType.value) => {
  searchKeyword.value = String(keyword || '').trim();
  searchType.value = type;
  currentPage.value = 1;
  updateRoute();
};

const useKeyword = (word) => {
  searchKeyword.value = word;
  searchType.value = 'product';
  currentPage.value = 1;
  updateRoute();
};

const toPage = (page) => {
  const target = Math.max(1, Math.min(page, totalPages.value));
  if (target === currentPage.value) {
    return;
  }
  currentPage.value = target;
  updateRoute();
};

const quickSearchHistory = computed(() => historyList.value.slice(0, 6));

watch(
  () => [route.query.keyword, route.query.type, route.query.sort, route.query.page, route.query.size],
  () => {
    loadSearch();
  },
  { immediate: true }
);

onMounted(() => {
  speechRecognitionSupported.value = Boolean(getSpeechRecognitionCtor());
});

onBeforeUnmount(() => {
  stopSpeechInput();
});
</script>

<template>
  <div class="search-page">
    <header class="search-topbar">
      <div class="container search-topbar-inner">
        <button class="brand-link" @click="goHome">
          <span class="brand-logo">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
              <line x1="3" y1="6" x2="21" y2="6"></line>
              <path d="M16 10a4 4 0 0 1-8 0"></path>
            </svg>
          </span>
          <strong>ShopMall</strong>
        </button>
        <div class="top-actions">
          <button class="btn btn-outline btn-sm" @click="goHome">返回首页</button>
          <span class="user-badge">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
            {{ username }}
          </span>
        </div>
      </div>
    </header>

    <main class="container search-shell">
      <section class="card search-panel fade-in">
        <div class="search-bar">
          <select v-model="searchType" class="search-select">
            <option v-for="option in SEARCH_TYPE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <input v-model="searchKeyword" class="search-input" placeholder="输入关键词搜索商品或店铺" @keyup.enter="submitSearch()" />
          <button
            v-if="speechRecognitionSupported"
            class="btn btn-outline voice-btn"
            :class="{ listening: speechListening }"
            :title="speechListening ? '点击停止语音输入' : '点击语音输入'"
            @click="toggleSpeechInput"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z"></path>
              <path d="M19 10v1a7 7 0 0 1-14 0v-1"></path>
              <line x1="12" y1="18" x2="12" y2="22"></line>
              <line x1="8" y1="22" x2="16" y2="22"></line>
            </svg>
          </button>
          <select v-model="searchSort" class="search-sort">
            <option v-for="option in SEARCH_SORT_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <button class="btn btn-primary search-btn" @click="submitSearch()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <circle cx="11" cy="11" r="8"></circle>
              <path d="m21 21-4.35-4.35"></path>
            </svg>
            搜索
          </button>
        </div>
        <p v-if="speechTip" class="speech-tip">{{ speechTip }}</p>

        <div class="search-hints" v-if="homeData.hotKeywords.length">
          <span class="hint-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
            </svg>
            热搜
          </span>
          <button v-for="word in homeData.hotKeywords" :key="word" class="pill" @click="useKeyword(word)">{{ word }}</button>
        </div>

        <div v-if="quickSearchHistory.length" class="search-hints">
          <span class="hint-label">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
            最近
          </span>
          <button
            v-for="item in quickSearchHistory"
            :key="`${item.type}-${item.keyword}`"
            class="pill pill-light"
            @click="submitSearch(item.keyword, item.type || 'product')"
          >
            {{ item.keyword }}
          </button>
        </div>
      </section>

      <section class="card result-summary slide-in-right">
        <div class="summary-left">
          <span class="summary-label">搜索结果</span>
          <h2 class="summary-title">{{ searchKeyword || '热门推荐' }}</h2>
        </div>
        <div class="summary-meta">
          <span class="meta-item">
            <span class="meta-label">类型</span>
            <strong class="meta-value">{{ activeTypeLabel }}</strong>
          </span>
          <span class="meta-item">
            <span class="meta-label">结果</span>
            <strong class="meta-value meta-highlight">{{ selectedTotal }} 条</strong>
          </span>
          <span class="meta-item">
            <span class="meta-label">页码</span>
            <strong class="meta-value">{{ currentPage }} / {{ totalPages }}</strong>
          </span>
          <span class="meta-divider"></span>
          <span class="meta-stats">
            <span>商品 {{ results.productTotal }}</span>
            <span>店铺 {{ results.storeTotal }}</span>
          </span>
        </div>
      </section>

      <section v-if="errorMessage" class="card error-panel">
        <div class="error-icon">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        </div>
        <strong>搜索请求失败</strong>
        <p>{{ errorMessage }}</p>
      </section>

      <section class="card tab-panel">
        <button :class="{ active: searchType === 'all' }" @click="submitSearch(searchKeyword, 'all')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7"></rect>
            <rect x="14" y="3" width="7" height="7"></rect>
            <rect x="14" y="14" width="7" height="7"></rect>
            <rect x="3" y="14" width="7" height="7"></rect>
          </svg>
          全部
        </button>
        <button :class="{ active: searchType === 'product' }" @click="submitSearch(searchKeyword, 'product')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
            <line x1="3" y1="6" x2="21" y2="6"></line>
            <path d="M16 10a4 4 0 0 1-8 0"></path>
          </svg>
          商品
        </button>
        <button :class="{ active: searchType === 'store' }" @click="submitSearch(searchKeyword, 'store')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
          </svg>
          店铺
        </button>
      </section>

      <section class="card result-list">
        <div v-if="loading" class="loading-box">
          <div class="spinner"></div>
          <span>搜索中...</span>
        </div>
        <template v-else>
          <div v-if="visibleItems.length" :class="searchType === 'store' ? 'store-grid' : 'result-grid'">
            <article v-for="item in visibleItems" :key="`${item.type}-${item.id}`" class="result-card hover-lift" @click="openItem(item)">
              <div class="result-media" :class="item.type === 'store' ? 'store-media' : 'product-media'">
                <img
                  v-if="item.type === 'store' ? item.storeImageUrl : item.imageUrl"
                  :src="item.type === 'store' ? item.storeImageUrl : item.imageUrl"
                  class="result-cover"
                  alt="cover"
                />
                <span class="media-badge">{{ item.tag || (item.type === 'store' ? '店铺' : '商品') }}</span>
                <div class="media-overlay">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"></circle>
                    <path d="m21 21-4.35-4.35"></path>
                  </svg>
                </div>
              </div>
              <div class="result-body">
                <span class="result-type">{{ item.type === 'store' ? '店铺' : '商品' }}</span>
                <h3>{{ item.title }}</h3>
                <p>{{ item.subtitle || item.description }}</p>

                <template v-if="item.type === 'store'">
                  <div class="result-meta">
                    <strong class="rating">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                        <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                      </svg>
                      {{ item.rating }}
                    </strong>
                    <span>{{ item.productCount }} 款商品</span>
                  </div>
                  <div class="result-meta">
                    <span>{{ item.city }}</span>
                    <span>{{ item.followers }} 关注</span>
                  </div>
                </template>
                <template v-else>
                  <div class="result-meta">
                    <strong class="price">{{ formatCurrency(item.price) }}</strong>
                    <span>销量 {{ item.salesCount }}</span>
                  </div>
                  <div class="result-meta">
                    <span>{{ item.storeName }}</span>
                    <span>库存 {{ item.stock }}</span>
                  </div>
                </template>
              </div>
            </article>
          </div>
          <div v-else-if="!errorMessage" class="empty-state">
            <div class="empty-icon">
              <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <circle cx="11" cy="11" r="8"></circle>
                <path d="m21 21-4.35-4.35"></path>
              </svg>
            </div>
            <p>没有找到匹配的结果</p>
            <span>试试换个关键词或点击热搜词</span>
          </div>
        </template>
      </section>

      <section class="card pager-panel" v-if="totalPages > 1">
        <button class="pager-btn pager-nav" :disabled="currentPage <= 1" @click="toPage(currentPage - 1)">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15 18 9 12 15 6"></polyline>
          </svg>
          上一页
        </button>
        <div class="pager-numbers">
          <button
            v-for="page in pageNumbers"
            :key="page"
            class="pager-btn"
            :class="{ active: page === currentPage }"
            @click="toPage(page)"
          >
            {{ page }}
          </button>
        </div>
        <button class="pager-btn pager-nav" :disabled="currentPage >= totalPages" @click="toPage(currentPage + 1)">
          下一页
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 18 15 12 9 6"></polyline>
          </svg>
        </button>
      </section>
    </main>
  </div>
</template>

<style scoped>
.search-page {
  min-height: 100vh;
  background: var(--color-rose-50);
}

.search-topbar {
  background: var(--gradient-glass);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(225, 29, 72, 0.1);
  box-shadow: var(--shadow-sm);
}

.search-topbar-inner {
  min-height: 80px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 12px;
  background: transparent;
  border: none;
}

.brand-logo {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-lg);
  background: var(--gradient-primary);
  color: #fff;
  display: grid;
  place-items: center;
  box-shadow: var(--shadow-md);
}

.brand-link strong {
  font-size: 20px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--color-rose-100);
  border-radius: var(--radius-full);
  color: var(--color-primary);
  font-weight: 600;
  font-size: 14px;
}

.search-shell {
  padding: 24px 0 40px;
  display: grid;
  gap: 16px;
}

.search-panel {
  padding: 24px;
}

.search-bar {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr) 52px 160px 140px;
  gap: 12px;
  align-items: center;
}

.search-select,
.search-sort {
  height: 48px;
  border-radius: var(--radius-lg);
  border: 2px solid var(--color-gray-200);
  background: var(--color-surface);
  font-weight: 600;
}

.search-input {
  height: 48px;
  border-radius: var(--radius-lg);
  border: 2px solid var(--color-gray-200);
  background: var(--color-surface);
  font-size: 15px;
}

.search-btn {
  height: 48px;
}

.voice-btn {
  width: 48px;
  min-width: 48px;
  height: 48px;
  padding: 0;
}

.voice-btn.listening {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-rose-50);
}

.speech-tip {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--color-gray-500);
}

.search-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-top: 16px;
}

.hint-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-gray-500);
  font-weight: 600;
}

.hint-label svg {
  color: var(--color-warning);
}

.pill {
  padding: 8px 14px;
  border-radius: var(--radius-full);
  border: 2px solid var(--color-gray-200);
  background: var(--color-surface);
  font-size: 13px;
  font-weight: 500;
  color: var(--color-gray-700);
  transition: all var(--transition-base);
}

.pill:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-rose-50);
}

.pill-light {
  background: var(--color-gray-100);
  border-color: transparent;
}

.result-summary {
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.summary-label {
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-primary);
  font-weight: 700;
  margin-bottom: 4px;
}

.summary-title {
  margin: 0;
  font-size: 24px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.summary-meta {
  display: flex;
  align-items: center;
  gap: 16px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.meta-label {
  font-size: 11px;
  color: var(--color-gray-500);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.meta-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.meta-highlight {
  color: var(--color-primary);
  font-size: 18px;
}

.meta-divider {
  width: 1px;
  height: 32px;
  background: var(--color-gray-200);
}

.meta-stats {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: var(--color-gray-500);
}

.error-panel {
  padding: 48px;
  text-align: center;
  border-color: var(--color-danger);
  background: linear-gradient(135deg, #FEF2F2 0%, #FEE2E2 100%);
}

.error-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: var(--radius-full);
  display: grid;
  place-items: center;
  color: var(--color-danger);
}

.error-panel strong {
  color: var(--color-danger);
  font-size: 18px;
}

.error-panel p {
  margin: 8px 0 0;
  color: var(--color-gray-600);
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

.pager-panel {
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.pager-numbers {
  display: flex;
  gap: 6px;
}

.pager-btn {
  min-width: 40px;
  height: 40px;
  border-radius: var(--radius-lg);
  border: 2px solid var(--color-gray-200);
  background: var(--color-surface);
  font-weight: 600;
  font-size: 14px;
  color: var(--color-gray-700);
  transition: all var(--transition-base);
}

.pager-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.pager-btn.active {
  background: var(--gradient-primary);
  color: #fff;
  border-color: transparent;
}

.pager-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pager-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
}

.result-grid,
.store-grid {
  display: grid;
  gap: 16px;
}

.result-grid {
  grid-template-columns: repeat(3, 1fr);
}

.store-grid {
  grid-template-columns: repeat(2, 1fr);
}

.result-card {
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  overflow: hidden;
  cursor: pointer;
  border: 2px solid var(--color-gray-100);
  transition: all var(--transition-base);
}

.result-card:hover {
  border-color: var(--color-primary);
}

.result-media {
  height: 180px;
  padding: 16px;
  display: grid;
  place-items: end start;
  position: relative;
  overflow: hidden;
}

.result-cover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-media {
  background: linear-gradient(135deg, var(--color-gray-800), var(--color-primary));
}

.store-media {
  background: linear-gradient(135deg, #F97316, #EA580C);
}

.media-badge {
  z-index: 1;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.media-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: grid;
  place-items: center;
  z-index: 1;
  opacity: 0;
  transition: opacity var(--transition-base);
  color: #fff;
}

.result-card:hover .media-overlay {
  opacity: 1;
}

.result-body {
  padding: 16px;
}

.result-type {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  background: var(--color-rose-100);
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 10px;
}

.result-body h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-gray-900);
  line-height: 1.4;
}

.result-body p {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--color-gray-500);
  line-height: 1.5;
}

.result-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-gray-500);
}

.result-meta + .result-meta {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-gray-100);
}

.price {
  font-size: 18px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-primary);
}

.rating {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--color-warning);
  font-weight: 600;
}

.loading-box {
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--color-gray-500);
}

.empty-state {
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  text-align: center;
}

.empty-icon {
  color: var(--color-gray-300);
}

.empty-state p {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.empty-state span {
  font-size: 14px;
  color: var(--color-gray-500);
}

@media (max-width: 1024px) {
  .result-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .search-bar,
  .result-summary,
  .search-topbar-inner {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .result-grid,
  .store-grid {
    grid-template-columns: 1fr;
  }

  .summary-meta {
    flex-wrap: wrap;
    justify-content: center;
  }

  .pager-panel {
    flex-direction: column;
  }

  .pager-numbers {
    order: -1;
  }
}
</style>
