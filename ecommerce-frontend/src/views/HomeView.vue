<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  fetchHomeData,
  fetchRecommendations,
  formatCurrency,
  loadSearchHistory,
  saveSearchHistory,
  SEARCH_TYPE_OPTIONS
} from '../services/home';
import { api, toFileUrl } from '../services/api';
import { fetchChatConversations, formatChatTime } from '../services/chat';
import { addCartItem } from '../services/cart';
import { clearAuthStorage, ensureCurrentUserId, setCurrentUserId } from '../services/auth';
import { queryMerchantOrderStats, queryUserOrderNotificationSummary } from '../services/order';

const router = useRouter();
const username = ref(localStorage.getItem('nickname') || localStorage.getItem('username') || '用户');
const role = ref(localStorage.getItem('role') || 'USER');
const points = ref(localStorage.getItem('points') || '0');
const avatarUrl = ref('');
const merchantStatus = ref('UNKNOWN');

const loadingHome = ref(false);
const loadingRecommend = ref(false);
const errorMessage = ref('');
const recommendationError = ref('');
const chatNotifications = ref([]);
const searchKeyword = ref('');
const searchType = ref('product');
const home = ref({
  hero: {},
  banners: [],
  categories: [],
  hotKeywords: [],
  featuredProducts: [],
  featuredStores: [],
  notices: [],
  promotions: []
});
const recommendations = ref([]);
const searchHistory = ref([]);
const hasUserOrderUpdates = ref(false);
const hasMerchantOrderUpdates = ref(false);
const userLatestOrderUpdatedAt = ref(0);
const merchantLatestOrderUpdatedAt = ref(0);
const merchantStoreId = ref(0);

const quickKeywords = computed(() => home.value.hotKeywords.slice(0, 6));
const displayHistory = computed(() => {
  return [...searchHistory.value]
    .sort((a, b) => Number(b?.ts || 0) - Number(a?.ts || 0))
    .slice(0, 6);
});
const merchantEntryLabel = computed(() => {
  if (role.value === 'ADMIN') {
    return '商家审核';
  }
  return merchantStatus.value === 'APPROVED' ? '商家中心' : '商家入驻';
});

const chatNotificationItems = computed(() => {
  return [...chatNotifications.value]
    .sort((left, right) => {
      const unreadDiff = Number(right.mineUnreadCount || 0) - Number(left.mineUnreadCount || 0);
      if (unreadDiff !== 0) {
        return unreadDiff;
      }
      return new Date(String(right.lastMessageAt || 0)).getTime() - new Date(String(left.lastMessageAt || 0)).getTime();
    })
    .slice(0, 4)
    .map((item) => ({
      ...item,
      unreadCount: Number(item.mineUnreadCount || 0),
      lastMessageAtText: formatChatTime(item.lastMessageAt),
      previewText: item.lastMessageType === 'PRODUCT_CARD'
        ? (item.productTitle || item.lastMessageText || '商品卡片')
        : (item.lastMessageText || '暂无消息')
    }));
});

const chatUnreadTotal = computed(() => chatNotifications.value.reduce((sum, item) => sum + Number(item.mineUnreadCount || 0), 0));
const hasChatUnread = computed(() => chatUnreadTotal.value > 0);
const chatTooltipTitle = computed(() => (hasChatUnread.value ? `你有 ${chatUnreadTotal.value} 条新消息` : '暂无新消息'));
const showMerchantOrderDot = computed(() => role.value !== 'ADMIN' && merchantStatus.value === 'APPROVED' && hasMerchantOrderUpdates.value);

const parseServerDateMs = (value) => {
  if (!value) {
    return 0;
  }
  const parsed = new Date(String(value).replace(' ', 'T'));
  return Number.isNaN(parsed.getTime()) ? 0 : parsed.getTime();
};

const seenKey = (scope, id) => `ecommerce:order-seen:${scope}:${id}`;

const getSeenAt = (scope, id) => {
  const raw = Number(localStorage.getItem(seenKey(scope, id)) || 0);
  return Number.isFinite(raw) && raw > 0 ? raw : 0;
};

const setSeenAt = (scope, id, value) => {
  if (!id) {
    return;
  }
  const safe = Number(value || Date.now());
  localStorage.setItem(seenKey(scope, id), String(safe > 0 ? safe : Date.now()));
};

