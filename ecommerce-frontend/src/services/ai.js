import { api } from './api';

export const sendAiMessage = async ({ message, orderNo = '', confirmationToken = '', confirm = false } = {}) => {
  const payload = {
    message,
    orderNo: orderNo || null,
    confirmationToken: confirmationToken || null,
    confirm: !!confirm
  };
  const { data } = await api.post('/api/ai/chat', payload);
  return data || null;
};

void sendAiMessage;

