<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { clearCart, fetchCartSummary, onCartChanged, removeCartItem, updateCartItem } from '../services/cart';
import { formatCurrency } from '../services/home';

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['close']);
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

const closeDrawer = () => emit('close');

const loadCart = async () => {
  if (!props.visible) {
    return;
  }
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
  await updateCartItem(item.id, {
    quantity: next,
    selected: item.selected,
    source: 'cart-drawer',
    behaviorDetail: '在购物车抽屉调整数量'
  });
  await loadCart();
};

const toggleSelected = async (item) => {
  await updateCartItem(item.id, {
    quantity: Number(item.quantity || 1),
    selected: !item.selected,
    source: 'cart-drawer',
    behaviorDetail: '在购物车抽屉切换选择状态'
  });
  await loadCart();
};

const removeItemQuickly = async (item) => {
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

const openProduct = (productId) => {
  if (!productId) {
    return;
  }
  closeDrawer();
  router.push(`/product/${productId}`);
};

const openCartPage = () => {
  closeDrawer();
  router.push('/cart');
};

const openCheckout = () => {
  if (!selectedItems.value.length) {
    errorMessage.value = '请先勾选要结算的商品';
    return;
  }
  closeDrawer();
  router.push('/checkout');
};

watch(() => props.visible, async (next) => {
  if (next) {
    await loadCart();
  }
});

onMounted(() => {
  offChanged = onCartChanged(loadCart);
});

onBeforeUnmount(() => {
  if (offChanged) {
    offChanged();
  }
});
</script>

<template>
  <div v-if="visible" class="cart-drawer-wrapper" @click.self="closeDrawer">
    <aside class="cart-drawer">
      <header class="cart-drawer-header">
        <div>
          <h2>购物车</h2>
          <p>已选 {{ totalQuantity }} 件</p>
        </div>
        <button class="btn btn-outline" @click="closeDrawer">关闭</button>
      </header>

      <section v-if="loading" class="cart-drawer-state">
        <div class="spinner"></div>
        <p>加载中...</p>
      </section>

      <section v-else-if="errorMessage" class="cart-drawer-state">
        <p>{{ errorMessage }}</p>
      </section>

      <section v-else class="cart-drawer-body">
        <div v-if="items.length" class="cart-drawer-items">
          <article v-for="item in items" :key="item.id" class="cart-drawer-item">
            <label class="drawer-check">
              <input type="checkbox" :checked="item.selected" @change="toggleSelected(item)" />
            </label>
            <img v-if="item.coverImageUrl" :src="item.coverImageUrl" alt="cart item" class="drawer-thumb" @click="openProduct(item.productId)" />
            <div class="drawer-info">
              <h3 @click="openProduct(item.productId)">{{ item.title }}</h3>
              <p>{{ formatCurrency(item.price) }}</p>
              <div class="drawer-item-actions">
                <button class="btn btn-secondary btn-sm" @click="changeQuantity(item, -1)">-</button>
                <span>{{ item.quantity }}</span>
                <button class="btn btn-secondary btn-sm" :disabled="Number(item.quantity || 1) >= Number(item.maxQuantity || 99)" @click="changeQuantity(item, 1)">+</button>
                <button class="btn btn-outline btn-sm" @click="removeItemQuickly(item)">删除</button>
              </div>
            </div>
          </article>
        </div>
        <div v-else class="cart-drawer-empty">
          <p>购物车还是空的，先去逛逛吧。</p>
          <button class="btn btn-primary" @click="openCartPage">去购物车页</button>
        </div>
      </section>

      <footer class="cart-drawer-footer">
        <div class="drawer-total">
          <span>合计</span>
          <strong>{{ formatCurrency(totalAmount) }}</strong>
        </div>
        <div class="drawer-actions">
          <button class="btn btn-secondary" :disabled="!items.length" @click="clearAll">清空</button>
          <button class="btn btn-outline" @click="openCartPage">购物车页</button>
          <button class="btn btn-primary" :disabled="!selectedItems.length" @click="openCheckout">去结算</button>
        </div>
      </footer>
    </aside>

    <div v-if="clearConfirmVisible" class="drawer-modal-mask" @click.self="closeClearConfirm">
      <div class="drawer-modal card">
        <h3>确认清空购物车？</h3>
        <p>将移除当前购物车中的全部商品。</p>
        <div class="drawer-modal-actions">
          <button class="btn btn-secondary" @click="closeClearConfirm">取消</button>
          <button class="btn btn-primary" @click="confirmClearAll">确认清空</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cart-drawer-wrapper {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.35);
  z-index: var(--z-modal-backdrop);
  display: flex;
  justify-content: flex-end;
}

.cart-drawer {
  width: min(430px, 92vw);
  height: 100%;
  background: #fff;
  box-shadow: -12px 0 30px rgba(15, 23, 42, 0.2);
  display: grid;
  grid-template-rows: auto 1fr auto;
}

.cart-drawer-header {
  padding: 16px;
  border-bottom: 1px solid var(--color-gray-200);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cart-drawer-header h2 {
  font-size: 22px;
  font-family: var(--font-display);
}

.cart-drawer-header p {
  color: var(--color-gray-500);
  font-size: 13px;
}

.cart-drawer-state,
.cart-drawer-empty {
  height: 100%;
  display: grid;
  place-items: center;
  text-align: center;
  padding: 24px;
}

.cart-drawer-body {
  overflow: auto;
}

.cart-drawer-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.cart-drawer-item {
  display: grid;
  grid-template-columns: auto 72px 1fr;
  gap: 10px;
  align-items: center;
  padding: 10px;
  border: 1px solid rgba(225, 29, 72, 0.12);
  border-radius: 14px;
}

.drawer-check input {
  width: 16px;
  height: 16px;
}

.drawer-thumb {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 12px;
  cursor: pointer;
}

.drawer-info h3 {
  font-size: 14px;
  margin-bottom: 2px;
  cursor: pointer;
}

.drawer-info p {
  color: var(--color-primary);
  font-weight: 700;
  font-size: 13px;
}

.drawer-item-actions {
  margin-top: 8px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.drawer-item-actions span {
  min-width: 20px;
  text-align: center;
  font-size: 13px;
  font-weight: 700;
}

.cart-drawer-footer {
  border-top: 1px solid var(--color-gray-200);
  padding: 14px;
  display: grid;
  gap: 12px;
}

.drawer-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.drawer-total strong {
  color: var(--color-primary);
  font-size: 20px;
  font-family: var(--font-display);
}

.drawer-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.drawer-modal-mask {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.36);
  display: grid;
  place-items: center;
  z-index: 2;
}

.drawer-modal {
  width: min(360px, 90vw);
  padding: 20px;
}

.drawer-modal h3 {
  margin-bottom: 8px;
}

.drawer-modal p {
  color: var(--color-gray-500);
  margin-bottom: 16px;
}

.drawer-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>

