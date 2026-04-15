import { api, toFileUrl } from './api';
import { resolveCoverImageUrl, resolveImageUrls } from './images';

const historyKey = (username = 'guest') => `ecommerce:search-history:${username}`;

const normalizeText = (value) => String(value || '').trim().toLowerCase();

const normalizeProduct = (product = {}) => ({
  ...product,
  type: 'product',
  imageUrls: resolveImageUrls(product.imageUrls || product.imageUrl),
  imageUrl: resolveCoverImageUrl(product.imageUrls || product.imageUrl),
  coverImageUrl: resolveCoverImageUrl(product.imageUrls || product.imageUrl),
  storeImageUrl: toFileUrl(product.storeImageUrl)
});

const normalizeStore = (store = {}) => ({
  ...store,
  type: 'store',
  imageUrl: toFileUrl(store.imageUrl),
  storeImageUrl: toFileUrl(store.storeImageUrl)
});

const normalizeSearchItem = (item = {}) => (item.type === 'store' ? normalizeStore(item) : normalizeProduct(item));

const normalizeHomePayload = (data = {}) => ({
  hero: data.hero || {},
  banners: data.banners || [],
  categories: data.categories || [],
  hotKeywords: data.hotKeywords || [],
  featuredProducts: (data.featuredProducts || []).map(normalizeProduct),
  featuredStores: (data.featuredStores || []).map(normalizeStore),
  notices: data.notices || [],
  promotions: data.promotions || []
});

const normalizeSearchPayload = (data = {}) => ({
  keyword: data.keyword || '',
  type: data.type || 'all',
  sort: data.sort || 'relevance',
  page: Number(data.page || 1),
  size: Number(data.size || 24),
  total: Number(data.total || 0),
  selectedTotal: Number(data.selectedTotal || data.total || 0),
  totalPages: Number(data.totalPages || 1),
  products: (data.products || []).map(normalizeProduct),
  stores: (data.stores || []).map(normalizeStore),
  items: (data.items || []).map(normalizeSearchItem)
});

const normalizeRecommendationPayload = (data = {}) => ({
  items: (data.items || []).map(normalizeProduct)
});

const normalizeProductDetailPayload = (data = {}) => ({
  product: data.product ? normalizeProduct(data.product) : null,
  store: data.store ? normalizeStore(data.store) : null,
  relatedProducts: (data.relatedProducts || []).map(normalizeProduct)
});

const normalizeStoreDetailPayload = (data = {}) => ({
  store: data.store ? normalizeStore(data.store) : null,
  products: (data.products || []).map(normalizeProduct)
});

export const fetchHomeData = async () => {
  const { data } = await api.get('/api/home');
  return normalizeHomePayload(data);
};

export const searchCatalog = async ({ keyword = '', type = 'all', sort = 'relevance', page = 1, size = 24 } = {}) => {
  const { data } = await api.get('/api/home/search', {
	params: {
	  keyword,
	  type,
	  sort,
	  page,
	  size
	}
  });
  return normalizeSearchPayload(data);
};

export const fetchProductDetail = async (id) => {
  const { data } = await api.get(`/api/home/products/${id}`);
  return normalizeProductDetailPayload(data);
};

export const fetchStoreDetail = async (id) => {
  const { data } = await api.get(`/api/home/stores/${id}`);
  return normalizeStoreDetailPayload(data);
};

export const fetchBannerDetail = async (id) => {
  const { data } = await api.get(`/api/home/banners/${id}`);
  return data || {};
};

export const fetchNoticeDetail = async (id) => {
  const { data } = await api.get(`/api/home/notices/${id}`);
  return data || {};
};

export const fetchPromotionDetail = async (id) => {
  const { data } = await api.get(`/api/home/promotions/${id}`);
  return data || {};
};

export const fetchRecommendations = async ({ username, history = [], limit = 8 } = {}) => {
  const { data } = await api.get('/api/home/recommendations', {
	params: {
	  username,
	  history: Array.isArray(history)
		? history.map((item) => `${item.type || 'product'}:${normalizeText(item.keyword || '')}`).filter(Boolean).join(',')
		: '',
	  limit
	}
  });
  return normalizeRecommendationPayload(data);
};

export const loadSearchHistory = (username = 'guest') => {
  try {
	const raw = localStorage.getItem(historyKey(username));
	const parsed = raw ? JSON.parse(raw) : [];
	return Array.isArray(parsed) ? parsed : [];
  } catch {
	return [];
  }
};

export const saveSearchHistory = (username = 'guest', keyword, type = 'product') => {
  const cleanKeyword = String(keyword || '').trim();
  if (!cleanKeyword) {
	return [];
  }
  const key = historyKey(username);
  const next = [
	{ keyword: cleanKeyword, type, ts: Date.now() },
	...loadSearchHistory(username).filter((item) => normalizeText(item.keyword) !== normalizeText(cleanKeyword) || item.type !== type)
  ].slice(0, 12);
  localStorage.setItem(key, JSON.stringify(next));
  return next;
};


export const formatCurrency = (value) => `￥${Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;

export const SEARCH_TYPE_OPTIONS = [
  { label: '商品', value: 'product' },
  { label: '店铺', value: 'store' }
];

export const SEARCH_SORT_OPTIONS = [
  { label: '默认排序', value: 'relevance' },
  { label: '价格升序', value: 'price-asc' },
  { label: '价格降序', value: 'price-desc' },
  { label: '销量优先', value: 'sales-desc' },
  { label: '库存优先', value: 'stock-desc' },
  { label: '最新上架', value: 'newest' }
];

