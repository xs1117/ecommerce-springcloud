import { api } from './api';

const CART_CHANGED_EVENT = 'ecommerce:cart-changed';

const notifyCartChanged = () => {
  window.dispatchEvent(new CustomEvent(CART_CHANGED_EVENT));
};

export const onCartChanged = (handler) => {
  window.addEventListener(CART_CHANGED_EVENT, handler);
  return () => window.removeEventListener(CART_CHANGED_EVENT, handler);
};

export const fetchCartSummary = async () => {
  const { data } = await api.get('/api/cart/summary');
  return data || { items: [] };
};

export const fetchCartItems = async () => {
  const { data } = await api.get('/api/cart/items');
  return Array.isArray(data) ? data : [];
};

export const fetchCartCount = async () => {
  const { data } = await api.get('/api/cart/count');
  return Number(data?.count || 0);
};

export const fetchCartBehaviors = async (limit = 10) => {
  const { data } = await api.get('/api/cart/behaviors', { params: { limit } });
  return Array.isArray(data) ? data : [];
};

export const addCartItem = async (payload) => {
  const { data } = await api.post('/api/cart/items', payload);
  notifyCartChanged();
  return data;
};

export const updateCartItem = async (itemId, payload) => {
  const { data } = await api.patch(`/api/cart/items/${itemId}`, payload);
  notifyCartChanged();
  return data;
};

export const removeCartItem = async (itemId) => {
  const { data } = await api.delete(`/api/cart/items/${itemId}`);
  notifyCartChanged();
  return data;
};

export const clearCart = async () => {
  const { data } = await api.delete('/api/cart/items');
  notifyCartChanged();
  return data;
};

export const recordCartBehavior = async (payload) => {
  const { data } = await api.post('/api/cart/behavior', payload);
  return data;
};

export { notifyCartChanged };

