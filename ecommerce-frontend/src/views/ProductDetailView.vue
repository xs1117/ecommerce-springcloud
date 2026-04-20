<script setup>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchProductDetail, formatCurrency } from '../services/home';
import { api, uploadImage } from '../services/api';
import { resolveImageUrls, serializeImageUrls } from '../services/images';
import { addCartItem } from '../services/cart';
import { getCurrentUserId } from '../services/auth';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const errorMessage = ref('');
const activeImageIndex = ref(0);
const role = ref(localStorage.getItem('role') || 'USER');
const currentUserId = ref(getCurrentUserId());
const ownStoreIds = ref([]);
const ownershipResolved = ref(false);
const ownStoreOwnerUserId = ref(null);
const commentLoading = ref(false);
const commentError = ref('');
const commentSubmitting = ref(false);
const commentUploadLoading = ref(false);
const comments = ref([]);
const commentPreviewVisible = ref(false);
const commentPreviewImages = ref([]);
const commentPreviewIndex = ref(0);
const cartLoading = ref(false);
const commentForm = ref({
  content: '',
  imageUrls: []
});
const detail = ref({
  product: null,
  store: null,
  relatedProducts: []
});

const productId = computed(() => Number(route.params.id));
const galleryImages = computed(() => {
  const product = detail.value.product;
  const images = Array.isArray(product?.imageUrls) && product.imageUrls.length
    ? product.imageUrls
    : (product?.imageUrl ? [product.imageUrl] : []);
  return images.filter(Boolean);
});
const activeImageUrl = computed(() => galleryImages.value[activeImageIndex.value] || galleryImages.value[0] || '');
const isAdmin = computed(() => role.value === 'ADMIN');
const isOwnStoreMerchant = computed(() => {
  if (role.value !== 'MERCHANT') {
    return false;
  }
  const storeId = Number(detail.value.product?.storeId || detail.value.store?.id || 0);
  if (!storeId) {
    return false;
  }
  const ownerUserId = Number(
    detail.value.store?.ownerUserId
    || detail.value.product?.ownerUserId
    || ownStoreOwnerUserId.value
    || 0
  );
  if (ownerUserId > 0 && currentUserId.value && ownerUserId === currentUserId.value) {
    return true;
  }
  return ownStoreIds.value.includes(storeId);
});
const canContactMerchant = computed(() => {
  if (role.value !== 'MERCHANT') {
    return true;
  }
  if (!ownershipResolved.value) {
    return false;
  }
  return !isOwnStoreMerchant.value;
});
const storeManagePath = computed(() => {
  const storeId = detail.value.product?.storeId;
  if (!storeId) {
    return '/admin/merchant/review';
  }
  const storeName = detail.value.product?.storeName || '';
  return `/admin/merchant/stores/${storeId}?storeName=${encodeURIComponent(storeName)}`;
});
const commentPreviewImage = computed(() => commentPreviewImages.value[commentPreviewIndex.value] || '');

const loadDetail = async () => {
  loading.value = true;
  errorMessage.value = '';
  activeImageIndex.value = 0;
  try {
    detail.value = await fetchProductDetail(productId.value);
    await resolveMerchantOwnership();
  } catch (error) {
    detail.value = {
      product: null,
      store: null,
      relatedProducts: []
    };
    errorMessage.value = error?.response?.data?.message || '加载商品详情失败';
  } finally {
    loading.value = false;
  }
};

const resolveMerchantOwnership = async () => {
  if (role.value !== 'MERCHANT') {
    ownershipResolved.value = true;
    ownStoreOwnerUserId.value = null;
    return;
  }

  ownershipResolved.value = false;
  ownStoreOwnerUserId.value = null;
  await loadMyStoreIdsIfNeeded();

  const storeId = Number(detail.value.product?.storeId || detail.value.store?.id || 0);
  if (!storeId) {
    ownershipResolved.value = true;
    return;
  }

  if (!currentUserId.value) {
    currentUserId.value = getCurrentUserId();
  }

  try {
    const { data } = await api.get(`/api/merchant/public/stores/${storeId}`);
    const owner = Number(data?.ownerUserId || 0);
    ownStoreOwnerUserId.value = owner > 0 ? owner : null;
  } catch {
    // Keep fallback based on ownStoreIds when public store detail is unavailable.
  } finally {
    ownershipResolved.value = true;
  }
};

