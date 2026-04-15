<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { fetchCartSummary, removeCartItem } from '../services/cart';
import { ensureCurrentUserId } from '../services/auth';
import { createOrder } from '../services/order';
import { formatCurrency } from '../services/home';
import { queryMyCoupons } from '../services/coupon';

const router = useRouter();
const loading = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const summary = ref({ items: [] });
const remark = ref('');
const coupons = ref([]);
const selectedCouponId = ref('');

const selectedItems = computed(() => (summary.value.items || []).filter((item) => item.selected));
const payAmount = computed(() => Number(summary.value.selectedAmount ?? 0));
const selectedQuantity = computed(() => Number(summary.value.selectedQuantity ?? 0));
const selectedCoupon = computed(() => coupons.value.find((item) => String(item.id) === String(selectedCouponId.value)) || null);
const couponDiscount = computed(() => Number(selectedCoupon.value?.discountAmount || 0));
const finalPayAmount = computed(() => Math.max(0, payAmount.value - couponDiscount.value));
const availableCoupons = computed(() => coupons.value.filter((item) => {
  if (item.status !== 'AVAILABLE') {
    return false;
  }
  return payAmount.value >= Number(item.threshold || 0);
}));

const loadSummary = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const [cartData, myCoupons] = await Promise.all([fetchCartSummary(), queryMyCoupons()]);
    summary.value = cartData;
    coupons.value = Array.isArray(myCoupons) ? myCoupons : [];
    if (selectedCoupon.value && !availableCoupons.value.some((item) => item.id === selectedCoupon.value.id)) {
      selectedCouponId.value = '';
    }
  } catch (error) {
    summary.value = { items: [] };
    coupons.value = [];
    errorMessage.value = error?.response?.data?.message || '加载结算信息失败';
  } finally {
    loading.value = false;
  }
};

const submitOrder = async () => {
  if (!selectedItems.value.length) {
    errorMessage.value = '请先选择要结算的商品';
    return;
  }
  submitting.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const userId = await ensureCurrentUserId();
    const payload = {
      userId,
      items: selectedItems.value.map((item) => ({
        productId: item.productId,
        productName: item.title,
        storeId: item.storeId,
        storeName: item.storeName,
        productImageUrl: item.coverImageUrl,
        productDescription: item.description,
        unitPrice: Number(item.price || 0),
        quantity: Number(item.quantity || 1)
      })),
      remark: remark.value || '',
      couponId: selectedCoupon.value?.id || null
    };
    const created = await createOrder(payload);
    await Promise.all(selectedItems.value.map((item) => removeCartItem(item.id)));
    successMessage.value = '订单创建成功，正在跳转订单详情...';
    setTimeout(() => {
      router.push(`/orders/${created.orderNo}`);
    }, 300);
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '创建订单失败';
  } finally {
    submitting.value = false;
  }
};

const goCart = () => router.push('/cart');

onMounted(loadSummary);
</script>

<template>
  <div class="checkout-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <h1>确认订单</h1>
        <button class="btn btn-outline" @click="goCart">返回购物车</button>
      </div>
    </header>

    <main class="container checkout-main">
      <section v-if="loading" class="card state-panel">
        <div class="spinner"></div>
        <p>正在加载结算信息...</p>
      </section>

      <template v-else>
        <section class="card list-panel">
          <h2>已选商品（{{ selectedQuantity }} 件）</h2>
          <p v-if="!selectedItems.length" class="hint">暂无选中商品，请先在购物车勾选后再结算。</p>
          <article v-for="item in selectedItems" :key="item.id" class="checkout-item">
            <img v-if="item.coverImageUrl" :src="item.coverImageUrl" alt="item" class="thumb" />
            <div class="meta">
              <h3>{{ item.title }}</h3>
              <p>{{ item.storeName || '店铺信息未知' }}</p>
            </div>
            <div class="amount">
              <strong>{{ formatCurrency(item.lineAmount) }}</strong>
              <span>x {{ item.quantity }}</span>
            </div>
          </article>
        </section>

        <aside class="card pay-panel">
          <h2>支付信息</h2>
          <div class="row"><span>订单金额</span><strong>{{ formatCurrency(payAmount) }}</strong></div>
          <div class="coupon-block">
            <label>使用优惠券</label>
            <select v-model="selectedCouponId" class="coupon-select">
              <option value="">不使用优惠券</option>
              <option v-for="item in availableCoupons" :key="item.id" :value="String(item.id)">
                {{ item.code }} ｜ 满{{ formatCurrency(item.threshold) }}减{{ formatCurrency(item.discountAmount) }}
              </option>
            </select>
          </div>
          <div class="row"><span>优惠金额</span><strong>-{{ formatCurrency(couponDiscount) }}</strong></div>
          <div class="row"><span>应付金额</span><strong>{{ formatCurrency(finalPayAmount) }}</strong></div>
          <textarea v-model="remark" class="remark" rows="4" placeholder="选填：备注信息"></textarea>
          <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
          <p v-if="successMessage" class="success">{{ successMessage }}</p>
          <button class="btn btn-success btn-block" :disabled="submitting || !selectedItems.length" @click="submitOrder">
            {{ submitting ? '提交中...' : '提交订单' }}
          </button>
        </aside>
      </template>
    </main>
  </div>
</template>

<style scoped>
.checkout-page { min-height: 100vh; background: var(--gradient-hero); }
.checkout-main { display: grid; grid-template-columns: minmax(0,1fr) 340px; gap: 20px; padding: 24px 0 40px; }
.state-panel { min-height: 260px; display: grid; place-items: center; }
.list-panel, .pay-panel { padding: 20px; }
.checkout-item { display: grid; grid-template-columns: 76px 1fr auto; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--color-gray-200); }
.thumb { width: 76px; height: 76px; object-fit: cover; border-radius: 12px; }
.meta p, .hint { color: var(--color-gray-500); }
.amount { text-align: right; }
.amount span { color: var(--color-gray-500); font-size: 13px; }
.row { display: flex; justify-content: space-between; margin-bottom: 14px; }
.coupon-block { margin-bottom: 12px; display: grid; gap: 8px; }
.coupon-select { border: 1px solid var(--color-gray-300); border-radius: 10px; padding: 8px; }
.remark { width: 100%; border: 1px solid var(--color-gray-300); border-radius: 12px; padding: 10px; margin-bottom: 12px; }
.error { color: #b91c1c; }
.success { color: #15803d; }
.btn-block { width: 100%; }
@media (max-width: 980px) { .checkout-main { grid-template-columns: 1fr; } }
</style>