const loadOrderNotificationSummary = async () => {
  try {
    const userId = await ensureCurrentUserId();
    const userSummary = await queryUserOrderNotificationSummary(userId);
    const userPending = Number(userSummary.pendingCount || 0);
    userLatestOrderUpdatedAt.value = parseServerDateMs(userSummary.latestUpdatedAt);
    hasUserOrderUpdates.value = userPending > 0 && userLatestOrderUpdatedAt.value > getSeenAt('user', userId);

    if (role.value !== 'ADMIN' && merchantStatus.value === 'APPROVED') {
      const { data: stores } = await api.get('/api/merchant/stores/me');
      const storeId = Number(Array.isArray(stores) && stores.length ? stores[0]?.id : 0);
      merchantStoreId.value = storeId;
      if (storeId > 0) {
        const stats = await queryMerchantOrderStats(storeId);
        const merchantPending = Number(stats.pendingOrders || 0);
        merchantLatestOrderUpdatedAt.value = parseServerDateMs(stats.latestOrderUpdatedAt);
        hasMerchantOrderUpdates.value = merchantPending > 0
          && merchantLatestOrderUpdatedAt.value > getSeenAt('merchant', storeId);
      } else {
        hasMerchantOrderUpdates.value = false;
      }
    } else {
      merchantStoreId.value = 0;
      hasMerchantOrderUpdates.value = false;
    }
  } catch {
    userLatestOrderUpdatedAt.value = 0;
    merchantLatestOrderUpdatedAt.value = 0;
    merchantStoreId.value = 0;
    hasUserOrderUpdates.value = false;
    hasMerchantOrderUpdates.value = false;
  }
};

const loadHome = async () => {
  loadingHome.value = true;
  errorMessage.value = '';
  try {
    home.value = await fetchHomeData();
  } catch (error) {
    home.value = {
      hero: {},
      banners: [],
      categories: [],
      hotKeywords: [],
      featuredProducts: [],
      featuredStores: [],
      notices: [],
      promotions: []
    };
    errorMessage.value = error?.response?.data?.message || '首页数据加载失败';
  } finally {
    loadingHome.value = false;
  }
};

const loadRecommendationPanel = async () => {
  loadingRecommend.value = true;
  recommendationError.value = '';
  try {
    searchHistory.value = loadSearchHistory(username.value);
    const data = await fetchRecommendations({ username: username.value, history: searchHistory.value, limit: 8 });
    recommendations.value = data.items || [];
  } catch (error) {
    recommendations.value = [];
    recommendationError.value = error?.response?.data?.message || '推荐内容加载失败';
  } finally {
    loadingRecommend.value = false;
  }
};

const normalizeChatNotification = (item = {}) => ({
  ...item,
  storeImageUrl: toFileUrl(item.storeImageUrl),
  productImageUrl: toFileUrl(item.productImageUrl)
});

const loadChatNotifications = async () => {
  try {
    const data = await fetchChatConversations();
    chatNotifications.value = Array.isArray(data) ? data.map(normalizeChatNotification) : [];
  } catch {
    chatNotifications.value = [];
  }
};

const openChatConversation = (conversation) => {
  if (!conversation?.id) {
    return;
  }
  router.push({
    path: '/chat',
    query: {
      conversationId: conversation.id
    }
  });
};

const openLatestChatConversation = () => {
  const target = chatNotificationItems.value.find((item) => Number(item.unreadCount || 0) > 0)
    || chatNotificationItems.value[0];
  if (target) {
    openChatConversation(target);
    return;
  }
  router.push('/chat');
};

const loadAccountSummary = async () => {
  try {
    const { data } = await api.get('/api/user/account/me');
    username.value = data?.nickname || data?.username || username.value;
    role.value = data?.role || role.value;
    points.value = String(data?.points ?? points.value);
    avatarUrl.value = toFileUrl(data?.avatarUrl);
    localStorage.setItem('nickname', username.value);
    localStorage.setItem('role', role.value);
    localStorage.setItem('points', points.value);
    setCurrentUserId(data || {});
  } catch {
    avatarUrl.value = '';
  }
};

const loadMerchantStatus = async () => {
  if (role.value === 'ADMIN') {
    merchantStatus.value = 'APPROVED';
    return;
  }
  try {
    const { data } = await api.get('/api/merchant/applications/status');
    merchantStatus.value = data?.status || 'NONE';
  } catch {
    merchantStatus.value = 'UNKNOWN';
  }
};

const submitSearch = (keyword = searchKeyword.value, type = searchType.value) => {
  const cleanKeyword = String(keyword || '').trim();
  if (cleanKeyword) {
    saveSearchHistory(username.value, cleanKeyword, type);
    searchHistory.value = loadSearchHistory(username.value);
  }
  router.push({
    path: '/search',
    query: {
      keyword: cleanKeyword,
      type,
      sort: 'relevance'
    }
  });
};

const goProduct = (id) => {
  router.push(`/product/${id}`);
};

const goStore = (id) => {
  router.push(`/store/${id}`);
};

const goHomeContentDetail = (type, id) => {
  if (!id) {
    return;
  }
  router.push(`/home/content/${type}/${id}`);
};

const addRecommendedToCart = async (item) => {
  try {
    await addCartItem({
      productId: item.id,
      storeId: item.storeId,
      storeName: item.storeName,
      title: item.title,
      coverImageUrl: item.imageUrl || item.coverImageUrl || '',
      price: item.price,
      maxQuantity: item.stock,
      quantity: 1,
      selected: true,
      source: 'home-recommendation',
      behaviorDetail: '从首页推荐加入购物车'
    });
  } catch (error) {
    recommendationError.value = error?.response?.data?.message || '加入购物车失败';
  }
};