const loadMyStoreIdsIfNeeded = async () => {
  if (role.value !== 'MERCHANT' || ownStoreIds.value.length) {
    return;
  }
  try {
    const { data } = await api.get('/api/merchant/stores/me');
    ownStoreIds.value = Array.isArray(data)
      ? data.map((item) => Number(item?.id || 0)).filter((id) => id > 0)
      : [];
  } catch {
    ownStoreIds.value = [];
  }
};

const normalizeComment = (item = {}) => ({
  ...item,
  nickname: String(item.nickname || item.username || '用户'),
  imageList: resolveImageUrls(item.imageUrls || item.imageUrl),
  createdAtText: item.createdAt ? String(item.createdAt).replace('T', ' ').slice(0, 19) : ''
});

const loadComments = async () => {
  commentLoading.value = true;
  commentError.value = '';
  try {
    const { data } = await api.get(`/api/merchant/public/products/${productId.value}/comments`);
    comments.value = (data || []).map(normalizeComment);
  } catch (error) {
    comments.value = [];
    commentError.value = error?.response?.data?.message || '评论加载失败';
  } finally {
    commentLoading.value = false;
  }
};

const uploadCommentImages = async (event) => {
  const files = Array.from(event.target.files || []);
  if (!files.length) {
    return;
  }
  commentUploadLoading.value = true;
  try {
    const uploads = await Promise.all(files.map((file) => uploadImage('/api/merchant/upload/product', file)));
    commentForm.value.imageUrls = [
      ...commentForm.value.imageUrls,
      ...uploads.map((item) => item.url).filter(Boolean)
    ];
  } catch (error) {
    commentError.value = error?.response?.data?.message || '评论图片上传失败';
  } finally {
    commentUploadLoading.value = false;
    event.target.value = '';
  }
};

const removeCommentImage = (index) => {
  commentForm.value.imageUrls = commentForm.value.imageUrls.filter((_, current) => current !== index);
};

const openCommentImagePreview = (images, index = 0) => {
  const list = Array.isArray(images) ? images.filter(Boolean) : [];
  if (!list.length) {
    return;
  }
  commentPreviewImages.value = list;
  commentPreviewIndex.value = Math.min(Math.max(index, 0), list.length - 1);
  commentPreviewVisible.value = true;
};

const closeCommentImagePreview = () => {
  commentPreviewVisible.value = false;
  commentPreviewImages.value = [];
  commentPreviewIndex.value = 0;
};

const previewPrev = () => {
  if (!commentPreviewImages.value.length) {
    return;
  }
  const total = commentPreviewImages.value.length;
  commentPreviewIndex.value = (commentPreviewIndex.value - 1 + total) % total;
};

const previewNext = () => {
  if (!commentPreviewImages.value.length) {
    return;
  }
  commentPreviewIndex.value = (commentPreviewIndex.value + 1) % commentPreviewImages.value.length;
};

const submitComment = async () => {
  const content = String(commentForm.value.content || '').trim();
  if (!content) {
    commentError.value = '评论内容不能为空';
    return;
  }
  commentSubmitting.value = true;
  commentError.value = '';
  try {
    await api.post(`/api/merchant/products/${productId.value}/comments`, {
      content,
      imageUrls: serializeImageUrls(commentForm.value.imageUrls)
    });
    commentForm.value = { content: '', imageUrls: [] };
    await loadComments();
  } catch (error) {
    commentError.value = error?.response?.data?.message || '提交评论失败';
  } finally {
    commentSubmitting.value = false;
  }
};

const deleteComment = async (commentId) => {
  if (!isAdmin.value || !commentId) {
    return;
  }
  if (!window.confirm('确认删除这条评论吗？')) {
    return;
  }
  try {
    await api.delete(`/api/admin/merchant/products/${productId.value}/comments/${commentId}`);
    await loadComments();
  } catch (error) {
    commentError.value = error?.response?.data?.message || '删除评论失败';
  }
};

