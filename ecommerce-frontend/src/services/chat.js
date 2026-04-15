import { api } from './api';

export const openChatConversation = async ({ storeId, productId = null, sourceType = 'store-detail' } = {}) => {
  const { data } = await api.post('/api/chat/conversations/open', {
    storeId,
    productId,
    sourceType
  });
  return data || null;
};

export const openAfterSaleConversation = async ({ orderNo, storeId = null } = {}) => {
  const { data } = await api.post('/api/chat/conversations/open-after-sale', {
    orderNo,
    storeId
  });
  return data || null;
};

export const fetchChatConversations = async () => {
  const { data } = await api.get('/api/chat/conversations');
  return Array.isArray(data) ? data : [];
};

export const fetchChatMessages = async (conversationId) => {
  if (!conversationId) {
    return [];
  }
  const { data } = await api.get(`/api/chat/conversations/${conversationId}/messages`);
  return Array.isArray(data) ? data : [];
};

export const sendChatMessage = async (conversationId, payload = {}) => {
  const { data } = await api.post(`/api/chat/conversations/${conversationId}/messages`, payload);
  return data || null;
};

export const markChatConversationRead = async (conversationId) => {
  const { data } = await api.post(`/api/chat/conversations/${conversationId}/read`);
  return data || null;
};

export const applyAfterSaleChatAction = async (conversationId, { actionType, remark = '' } = {}) => {
  const { data } = await api.post(`/api/chat/conversations/${conversationId}/after-sale/action`, {
    actionType,
    remark
  });
  return data || null;
};

export const formatChatTime = (value) => {
  if (!value) {
    return '';
  }
  const text = String(value).replace('T', ' ');
  return text.length > 19 ? text.slice(0, 19) : text;
};

void openChatConversation;
void openAfterSaleConversation;
void fetchChatConversations;
void fetchChatMessages;
void sendChatMessage;
void markChatConversationRead;
void applyAfterSaleChatAction;
void formatChatTime;