const goOrders = async () => {
  try {
    const userId = await ensureCurrentUserId();
    setSeenAt('user', userId, userLatestOrderUpdatedAt.value || Date.now());
  } catch {
    // ignore marking read failures and continue navigation
  }
  hasUserOrderUpdates.value = false;
  router.push('/orders');
};
const goPointsMall = () => router.push('/points-mall');

const logout = () => {
  clearAuthStorage();
  router.push('/login');
};

const goMerchantCenter = () => {
  if (role.value === 'ADMIN') {
    router.push('/admin/merchant/review');
    return;
  }
  if (merchantStoreId.value > 0) {
    setSeenAt('merchant', merchantStoreId.value, merchantLatestOrderUpdatedAt.value || Date.now());
  }
  hasMerchantOrderUpdates.value = false;
  router.push(merchantStatus.value === 'APPROVED' ? '/merchant/center' : '/merchant/apply');
};
const goAccountCenter = () => router.push('/account/center');

const useKeyword = (word) => {
  searchKeyword.value = word;
  searchType.value = 'product';
  submitSearch(word, 'product');
};

const searchTypeLabel = (type) => SEARCH_TYPE_OPTIONS.find((item) => item.value === type)?.label || '商品';

const handleAccountSummaryUpdated = async (event) => {
  const nextPoints = event?.detail?.points;
  if (nextPoints != null) {
    points.value = String(nextPoints);
    localStorage.setItem('points', String(nextPoints));
  }
  await loadAccountSummary();
  await loadOrderNotificationSummary();
};

onMounted(async () => {
  await loadAccountSummary();
  await loadMerchantStatus();
  await loadOrderNotificationSummary();
  await loadHome();
  await loadRecommendationPanel();
  await loadChatNotifications();
  window.addEventListener('account-summary-updated', handleAccountSummaryUpdated);
  chatNotificationTimer = window.setInterval(loadChatNotifications, 15000);
  orderNotificationTimer = window.setInterval(loadOrderNotificationSummary, 20000);
});

let chatNotificationTimer = null;
let orderNotificationTimer = null;

onBeforeUnmount(() => {
  if (chatNotificationTimer) {
    window.clearInterval(chatNotificationTimer);
    chatNotificationTimer = null;
  }
  if (orderNotificationTimer) {
    window.clearInterval(orderNotificationTimer);
    orderNotificationTimer = null;
  }
  window.removeEventListener('account-summary-updated', handleAccountSummaryUpdated);
});
</script>