const goHome = () => router.push('/home');
const goSearch = () => router.push('/search');
const goStoreManage = () => router.push(storeManagePath.value);
const setActiveImage = (index) => {
  if (index >= 0 && index < galleryImages.value.length) {
    activeImageIndex.value = index;
  }
};
const openStore = () => {
  if (detail.value.store?.id) {
    router.push(`/store/${detail.value.store.id}`);
  }
};

const goChat = async () => {
  if (!detail.value.product?.storeId) {
    return;
  }
  if (role.value === 'MERCHANT' && !ownershipResolved.value) {
    await resolveMerchantOwnership();
  }
  if (!canContactMerchant.value) {
    return;
  }
  router.push({
    path: '/chat',
    query: {
      storeId: detail.value.product.storeId,
      productId: detail.value.product.id,
      source: 'product-detail'
    }
  });
};

const openProduct = (id) => router.push(`/product/${id}`);

const addToCart = async () => {
  const product = detail.value.product;
  if (!product) {
    return;
  }
  cartLoading.value = true;
  try {
    await addCartItem({
      productId: product.id,
      storeId: product.storeId,
      storeName: product.storeName,
      title: product.title,
      description: product.description || product.subtitle || '',
      coverImageUrl: activeImageUrl.value || product.imageUrl || '',
      price: product.price,
      maxQuantity: product.stock,
      quantity: 1,
      selected: true,
      source: 'product-detail',
      behaviorDetail: '从商品详情页加入购物车'
    });
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '加入购物车失败';
  } finally {
    cartLoading.value = false;
  }
};

watch(productId, async () => {
  await loadDetail();
  await loadComments();
}, { immediate: true });
</script>

