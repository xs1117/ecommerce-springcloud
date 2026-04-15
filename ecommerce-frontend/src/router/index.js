import { createRouter, createWebHistory } from 'vue-router';
import LoginView from '../views/LoginView.vue';
import HomeView from '../views/HomeView.vue';
import SearchResultsView from '../views/SearchResultsView.vue';
import ProductDetailView from '../views/ProductDetailView.vue';
import StoreDetailView from '../views/StoreDetailView.vue';
import CartView from '../views/CartView.vue';
import CheckoutView from '../views/CheckoutView.vue';
import OrderListView from '../views/OrderListView.vue';
import OrderDetailView from '../views/OrderDetailView.vue';
import MerchantCenterView from '../views/MerchantCenterView.vue';
import MerchantApplyView from '../views/MerchantApplyView.vue';
import AdminMerchantReviewView from '../views/AdminMerchantReviewView.vue';
import AdminStoreProductsView from '../views/AdminStoreProductsView.vue';
import AccountCenterView from '../views/AccountCenterView.vue';
import ChatView from '../views/ChatView.vue';
import HomeContentDetailView from '../views/HomeContentDetailView.vue';
import PointsMallView from '../views/PointsMallView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView },
    { path: '/home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/points-mall', component: PointsMallView, meta: { requiresAuth: true } },
    { path: '/home/content/:type/:id', component: HomeContentDetailView, meta: { requiresAuth: true }, props: true },
    { path: '/search', component: SearchResultsView, meta: { requiresAuth: true } },
    { path: '/product/:id', component: ProductDetailView, meta: { requiresAuth: true }, props: true },
    { path: '/store/:id', component: StoreDetailView, meta: { requiresAuth: true }, props: true },
    { path: '/cart', component: CartView, meta: { requiresAuth: true } },
    { path: '/checkout', component: CheckoutView, meta: { requiresAuth: true } },
    { path: '/orders', component: OrderListView, meta: { requiresAuth: true } },
    { path: '/orders/:orderNo', component: OrderDetailView, meta: { requiresAuth: true }, props: true },
    { path: '/merchant/apply', component: MerchantApplyView, meta: { requiresAuth: true, roles: ['USER'] } },
    { path: '/merchant/center', component: MerchantCenterView, meta: { requiresAuth: true, roles: ['USER'] } },
    { path: '/admin/merchant/review', component: AdminMerchantReviewView, meta: { requiresAuth: true, roles: ['ADMIN'] } },
    { path: '/admin/merchant/stores/:storeId', component: AdminStoreProductsView, meta: { requiresAuth: true, roles: ['ADMIN'] }, props: true },
    { path: '/account/center', component: AccountCenterView, meta: { requiresAuth: true } },
    { path: '/chat', component: ChatView, meta: { requiresAuth: true } }
  ]
});

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role') || 'USER';
  if (to.meta.requiresAuth && !token) {
    next('/login');
    return;
  }
  const allowedRoles = to.meta.roles;
  if (Array.isArray(allowedRoles) && allowedRoles.length && !allowedRoles.includes(role)) {
    next('/home');
    return;
  }
  if (to.path === '/login' && token) {
    next('/home');
    return;
  }
  next();
});

export default router;

