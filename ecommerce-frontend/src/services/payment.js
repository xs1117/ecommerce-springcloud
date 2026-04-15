import { api } from './api';

export const createPayment = async ({ orderNo, amount }) => {
  const { data } = await api.post('/api/payment/create', {
    orderNo,
    amount
  });
  return data || {};
};

export const mockPaymentSuccess = async (paymentNo) => {
  const { data } = await api.post('/api/payment/mock-success', {
    paymentNo
  });
  return data || {};
};

export const queryPayment = async (paymentNo) => {
  const { data } = await api.get('/api/payment/query', {
    params: { paymentNo }
  });
  return data || {};
};