<template>
  <div class="home-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <div class="brand" @click="router.push('/home')">
          <div class="brand-icon">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
              <line x1="3" y1="6" x2="21" y2="6"></line>
              <path d="M16 10a4 4 0 0 1-8 0"></path>
            </svg>
          </div>
          <div class="brand-text">
            <strong>ShopMall</strong>
            <span>发现好物</span>
          </div>
        </div>
        
        <nav class="nav-links">
          <button class="nav-link nav-link-with-dot" @click="goMerchantCenter">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
              <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
            </svg>
            {{ merchantEntryLabel }}
            <span v-if="showMerchantOrderDot" class="nav-red-dot"></span>
          </button>
          <button v-if="role !== 'ADMIN'" class="nav-link" @click="goPointsMall">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="9"></circle>
              <path d="M8 12h8"></path>
              <path d="M12 8v8"></path>
            </svg>
            积分商城
          </button>
          <button class="nav-link" @click="goAccountCenter">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
            个人中心
          </button>
          <button class="nav-link nav-link-with-dot" @click="goOrders">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 11l3 3L22 4"></path>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path>
            </svg>
            我的订单
            <span v-if="hasUserOrderUpdates" class="nav-red-dot"></span>
          </button>
          <button v-if="role === 'ADMIN'" class="nav-link" @click="router.push('/chat?mode=admin')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a4 4 0 0 1-4 4H7l-4 4V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z"></path>
            </svg>
            售后会话
          </button>
        </nav>

        <div class="user-info">
          <div class="chat-notification-anchor">
            <button class="user-avatar-button" :title="chatTooltipTitle" @click="openLatestChatConversation">
              <img v-if="avatarUrl" :src="avatarUrl" class="user-avatar user-avatar-img" alt="avatar" />
              <span v-else class="user-avatar">{{ username.charAt(0).toUpperCase() }}</span>
              <span v-if="hasChatUnread" class="chat-notification-dot"></span>
            </button>
            <div class="chat-notification-popover" role="dialog" :aria-label="chatTooltipTitle">
              <div class="chat-notification-header">
                <strong>{{ chatTooltipTitle }}</strong>
                <span>点击可进入会话</span>
              </div>
              <div v-if="chatNotificationItems.length" class="chat-notification-list">
                <button
                  v-for="item in chatNotificationItems"
                  :key="item.id"
                  class="chat-notification-item"
                  @click.stop="openChatConversation(item)"
                >
                  <img v-if="item.storeImageUrl" :src="item.storeImageUrl" alt="store" />
                  <span v-else class="chat-notification-item-avatar">{{ (item.counterpartName || '会话').charAt(0) }}</span>
                  <span class="chat-notification-item-body">
                    <span class="chat-notification-item-top">
                      <strong>{{ item.counterpartName || item.storeTitle || '会话' }}</strong>
                      <span>{{ item.lastMessageAtText }}</span>
                    </span>
                    <span class="chat-notification-item-preview">{{ item.previewText }}</span>
                  </span>
                  <span v-if="item.unreadCount" class="chat-notification-item-badge">{{ item.unreadCount }}</span>
                </button>
              </div>
              <p v-else class="chat-notification-empty">暂无新消息</p>
            </div>
          </div>
          <div class="user-details">
            <span class="user-name">{{ username }}</span>
            <div class="user-meta">
              <span class="badge badge-primary">{{ role === 'ADMIN' ? '管理员' : '会员' }}</span>
              <span class="user-points">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                </svg>
                {{ points }} 积分
              </span>
            </div>
          </div>
          <button class="btn-logout" @click="logout">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
          </button>
        </div>
      </div>
    </header>

    <main class="main-content">
      <section class="hero-section">
        <div class="hero-bg">
          <div class="hero-shape hero-shape-1"></div>
          <div class="hero-shape hero-shape-2"></div>
          <div class="hero-shape hero-shape-3"></div>
        </div>
        <div class="container hero-container">
          <div class="hero-content fade-in-up">
            <span class="hero-badge">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
              </svg>
              欢迎来到 ShopMall
            </span>
            <h1 class="hero-title">
              {{ home.hero.subtitle || '发现优质好物' }}
              <span class="gradient-text">享受购物乐趣</span>
            </h1>
            <p class="hero-desc">{{ home.hero.searchHint || '搜索商品或店铺，快速找到您想要的' }}</p>

            <div class="search-box">
              <div class="search-type-select">
                <select v-model="searchType">
                  <option v-for="option in SEARCH_TYPE_OPTIONS" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </div>
              <input
                v-model="searchKeyword"
                class="search-input"
                placeholder="搜索商品、店铺或品牌..."
                @keyup.enter="submitSearch()"
              />
              <button class="btn btn-primary btn-lg search-btn" @click="submitSearch()">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <circle cx="11" cy="11" r="8"></circle>
                  <path d="m21 21-4.35-4.35"></path>
                </svg>
                搜索
              </button>
            </div>

            <div class="keyword-tags" v-if="quickKeywords.length">
              <span class="tag-label">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"></path>
                </svg>
                热搜
              </span>
              <button v-for="word in quickKeywords" :key="word" class="tag" @click="useKeyword(word)">{{ word }}</button>
            </div>

            <div class="keyword-tags history-tags" v-if="displayHistory.length">
              <span class="tag-label">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"></circle>
                  <polyline points="12 6 12 12 16 14"></polyline>
                </svg>
                最近
              </span>
              <button
                v-for="item in displayHistory"
                :key="`${item.type}-${item.keyword}`"
                class="tag tag-ghost"
                @click="submitSearch(item.keyword, item.type || 'product')"
              >
                {{ searchTypeLabel(item.type) }} · {{ item.keyword }}
              </button>
            </div>
          </div>

          <div class="hero-categories slide-in-right card">
            <div class="section-header hero-categories-header">
              <div>
                <span class="section-label">分类导航</span>
                <h2 class="section-title">快速进入热门类目</h2>
              </div>
            </div>
            <div class="category-scroll">
              <div class="category-grid hero-category-grid">
                <button
                  v-for="(item, index) in home.categories"
                  :key="item.name"
                  class="category-item"
                  :class="`category-${index % 6}`"
                  @click="useKeyword(item.name)"
                >
                  <span class="category-icon">{{ item.icon }}</span>
                  <span class="category-name">{{ item.name }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="container content-container">
        <section v-if="errorMessage" class="card error-card">
          <div class="error-icon">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="12"></line>
              <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
          </div>
          <strong>加载失败</strong>
          <p>{{ errorMessage }}</p>
        </section>

        <section class="banners-grid" v-if="home.banners.length">
          <article v-for="(item, index) in home.banners" :key="item.id" class="banner-card" :class="`banner-${index % 3}`">
            <div class="banner-content">
              <span class="banner-badge">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                </svg>
                活动
              </span>
              <h3>{{ item.title }}</h3>
              <p>{{ item.subtitle }}</p>
            </div>
            <button class="btn btn-primary" @click="goHomeContentDetail('banner', item.id)">立即查看</button>
          </article>
        </section>

        <section class="card section-card" v-if="home.promotions.length">
          <div class="section-header">
            <div>
              <span class="section-label">限时促销</span>
              <h2 class="section-title">平台精选优惠活动</h2>
            </div>
            <span class="section-hint">点击查看活动详情</span>
          </div>
          <div class="promotion-grid">
            <article
              v-for="(item, index) in home.promotions"
              :key="item.id || `${item.title}-${index}`"
              class="promotion-item"
              :class="`promotion-${index % 3}`"
              @click="goHomeContentDetail('promotion', item.id)"
            >
              <span class="badge badge-warning">促销</span>
              <strong>{{ item.title }}</strong>
              <p>{{ item.subtitle || item.content || '点击查看活动说明' }}</p>
            </article>
          </div>
        </section>

        <section class="card section-card">
          <div class="section-header">
            <div>
              <span class="section-label">猜你喜欢</span>
              <h2 class="section-title">{{ loadingRecommend ? '正在加载推荐...' : '基于您的喜好推荐' }}</h2>
            </div>
            <span class="section-hint">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"></path>
              </svg>
              个性化推荐
            </span>
          </div>
          <p v-if="recommendationError" class="error-text">{{ recommendationError }}</p>
          <div v-if="recommendations.length" class="product-grid">
            <article v-for="item in recommendations" :key="item.id" class="product-card hover-lift" @click="goProduct(item.id)">
              <div class="product-image">
                <img v-if="item.imageUrl" :src="item.imageUrl" class="cover-image" alt="product" />
                <span class="badge badge-primary">{{ item.category }}</span>
                <div class="product-overlay">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"></circle>
                    <path d="m21 21-4.35-4.35"></path>
                  </svg>
                </div>
              </div>
              <div class="product-body">
                <span class="badge badge-warning">{{ item.tag }}</span>
                <h3>{{ item.title }}</h3>
                <p>{{ item.subtitle || item.description }}</p>
                <div class="product-meta">
                  <strong class="price">{{ formatCurrency(item.price) }}</strong>
                  <span class="sales">销量 {{ item.salesCount }}</span>
                </div>
                <button class="btn btn-success btn-sm btn-block" @click.stop="addRecommendedToCart(item)">加入购物车</button>
              </div>
            </article>
          </div>
          <p v-else class="empty-text">{{ loadingRecommend ? '推荐加载中...' : '暂时没有推荐内容' }}</p>
        </section>

        <section class="card section-card">
          <div class="section-header">
            <div>
              <span class="section-label">精选店铺</span>
              <h2 class="section-title">浏览店铺主页与店内商品</h2>
            </div>
            <span class="section-hint">点击店铺进入详情页</span>
          </div>
          <div class="store-grid">
            <article v-for="item in home.featuredStores" :key="item.id" class="store-card hover-lift" @click="goStore(item.id)">
              <div class="store-image">
                <img v-if="item.storeImageUrl" :src="item.storeImageUrl" class="cover-image" alt="store" />
                <span class="badge badge-success">{{ item.tag }}</span>
                <div class="store-overlay">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"></circle>
                    <path d="m21 21-4.35-4.35"></path>
                  </svg>
                </div>
              </div>
              <div class="store-body">
                <h3>{{ item.title }}</h3>
                <p>{{ item.storeIntro }}</p>
                <div class="store-meta">
                  <strong class="rating">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                    </svg>
                    {{ item.rating }}
                  </strong>
                  <span>{{ item.productCount }} 款商品</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section class="card section-card">
          <div class="section-header">
            <div>
              <span class="section-label">商城公告</span>
              <h2 class="section-title">优惠、售后与新人福利</h2>
            </div>
            <span class="section-hint">及时关注最新消息</span>
          </div>
          <div class="notice-grid">
            <article
              v-for="(item, index) in home.notices"
              :key="item.id || `${item.title}-${index}`"
              class="notice-item"
              :class="`notice-${index % 3}`"
              @click="goHomeContentDetail('notice', item.id)"
            >
              <div class="notice-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                  <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                </svg>
              </div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.content }}</p>
            </article>
          </div>
        </section>
      </div>
    </main>

    <footer class="footer">
      <div class="container footer-inner">
        <div class="footer-brand">
          <div class="brand-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
              <line x1="3" y1="6" x2="21" y2="6"></line>
              <path d="M16 10a4 4 0 0 1-8 0"></path>
            </svg>
          </div>
          <span>ShopMall</span>
        </div>
        <p>© 2026 ShopMall. 发现好物，享受购物乐趣。</p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--color-rose-50);
  display: flex;
  flex-direction: column;
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
  min-height: 80px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  cursor: pointer;
  transition: transform var(--transition-base);
}

