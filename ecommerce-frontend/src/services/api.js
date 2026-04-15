import axios from 'axios';

export const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 5000
});

export const toFileUrl = (url) => {
  if (!url) {
    return '';
  }
  if (/^(https?:)?\/\//i.test(url) || /^data:/i.test(url) || /^blob:/i.test(url)) {
    return url;
  }
  const base = (api.defaults.baseURL || '').replace(/\/$/, '');
  const path = String(url).startsWith('/') ? url : `/${url}`;
  return `${base}${path}`;
};

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const uploadImage = async (url, file) => {
  const formData = new FormData();
  formData.append('file', file);
  const { data } = await api.post(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  });
  if (data?.url) {
    data.url = toFileUrl(data.url);
  }
  return data;
};

