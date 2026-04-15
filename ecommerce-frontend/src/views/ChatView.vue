<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchProductDetail, fetchStoreDetail } from '../services/home';
import {
  applyAfterSaleChatAction,
  fetchChatConversations,
  fetchChatMessages,
  formatChatTime,
  markChatConversationRead,
  openAfterSaleConversation,
  openChatConversation,
  sendChatMessage
} from '../services/chat';
import { toFileUrl } from '../services/api';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const errorMessage = ref('');
const conversations = ref([]);
const messages = ref([]);
const currentConversation = ref(null);
const draftText = ref('');
const sending = ref(false);
const opening = ref(false);
const draftProduct = ref(null);
const draftStore = ref(null);

const storeId = computed(() => Number(route.query.storeId || 0));
const productId = computed(() => Number(route.query.productId || 0));
const conversationId = computed(() => Number(route.query.conversationId || 0));
const afterSaleOrderNo = computed(() => String(route.query.afterSaleOrderNo || '').trim());
const afterSaleStoreId = computed(() => Number(route.query.afterSaleStoreId || 0));
const sourceType = computed(() => String(route.query.source || (productId.value > 0 ? 'product-detail' : 'store-detail')));
const isMerchantMode = computed(() => String(route.query.mode || '') === 'merchant');

const selectedConversationId = computed(() => currentConversation.value?.id || null);
const isAfterSaleConversation = computed(() => currentConversation.value?.bizType === 'AFTER_SALE');
const actionRunning = ref(false);
const remarkDialogVisible = ref(false);
const remarkDialogAction = ref('');
const remarkInput = ref('');
const remarkDialogTitle = ref('');

const afterSaleTerminalStatuses = new Set(['ADMIN_FORCE_CANCELED', 'ADMIN_FORCE_REFUNDED']);
const afterSaleStatusRank = {
  PENDING: 1,
  RETURN_REQUESTED: 2,
  EXCHANGE_REQUESTED: 2,
  MERCHANT_APPROVED_RETURN: 3,
  MERCHANT_APPROVED_EXCHANGE: 3,
  MERCHANT_REJECTED: 3,
  ADMIN_REQUESTED: 4,
  ADMIN_FORCE_CANCELED: 5,
  ADMIN_FORCE_REFUNDED: 5
};

const afterSaleStatusMap = {
  PENDING: '待处理',
  RETURN_REQUESTED: '用户申请退货',
  EXCHANGE_REQUESTED: '用户申请换货',
  MERCHANT_APPROVED_RETURN: '商家同意退货',
  MERCHANT_APPROVED_EXCHANGE: '商家同意换货',
  MERCHANT_REJECTED: '商家已拒绝',
  ADMIN_REQUESTED: '等待管理员介入',
  ADMIN_FORCE_CANCELED: '管理员强制取消售后',
  ADMIN_FORCE_REFUNDED: '管理员强制退款'
};

const actionButtonMap = {
  APPLY_RETURN: '申请退货',
  APPLY_EXCHANGE: '申请换货',
  REQUEST_ADMIN_INTERVENTION: '申请管理员介入',
  MERCHANT_APPROVE_RETURN: '同意退货',
  MERCHANT_APPROVE_EXCHANGE: '同意换货',
  MERCHANT_REJECT: '拒绝申请',
  ADMIN_JOIN: '进入会话',
  ADMIN_FORCE_CANCEL: '强制取消售后',
  ADMIN_FORCE_REFUND: '强制退款'
};

const needRemarkActions = new Set([
  'REQUEST_ADMIN_INTERVENTION',
  'MERCHANT_REJECT',
  'ADMIN_FORCE_CANCEL',
  'ADMIN_FORCE_REFUND'
]);

const actionButtonClass = (action) => {
  if (action === 'ADMIN_FORCE_CANCEL' || action === 'MERCHANT_REJECT') {
    return 'btn btn-outline';
  }
  if (action === 'ADMIN_FORCE_REFUND') {
    return 'btn btn-danger';
  }
  return 'btn btn-primary';
};

