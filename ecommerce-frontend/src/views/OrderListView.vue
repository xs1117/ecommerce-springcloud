<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ensureCurrentUserId } from '../services/auth';
import { queryOrders } from '../services/order';
import { formatCurrency } from '../services/home';

const router = useRouter();
const loading = ref(false);
const errorMessage = ref('');
const status = ref('');
const orders = ref([]);

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待支付', value: 'WAIT_PAY' },
  { label: '待发货', value: 'TO_SHIP' },
  { label: '待收货', value: 'TO_RECEIVE' },
  { label: '已完成', value: 'FINISHED' },
  { label: '售后中', value: 'AFTER_SALE' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '库存失败', value: 'STOCK_FAILED' }
];

const loadOrders = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const userId = await ensureCurrentUserId();
    localStorage.setItem(`ecommerce:order-seen:user:${userId}`, String(Date.now()));
    if (status.value === 'WAIT_PAY') {
      const [waitPayOrders, createdOrders] = await Promise.all([
        queryOrders({ userId, status: 'WAIT_PAY', limit: 30 }),
        queryOrders({ userId, status: 'CREATED', limit: 30 })
      ]);
      const merged = [...waitPayOrders, ...createdOrders];
      const unique = new Map();
      merged.forEach((item) => {
        unique.set(item.orderNo, item);
      });
      orders.value = Array.from(unique.values());
    } else {
      orders.value = await queryOrders({ userId, status: status.value, limit: 30 });
    }
  } catch (error) {
    orders.value = [];
    errorMessage.value = error?.response?.data?.message || '加载订单失败';
  } finally {
    loading.value = false;
  }
};

const openDetail = (orderNo) => router.push(`/orders/${orderNo}`);
const statusText = (value) => {
  const map = {
    CREATED: '待支付(确认中)',
    STOCK_CONFIRMED: '库存已锁定',
    WAIT_PAY: '待支付',
    TO_SHIP: '待发货',
    TO_RECEIVE: '待收货',
    FINISHED: '已完成',
    AFTER_SALE: '售后中',
    PAID: '已支付',
    CLOSED: '已关闭',
    STOCK_FAILED: '库存不足'
  };
  return map[value] || value;
};

onMounted(loadOrders);
</script>

<template>
  <div class="orders-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <h1>我的订单</h1>
        <div class="actions">
          <select v-model="status" class="status-select" @change="loadOrders">
            <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
          <button class="btn btn-outline" @click="router.push('/home')">返回首页</button>
        </div>
      </div>
    </header>

    <main class="container orders-main">
      <section v-if="loading" class="card state-panel"><div class="spinner"></div><p>加载中...</p></section>
      <section v-else-if="errorMessage" class="card state-panel"><p>{{ errorMessage }}</p></section>
      <section v-else class="card list-panel">
        <article v-for="order in orders" :key="order.orderNo" class="order-card" @click="openDetail(order.orderNo)">
          <div>
            <h3>{{ order.orderNo }}</h3>
            <p>{{ order.createdAt }}</p>
          </div>
          <div class="order-mid">
            <span>{{ statusText(order.status) }}</span>
            <small>共 {{ order.itemCount || 0 }} 件</small>
          </div>
          <strong>{{ formatCurrency(order.payAmount) }}</strong>
        </article>
        <p v-if="!orders.length" class="hint">暂无订单，去购物车下单吧。</p>
      </section>
    </main>
  </div>
</template>

<style scoped>
.orders-page { min-height: 100vh; background: var(--gradient-hero); }
.orders-main { padding: 24px 0 40px; }
.actions { display: flex; gap: 10px; }
.status-select { border: 1px solid var(--color-gray-300); border-radius: 10px; padding: 8px 10px; }
.list-panel { padding: 20px; display: flex; flex-direction: column; gap: 12px; }
.order-card { display: grid; grid-template-columns: 1fr auto auto; gap: 12px; align-items: center; padding: 14px; border: 1px solid var(--color-gray-200); border-radius: 14px; cursor: pointer; }
.order-card:hover { border-color: rgba(225, 29, 72, 0.28); }
.order-mid { display: grid; justify-items: end; color: var(--color-gray-500); }
.state-panel { min-height: 220px; display: grid; place-items: center; }
.hint { color: var(--color-gray-500); text-align: center; padding: 18px 0; }
@media (max-width: 768px) { .order-card { grid-template-columns: 1fr; } }
</style>

