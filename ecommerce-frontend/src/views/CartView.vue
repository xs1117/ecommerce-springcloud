<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { clearCart, fetchCartSummary, onCartChanged, removeCartItem, updateCartItem } from '../services/cart';
import { formatCurrency } from '../services/home';

const router = useRouter();
const loading = ref(false);
const errorMessage = ref('');
const summary = ref({ items: [], behaviors: [] });
const clearConfirmVisible = ref(false);

let offChanged = null;

const items = computed(() => summary.value.items || []);
const selectedItems = computed(() => items.value.filter((item) => item.selected));
const totalAmount = computed(() => Number(summary.value.selectedAmount ?? summary.value.totalAmount ?? 0));
const totalQuantity = computed(() => Number(summary.value.selectedQuantity ?? summary.value.totalQuantity ?? 0));

const loadCart = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    summary.value = await fetchCartSummary();
  } catch (error) {
    summary.value = { items: [], behaviors: [] };
    errorMessage.value = error?.response?.data?.message || '购物车加载失败';
  } finally {
    loading.value = false;
  }
};

const changeQuantity = async (item, delta) => {
  const maxQuantity = Number(item.maxQuantity || 99);
  const next = Math.max(1, Number(item.quantity || 1) + delta);
  if (next > maxQuantity) {
    errorMessage.value = '数量不能超过可购上限';
    return;
  }
  try {
    await updateCartItem(item.id, {
      quantity: next,
      selected: item.selected,
      source: 'cart',
      behaviorDetail: '调整购物车数量'
    });
    await loadCart();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '更新商品数量失败';
  }
};

const toggleSelected = async (item) => {
  try {
    await updateCartItem(item.id, {
      quantity: Number(item.quantity || 1),
      selected: !item.selected,
      source: 'cart',
      behaviorDetail: '切换购物车选择状态'
    });
    await loadCart();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '更新购物车选择状态失败';
  }
};

const removeItem = async (item) => {
  if (!window.confirm(`确认从购物车移除「${item.title}」吗？`)) {
    return;
  }
  await removeCartItem(item.id);
  await loadCart();
};

const clearAll = async () => {
  if (!items.value.length) {
    return;
  }
  clearConfirmVisible.value = true;
};

const closeClearConfirm = () => {
  clearConfirmVisible.value = false;
};

const confirmClearAll = async () => {
  await clearCart();
  await loadCart();
  closeClearConfirm();
};

const goProduct = (productId) => {
  if (productId) {
    router.push(`/product/${productId}`);
  }
};

const goShopping = () => router.push('/home');
const checkout = () => {
  if (!selectedItems.value.length) {
    errorMessage.value = '请先勾选要结算的商品';
    return;
  }
  router.push('/checkout');
};

onMounted(async () => {
  await loadCart();
  offChanged = onCartChanged(loadCart);
});

onBeforeUnmount(() => {
  if (offChanged) {
    offChanged();
  }
});
</script>

