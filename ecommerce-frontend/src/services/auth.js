import { api } from './api';

export const getCurrentUserId = () => {
  const value = Number(localStorage.getItem('userId') || 0);
  return value > 0 ? value : null;
};

export const setCurrentUserId = (payload = {}) => {
  const raw = payload.userId ?? payload.id;
  const value = Number(raw || 0);
  if (value > 0) {
    localStorage.setItem('userId', String(value));
  }
  return value > 0 ? value : null;
};

export const ensureCurrentUserId = async () => {
  const local = getCurrentUserId();
  if (local) {
    return local;
  }
  const { data } = await api.get('/api/user/account/me');
  const value = setCurrentUserId(data || {});
  if (!value) {
    throw new Error('无法获取当前用户ID');
  }
  return value;
};

export const clearAuthStorage = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  localStorage.removeItem('nickname');
  localStorage.removeItem('role');
  localStorage.removeItem('points');
  localStorage.removeItem('memberLevel');
  localStorage.removeItem('userId');
};