.brand:hover {
  transform: scale(1.02);
}

.brand-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-lg);
  background: var(--gradient-primary);
  color: #fff;
  display: grid;
  place-items: center;
  box-shadow: var(--shadow-md), 0 4px 16px rgba(225, 29, 72, 0.3);
}

.brand-text strong {
  display: block;
  font-size: 22px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.brand-text span {
  font-size: 13px;
  color: var(--color-gray-500);
  font-weight: 500;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: var(--radius-lg);
  color: var(--color-gray-700);
  font-weight: 600;
  font-size: 14px;
  transition: all var(--transition-base);
}

.nav-link:hover {
  background: var(--color-rose-100);
  color: var(--color-primary);
}

.nav-link-with-dot {
  position: relative;
}

.nav-red-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 14px;
}

.chat-notification-anchor {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.user-avatar-button {
  position: relative;
  padding: 0;
  border: 0;
  background: transparent;
  border-radius: var(--radius-full);
  cursor: pointer;
}

.user-avatar-button:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 3px;
}

.user-avatar {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-full);
  background: var(--gradient-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 18px;
  font-family: var(--font-display);
  box-shadow: var(--shadow-sm);
}

.user-avatar-img {
  object-fit: cover;
  display: block;
  background: transparent;
}

.chat-notification-dot {
  position: absolute;
  top: 0;
  right: 0;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #ef4444;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.18);
}

