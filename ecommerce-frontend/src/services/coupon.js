import { api } from './api';

export const queryCouponTemplates = async () => {
  const { data } = await api.get('/api/user/coupons/templates');
  return Array.isArray(data) ? data : [];
};

export const queryMyCoupons = async () => {
  const { data } = await api.get('/api/user/coupons/mine');
  return Array.isArray(data) ? data : [];
};

export const redeemCoupon = async (templateId) => {
  const { data } = await api.post('/api/user/coupons/redeem', { templateId });
  return data || {};
};

