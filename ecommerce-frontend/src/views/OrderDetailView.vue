<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { createPayment, mockPaymentSuccess, queryPayment } from '../services/payment';
import { closeOrder, confirmReceipt, getOrderDetail, requestAfterSale } from '../services/order';
import { openAfterSaleConversation } from '../services/chat';
import { formatCurrency } from '../services/home';
import { toFileUrl } from '../services/api';

const route = useRoute();
const router = useRouter();
const fromMerchantOrders = computed(() => route.query.from === 'merchant-orders');
const loading = ref(false);
const paying = ref(false);
const closing = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const paymentNo = ref('');
const detail = ref({ order: null, items: [] });
const polling = ref(false);
const pollingTip = ref('');
const remainingSeconds = ref(null);

let orderPollTimer = null;
let paymentPollTimer = null;
let countdownTimer = null;
let paymentPollCount = 0;
const PAYMENT_POLL_MAX = 12;
const PAY_TIMEOUT_SECONDS = 10 * 60;

const order = computed(() => detail.value.order || {});
const items = computed(() => (detail.value.items || []).map((item) => {
  const quantity = Number(item.quantity || 0);
  const unitPrice = Number(item.unitPrice || 0);
  return {
    ...item,
    quantity,
    unitPrice,
    lineAmount: unitPrice * quantity,
    displayStoreName: item.storeName || '店铺信息暂不可用'
  };
}));
const itemCount = computed(() => items.value.length);
const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0));
const canPay = computed(() => order.value.status === 'WAIT_PAY');
const canCancel = computed(() => order.value.status === 'WAIT_PAY');
const countdownText = computed(() => {
  if (remainingSeconds.value == null) {
    return '';
  }
  const total = Math.max(0, remainingSeconds.value);
  const minutes = Math.floor(total / 60).toString().padStart(2, '0');
  const seconds = (total % 60).toString().padStart(2, '0');
  return `${minutes}:${seconds}`;
});

const clearOrderPolling = () => {
  if (orderPollTimer) {
    clearInterval(orderPollTimer);
    orderPollTimer = null;
  }
};

const clearPaymentPolling = () => {
  if (paymentPollTimer) {
    clearInterval(paymentPollTimer);
    paymentPollTimer = null;
  }
  paymentPollCount = 0;
  polling.value = false;
  pollingTip.value = '';
};

const clearCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
};

const parseServerDate = (value) => {
  if (!value) {
    return null;
  }
  const parsed = new Date(String(value).replace(' ', 'T'));
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};

const syncCountdown = () => {
  if (order.value.status !== 'WAIT_PAY') {
    remainingSeconds.value = null;
    clearCountdown();
    return;
  }
  const createdAt = parseServerDate(order.value.createdAt);
  if (!createdAt) {
    remainingSeconds.value = null;
    return;
  }
  const deadline = createdAt.getTime() + PAY_TIMEOUT_SECONDS * 1000;
  const delta = Math.ceil((deadline - Date.now()) / 1000);
  remainingSeconds.value = Math.max(0, delta);
  if (delta <= 0) {
    clearCountdown();
    loadDetail(true);
  }
};

const startCountdown = () => {
  clearCountdown();
  syncCountdown();
  if (order.value.status === 'WAIT_PAY') {
    countdownTimer = setInterval(syncCountdown, 1000);
  }
};

const loadDetail = async (silent = false) => {
  if (!silent) {
    loading.value = true;
  }
  if (!silent) {
    errorMessage.value = '';
  }
  try {
    detail.value = await getOrderDetail(route.params.orderNo);
    startCountdown();
    if (detail.value?.order?.status === 'WAIT_PAY' && !paying.value && !polling.value) {
      startOrderAutoRefresh();
    } else if (detail.value?.order?.status !== 'WAIT_PAY') {
      clearOrderPolling();
    }
  } catch (error) {
    detail.value = { order: null, items: [] };
    errorMessage.value = error?.response?.data?.message || '加载订单详情失败';
  } finally {
    if (!silent) {
      loading.value = false;
    }
  }
};

const startOrderAutoRefresh = () => {
  clearOrderPolling();
  orderPollTimer = setInterval(() => {
    if (polling.value) {
      return;
    }
    loadDetail(true);
  }, 5000);
};

