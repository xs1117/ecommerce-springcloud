import { api, uploadImage } from './api';

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const AI_CHAT_TIMEOUT_MS = 60000;

const shouldRetryOnce = (error) => {
  const status = error?.response?.status;
  if (status === 429 || (status >= 500 && status <= 599)) {
    return true;
  }
  // No response usually means transient network/timeout/proxy hiccup.
  return !error?.response;
};

export const uploadAiImage = async (file) => {
  return uploadImage('/api/merchant/upload/product', file);
};

export const sendAiMessage = async ({ message, imageUrl = '', orderNo = '', confirmationToken = '', confirm = false } = {}) => {
  const payload = {
    message: message || null,
    imageUrl: imageUrl || null,
    orderNo: orderNo || null,
    confirmationToken: confirmationToken || null,
    confirm: !!confirm
  };
  try {
    const { data } = await api.post('/api/ai/chat', payload, { timeout: AI_CHAT_TIMEOUT_MS });
    return data || null;
  } catch (firstError) {
    if (!shouldRetryOnce(firstError)) {
      throw firstError;
    }
    // Silent one-time retry for occasional first-call failures.
    await sleep(250);
    const { data } = await api.post('/api/ai/chat', payload, { timeout: AI_CHAT_TIMEOUT_MS });
    return data || null;
  }
};

void sendAiMessage;
void uploadAiImage;