.chat-notification-popover {
  position: absolute;
  top: calc(100% + 12px);
  right: 0;
  width: 340px;
  background: #fff;
  border-radius: var(--radius-xl);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: var(--shadow-xl);
  padding: 14px;
  opacity: 0;
  visibility: hidden;
  transform: translateY(8px);
  transition: opacity var(--transition-base), transform var(--transition-base), visibility var(--transition-base);
  z-index: 60;
}

.chat-notification-anchor:hover .chat-notification-popover,
.chat-notification-anchor:focus-within .chat-notification-popover {
  opacity: 1;
  visibility: visible;
  transform: translateY(0);
}

.chat-notification-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-gray-100);
}

.chat-notification-header strong {
  color: var(--color-gray-900);
  font-size: 14px;
}

.chat-notification-header span {
  color: var(--color-gray-500);
  font-size: 12px;
}

.chat-notification-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-top: 12px;
  max-height: 320px;
  overflow: auto;
}

.chat-notification-item {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border-radius: var(--radius-lg);
  background: var(--color-rose-50);
  text-align: left;
}

.chat-notification-item:hover {
  background: var(--color-rose-100);
}

.chat-notification-item img,
.chat-notification-item-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  display: grid;
  place-items: center;
  background: var(--gradient-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.chat-notification-item-body {
  min-width: 0;
}

.chat-notification-item-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.chat-notification-item-top strong,
.chat-notification-item-top span,
.chat-notification-item-body p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-notification-item-top strong {
  color: var(--color-gray-900);
  font-size: 13px;
}

.chat-notification-item-top span {
  color: var(--color-gray-500);
  font-size: 11px;
}

.chat-notification-item-preview {
  display: block;
  margin: 4px 0 0;
  color: var(--color-gray-600);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-notification-item-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.chat-notification-empty {
  margin: 0;
  padding: 14px 2px 4px;
  color: var(--color-gray-500);
  font-size: 13px;
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-name {
  font-weight: 600;
  color: var(--color-gray-900);
  font-size: 15px;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-points {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-gray-600);
}

.user-points svg {
  color: var(--color-warning);
}

.btn-logout {
  padding: 10px;
  border-radius: var(--radius-lg);
  color: var(--color-gray-500);
  transition: all var(--transition-base);
}

.btn-logout:hover {
  background: var(--color-rose-100);
  color: var(--color-primary);
}

.main-content {
  flex: 1;
}

.hero-section {
  position: relative;
  padding: 80px 0 60px;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.hero-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.6;
}

.hero-shape-1 {
  width: 500px;
  height: 500px;
  background: var(--color-rose-200);
  top: -200px;
  right: -100px;
  animation: float 8s ease-in-out infinite;
}

.hero-shape-2 {
  width: 400px;
  height: 400px;
  background: var(--color-rose-300);
  bottom: -150px;
  left: -100px;
  animation: float 10s ease-in-out infinite reverse;
}

.hero-shape-3 {
  width: 300px;
  height: 300px;
  background: rgba(37, 99, 235, 0.2);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: pulse 6s ease-in-out infinite;
}

.hero-container {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 420px;
  gap: 48px;
  align-items: center;
}

.hero-content {
  max-width: 680px;
}

.hero-categories {
  padding: 24px;
}

.hero-categories-header {
  margin-bottom: 16px;
}

.category-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 10px;
  scrollbar-gutter: stable;
}

.category-scroll::-webkit-scrollbar {
  height: 8px;
}

.category-scroll::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.55);
  border-radius: 999px;
}

.category-scroll::-webkit-scrollbar-track {
  background: rgba(226, 232, 240, 0.65);
  border-radius: 999px;
}

.hero-category-grid {
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 12px;
  min-width: 760px;
  width: max-content;
}

.hero-category-grid .category-item {
  padding: 16px 10px;
}

.hero-category-grid .category-icon {
  font-size: 30px;
  margin-bottom: 8px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--color-rose-100);
  color: var(--color-primary);
  border-radius: var(--radius-full);
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 20px;
}

.hero-badge svg {
  color: var(--color-warning);
}