const afterSaleStatusText = computed(() => {
  const key = String(currentConversation.value?.afterSaleStatus || '').trim();
  return afterSaleStatusMap[key] || (key || '待处理');
});

const afterSaleTimelineNodes = computed(() => {
  if (!isAfterSaleConversation.value) {
    return [];
  }
  const status = String(currentConversation.value?.afterSaleStatus || 'PENDING').trim();
  const rank = Number(afterSaleStatusRank[status] || 1);
  const isApplyNodeCurrent = status === 'RETURN_REQUESTED' || status === 'EXCHANGE_REQUESTED';
  const isMerchantNodeCurrent = [
    'MERCHANT_APPROVED_RETURN',
    'MERCHANT_APPROVED_EXCHANGE',
    'MERCHANT_REJECTED'
  ].includes(status);

  return [
    {
      key: 'start',
      title: '售后会话创建',
      detail: '用户与商家开始售后沟通',
      state: rank > 1 ? 'done' : 'current'
    },
    {
      key: 'apply',
      title: '用户提出诉求',
      detail: status === 'EXCHANGE_REQUESTED' ? '当前诉求：换货' : '当前诉求：退货',
      state: rank < 2 ? 'pending' : (isApplyNodeCurrent ? 'current' : 'done')
    },
    {
      key: 'merchant',
      title: '商家处理',
      detail: status === 'MERCHANT_APPROVED_RETURN'
        ? '商家同意退货'
        : (status === 'MERCHANT_APPROVED_EXCHANGE'
          ? '商家同意换货'
          : (status === 'MERCHANT_REJECTED' ? '商家拒绝申请' : '待商家处理')),
      state: rank < 3 ? 'pending' : (isMerchantNodeCurrent ? 'current' : 'done')
    },
    {
      key: 'admin',
      title: '管理员介入',
      detail: status === 'ADMIN_REQUESTED'
        ? ((currentConversation.value?.adminJoined ? `${currentConversation.value?.adminNickname || '管理员'}已进入会话` : '等待管理员进入会话'))
        : (rank > 4 ? `${currentConversation.value?.adminNickname || '管理员'}已处理` : '未申请管理员介入'),
      state: rank < 4 ? 'pending' : (status === 'ADMIN_REQUESTED' ? 'current' : 'done')
    },
    {
      key: 'done',
      title: '售后结论',
      detail: status === 'ADMIN_FORCE_REFUNDED'
        ? '管理员强制退款完成'
        : (status === 'ADMIN_FORCE_CANCELED' ? '管理员强制取消售后' : '处理中'),
      state: rank >= 5 ? 'current' : 'pending'
    }
  ];
});