const startPaymentPolling = () => {
  clearPaymentPolling();
  polling.value = true;
  pollingTip.value = '支付处理中，正在同步订单状态...';
  paymentPollTimer = setInterval(async () => {
    paymentPollCount += 1;
    try {
      if (paymentNo.value) {
        const latest = await queryPayment(paymentNo.value);
        if (latest?.status === 'SUCCESS') {
          pollingTip.value = '支付成功，订单状态同步中...';
        }
      }
      await loadDetail(true);
      if (detail.value?.order?.status === 'TO_SHIP') {
        successMessage.value = `支付成功，支付单号：${paymentNo.value}`;
        window.dispatchEvent(new CustomEvent('account-summary-updated'));
        clearPaymentPolling();
        clearOrderPolling();
        return;
      }
      if (paymentPollCount >= PAYMENT_POLL_MAX) {
        pollingTip.value = '状态同步稍慢，请稍后手动刷新订单状态';
        clearPaymentPolling();
      }
    } catch {
      if (paymentPollCount >= PAYMENT_POLL_MAX) {
        clearPaymentPolling();
      }
    }
  }, 2000);
};

const payNow = async () => {
  if (!order.value.orderNo) {
    return;
  }
  paying.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const created = await createPayment({ orderNo: order.value.orderNo, amount: Number(order.value.payAmount || 0) });
    paymentNo.value = created.paymentNo;
    await mockPaymentSuccess(created.paymentNo);
    startPaymentPolling();
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '支付失败';
    clearPaymentPolling();
  } finally {
    paying.value = false;
  }
};

const cancelNow = async () => {
  if (!order.value.orderNo || !canCancel.value) {
    return;
  }
  closing.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await closeOrder(order.value.orderNo);
    successMessage.value = '订单已关闭';
    await loadDetail(true);
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '关闭订单失败';
  } finally {
    closing.value = false;
  }
};

const confirmNow = async () => {
  if (!order.value.orderNo || order.value.status !== 'TO_RECEIVE') {
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await confirmReceipt(order.value.orderNo);
    successMessage.value = '确认收货成功';
    await loadDetail(true);
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '确认收货失败';
  } finally {
    loading.value = false;
  }
};

const applyAfterSale = async () => {
  if (!order.value.orderNo || order.value.status !== 'TO_RECEIVE') {
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await requestAfterSale(order.value.orderNo);
    successMessage.value = '已提交售后申请';
    await loadDetail(true);
    const firstStoreId = Number(items.value[0]?.storeId || 0) || null;
    const conversation = await openAfterSaleConversation({
      orderNo: order.value.orderNo,
      storeId: firstStoreId
    });
    if (conversation?.id) {
      router.push(`/chat?conversationId=${conversation.id}`);
    }
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '售后申请失败';
  } finally {
    loading.value = false;
  }
};