.hero-title {
  margin: 0 0 20px;
  font-size: 48px;
  font-family: var(--font-display);
  font-weight: 800;
  color: var(--color-gray-900);
  line-height: 1.15;
}

.hero-desc {
  margin: 0 0 32px;
  color: var(--color-gray-600);
  font-size: 18px;
  line-height: 1.7;
}

.search-box {
  display: grid;
  grid-template-columns: 140px 1fr 160px;
  gap: 12px;
  align-items: center;
  margin-bottom: 24px;
  background: var(--color-surface);
  padding: 8px;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  border: 2px solid rgba(225, 29, 72, 0.1);
}

.search-type-select select {
  height: 48px;
  border: none;
  background: var(--color-gray-100);
  border-radius: var(--radius-lg);
  font-weight: 600;
}

.search-input {
  height: 48px;
  border: none;
  background: transparent;
  font-size: 16px;
  padding: 0 16px;
}

.search-input:focus {
  box-shadow: none;
}

.search-btn {
  height: 48px;
}

.keyword-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.tag-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--color-gray-600);
  font-weight: 600;
}

.tag-label svg {
  color: var(--color-primary);
}

.tag {
  padding: 8px 16px;
  border-radius: var(--radius-full);
  background: var(--color-surface);
  border: 2px solid var(--color-gray-200);
  color: var(--color-gray-700);
  font-size: 14px;
  font-weight: 500;
  transition: all var(--transition-base);
}

.tag:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--color-rose-50);
  transform: translateY(-2px);
}

.tag-ghost {
  background: var(--color-gray-100);
  border-color: transparent;
}

.history-tags {
  margin-top: 12px;
}

.history-tags .tag {
  padding: 6px 12px;
  font-size: 12px;
}

.hero-stats {
  display: grid;
  gap: 16px;
}

.stats-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  border: 1px solid rgba(225, 29, 72, 0.08);
  transition: all var(--transition-base);
}

.stats-card:hover {
  transform: translateX(8px);
  box-shadow: var(--shadow-lg);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-lg);
  display: grid;
  place-items: center;
}

.stat-icon-products {
  background: linear-gradient(135deg, rgba(225, 29, 72, 0.1) 0%, rgba(251, 113, 133, 0.1) 100%);
  color: var(--color-primary);
}

.stat-icon-stores {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(52, 211, 153, 0.1) 100%);
  color: var(--color-success);
}

.stat-icon-promos {
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
  font-size: 14px;
  color: var(--color-gray-500);
}

.hero-cta {
  margin-top: 8px;
}

.content-container {
  padding: 40px 0 60px;
  display: grid;
  gap: 32px;
}

.error-card {
  padding: 40px;
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

.error-card strong {
  color: var(--color-danger);
  font-size: 18px;
}

.banners-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.banner-card {
  padding: 28px;
  min-height: 180px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border-radius: var(--radius-xl);
  position: relative;
  overflow: hidden;
}

.banner-0 {
  background: linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%);
  border: 2px solid #F59E0B;
}

.banner-1 {
  background: linear-gradient(135deg, #DBEAFE 0%, #BFDBFE 100%);
  border: 2px solid #3B82F6;
}

.banner-2 {
  background: linear-gradient(135deg, #FCE7F3 0%, #FBCFE8 100%);
  border: 2px solid #EC4899;
}

.banner-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: var(--radius-full);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-gray-700);
  margin-bottom: 12px;
}

.banner-badge svg {
  color: var(--color-warning);
}

.banner-content h3 {
  margin: 0 0 8px;
  font-size: 22px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.banner-content p {
  margin: 0;
  color: var(--color-gray-700);
  font-size: 14px;
}

.section-card {
  padding: 32px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 28px;
  gap: 16px;
}

.section-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--color-gray-500);
}

.section-hint svg {
  color: var(--color-primary);
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

.category-grid.hero-category-grid {
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  max-width: 760px;
  margin: 0 auto;
  justify-content: center;
}

.category-item {
  padding: 24px 16px;
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  border: 2px solid var(--color-gray-100);
  text-align: center;
  transition: all var(--transition-base);
}

.category-item:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-lg);
  transform: translateY(-4px);
}