const afterSaleActionState = (actionType) => {
  const status = String(currentConversation.value?.afterSaleStatus || '').trim();
  const adminJoined = Boolean(currentConversation.value?.adminJoined);
  const isTerminal = afterSaleTerminalStatuses.has(status);

  if (!isAfterSaleConversation.value) {
    return { disabled: true, reason: '仅售后会话支持该操作' };
  }
  if (isTerminal) {
    return { disabled: true, reason: '售后已结束，当前动作不可执行' };
  }

  switch (actionType) {
    case 'APPLY_RETURN':
    case 'APPLY_EXCHANGE': {
      const can = status === 'PENDING' || status === 'MERCHANT_REJECTED';
      return can ? { disabled: false, reason: '' } : { disabled: true, reason: '当前状态不可重复发起新的售后诉求' };
    }
    case 'REQUEST_ADMIN_INTERVENTION': {
      const can = status !== 'PENDING' && !adminJoined && status !== 'ADMIN_REQUESTED';
      return can ? { disabled: false, reason: '' } : { disabled: true, reason: '当前无需重复申请管理员介入' };
    }
    case 'MERCHANT_APPROVE_RETURN': {
      const can = status === 'RETURN_REQUESTED';
      return can ? { disabled: false, reason: '' } : { disabled: true, reason: '仅当用户申请退货时可执行' };
    }
    case 'MERCHANT_APPROVE_EXCHANGE': {
      const can = status === 'EXCHANGE_REQUESTED';
      return can ? { disabled: false, reason: '' } : { disabled: true, reason: '仅当用户申请换货时可执行' };
    }
    case 'MERCHANT_REJECT': {
      const can = status === 'RETURN_REQUESTED' || status === 'EXCHANGE_REQUESTED';
      return can ? { disabled: false, reason: '' } : { disabled: true, reason: '当前没有可拒绝的用户申请' };
    }
    case 'ADMIN_JOIN': {
      const can = status === 'ADMIN_REQUESTED' && !adminJoined;
      return can ? { disabled: false, reason: '' } : { disabled: true, reason: '仅在待管理员介入时可进入会话' };
    }
    case 'ADMIN_FORCE_CANCEL':
    case 'ADMIN_FORCE_REFUND': {
      const can = adminJoined && status === 'ADMIN_REQUESTED';
      return can ? { disabled: false, reason: '' } : { disabled: true, reason: '管理员进入会话后方可强制处理' };
    }
    default:
      return { disabled: false, reason: '' };
  }
};

