<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { queryCouponTemplates, queryMyCoupons, redeemCoupon } from '../services/coupon';
import { formatCurrency } from '../services/home';

const router = useRouter();
const loading = ref(false);
const redeemingTemplateId = ref(0);
const errorMessage = ref('');
const successMessage = ref('');
const templates = ref([]);
const myCoupons = ref([]);
const points = ref(Number(localStorage.getItem('points') || 0));

const availableCoupons = computed(() => myCoupons.value.filter((item) => item.status === 'AVAILABLE'));

const refreshData = async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    const [tplData, myData] = await Promise.all([queryCouponTemplates(), queryMyCoupons()]);
    templates.value = tplData;
    myCoupons.value = myData;
    points.value = Number(localStorage.getItem('points') || points.value || 0);
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '加载积分商城失败';
  } finally {
    loading.value = false;
  }
};

const doRedeem = async (item) => {
  if (!item?.id) {
    return;
  }
  redeemingTemplateId.value = item.id;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const result = await redeemCoupon(item.id);
    const nextPoints = Number(result?.member?.points ?? points.value);
    points.value = nextPoints;
    localStorage.setItem('points', String(nextPoints));
    window.dispatchEvent(new CustomEvent('account-summary-updated', {
      detail: {
        points: nextPoints,
        memberLevel: result?.member?.memberLevel || ''
      }
    }));
    await refreshData();
    successMessage.value = '兑换成功，优惠券已发放到我的券';
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '兑换失败';
  } finally {
    redeemingTemplateId.value = 0;
  }
};

onMounted(refreshData);
</script>

<template>
  <div class="points-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <h1>积分商城</h1>
        <div class="actions">
          <span class="points">当前积分：{{ points }}</span>
          <button class="btn btn-outline" @click="router.push('/home')">返回首页</button>
        </div>
      </div>
    </header>

    <main class="container points-main">
      <section v-if="loading" class="card state-panel">
        <div class="spinner"></div>
        <p>加载中...</p>
      </section>
      <template v-else>
        <section class="card panel">
          <div class="section-head">
            <h2>可兑换优惠券</h2>
            <button class="btn btn-outline" @click="refreshData">刷新</button>
          </div>
          <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
          <p v-if="successMessage" class="success">{{ successMessage }}</p>
          <div v-if="templates.length" class="grid">
            <article v-for="item in templates" :key="item.id" class="coupon-card">
              <h3>{{ item.name }}</h3>
              <p>{{ item.description || '积分兑换后可在结算页使用' }}</p>
              <div class="meta">
                <span>门槛 {{ formatCurrency(item.threshold) }}</span>
                <strong>减 {{ formatCurrency(item.discountAmount) }}</strong>
              </div>
              <div class="meta">
                <span>消耗积分 {{ item.pointsCost }}</span>
              </div>
              <button class="btn btn-primary btn-block" :disabled="redeemingTemplateId === item.id || points < Number(item.pointsCost || 0)" @click="doRedeem(item)">
                {{ points < Number(item.pointsCost || 0) ? '积分不足' : (redeemingTemplateId === item.id ? '兑换中...' : '立即兑换') }}
              </button>
            </article>
          </div>
          <p v-else class="hint">暂无可兑换优惠券</p>
        </section>

        <section class="card panel">
          <h2>我的可用券（{{ availableCoupons.length }}）</h2>
          <article v-for="item in availableCoupons" :key="item.id" class="my-coupon-item">
            <div>
              <strong>{{ item.code }}</strong>
              <p>满 {{ formatCurrency(item.threshold) }} 减 {{ formatCurrency(item.discountAmount) }}</p>
            </div>
            <span class="badge badge-success">可用</span>
          </article>
          <p v-if="!availableCoupons.length" class="hint">暂无可用券，先去兑换一张吧。</p>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.points-page { min-height: 100vh; background: var(--gradient-hero); }
.points-main { display: grid; gap: 20px; padding: 24px 0 40px; }
.panel, .state-panel { padding: 20px; }
.actions { display: flex; gap: 10px; align-items: center; }
.points { color: var(--color-gray-600); font-weight: 600; }
.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.coupon-card { border: 1px solid var(--color-gray-200); border-radius: 14px; padding: 14px; }
.meta { display: flex; justify-content: space-between; margin-bottom: 8px; color: var(--color-gray-600); }
.my-coupon-item { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--color-gray-200); padding: 10px 0; }
.btn-block { width: 100%; }
.error { color: #b91c1c; }
.success { color: #15803d; }
.hint { color: var(--color-gray-500); }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }
</style>