<template>
  <div class="detail-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <button class="btn btn-outline" @click="goHome">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
            <polyline points="9 22 9 12 15 12 15 22"></polyline>
          </svg>
          返回首页
        </button>
        <button v-if="isAdmin" class="btn btn-outline" @click="goStoreManage">返回店铺管理</button>
        <button class="btn btn-primary" @click="goSearch">继续搜索</button>
      </div>
    </header>

    <main class="container main-content">
      <section v-if="loading" class="card loading-state">
        <div class="spinner"></div>
        <p>加载商品详情中...</p>
      </section>
      
      <section v-else-if="errorMessage" class="card error-state">
        <div class="error-icon">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        </div>
        <strong>商品详情加载失败</strong>
        <p>{{ errorMessage }}</p>
      </section>
      
      <template v-else>
        <section v-if="detail.product" class="product-hero fade-in">
          <div class="product-gallery">
            <div class="product-image">
              <img v-if="activeImageUrl" :src="activeImageUrl" class="cover-image" alt="product" />
              <div v-else class="cover-empty">
                <svg width="72" height="72" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <rect x="3" y="3" width="18" height="18" rx="2"></rect>
                  <path d="m8 13 2.5-2.5 3 3L17 9"></path>
                  <circle cx="8.5" cy="8.5" r="1.5"></circle>
                </svg>
              </div>
              <span class="badge badge-primary">{{ detail.product.tag || '商品' }}</span>
              <div class="image-overlay">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8"></circle>
                  <path d="m21 21-4.35-4.35"></path>
                </svg>
              </div>
            </div>
            <div v-if="galleryImages.length" class="gallery-thumbnails-panel">
              <div class="gallery-thumbnails">
                <button
                  v-for="(url, index) in galleryImages"
                  :key="`${url}-${index}`"
                  type="button"
                  class="thumbnail"
                  :class="{ active: index === activeImageIndex }"
                  @click="setActiveImage(index)"
                >
                  <img :src="url" class="thumbnail-image" :alt="`thumbnail-${index + 1}`" />
                </button>
              </div>
            </div>
          </div>
          
          <div class="product-info slide-in-right">
            <span class="product-category">{{ detail.product.category }}</span>
            <h1 class="product-title">{{ detail.product.title }}</h1>
            <p class="product-desc">{{ detail.product.subtitle || detail.product.description }}</p>

            <div class="price-section">
              <div class="price-main">
                <span class="price-label">价格</span>
                <strong class="price">{{ formatCurrency(detail.product.price) }}</strong>
              </div>
              <div class="price-meta">
                <span class="sales">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"></path>
                  </svg>
                  销量 {{ detail.product.salesCount }}
                </span>
                <span class="stock">库存 {{ detail.product.stock }}</span>
              </div>
            </div>

            <div class="info-grid">
              <div class="info-item">
                <div class="info-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
                    <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
                  </svg>
                </div>
                <div class="info-content">
                  <span class="info-label">店铺</span>
                  <strong class="info-value">{{ detail.product.storeName }}</strong>
                </div>
              </div>
              <div class="info-item">
                <div class="info-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                    <circle cx="12" cy="10" r="3"></circle>
                  </svg>
                </div>
                <div class="info-content">
                  <span class="info-label">城市</span>
                  <strong class="info-value">{{ detail.product.city }}</strong>
                </div>
              </div>
              <div class="info-item">
                <div class="info-icon info-icon-rating">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                  </svg>
                </div>
                <div class="info-content">
                  <span class="info-label">评分</span>
                  <strong class="info-value rating">{{ detail.product.rating }}</strong>
                </div>
              </div>
              <div class="info-item">
                <div class="info-icon info-icon-stock">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path>
                    <line x1="3" y1="6" x2="21" y2="6"></line>
                    <path d="M16 10a4 4 0 0 1-8 0"></path>
                  </svg>
                </div>
                <div class="info-content">
                  <span class="info-label">库存</span>
                  <strong class="info-value">{{ detail.product.stock }} 件</strong>
                </div>
              </div>
            </div>

            <div class="action-buttons">
              <button
                class="btn btn-outline btn-lg"
                :disabled="!canContactMerchant"
                :title="!ownershipResolved && role === 'MERCHANT'
                  ? '正在校验店铺归属...'
                  : (isOwnStoreMerchant ? '商家不能联系客服咨询自己店铺商品' : '联系客服')"
                @click="goChat"
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15a4 4 0 0 1-4 4H7l-4 4V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z"></path>
                </svg>
                联系客服
              </button>
              <button class="btn btn-primary btn-lg" @click="openStore">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
                  <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
                </svg>
                进入店铺
              </button>
              <button class="btn btn-success btn-lg" :disabled="cartLoading" @click="addToCart">
                <svg v-if="!cartLoading" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="9" cy="20" r="1.8"></circle>
                  <circle cx="18" cy="20" r="1.8"></circle>
                  <path d="M3 4h2l2.2 10.5a2 2 0 0 0 2 1.5h7.6a2 2 0 0 0 1.95-1.6L20 8H6"></path>
                </svg>
                {{ cartLoading ? '加入中...' : '加入购物车' }}
              </button>
              <button class="btn btn-outline btn-lg" @click="goSearch">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8"></circle>
                  <path d="m21 21-4.35-4.35"></path>
                </svg>
                回到搜索
              </button>
            </div>
            <p v-if="role === 'MERCHANT' && !ownershipResolved" class="owner-contact-tip">正在校验店铺归属，请稍候...</p>
            <p v-if="isOwnStoreMerchant" class="owner-contact-tip">你是该店铺商家，暂不支持咨询自己店铺商品。</p>
          </div>
        </section>

        <section v-if="detail.store" class="card store-info fade-in">
          <div class="store-header">
            <div class="store-avatar">
              <img v-if="detail.store.storeImageUrl" :src="detail.store.storeImageUrl" class="cover-image" alt="store" />
              <span class="badge badge-success">{{ detail.store.tag || '店铺' }}</span>
            </div>
            <div class="store-meta">
              <span class="store-label">{{ detail.store.category }}</span>
              <h2>{{ detail.store.title }}</h2>
              <p class="store-intro">{{ detail.store.storeIntro || detail.store.slogan }}</p>
            </div>
          </div>
          <div class="store-stats">
            <span class="stat">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
              </svg>
              {{ detail.store.rating }}
            </span>
            <span class="stat">{{ detail.store.productCount }} 款商品</span>
            <span class="stat">{{ detail.store.followers }} 关注</span>
            <span class="stat">{{ detail.store.city }}</span>
          </div>
        </section>

        <section class="card section-card fade-in">
          <div class="section-header">
            <div>
              <span class="section-label">详情</span>
              <h2 class="section-title">商品说明</h2>
            </div>
          </div>
          <p class="description">{{ detail.product.description || '暂无商品说明。' }}</p>
        </section>

        <section class="card section-card fade-in">
          <div class="section-header">
            <div>
              <span class="section-label">评论</span>
              <h2 class="section-title">商品评价</h2>
            </div>
          </div>

          <div class="comment-form">
            <textarea
              v-model="commentForm.content"
              class="comment-input"
              rows="3"
              placeholder="说点什么吧..."
            ></textarea>
            <div class="comment-form-actions">
              <input type="file" accept="image/*" multiple @change="uploadCommentImages" />
              <button class="btn btn-primary" :disabled="commentSubmitting" @click="submitComment">
                {{ commentSubmitting ? '提交中...' : '发表评论' }}
              </button>
            </div>
            <div v-if="commentUploadLoading" class="comment-upload-tip">图片上传中...</div>
            <div v-if="commentForm.imageUrls.length" class="comment-image-strip">
              <div v-for="(url, index) in commentForm.imageUrls" :key="`${url}-${index}`" class="comment-image-item">
                <img :src="url" class="comment-image-thumb" alt="comment-image" />
                <button class="btn btn-danger btn-xs" @click="removeCommentImage(index)">删除</button>
              </div>
            </div>
          </div>

          <p v-if="commentError" class="comment-error">{{ commentError }}</p>
          <div v-if="commentLoading" class="comment-empty">评论加载中...</div>
          <div v-else-if="comments.length" class="comment-list">
            <article v-for="item in comments" :key="item.id" class="comment-item">
              <div class="comment-head">
                <strong>{{ item.nickname }}</strong>
                <span>{{ item.createdAtText }}</span>
              </div>
              <p class="comment-content">{{ item.content }}</p>
              <div v-if="item.imageList.length" class="comment-images">
                <img
                  v-for="(url, idx) in item.imageList"
                  :key="`${item.id}-${idx}`"
                  :src="url"
                  class="comment-image"
                  alt="comment"
                  @click="openCommentImagePreview(item.imageList, idx)"
                />
              </div>
              <div v-if="isAdmin" class="comment-admin-actions">
                <button class="btn btn-danger btn-xs" @click="deleteComment(item.id)">删除评论</button>
              </div>
            </article>
          </div>
          <div v-else class="comment-empty">还没有评论，欢迎抢先评价。</div>
        </section>

        <section class="card section-card fade-in">
          <div class="section-header">
            <div>
              <span class="section-label">猜你喜欢</span>
              <h2 class="section-title">同店或同类商品</h2>
            </div>
          </div>
          <div v-if="detail.relatedProducts.length" class="related-grid">
            <article
              v-for="item in detail.relatedProducts"
              :key="item.id"
              class="related-card hover-lift"
              @click="openProduct(item.id)"
            >
              <div class="related-image">
                <img v-if="item.imageUrl" :src="item.imageUrl" class="cover-image" alt="related" />
                <span class="badge badge-primary">{{ item.tag || '推荐' }}</span>
                <div class="related-overlay">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"></circle>
                    <path d="m21 21-4.35-4.35"></path>
                  </svg>
                </div>
              </div>
              <div class="related-body">
                <h3>{{ item.title }}</h3>
                <p>{{ item.subtitle }}</p>
                <strong class="related-price">{{ formatCurrency(item.price) }}</strong>
              </div>
            </article>
          </div>
          <p v-else class="empty-text">暂无相关商品。</p>
        </section>
      </template>
    </main>

    <div v-if="commentPreviewVisible" class="comment-preview-overlay" @click="closeCommentImagePreview">
      <div class="comment-preview-dialog" @click.stop>
        <button class="comment-preview-close" @click="closeCommentImagePreview">×</button>
        <img v-if="commentPreviewImage" :src="commentPreviewImage" class="comment-preview-image" alt="preview" />
        <div v-if="commentPreviewImages.length > 1" class="comment-preview-toolbar">
          <button class="btn btn-outline btn-sm" @click="previewPrev">上一张</button>
          <span>{{ commentPreviewIndex + 1 }} / {{ commentPreviewImages.length }}</span>
          <button class="btn btn-outline btn-sm" @click="previewNext">下一张</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.detail-page {
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

.product-hero {
  display: grid;
  grid-template-columns: 480px 1fr;
  gap: 48px;
  padding: 40px;
  background: var(--color-surface);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-xl);
}