<template>
  <div class="cart-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <div class="cart-title">
          <h1>我的购物车</h1>
        </div>
        <div class="cart-header-actions">
          <button class="btn btn-outline continue-shopping-btn" @click="goShopping">继续购物</button>
          <button class="btn btn-primary" :disabled="!items.length" @click="clearAll">清空购物车</button>
        </div>
      </div>
    </header>

    <main class="container cart-layout">
      <section v-if="loading" class="card loading-panel">
        <div class="spinner"></div>
        <p>正在加载购物车...</p>
      </section>

      <section v-else-if="errorMessage" class="card error-panel">
        <strong>购物车加载失败</strong>
        <p>{{ errorMessage }}</p>
      </section>

      <template v-else>
        <section class="card cart-items-panel">
          <div v-if="items.length" class="cart-items">
            <article v-for="item in items" :key="item.id" class="cart-item">
              <label class="cart-check">
                <input type="checkbox" :checked="item.selected" @change="toggleSelected(item)" />
              </label>
              <img v-if="item.coverImageUrl" :src="item.coverImageUrl" class="cart-thumb" alt="cart item" @click="goProduct(item.productId)" />
              <div class="cart-info">
                <h3 @click="goProduct(item.productId)">{{ item.title }}</h3>
                <p>{{ item.storeName || '店铺信息未知' }}</p>
                <div class="cart-meta">
                  <strong class="price">{{ formatCurrency(item.price) }}</strong>
                  <span>小计 {{ formatCurrency(item.lineAmount) }}</span>
                </div>
              </div>
              <div class="cart-actions">
                <div class="quantity-control">
                  <button class="btn btn-secondary" @click="changeQuantity(item, -1)">-</button>
                  <span>{{ item.quantity }}</span>
                  <button class="btn btn-secondary" :disabled="Number(item.quantity || 1) >= Number(item.maxQuantity || 99)" @click="changeQuantity(item, 1)">+</button>
                </div>
                <button class="btn btn-outline" @click="removeItem(item)">删除</button>
              </div>
            </article>
          </div>
          <div v-else class="empty-state">
            <p>购物车为空，去挑选一些商品吧。</p>
            <button class="btn btn-primary" @click="goShopping">去逛逛</button>
          </div>
        </section>

        <aside class="card cart-summary-panel">
          <h2>结算预览</h2>
          <div class="summary-row"><span>商品件数</span><strong>{{ totalQuantity }}</strong></div>
          <div class="summary-row"><span>应付总额</span><strong>{{ formatCurrency(totalAmount) }}</strong></div>
          <div class="summary-row"><span>全部商品</span><strong>{{ summary.itemCount || 0 }}</strong></div>
          <div class="behavior-panel" v-if="summary.behaviors?.length">
            <h3>最近行为</h3>
            <ul>
              <li v-for="event in summary.behaviors" :key="event.id">
                <span>{{ event.action }}</span>
                <small>{{ event.createdAt }}</small>
              </li>
            </ul>
          </div>
          <button class="btn btn-success btn-block" :disabled="!selectedItems.length" @click="checkout">去结算</button>
        </aside>
      </template>
    </main>

    <div v-if="clearConfirmVisible" class="simple-modal-mask" @click.self="closeClearConfirm">
      <div class="simple-modal card">
        <h3>确认清空购物车？</h3>
        <p>此操作会移除当前购物车中的所有商品。</p>
        <div class="simple-modal-actions">
          <button class="btn btn-secondary" @click="closeClearConfirm">取消</button>
          <button class="btn btn-primary" @click="confirmClearAll">确认清空</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cart-page {
  min-height: 100vh;
  background: var(--gradient-hero);
}

.cart-title {
  text-align: center;
}

.cart-title h1 {
  font-family: var(--font-display);
  font-size: 28px;
  margin-top: 10px;
  margin-bottom: 4px;
}

.cart-title p {
  color: var(--color-gray-500);
}

.cart-header-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.cart-header-actions .btn {
  min-width: 136px;
  justify-content: center;
}

.continue-shopping-btn {
  margin-top: 18px;
}

.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 24px;
  padding: 28px 0 40px;
}

.cart-items-panel,
.cart-summary-panel {
  padding: 24px;
}

.cart-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.cart-item {
  display: grid;
  grid-template-columns: auto 92px 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(225, 29, 72, 0.08);
}

.cart-check input {
  width: 18px;
  height: 18px;
}

.cart-thumb {
  width: 92px;
  height: 92px;
  object-fit: cover;
  border-radius: 16px;
  cursor: pointer;
}

.cart-info h3 {
  cursor: pointer;
  margin-bottom: 6px;
}

.cart-info p,
.cart-meta span {
  color: var(--color-gray-500);
}

.cart-meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
}

.cart-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
}

.quantity-control {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.quantity-control span {
  min-width: 28px;
  text-align: center;
  font-weight: 700;
}

.cart-summary-panel {
  position: sticky;
  top: 24px;
  height: fit-content;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
}

.behavior-panel {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px dashed rgba(225, 29, 72, 0.14);
}

.behavior-panel ul {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.behavior-panel li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: var(--color-gray-500);
}

.empty-state,
.loading-panel,
.error-panel {
  min-height: 280px;
  display: grid;
  place-items: center;
  text-align: center;
  padding: 32px;
}

.btn-block {
  width: 100%;
  margin-top: 22px;
}

.simple-modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.36);
  display: grid;
  place-items: center;
  z-index: var(--z-modal-backdrop);
}

.simple-modal {
  width: min(420px, 92vw);
  padding: 22px;
}

.simple-modal h3 {
  margin-bottom: 8px;
}

.simple-modal p {
  color: var(--color-gray-500);
  margin-bottom: 18px;
}

.simple-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 1024px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }

  .cart-summary-panel {
    position: static;
  }
}

@media (max-width: 768px) {
  .navbar-inner {
    align-items: flex-start;
  }

  .cart-header-actions {
    align-items: stretch;
    width: 100%;
    margin-top: 12px;
  }

  .cart-item {
    grid-template-columns: auto 72px 1fr;
  }

  .cart-actions {
    grid-column: 1 / -1;
    align-items: stretch;
    flex-direction: row;
    justify-content: space-between;
  }
}
</style>