const openAfterSaleChat = async () => {
  if (!order.value.orderNo) {
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  try {
    const firstStoreId = Number(items.value[0]?.storeId || 0) || null;
    const conversation = await openAfterSaleConversation({
      orderNo: order.value.orderNo,
      storeId: firstStoreId
    });
    if (conversation?.id) {
      router.push(`/chat?conversationId=${conversation.id}`);
    }
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '打开售后会话失败';
  } finally {
    loading.value = false;
  }
};

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

const goBackFromDetail = () => {
  if (fromMerchantOrders.value) {
    router.push('/merchant/center?tab=orders');
    return;
  }
  router.push('/orders');
};

onMounted(async () => {
  await loadDetail();
  if (detail.value?.order?.status === 'WAIT_PAY') {
    startOrderAutoRefresh();
  }
});

onBeforeUnmount(() => {
  clearOrderPolling();
  clearPaymentPolling();
  clearCountdown();
});
</script>

<template>
  <div class="order-detail-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <h1>订单详情</h1>
        <div class="actions">
          <button class="btn btn-outline" @click="goBackFromDetail">{{ fromMerchantOrders ? '返回订单管理' : '返回订单列表' }}</button>
          <button class="btn btn-outline" @click="router.push('/home')">返回首页</button>
        </div>
      </div>
    </header>

    <main class="container detail-main">
      <section v-if="loading" class="card state-panel"><div class="spinner"></div><p>加载中...</p></section>
      <template v-else>
        <section class="card info-panel" v-if="order.orderNo">
          <h2>{{ order.orderNo }}</h2>
          <div class="row"><span>订单状态</span><strong>{{ statusText(order.status) }}</strong></div>
          <div class="row"><span>应付金额</span><strong>{{ formatCurrency(order.payAmount) }}</strong></div>
          <div class="row"><span>商品种类</span><span>{{ itemCount }} 种</span></div>
          <div class="row"><span>商品件数</span><span>{{ totalQuantity }} 件</span></div>
          <div class="row"><span>创建时间</span><span>{{ order.createdAt }}</span></div>
          <div class="row" v-if="countdownText"><span>支付倒计时</span><strong class="countdown">{{ countdownText }}</strong></div>
          <div class="pay-actions" v-if="canPay">
            <button class="btn btn-success" :disabled="paying || closing" @click="payNow">{{ paying ? '支付中...' : '立即支付' }}</button>
            <button class="btn btn-outline" v-if="canCancel" :disabled="paying || closing || polling" @click="cancelNow">{{ closing ? '关闭中...' : '取消支付' }}</button>
          </div>
          <div class="pay-actions" v-if="order.status === 'TO_RECEIVE'">
            <button class="btn btn-success" :disabled="loading" @click="confirmNow">确认收货</button>
            <button class="btn btn-outline" :disabled="loading" @click="applyAfterSale">申请售后</button>
          </div>
          <div class="pay-actions" v-if="order.status === 'AFTER_SALE'">
            <button class="btn btn-primary" :disabled="loading" @click="openAfterSaleChat">售后沟通</button>
          </div>
          <p v-if="paymentNo" class="hint">当前支付单号：{{ paymentNo }}</p>
          <p v-if="pollingTip" class="hint">{{ pollingTip }}</p>
          <p v-if="successMessage" class="success">{{ successMessage }}</p>
          <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        </section>

        <section class="card items-panel" v-if="items.length">
          <h3>商品清单</h3>
          <article v-for="item in items" :key="item.id" class="item-row">
            <img v-if="item.productImageUrl" :src="toFileUrl(item.productImageUrl)" class="item-image" alt="item" />
            <div v-else class="item-image item-image-placeholder"></div>
            <div class="item-main">
              <strong>{{ item.productName }}</strong>
              <p>店铺：{{ item.displayStoreName }}</p>
              <p v-if="item.productDescription">说明：{{ item.productDescription }}</p>
              <p>商品ID：{{ item.productId }}</p>
            </div>
            <div class="price">
              <p>{{ formatCurrency(item.unitPrice) }} x {{ item.quantity }}</p>
              <strong>{{ formatCurrency(item.lineAmount) }}</strong>
            </div>
          </article>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.order-detail-page { min-height: 100vh; background: var(--gradient-hero); }
.detail-main { display: grid; grid-template-columns: 1fr; gap: 16px; padding: 24px 0 40px; }
.actions { display: flex; gap: 10px; }
.info-panel, .items-panel { padding: 20px; }
.row { display: flex; justify-content: space-between; margin-top: 10px; }
.pay-actions { margin-top: 16px; display: flex; gap: 10px; }
.item-row { display: grid; grid-template-columns: 64px 1fr auto; gap: 12px; align-items: center; border-bottom: 1px solid var(--color-gray-200); padding: 12px 0; }
.item-image { width: 64px; height: 64px; border-radius: 10px; object-fit: cover; border: 1px solid var(--color-gray-200); }
.item-image-placeholder { background: var(--color-gray-100); }
.item-main p { margin: 0; }
.item-row p, .hint { color: var(--color-gray-500); }
.price p { margin: 0; color: var(--color-gray-500); }
.success { color: #15803d; margin-top: 8px; }
.error { color: #b91c1c; margin-top: 8px; }
.state-panel { min-height: 220px; display: grid; place-items: center; }
.countdown { color: #b91c1c; }
@media (max-width: 768px) { .actions { flex-wrap: wrap; } .item-row { grid-template-columns: 1fr; } }
</style>