.product-gallery {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.product-image {
  height: 480px;
  background: linear-gradient(135deg, var(--color-gray-800), var(--color-primary));
  border-radius: var(--radius-xl);
  display: grid;
  place-items: end start;
  padding: 24px;
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

.cover-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.75);
}

.image-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: grid;
  place-items: center;
  opacity: 0;
  transition: opacity var(--transition-base);
  color: #fff;
}

.product-image .badge,
.store-avatar .badge,
.related-image .badge,
.image-overlay,
.related-overlay {
  z-index: 1;
}

.product-image:hover .image-overlay {
  opacity: 1;
}

.gallery-thumbnails-panel {
  padding: 12px;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.96) 0%, rgba(255, 241, 242, 0.96) 100%);
  border: 1px solid rgba(225, 29, 72, 0.12);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.gallery-thumbnails {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.thumbnail {
  width: 84px;
  height: 84px;
  border-radius: var(--radius-lg);
  background: #fff;
  border: 3px solid transparent;
  cursor: pointer;
  transition: all var(--transition-base);
  overflow: hidden;
  flex-shrink: 0;
  padding: 0;
}

.thumbnail.active {
  border-color: var(--color-primary);
  box-shadow: 0 8px 18px rgba(225, 29, 72, 0.18);
}

.thumbnail:hover {
  border-color: var(--color-primary-light);
  transform: translateY(-1px);
}

.thumbnail-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.product-category {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-primary);
  font-weight: 700;
}

