import { api } from './api';

export const createOrder = async (payload) => {
  const { data } = await api.post('/api/order/orders', payload);
  return data || {};
};

export const queryOrders = async ({ userId, status, limit = 20 } = {}) => {
  const { data } = await api.get('/api/order/orders', {
    params: {
      userId,
      status,
      limit
    }
  });
  return Array.isArray(data) ? data : [];
};

export const getOrderDetail = async (orderNo) => {
  const { data } = await api.get(`/api/order/orders/${orderNo}`);
  return data || {};
};

export const closeOrder = async (orderNo) => {
  const { data } = await api.post(`/api/order/orders/${orderNo}/close`);
  return data || {};
};

export const queryMerchantOrders = async ({ storeId, status, limit = 30 } = {}) => {
  const { data } = await api.get('/api/order/merchant/orders', {
    params: {
      storeId,
      status,
      limit
    }
  });
  return Array.isArray(data) ? data : [];
};

export const queryMerchantOrderStats = async (storeId) => {
  const { data } = await api.get('/api/order/merchant/orders/stats', {
    params: { storeId }
  });
  return data || {};
};

export const shipMerchantOrder = async ({ orderNo, storeId }) => {
  const { data } = await api.post(`/api/order/merchant/orders/${orderNo}/ship`, null, {
    params: { storeId }
  });
  return data || {};
};

export const confirmReceipt = async (orderNo) => {
  const { data } = await api.post(`/api/order/orders/${orderNo}/confirm-receipt`);
  return data || {};
};

export const requestAfterSale = async (orderNo) => {
  const { data } = await api.post(`/api/order/orders/${orderNo}/after-sale`);
  return data || {};
};

export const queryUserOrderNotificationSummary = async (userId) => {
  const { data } = await api.get('/api/order/orders/notification-summary', {
    params: { userId }
  });
  return data || {};
};