const createClientMsgId = () => {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') {
    return window.crypto.randomUUID();
  }
  return `msg-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const normalizeConversation = (item = {}) => ({
  ...item,
  storeImageUrl: toFileUrl(item.storeImageUrl),
  productImageUrl: toFileUrl(item.productImageUrl),
  lastMessageAtText: formatChatTime(item.lastMessageAt),
  unreadCount: Number(item.mineUnreadCount || 0),
  availableActions: Array.isArray(item.availableActions) ? item.availableActions : [],
  counterpartName: item.counterpartName || item.storeTitle || item.buyerNickname || '会话'
});

const normalizeMessage = (item = {}) => ({
  ...item,
  createdAtText: formatChatTime(item.createdAt),
  payload: item.payload || {},
  productImageUrl: toFileUrl(item.payload?.productImageUrl || item.payload?.imageUrl || ''),
  storeImageUrl: toFileUrl(item.payload?.storeImageUrl || '')
});

const loadDraftContext = async () => {
  draftProduct.value = null;
  draftStore.value = null;
  if (storeId.value > 0) {
    try {
      const store = await fetchStoreDetail(storeId.value);
      draftStore.value = store?.store || null;
    } catch {
      draftStore.value = null;
    }
  }
  if (productId.value > 0) {
    try {
      const product = await fetchProductDetail(productId.value);
      draftProduct.value = product?.product ? {
        ...product.product,
        store: product.store || null
      } : null;
      if (!draftStore.value && product?.store) {
        draftStore.value = product.store;
      }
    } catch {
      draftProduct.value = null;
    }
  }
};

const loadConversations = async () => {
  conversations.value = (await fetchChatConversations()).map(normalizeConversation);
};

const selectConversationById = async (targetConversationId) => {
  if (!targetConversationId) {
    return false;
  }
  const target = conversations.value.find((item) => Number(item.id) === Number(targetConversationId));
  if (!target) {
    return false;
  }
  currentConversation.value = target;
  await loadMessages(target.id);
  return true;
};

const loadMessages = async (conversationId) => {
  if (!conversationId) {
    messages.value = [];
    return;
  }
  messages.value = (await fetchChatMessages(conversationId)).map(normalizeMessage);
  await markChatConversationRead(conversationId).catch(() => {});
  await loadConversations().catch(() => {});
};

const selectConversation = async (conversation) => {
  if (!conversation?.id) {
    return;
  }
  currentConversation.value = conversation;
  await loadMessages(conversation.id);
};

const startConversation = async () => {
  if (!storeId.value) {
    return;
  }
  opening.value = true;
  errorMessage.value = '';
  try {
    const conversation = await openChatConversation({
      storeId: storeId.value,
      productId: productId.value > 0 ? productId.value : null,
      sourceType: sourceType.value
    });
    currentConversation.value = normalizeConversation(conversation);
    await loadConversations();
    await loadMessages(conversation.id);
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '打开会话失败';
  } finally {
    opening.value = false;
  }
};

const startAfterSaleConversation = async () => {
  if (!afterSaleOrderNo.value) {
    return;
  }
  opening.value = true;
  errorMessage.value = '';
  try {
    const conversation = await openAfterSaleConversation({
      orderNo: afterSaleOrderNo.value,
      storeId: afterSaleStoreId.value > 0 ? afterSaleStoreId.value : null
    });
    currentConversation.value = normalizeConversation(conversation);
    await loadConversations();
    await loadMessages(conversation.id);
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '打开售后会话失败';
  } finally {
    opening.value = false;
  }
};

const executeAfterSaleAction = async (actionType) => {
  if (!selectedConversationId.value || !actionType) {
    return;
  }
  const state = afterSaleActionState(actionType);
  if (state.disabled) {
    errorMessage.value = state.reason || '当前状态下无法执行该操作';
    return;
  }
  if (needRemarkActions.has(actionType)) {
    remarkDialogAction.value = actionType;
    remarkDialogTitle.value = `${actionButtonMap[actionType] || actionType}（可填写处理备注）`;
    remarkInput.value = '';
    remarkDialogVisible.value = true;
    return;
  }
  await runAfterSaleAction(actionType, '');
};

const closeRemarkDialog = () => {
  remarkDialogVisible.value = false;
  remarkDialogAction.value = '';
  remarkInput.value = '';
  remarkDialogTitle.value = '';
};

const confirmRemarkDialog = async () => {
  const action = remarkDialogAction.value;
  if (!action) {
    closeRemarkDialog();
    return;
  }
  const remark = String(remarkInput.value || '').trim();
  closeRemarkDialog();
  await runAfterSaleAction(action, remark);
};

const runAfterSaleAction = async (actionType, remark = '') => {
  actionRunning.value = true;
  errorMessage.value = '';
  try {
    const updated = await applyAfterSaleChatAction(selectedConversationId.value, { actionType, remark });
    currentConversation.value = normalizeConversation(updated || currentConversation.value || {});
    await loadConversations();
    await loadMessages(selectedConversationId.value);
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '售后操作失败';
  } finally {
    actionRunning.value = false;
  }
};

const sendText = async () => {
  const content = String(draftText.value || '').trim();
  if (!content || !selectedConversationId.value) {
    return;
  }
  sending.value = true;
  try {
    await sendChatMessage(selectedConversationId.value, {
      clientMsgId: createClientMsgId(),
      messageType: 'TEXT',
      content,
      payloadJson: ''
    });
    draftText.value = '';
    await loadMessages(selectedConversationId.value);
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '发送消息失败';
  } finally {
    sending.value = false;
  }
};

const sendProductCard = async () => {
  if (!draftProduct.value || !selectedConversationId.value) {
    return;
  }
  sending.value = true;
  try {
    const payload = {
      productId: draftProduct.value.id,
      productTitle: draftProduct.value.title,
      productImageUrl: draftProduct.value.imageUrl || draftProduct.value.coverImageUrl || '',
      productDescription: draftProduct.value.description || '',
      productPrice: draftProduct.value.price,
      storeId: draftStore.value?.id || draftProduct.value.storeId,
      storeTitle: draftStore.value?.title || draftProduct.value.store?.title || '',
      storeImageUrl: draftStore.value?.storeImageUrl || draftProduct.value.store?.storeImageUrl || ''
    };
    await sendChatMessage(selectedConversationId.value, {
      clientMsgId: createClientMsgId(),
      messageType: 'PRODUCT_CARD',
      content: draftProduct.value.title,
      payloadJson: JSON.stringify(payload)
    });
    await loadMessages(selectedConversationId.value);
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '发送商品详情失败';
  } finally {
    sending.value = false;
  }
};

const goBack = () => {
  router.back();
};

const openProduct = () => {
  if (draftProduct.value?.id) {
    router.push(`/product/${draftProduct.value.id}`);
  }
};

const openStore = () => {
  const id = draftStore.value?.id || storeId.value;
  if (id) {
    router.push(`/store/${id}`);
  }
};

onMounted(async () => {
  loading.value = true;
  errorMessage.value = '';
  try {
    await loadDraftContext();
    if (afterSaleOrderNo.value) {
      await startAfterSaleConversation();
    } else if (storeId.value > 0) {
      await startConversation();
    } else {
      await loadConversations();
      const matched = await selectConversationById(conversationId.value);
      if (!matched && conversations.value.length) {
        currentConversation.value = conversations.value[0];
        await loadMessages(currentConversation.value.id);
      }
    }
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '加载聊天页失败';
  } finally {
    loading.value = false;
  }
});

watch(conversationId, async (targetId) => {
  if (!targetId || !conversations.value.length) {
    return;
  }
  await selectConversationById(targetId);
});
</script>

<template>
  <div class="chat-page">
    <header class="navbar">
      <div class="container navbar-inner">
        <button class="btn btn-outline" @click="goBack">返回</button>
        <div class="navbar-title">
          <strong>客服会话</strong>
          <span>{{ isMerchantMode ? '商家中心回复买家消息' : '与店铺客服沟通' }}</span>
        </div>
        <div class="nav-actions">
          <button v-if="draftStore" class="btn btn-outline" @click="openStore">查看店铺</button>
          <button v-if="draftProduct" class="btn btn-outline" @click="openProduct">查看商品</button>
        </div>
      </div>
    </header>

    <main class="container main-content">
      <section v-if="loading || opening" class="card loading-state">
        <div class="spinner"></div>
        <p>{{ opening ? '正在打开会话...' : '加载聊天中...' }}</p>
      </section>

      <section v-else-if="errorMessage" class="card error-state">
        <strong>聊天加载失败</strong>
        <p>{{ errorMessage }}</p>
      </section>

      <section v-else class="chat-layout">
        <aside class="card conversation-panel">
          <div class="panel-header">
            <h2>会话列表</h2>
          </div>
          <div v-if="conversations.length" class="conversation-list">
            <button
              v-for="item in conversations"
              :key="item.id"
              class="conversation-item"
              :class="{ active: item.id === selectedConversationId }"
              @click="selectConversation(item)"
            >
              <span class="conversation-avatar">
                <img v-if="item.storeImageUrl" :src="item.storeImageUrl" alt="store" />
                <span v-else>{{ (item.counterpartName || '会话').slice(0, 1) }}</span>
              </span>
              <span class="conversation-body">
                <span class="conversation-top">
                  <strong>{{ item.counterpartName }}</strong>
                  <span>{{ item.lastMessageAtText }}</span>
                </span>
                <p class="conversation-summary">{{ item.lastMessageText || '暂无消息' }}</p>
                <small v-if="item.unreadCount" class="unread-badge">{{ item.unreadCount }}</small>
              </span>
            </button>
          </div>
          <p v-else class="empty-text">暂无会话，去商品详情或店铺详情点击客服按钮即可发起。</p>
        </aside>

        <section class="card message-panel">
          <div v-if="currentConversation" class="message-header">
            <div>
              <span class="section-label">当前会话</span>
              <h2 class="section-title">{{ currentConversation.counterpartName }}</h2>
              <p class="message-subtitle">{{ currentConversation.storeTitle }}</p>
              <p v-if="isAfterSaleConversation" class="message-subtitle">售后状态：{{ afterSaleStatusText }}</p>
            </div>
            <div class="message-actions">
              <button v-if="draftProduct" class="btn btn-primary" :disabled="sending" @click="sendProductCard">
                {{ sending ? '发送中...' : '发送商品详情' }}
              </button>
            </div>
          </div>

          <div v-if="isAfterSaleConversation && currentConversation?.availableActions?.length" class="after-sale-actions">
            <button
              v-for="action in currentConversation.availableActions"
              :key="action"
              :class="actionButtonClass(action)"
              :disabled="actionRunning || afterSaleActionState(action).disabled"
              :title="afterSaleActionState(action).reason"
              @click="executeAfterSaleAction(action)"
            >
              {{ actionRunning ? '处理中...' : (actionButtonMap[action] || action) }}
            </button>
          </div>

          <div v-if="isAfterSaleConversation && afterSaleTimelineNodes.length" class="after-sale-timeline">
            <article
              v-for="(node, index) in afterSaleTimelineNodes"
              :key="node.key"
              class="timeline-node"
              :class="`timeline-${node.state}`"
            >
              <span class="timeline-dot">{{ index + 1 }}</span>
              <div class="timeline-content">
                <strong>{{ node.title }}</strong>
                <p>{{ node.detail }}</p>
              </div>
            </article>
          </div>

          <div v-if="draftProduct" class="card product-context">
            <img v-if="draftProduct.imageUrl || draftProduct.coverImageUrl" :src="draftProduct.imageUrl || draftProduct.coverImageUrl" alt="product" />
            <div>
              <strong>{{ draftProduct.title }}</strong>
              <p>{{ draftProduct.description || '商品详情会作为上下文发送给商家。' }}</p>
            </div>
          </div>

          <div class="message-list">
            <div v-if="messages.length" class="message-stream">
              <article v-for="message in messages" :key="message.id" class="message-item" :class="message.mine ? 'mine' : 'other'">
                <div class="message-meta">
                  <strong>{{ message.senderNickname }}</strong>
                  <span>{{ message.createdAtText }}</span>
                </div>
                <div class="message-bubble">
                  <template v-if="message.messageType === 'PRODUCT_CARD'">
                    <div class="message-card">
                      <img v-if="message.payload?.productImageUrl || message.payload?.imageUrl" :src="message.productImageUrl || toFileUrl(message.payload?.productImageUrl || message.payload?.imageUrl || '')" alt="product" />
                      <div>
                        <strong>{{ message.payload?.productTitle || message.content }}</strong>
                        <p>{{ message.payload?.productDescription || '' }}</p>
                        <small v-if="message.payload?.productPrice !== undefined">￥{{ message.payload?.productPrice }}</small>
                      </div>
                    </div>
                  </template>
                  <template v-else-if="message.messageType === 'AFTER_SALE_ACTION' || message.messageType === 'SYSTEM_NOTICE'">
                    <p class="system-message">{{ message.content }}</p>
                  </template>
                  <template v-else>
                    <p>{{ message.content }}</p>
                  </template>
                </div>
              </article>
            </div>
            <p v-else class="empty-text">暂无消息，请先发送内容。</p>
          </div>

          <div class="composer">
            <textarea v-model="draftText" rows="4" class="form-textarea" placeholder="输入消息，回车发送前请点击发送按钮"></textarea>
            <div class="composer-actions">
              <button class="btn btn-outline" :disabled="sending" @click="draftText = ''">清空</button>
              <button class="btn btn-primary" :disabled="sending || !draftText.trim()" @click="sendText">
                {{ sending ? '发送中...' : '发送消息' }}
              </button>
            </div>
          </div>
        </section>
      </section>
    </main>

    <div v-if="remarkDialogVisible" class="remark-modal-overlay" @click.self="closeRemarkDialog">
      <section class="remark-modal card">
        <h3>{{ remarkDialogTitle }}</h3>
        <textarea
          v-model="remarkInput"
          rows="4"
          class="form-textarea"
          placeholder="请输入处理备注（选填）"
        ></textarea>
        <div class="remark-modal-actions">
          <button class="btn btn-outline" :disabled="actionRunning" @click="closeRemarkDialog">取消</button>
          <button class="btn btn-primary" :disabled="actionRunning" @click="confirmRemarkDialog">
            {{ actionRunning ? '处理中...' : '确认提交' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  min-height: 100vh;
  background: var(--color-rose-50);
}

.navbar {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  background: var(--gradient-glass);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(225, 29, 72, 0.1);
  box-shadow: var(--shadow-sm);
}

.navbar-inner {
  min-height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.navbar-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.navbar-title span {
  color: var(--color-gray-500);
  font-size: 12px;
}

.main-content {
  padding: 24px 0 48px;
}

.chat-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  min-height: calc(100vh - 160px);
}

.conversation-panel,
.message-panel {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: auto;
}

.conversation-item {
  display: flex;
  gap: 12px;
  width: 100%;
  text-align: left;
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-lg);
  padding: 12px;
  background: white;
}

.conversation-item.active {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-sm);
}

.conversation-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-rose-100);
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.conversation-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.conversation-body {
  flex: 1;
  min-width: 0;
}

.conversation-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.conversation-summary {
  margin: 8px 0 0;
  color: var(--color-gray-500);
  font-size: 13px;
}

.unread-badge {
  display: inline-flex;
  margin-top: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-primary);
  color: white;
  font-size: 12px;
}

.message-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.message-subtitle {
  margin: 4px 0 0;
  color: var(--color-gray-500);
}

.after-sale-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.after-sale-timeline {
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-lg);
  padding: 12px;
  background: #fff;
  display: grid;
  gap: 10px;
}

.timeline-node {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.timeline-dot {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  border: 1px solid var(--color-gray-300);
  color: var(--color-gray-500);
  background: var(--color-gray-100);
}

.timeline-content strong {
  display: block;
  color: var(--color-gray-900);
  font-size: 13px;
}

.timeline-content p {
  margin: 4px 0 0;
  color: var(--color-gray-500);
  font-size: 12px;
}

.timeline-done .timeline-dot {
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.45);
  color: #047857;
}

.timeline-current .timeline-dot {
  background: rgba(225, 29, 72, 0.12);
  border-color: rgba(225, 29, 72, 0.45);
  color: #be123c;
}

.product-context {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--color-gray-200);
}

.product-context img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: var(--radius-md);
}

.message-list {
  flex: 1;
  min-height: 420px;
  border: 1px solid var(--color-gray-200);
  border-radius: var(--radius-lg);
  background: white;
  padding: 16px;
  overflow: auto;
}

.message-stream {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.message-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 78%;
}

.message-item.mine {
  align-self: flex-end;
}

.message-item.other {
  align-self: flex-start;
}

.message-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--color-gray-500);
  font-size: 12px;
}

.message-bubble {
  border-radius: 16px;
  padding: 12px 14px;
  background: var(--color-rose-50);
  border: 1px solid var(--color-gray-200);
}

.message-item.mine .message-bubble {
  background: rgba(225, 29, 72, 0.08);
}

.message-card {
  display: flex;
  gap: 12px;
  align-items: center;
}

.message-card img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: 12px;
}

.system-message {
  margin: 0;
  color: var(--color-gray-700);
  font-weight: 600;
}

.composer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.loading-state,
.error-state {
  padding: 80px 24px;
  text-align: center;
}

.remark-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.32);
  display: grid;
  place-items: center;
  z-index: 100;
}

.remark-modal {
  width: min(520px, calc(100vw - 32px));
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.remark-modal h3 {
  margin: 0;
  font-size: 16px;
}

.remark-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 1024px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }
}
</style>