.product-title {
  margin: 0 0 16px;
  font-size: 36px;
  font-family: var(--font-display);
  font-weight: 800;
  color: var(--color-gray-900);
  line-height: 1.2;
}

.product-desc {
  margin: 0 0 32px;
  color: var(--color-gray-600);
  font-size: 16px;
  line-height: 1.7;
}

.price-section {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 24px;
  margin-bottom: 32px;
  background: linear-gradient(135deg, var(--color-rose-50) 0%, #FFF1F2 100%);
  border-radius: var(--radius-xl);
  border: 2px solid var(--color-rose-100);
}

.price-label {
  font-size: 12px;
  color: var(--color-gray-500);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin-bottom: 4px;
}

.price {
  font-size: 42px;
  font-family: var(--font-display);
  font-weight: 800;
  color: var(--color-primary);
}

.price-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.sales {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--color-gray-600);
}

.sales svg {
  color: var(--color-primary);
}

.stock {
  font-size: 14px;
  color: var(--color-gray-500);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  border: 2px solid var(--color-gray-100);
  border-radius: var(--radius-xl);
  background: var(--color-surface);
  transition: all var(--transition-base);
}

.info-item:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
}

.info-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  background: var(--color-rose-100);
  display: grid;
  place-items: center;
  color: var(--color-primary);
  flex-shrink: 0;
}

.info-icon-rating {
  background: rgba(245, 158, 11, 0.1);
  color: var(--color-warning);
}

.info-icon-stock {
  background: rgba(16, 185, 129, 0.1);
  color: var(--color-success);
}

.info-label {
  display: block;
  font-size: 12px;
  color: var(--color-gray-500);
  margin-bottom: 4px;
}

.info-value {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-gray-900);
}

.info-value.rating {
  color: var(--color-warning);
}

.action-buttons {
  display: flex;
  gap: 16px;
}

.action-buttons .btn {
  flex: 1;
}

.owner-contact-tip {
  margin-top: 10px;
  font-size: 13px;
  color: var(--color-gray-500);
}

.store-info {
  padding: 32px;
}

.store-header {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
}

.store-avatar {
  width: 120px;
  height: 120px;
  background: linear-gradient(135deg, var(--color-success), #059669);
  border-radius: var(--radius-xl);
  display: grid;
  place-items: end start;
  padding: 16px;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

.store-label {
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

.store-meta h2 {
  margin: 0 0 12px;
  font-size: 28px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-gray-900);
}

.store-intro {
  margin: 0;
  color: var(--color-gray-600);
  font-size: 15px;
  line-height: 1.6;
}

.store-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding-top: 20px;
  border-top: 2px solid var(--color-gray-100);
}

.stat {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--color-gray-600);
  font-weight: 500;
}

