<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { fetchCartCount, fetchCartSummary, onCartChanged } from '../services/cart';

const emit = defineEmits(['open']);
const count = ref(0);
const amount = ref(0);
const loading = ref(false);
let offChanged = null;

const token = computed(() => localStorage.getItem('token'));

const load = async () => {
  if (!token.value) {
    count.value = 0;
    amount.value = 0;
    return;
  }
  loading.value = true;
  try {
    const [nextCount, summary] = await Promise.all([
      fetchCartCount(),
      fetchCartSummary()
    ]);
    count.value = nextCount;
    amount.value = Number(summary?.selectedAmount ?? summary?.totalAmount ?? 0);
  } catch {
    count.value = 0;
    amount.value = 0;
  } finally {
    loading.value = false;
  }
};

const openCart = () => emit('open');

onMounted(() => {
  load();
  offChanged = onCartChanged(load);
  window.addEventListener('storage', load);
});

onBeforeUnmount(() => {
  if (offChanged) {
    offChanged();
  }
  window.removeEventListener('storage', load);
});
</script>

<template>
  <button class="cart-floating-button" type="button" @click="openCart" :title="loading ? '购物车加载中' : '打开购物车'">
    <span class="cart-floating-icon">
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="9" cy="20" r="1.8"></circle>
        <circle cx="18" cy="20" r="1.8"></circle>
        <path d="M3 4h2l2.2 10.5a2 2 0 0 0 2 1.5h7.6a2 2 0 0 0 1.95-1.6L20 8H6"></path>
      </svg>
      <span v-if="count > 0" class="cart-floating-badge">{{ count }}</span>
    </span>
    <span class="cart-floating-label">购物车</span>
  </button>
</template>

<style scoped>
.cart-floating-button {
  position: fixed;
  right: 18px;
  top: 50%;
  transform: translateY(-50%);
  z-index: var(--z-fixed);
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 72px;
  padding: 14px 12px;
  border-radius: 18px;
  border: 1px solid rgba(225, 29, 72, 0.18);
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.12);
  color: var(--color-gray-700);
  backdrop-filter: blur(16px);
}

.cart-floating-button:hover {
  transform: translateY(-50%) translateX(-2px);
  box-shadow: 0 14px 30px rgba(225, 29, 72, 0.16);
  color: var(--color-primary);
}

.cart-floating-icon {
  position: relative;
  display: grid;
  place-items: center;
}

.cart-floating-badge {
  position: absolute;
  top: -8px;
  right: -10px;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 9999px;
  background: var(--color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  box-shadow: 0 6px 14px rgba(225, 29, 72, 0.35);
}

.cart-floating-label {
  font-size: 13px;
  font-weight: 700;
  writing-mode: vertical-rl;
  letter-spacing: 0.12em;
}


@media (max-width: 768px) {
  .cart-floating-button {
    right: 10px;
    min-width: 60px;
    padding: 12px 10px;
    border-radius: 16px;
  }

  .cart-floating-label {
    display: none;
  }
}
</style>