.category-0:hover { border-color: #E11D48; background: linear-gradient(135deg, #FFF1F2 0%, #FFFFFF 100%); }
.category-1:hover { border-color: #2563EB; background: linear-gradient(135deg, #EFF6FF 0%, #FFFFFF 100%); }
.category-2:hover { border-color: #10B981; background: linear-gradient(135deg, #ECFDF5 0%, #FFFFFF 100%); }
.category-3:hover { border-color: #F59E0B; background: linear-gradient(135deg, #FFFBEB 0%, #FFFFFF 100%); }
.category-4:hover { border-color: #8B5CF6; background: linear-gradient(135deg, #F5F3FF 0%, #FFFFFF 100%); }
.category-5:hover { border-color: #06B6D4; background: linear-gradient(135deg, #ECFEFF 0%, #FFFFFF 100%); }

.category-icon {
  display: block;
  font-size: 36px;
  margin-bottom: 12px;
}

.category-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-gray-700);
}

.product-grid,
.store-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.product-card,
.store-card {
  cursor: pointer;
  overflow: hidden;
  border-radius: var(--radius-xl);
}

.product-image,
.store-image {
  height: 200px;
  background: linear-gradient(135deg, var(--color-gray-800), var(--color-primary));
  display: grid;
  place-items: end start;
  padding: 16px;
  position: relative;
}

.cover-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-image .badge,
.store-image .badge,
.product-overlay,
.store-overlay {
  z-index: 1;
}

.store-image {
  background: linear-gradient(135deg, var(--color-success), #059669);
}

.product-overlay,
.store-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: grid;
  place-items: center;
  opacity: 0;
  transition: opacity var(--transition-base);
  color: #fff;
}

.product-card:hover .product-overlay,
.store-card:hover .store-overlay {
  opacity: 1;
}

.product-body,
.store-body {
  padding: 20px;
}

.product-body h3,
.store-body h3 {
  margin: 12px 0 8px;
  font-size: 16px;
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-gray-900);
  line-height: 1.4;
}

.product-body p,
.store-body p {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--color-gray-500);
  line-height: 1.5;
}

.product-meta,
.store-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--color-gray-100);
}

.price {
  font-size: 22px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-primary);
}

.sales {
  font-size: 13px;
  color: var(--color-gray-400);
}

.rating {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--color-warning);
  font-size: 16px;
  font-family: var(--font-display);
  font-weight: 600;
}

.notice-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.promotion-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.promotion-item {
  padding: 24px;
  border-radius: var(--radius-xl);
  border: 2px solid var(--color-gray-100);
  background: var(--color-surface);
  transition: all var(--transition-base);
  cursor: pointer;
}

.promotion-item:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.promotion-0 { border-left: 4px solid var(--color-warning); }
.promotion-1 { border-left: 4px solid var(--color-primary); }
.promotion-2 { border-left: 4px solid var(--color-success); }

.promotion-item strong {
  display: block;
  margin: 10px 0 8px;
  font-size: 16px;
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-gray-900);
}

.promotion-item p {
  margin: 0;
  font-size: 14px;
  color: var(--color-gray-500);
  line-height: 1.6;
}

.notice-item {
  padding: 24px;
  border-radius: var(--radius-xl);
  border: 2px solid var(--color-gray-100);
  background: var(--color-surface);
  transition: all var(--transition-base);
  cursor: pointer;
}

.notice-item:hover {
  border-color: var(--color-primary);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.notice-0 { border-left: 4px solid var(--color-primary); }
.notice-1 { border-left: 4px solid var(--color-accent); }
.notice-2 { border-left: 4px solid var(--color-success); }

.notice-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-lg);
  background: var(--color-rose-100);
  display: grid;
  place-items: center;
  color: var(--color-primary);
  margin-bottom: 16px;
}

.notice-item strong {
  display: block;
  margin-bottom: 8px;
  font-size: 16px;
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-gray-900);
}

.notice-item p {
  margin: 0;
  font-size: 14px;
  color: var(--color-gray-500);
  line-height: 1.6;
}

.error-text {
  color: var(--color-danger);
  margin: 0 0 16px;
}

.empty-text {
  text-align: center;
  color: var(--color-gray-400);
  padding: 60px 0;
  font-size: 15px;
}

.footer {
  background: var(--color-gray-900);
  color: var(--color-gray-400);
  padding: 32px 0;
  margin-top: auto;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-brand .brand-icon {
  width: 40px;
  height: 40px;
  box-shadow: none;
}

.footer-brand span {
  font-size: 18px;
  font-family: var(--font-display);
  font-weight: 700;
  color: #fff;
}

.footer p {
  margin: 0;
  font-size: 14px;
}

@media (max-width: 1200px) {
  .hero-container {
    grid-template-columns: 1fr;
    gap: 40px;
  }

  .hero-category-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1024px) {
  .product-grid,
  .store-grid,
  .promotion-grid,
  .notice-grid,
  .banners-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .category-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .category-grid.hero-category-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    max-width: none;
  }
}

@media (max-width: 768px) {
  .navbar-inner {
    flex-direction: column;
    align-items: stretch;
    gap: 16px;
    padding: 16px 0;
  }

  .nav-links,
  .user-info {
    justify-content: center;
  }

  .hero-section {
    padding: 40px 0;
  }

  .hero-title {
    font-size: 32px;
  }

  .hero-desc {
    font-size: 16px;
  }

  .search-box {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .hero-categories {
    padding: 20px;
  }

  .section-header,
  .banners-grid,
  .product-grid,
  .store-grid,
  .promotion-grid,
  .notice-grid,
  .category-grid {
    grid-template-columns: 1fr;
  }

  .category-grid.hero-category-grid {
    max-width: none;
  }

  .footer-inner {
    flex-direction: column;
    gap: 16px;
    text-align: center;
  }
}
</style>