.stat svg {
  color: var(--color-warning);
}

.section-card {
  padding: 32px;
}

.section-header {
  margin-bottom: 24px;
}

.description {
  margin: 0;
  color: var(--color-gray-700);
  line-height: 1.9;
  font-size: 15px;
}

.comment-form {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

.comment-input {
  width: 100%;
  padding: 12px 14px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-gray-300);
  resize: vertical;
  min-height: 90px;
}

.comment-form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.comment-upload-tip {
  color: var(--color-gray-500);
  font-size: 13px;
}

.comment-image-strip {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 8px 0;
}

.comment-image-item {
  display: grid;
  gap: 6px;
}

.comment-image-thumb {
  width: 88px;
  height: 88px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-gray-200);
  object-fit: cover;
}

.comment-error {
  color: var(--color-danger);
  margin: 0 0 12px;
}

.comment-list {
  display: grid;
  gap: 14px;
}

.comment-item {
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-lg);
  padding: 12px;
  background: #fff;
}

.comment-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--color-gray-500);
}

.comment-content {
  margin: 0;
  color: var(--color-gray-800);
  line-height: 1.7;
}

.comment-images {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  overflow-x: auto;
}

.comment-image {
  width: 96px;
  height: 96px;
  border-radius: var(--radius-md);
  object-fit: cover;
  border: 1px solid var(--color-gray-200);
  cursor: zoom-in;
  transition: transform var(--transition-base), box-shadow var(--transition-base);
}

.comment-image:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.comment-admin-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.comment-empty {
  text-align: center;
  color: var(--color-gray-500);
  padding: 20px 0;
}

.btn-xs {
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1;
}

.comment-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-modal) + 20);
  background: rgba(17, 24, 39, 0.75);
  backdrop-filter: blur(4px);
  display: grid;
  place-items: center;
  padding: 16px;
}

.comment-preview-dialog {
  width: min(92vw, 960px);
  max-height: 92vh;
  background: #fff;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-2xl);
  padding: 14px;
  display: grid;
  gap: 12px;
}

.comment-preview-close {
  justify-self: end;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: var(--color-gray-100);
  color: var(--color-gray-700);
  font-size: 22px;
  line-height: 1;
}

.comment-preview-image {
  width: 100%;
  max-height: 72vh;
  object-fit: contain;
  border-radius: var(--radius-lg);
  background: #f8fafc;
}

.comment-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--color-gray-600);
}

.related-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.related-card {
  background: var(--color-surface);
  border-radius: var(--radius-xl);
  overflow: hidden;
  cursor: pointer;
  border: 2px solid var(--color-gray-100);
  transition: all var(--transition-base);
}

.related-card:hover {
  border-color: var(--color-primary);
}

.related-image {
  height: 180px;
  background: linear-gradient(135deg, var(--color-gray-800), var(--color-primary));
  display: grid;
  place-items: end start;
  padding: 16px;
  position: relative;
  overflow: hidden;
}

.related-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: grid;
  place-items: center;
  opacity: 0;
  transition: opacity var(--transition-base);
  color: #fff;
}

.related-card:hover .related-overlay {
  opacity: 1;
}

.related-body {
  padding: 20px;
}

.related-body h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-gray-900);
  line-height: 1.4;
}

.related-body p {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--color-gray-500);
}

.related-price {
  display: block;
  font-size: 20px;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-primary);
}

.empty-text {
  text-align: center;
  color: var(--color-gray-400);
  padding: 60px 0;
  font-size: 15px;
}

@media (max-width: 1100px) {
  .product-hero,
  .related-grid {
    grid-template-columns: 1fr;
  }

  .product-image {
    height: 360px;
  }
}

@media (max-width: 768px) {
  .product-hero {
    padding: 24px;
  }

  .product-title {
    font-size: 28px;
  }

  .price {
    font-size: 32px;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .store-header {
    flex-direction: column;
  }

  .store-avatar {
    width: 100%;
    height: 140px;
  }

  .action-buttons {
    flex-direction: column;
  }
}
</style>
