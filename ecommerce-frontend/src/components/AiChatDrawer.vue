<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { sendAiMessage, uploadAiImage } from '../services/ai';
import { ensureCurrentUserId } from '../services/auth';
import { getOrderDetail, queryOrders } from '../services/order';
import { toFileUrl } from '../services/api';

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['close']);

const inputText = ref('');
const orderNo = ref('');
const selectedOrderNo = ref('');
const sending = ref(false);
const errorMessage = ref('');
const pendingToken = ref('');
const messages = ref([]);
const messagesContainer = ref(null);
const loadingOrderOptions = ref(false);
const orderOptionsError = ref('');
const orderOptions = ref([]);
const orderDropdownOpen = ref(false);
const selectedImageUrl = ref('');
const selectedImageName = ref('');
const imageUploading = ref(false);
const fileInputRef = ref(null);
const THINKING_VISIBLE_MS = 2400;

const speechSupported = ref(false);
const isRecording = ref(false);
const speechError = ref('');
const recognitionRef = ref(null);
const baseInputAtStart = ref('');

const resolveSpeechRecognition = () => {
  if (typeof window === 'undefined') {
    return null;
  }
  return window.SpeechRecognition || window.webkitSpeechRecognition || null;
};

speechSupported.value = !!resolveSpeechRecognition();

const ensureSpeechRecognition = () => {
  if (recognitionRef.value) {
    return recognitionRef.value;
  }
  const SpeechRecognition = resolveSpeechRecognition();
  if (!SpeechRecognition) {
    speechSupported.value = false;
    return null;
  }
  speechSupported.value = true;
  const recognition = new SpeechRecognition();
  recognition.lang = 'zh-CN';
  recognition.interimResults = true;
  recognition.continuous = true;
  recognition.onresult = (event) => {
    let finalText = '';
    let interimText = '';
    for (let i = event.resultIndex; i < event.results.length; i += 1) {
      const result = event.results[i];
      if (result.isFinal) {
        finalText += result[0].transcript;
      } else {
        interimText += result[0].transcript;
      }
    }
    inputText.value = `${baseInputAtStart.value}${finalText}${interimText}`.trim();
  };
  recognition.onerror = (event) => {
    speechError.value = event?.error ? `语音识别失败：${event.error}` : '语音识别失败';
    isRecording.value = false;
  };
  recognition.onend = () => {
    isRecording.value = false;
  };
  recognitionRef.value = recognition;
  return recognition;
};

const toggleVoiceInput = () => {
  speechError.value = '';
  const recognition = ensureSpeechRecognition();
  if (!recognition) {
    speechError.value = '当前浏览器不支持语音输入';
    return;
  }
  if (isRecording.value) {
    recognition.stop();
    return;
  }
  baseInputAtStart.value = inputText.value ? `${inputText.value.trim()} ` : '';
  isRecording.value = true;
  recognition.start();
};

onBeforeUnmount(() => {
  if (recognitionRef.value) {
    recognitionRef.value.stop();
  }
});

const ORDER_STATUS_TEXT = {
  CREATED: '待支付(确认中)',
  STOCK_CONFIRMED: '库存已锁定',
  WAIT_PAY: '待支付',
  TO_SHIP: '待发货',
  TO_RECEIVE: '待收货',
  FINISHED: '已完成',
  AFTER_SALE: '售后中',
  PAID: '已支付',
  CLOSED: '已关闭',
  STOCK_FAILED: '库存不足'
};

const parseAssistantSections = (content) => {
  const text = (content || '').trim();
  if (!text) {
    return { thinking: '', answer: '' };
  }
  const thinkBlockMatch = text.match(/<think>([\s\S]*?)<\/think>/i);
  if (thinkBlockMatch) {
    const thinking = (thinkBlockMatch[1] || '').trim();
    const answer = text.replace(/<think>[\s\S]*?<\/think>/ig, '').trim();
    return { thinking, answer: answer || text };
  }
  const thinkingIdx = text.indexOf('【思考内容】');
  const answerIdx = text.indexOf('【正式回答】');
  if (thinkingIdx !== -1 && answerIdx !== -1 && answerIdx > thinkingIdx) {
    const thinking = text.slice(thinkingIdx + '【思考内容】'.length, answerIdx).trim();
    const answer = text.slice(answerIdx + '【正式回答】'.length).trim();
    return { thinking, answer: answer || text };
  }
  return { thinking: '', answer: text };
};

let thinkingTicker = null;

const clearThinkingTicker = () => {
  if (thinkingTicker) {
    window.clearInterval(thinkingTicker);
    thinkingTicker = null;
  }
};

const scrollMessagesToBottom = async () => {
  await nextTick();
  const container = messagesContainer.value;
  if (!container) {
    return;
  }
  container.scrollTop = container.scrollHeight;
};

const canSubmit = computed(() => !sending.value && !imageUploading.value && (!!inputText.value.trim() || !!selectedImageUrl.value));
const selectedOrderMeta = computed(() => orderOptions.value.find((item) => item.orderNo === selectedOrderNo.value) || null);
const activeOrderMeta = computed(() => {
  const byInput = orderOptions.value.find((item) => item.orderNo === orderNo.value.trim());
  return byInput || selectedOrderMeta.value || null;
});

const parseOrderTime = (value) => {
  if (!value) {
    return 0;
  }
  const parsed = new Date(String(value).replace(' ', 'T')).getTime();
  return Number.isNaN(parsed) ? 0 : parsed;
};

const selectOrder = (item) => {
  selectedOrderNo.value = item.orderNo;
  orderNo.value = item.orderNo;
  orderDropdownOpen.value = false;
};

const clearOrderSelection = () => {
  selectedOrderNo.value = '';
  orderNo.value = '';
  orderDropdownOpen.value = false;
};

const toggleOrderDropdown = () => {
  if (!orderOptions.value.length || loadingOrderOptions.value) {
    return;
  }
  orderDropdownOpen.value = !orderDropdownOpen.value;
};

const loadOrderOptions = async () => {
  loadingOrderOptions.value = true;
  orderOptionsError.value = '';
  try {
    const userId = await ensureCurrentUserId();
    const data = await queryOrders({ userId, status: '', limit: 50 });
    const unique = new Map();
    data.forEach((item) => {
      if (!item?.orderNo) {
        return;
      }
      const key = String(item.orderNo);
      if (!unique.has(key)) {
        unique.set(key, {
          orderNo: key,
          createdAt: item.createdAt || '',
          status: item.status || '',
          itemCount: Number(item.itemCount || 0)
        });
      }
    });
    const sorted = Array.from(unique.values()).sort((left, right) => parseOrderTime(right.createdAt) - parseOrderTime(left.createdAt));
    const topOrders = sorted.slice(0, 20);
    orderOptions.value = await Promise.all(topOrders.map(async (item) => {
      try {
        const detail = await getOrderDetail(item.orderNo);
        const order = detail?.order || {};
        const items = Array.isArray(detail?.items) ? detail.items : [];
        const firstItem = items[0] || {};
        const productTitle = firstItem.title || firstItem.productTitle || firstItem.productName || '商品信息暂不可用';
        const productImageUrl = toFileUrl(firstItem.imageUrl || firstItem.coverImageUrl || firstItem.productImageUrl || '');
        const itemCount = Number(order.itemCount || items.length || item.itemCount || 0);
        return {
          ...item,
          status: order.status || item.status,
          createdAt: order.createdAt || item.createdAt,
          productTitle,
          productImageUrl,
          itemCount
        };
      } catch {
        return {
          ...item,
          productTitle: '商品信息加载失败',
          productImageUrl: ''
        };
      }
    }));
  } catch {
    orderOptions.value = [];
    orderOptionsError.value = '订单列表加载失败，可手动输入订单号';
  } finally {
    loadingOrderOptions.value = false;
  }
};

const closeDrawer = () => emit('close');

const formatPrice = (value) => {
  const amount = Number(value);
  if (Number.isNaN(amount)) {
    return '';
  }
  return `￥${amount.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
};

const normalizeRecommendProducts = (value) => {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter((item) => item && item.id)
    .map((item) => ({
      id: String(item.id),
      title: item.title || '商品',
      priceText: formatPrice(item.price),
      imageUrl: toFileUrl(item.imageUrl || ''),
      storeName: item.storeName || '',
      link: item.link || `/product/${item.id}`,
      similarityScore: Number(item.similarityScore || 0)
    }));
};

const buildConversationHistory = (latestUserMessage = '') => {
  const turns = [];
  const recentMessages = messages.value
    .filter((item) => item && (item.role === 'user' || item.role === 'assistant'))
    .filter((item) => !item.pending)
    .slice(-8);
  recentMessages.forEach((item) => {
    if (!item?.content) {
      return;
    }
    turns.push({
      role: item.role,
      content: String(item.content)
    });
  });
  if (latestUserMessage) {
    turns.push({ role: 'user', content: latestUserMessage });
  }
  return turns;
};

const appendMessage = (role, content, options = {}) => {
  // options.allowThinking: whether this assistant message is allowed to display thinking content
  const allowThinking = !!options.allowThinking;
  const { thinking, answer } = role === 'assistant'
    ? (options.thinking !== undefined
      ? { thinking: options.thinking || '', answer: content }
      : parseAssistantSections(content))
    : { thinking: '', answer: content };
  const next = {
    id: `${Date.now()}-${Math.random()}`,
    role,
    content: answer,
    thinking: allowThinking ? thinking : '',
    // showThinking only if allowed and requested
    showThinking: allowThinking && !!options.showThinking,
    showAnswer: options.showAnswer !== undefined ? !!options.showAnswer : (role !== 'assistant' || !allowThinking || !thinking),
    pending: !!options.pending,
    allowThinking,
    userImageUrl: options.userImageUrl || '',
    recommendProducts: normalizeRecommendProducts(options.recommendProducts),
    at: new Date().toLocaleTimeString()
  };
  messages.value.push(next);
  scrollMessagesToBottom();
  return next;
};

const startPendingThinking = () => {
  const placeholder = appendMessage('assistant', '', {
    showThinking: true,
    showAnswer: false,
    pending: true,
    allowThinking: true
  });
  const base = '正在思考';
  let dots = 0;
  clearThinkingTicker();
  thinkingTicker = window.setInterval(() => {
    dots = (dots + 1) % 4;
    const current = messages.value.find((item) => item.id === placeholder.id);
    if (!current) {
      clearThinkingTicker();
      return;
    }
    current.thinking = `${base}${'.'.repeat(dots)}`;
    scrollMessagesToBottom();
  }, 380);
  return placeholder.id;
};

const finishPendingThinking = (messageId, result) => {
  clearThinkingTicker();
  const target = messages.value.find((item) => item.id === messageId);
  if (!target) {
    if (result?.reply) {
      // Non-main-flow assistant message: do NOT display thinking
      appendMessage('assistant', result.reply, {
        thinking: result?.thinking,
        showThinking: false,
        showAnswer: true,
        allowThinking: false,
        recommendProducts: result?.recommendProducts
      });
    }
    return;
  }
  const structuredThinking = typeof result?.thinking === 'string' ? result.thinking.trim() : '';
  const structuredReply = typeof result?.reply === 'string' ? result.reply.trim() : '';
  const parsed = parseAssistantSections(structuredReply || '');
  const thinking = structuredThinking || parsed.thinking;
  const answer = structuredReply || parsed.answer;
  target.pending = false;
  target.thinking = target.allowThinking ? thinking : '';
  target.content = answer;
  target.showThinking = target.allowThinking && !!thinking;
  target.showAnswer = !target.showThinking;
  scrollMessagesToBottom();
  if (thinking) {
    window.setTimeout(() => {
      if (!target.pending) {
        target.showThinking = false;
        target.showAnswer = true;
      }
    }, THINKING_VISIBLE_MS);
  }
};

const toggleThinking = (message) => {
  message.showThinking = !message.showThinking;
};

const ensureWelcome = () => {
  if (messages.value.length) {
    return;
  }
  appendMessage('assistant', '你好，我是AI导购。你可以告诉我你的问题，涉及退货我会先让你二次确认。');
};

const openImagePicker = () => {
  if (imageUploading.value || sending.value) {
    return;
  }
  fileInputRef.value?.click();
};

const clearSelectedImage = () => {
  selectedImageUrl.value = '';
  selectedImageName.value = '';
  if (fileInputRef.value) {
    fileInputRef.value.value = '';
  }
};

const onImageFileChange = async (event) => {
  const [file] = event?.target?.files || [];
  if (!file) {
    return;
  }
  if (!file.type?.startsWith('image/')) {
    errorMessage.value = '仅支持上传图片文件';
    clearSelectedImage();
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    errorMessage.value = '图片不能超过5MB';
    clearSelectedImage();
    return;
  }

  imageUploading.value = true;
  errorMessage.value = '';
  try {
    const uploaded = await uploadAiImage(file);
    selectedImageUrl.value = uploaded?.url || '';
    selectedImageName.value = file.name || '已上传图片';
    if (!selectedImageUrl.value) {
      throw new Error('upload failed');
    }
  } catch {
    clearSelectedImage();
    errorMessage.value = '图片上传失败，请稍后重试';
  } finally {
    imageUploading.value = false;
  }
};

const attachAssistantPayload = (messageId, result) => {
  const target = messages.value.find((item) => item.id === messageId);
  if (!target) {
    return;
  }
  if (result?.thinking !== undefined) {
    target.thinking = target.allowThinking ? (result.thinking || '') : '';
    target.showThinking = target.allowThinking && !!result.thinking;
    target.showAnswer = !target.showThinking;
  }
  target.recommendProducts = normalizeRecommendProducts(result?.recommendProducts);
  scrollMessagesToBottom();
};

const submit = async () => {
  const text = inputText.value.trim();
  const imageUrl = selectedImageUrl.value;
  if ((!text && !imageUrl) || sending.value || imageUploading.value) {
    return;
  }
  errorMessage.value = '';
  const history = buildConversationHistory(text || '【图片识别】');
  appendMessage('user', text || '【图片识别】', { userImageUrl: imageUrl });
  const pendingAssistantId = startPendingThinking();
  inputText.value = '';

  sending.value = true;
  try {
    const result = await sendAiMessage({
      message: text || null,
      imageUrl,
      orderNo: orderNo.value.trim(),
      confirmationToken: pendingToken.value,
      confirm: false,
      history
    });
    if (result?.reply) {
      finishPendingThinking(pendingAssistantId, result);
    } else {
      finishPendingThinking(pendingAssistantId, { reply: '我已收到你的消息，正在处理中。', thinking: '' });
    }
    attachAssistantPayload(pendingAssistantId, result);
    clearSelectedImage();
    if (result?.requiresConfirmation && result?.confirmationToken) {
      pendingToken.value = result.confirmationToken;
    }
    if (result?.executed) {
      pendingToken.value = '';
    }
  } catch (error) {
    clearThinkingTicker();
    messages.value = messages.value.filter((item) => item.id !== pendingAssistantId);
    errorMessage.value = error?.response?.data?.message || 'AI客服暂时不可用，请稍后重试';
  } finally {
    sending.value = false;
  }
};

const confirmPendingAction = async () => {
  if (!pendingToken.value || sending.value) {
    return;
  }
  errorMessage.value = '';
  appendMessage('user', '确认');
  sending.value = true;
  try {
    const result = await sendAiMessage({
      message: '确认',
      orderNo: orderNo.value.trim(),
      confirmationToken: pendingToken.value,
      confirm: true,
      history: buildConversationHistory('确认')
    });
    if (result?.reply) {
      const message = appendMessage('assistant', result.reply, {
        thinking: result?.thinking,
        showThinking: !!result?.thinking,
        showAnswer: !result?.thinking,
        recommendProducts: result?.recommendProducts,
        allowThinking: false
      });
      if (result?.thinking) {
        window.setTimeout(() => {
          const current = messages.value.find((item) => item.id === message.id);
          if (current) {
            current.showThinking = false;
            current.showAnswer = true;
          }
        }, THINKING_VISIBLE_MS);
      }
    }
    pendingToken.value = '';
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '确认失败，请稍后重试';
  } finally {
    sending.value = false;
  }
};

const cancelPendingAction = async () => {
  if (!pendingToken.value || sending.value) {
    return;
  }
  errorMessage.value = '';
  appendMessage('user', '取消');
  sending.value = true;
  try {
    const result = await sendAiMessage({
      message: '取消',
      orderNo: orderNo.value.trim(),
      confirmationToken: pendingToken.value,
      confirm: false,
      history: buildConversationHistory('取消')
    });
    if (result?.reply) {
      const message = appendMessage('assistant', result.reply, {
        thinking: result?.thinking,
        showThinking: !!result?.thinking,
        showAnswer: !result?.thinking,
        recommendProducts: result?.recommendProducts,
        allowThinking: false
      });
      if (result?.thinking) {
        window.setTimeout(() => {
          const current = messages.value.find((item) => item.id === message.id);
          if (current) {
            current.showThinking = false;
            current.showAnswer = true;
          }
        }, THINKING_VISIBLE_MS);
      }
    }
    pendingToken.value = '';
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '取消失败，请稍后重试';
  } finally {
    sending.value = false;
  }
};

watch(() => props.visible, (next) => {
  if (next) {
    ensureWelcome();
    selectedOrderNo.value = '';
    orderDropdownOpen.value = false;
    loadOrderOptions();
    scrollMessagesToBottom();
    return;
  }
  orderDropdownOpen.value = false;
});

watch(sending, (next) => {
  if (!next) {
    clearThinkingTicker();
    scrollMessagesToBottom();
  }
});

watch(orderNo, (next) => {
  const clean = String(next || '').trim();
  const matched = orderOptions.value.find((item) => item.orderNo === clean);
  selectedOrderNo.value = matched ? matched.orderNo : '';
});
</script>

<template>
  <div v-if="visible" class="ai-drawer-wrapper" @click.self="closeDrawer">
    <aside class="ai-drawer">
      <header class="ai-drawer-header">
        <div>
          <h2>AI导购</h2>
          <p>可咨询问题，或发起退货申请</p>
        </div>
        <button class="btn btn-outline" @click="closeDrawer">关闭</button>
      </header>

      <section class="ai-drawer-order-box">
        <label for="ai-order-no">订单号（可选）</label>
        <div class="ai-order-combo-row">
          <input id="ai-order-no" v-model="orderNo" type="text" placeholder="例如 202604150001" />
          <button
            type="button"
            class="ai-order-combo-btn"
            :disabled="loadingOrderOptions || !orderOptions.length"
            @click="toggleOrderDropdown"
          >{{ orderDropdownOpen ? '▲' : '▼' }}</button>
          <button type="button" class="ai-order-clear-btn" :disabled="!orderNo" @click="clearOrderSelection">清空</button>
        </div>
        <div v-if="orderDropdownOpen" class="ai-order-dropdown-menu">
          <button
            v-for="item in orderOptions"
            :key="item.orderNo"
            type="button"
            class="ai-order-option"
            :class="{ active: item.orderNo === selectedOrderNo }"
            @click="selectOrder(item)"
          >
            <img v-if="item.productImageUrl" :src="item.productImageUrl" alt="product" class="ai-order-option-image" />
            <span v-else class="ai-order-option-image ai-order-option-placeholder">图</span>
            <span class="ai-order-option-body">
              <strong>{{ item.orderNo }}</strong>
              <small>{{ ORDER_STATUS_TEXT[item.status] || item.status || '状态未知' }} · {{ item.createdAt || '时间未知' }}</small>
              <small>{{ item.productTitle }}<template v-if="item.itemCount > 1"> 等 {{ item.itemCount }} 件</template></small>
            </span>
          </button>
        </div>
        <div v-if="activeOrderMeta" class="ai-order-selected-detail">
          <img
            v-if="activeOrderMeta.productImageUrl"
            :src="activeOrderMeta.productImageUrl"
            alt="product"
            class="ai-order-selected-image"
          />
          <span v-else class="ai-order-selected-image ai-order-option-placeholder">图</span>
          <span class="ai-order-selected-body">
            <strong>{{ activeOrderMeta.productTitle || '商品信息暂不可用' }}</strong>
            <small>{{ ORDER_STATUS_TEXT[activeOrderMeta.status] || activeOrderMeta.status || '状态未知' }}</small>
            <small>订单号：{{ activeOrderMeta.orderNo }}</small>
          </span>
        </div>
        <p v-if="orderOptionsError" class="ai-order-hint ai-order-hint-error">{{ orderOptionsError }}</p>
        <p v-else-if="orderOptions.length" class="ai-order-hint">可在上方下拉中查看订单状态、商品图片和名称，点击后自动填入订单号</p>
      </section>

      <section ref="messagesContainer" class="ai-drawer-messages">
        <article v-for="message in messages" :key="message.id" class="ai-message" :class="[`is-${message.role}`, { pending: message.pending }]">
          <div
            v-if="message.role === 'assistant' && message.allowThinking && (message.pending || message.thinking)"
            class="ai-thinking-block"
          >
            <button class="ai-thinking-toggle" @click="toggleThinking(message)">
              <span class="ai-thinking-triangle" :class="{ expanded: message.showThinking }">▶</span>
              <span>{{ message.showThinking ? '隐藏思考过程' : '展示思考过程' }}</span>
            </button>
            <pre v-if="message.showThinking" class="ai-thinking-content">{{ message.thinking }}</pre>
          </div>
          <pre v-if="message.showAnswer || message.role !== 'assistant'" class="ai-answer-content">{{ message.content }}</pre>
          <img v-if="message.userImageUrl" :src="message.userImageUrl" alt="uploaded" class="ai-message-image" />
          <div v-if="message.role === 'assistant' && message.recommendProducts?.length" class="ai-recommend-list">
            <a v-for="item in message.recommendProducts" :key="item.id" :href="item.link" class="ai-recommend-card">
              <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.title" class="ai-recommend-image" />
              <span v-else class="ai-recommend-image ai-order-option-placeholder">图</span>
              <span class="ai-recommend-body">
                <strong>{{ item.title }}</strong>
                <small v-if="item.similarityScore > 0">图片相似度：{{ Math.round(item.similarityScore * 100) }}%</small>
                <small v-if="item.storeName">{{ item.storeName }}</small>
                <small v-if="item.priceText">{{ item.priceText }}</small>
              </span>
            </a>
          </div>
          <span>{{ message.at }}</span>
        </article>
        <p v-if="errorMessage" class="ai-error">{{ errorMessage }}</p>
      </section>

      <footer class="ai-drawer-footer">
        <div v-if="pendingToken" class="ai-confirm-bar">
          <span>检测到待确认操作</span>
          <div class="ai-confirm-actions">
            <button class="btn btn-secondary" :disabled="sending" @click="cancelPendingAction">取消</button>
            <button class="btn btn-primary" :disabled="sending" @click="confirmPendingAction">确认发起</button>
          </div>
        </div>
        <div class="ai-upload-row">
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            class="ai-upload-hidden"
            @change="onImageFileChange"
          />
          <button type="button" class="btn btn-outline" :disabled="sending || imageUploading" @click="openImagePicker">
            {{ imageUploading ? '上传中...' : '上传图片' }}
          </button>
          <div v-if="selectedImageUrl" class="ai-upload-preview">
            <img :src="selectedImageUrl" alt="selected" />
            <span :title="selectedImageName">{{ selectedImageName || '已选图片' }}</span>
            <button type="button" class="ai-upload-clear" :disabled="sending || imageUploading" @click="clearSelectedImage">清除</button>
          </div>
        </div>
        <div class="ai-input-row">
          <input
            v-model="inputText"
            type="text"
            placeholder="请输入问题，或上传商品图片让我帮你找同款"
            @keydown.enter="submit"
          />
          <button
            type="button"
            class="btn btn-outline ai-voice-btn"
            :disabled="sending || imageUploading"
            :title="speechSupported ? '语音输入' : '浏览器不支持语音输入'"
            @click="toggleVoiceInput"
          >
            <svg class="ai-voice-icon" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 14a3 3 0 0 0 3-3V7a3 3 0 0 0-6 0v4a3 3 0 0 0 3 3zm7-3a1 1 0 1 0-2 0 5 5 0 0 1-10 0 1 1 0 1 0-2 0 7 7 0 0 0 6 6.93V21a1 1 0 1 0 2 0v-3.07A7 7 0 0 0 19 11z" />
            </svg>
          </button>
          <button class="btn btn-accent" :disabled="!canSubmit" @click="submit">发送</button>
        </div>
        <p v-if="speechError" class="ai-error">{{ speechError }}</p>
      </footer>
    </aside>
  </div>
</template>

<style scoped>
.ai-voice-btn {
  min-width: 36px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.ai-voice-icon {
  width: 16px;
  height: 16px;
  fill: currentColor;
}
.ai-drawer-wrapper {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.32);
  z-index: var(--z-modal-backdrop);
  display: flex;
  justify-content: flex-end;
}

.ai-drawer {
  width: min(460px, 96vw);
  height: 100%;
  background: #fff;
  box-shadow: -12px 0 30px rgba(15, 23, 42, 0.2);
  display: grid;
  grid-template-rows: auto auto 1fr auto;
}

.ai-drawer-header {
  padding: 16px;
  border-bottom: 1px solid var(--color-gray-200);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ai-drawer-header h2 {
  font-size: 22px;
  font-family: var(--font-display);
}

.ai-drawer-header p {
  font-size: 13px;
  color: var(--color-gray-500);
}

.ai-drawer-order-box {
  border-bottom: 1px solid var(--color-gray-200);
  padding: 12px 16px;
  display: grid;
  gap: 8px;
}

.ai-drawer-order-box label {
  font-size: 12px;
  color: var(--color-gray-500);
}

.ai-drawer-order-box input {
  width: 100%;
}

.ai-order-combo-row {
  display: grid;
  grid-template-columns: 1fr 44px auto;
  gap: 8px;
}

.ai-order-combo-btn {
  border: 1px solid var(--color-gray-300);
  border-radius: 10px;
  background: #fff;
  color: var(--color-gray-700);
}

.ai-order-dropdown-menu {
  max-height: 240px;
  overflow: auto;
  border: 1px solid var(--color-gray-200);
  border-radius: 10px;
  background: #fff;
  display: grid;
}

.ai-order-option {
  border: none;
  border-bottom: 1px solid var(--color-gray-100);
  background: #fff;
  padding: 8px;
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 8px;
  text-align: left;
}

.ai-order-option:last-child {
  border-bottom: none;
}

.ai-order-option:hover {
  background: var(--color-rose-50);
}

.ai-order-option.active {
  background: rgba(37, 99, 235, 0.08);
  border-left: 3px solid rgba(37, 99, 235, 0.7);
}

.ai-order-option-image {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--color-gray-200);
}

.ai-order-option-placeholder {
  display: grid;
  place-items: center;
  color: var(--color-gray-500);
  background: var(--color-gray-100);
}

.ai-order-option-body {
  display: grid;
  gap: 2px;
}

.ai-order-option-body strong {
  font-size: 13px;
  color: var(--color-gray-800);
}

.ai-order-option-body small {
  color: var(--color-gray-500);
  font-size: 12px;
}

.ai-order-clear-btn {
  min-width: 62px;
  border: 1px solid var(--color-gray-300);
  border-radius: 10px;
  padding: 0 10px;
  background: #fff;
  color: var(--color-gray-600);
}

.ai-order-clear-btn:disabled {
  opacity: 0.5;
}

.ai-order-selected-detail {
  border: 1px solid var(--color-gray-200);
  border-radius: 10px;
  padding: 8px;
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 8px;
  align-items: center;
  background: #fff;
}

.ai-order-selected-image {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--color-gray-200);
}

.ai-order-selected-body {
  display: grid;
  gap: 2px;
}

.ai-order-selected-body strong {
  font-size: 13px;
  color: var(--color-gray-800);
}

.ai-order-selected-body small {
  font-size: 12px;
  color: var(--color-gray-500);
}

.ai-order-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-gray-500);
}

.ai-order-hint-error {
  color: var(--color-danger);
}

.ai-drawer-messages {
  padding: 12px 16px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: linear-gradient(180deg, #fff, #f8fafc);
}

.ai-message {
  max-width: 88%;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--color-gray-200);
  background: #fff;
  display: grid;
  gap: 4px;
}

.ai-message.pending {
  opacity: 0.92;
}

.ai-thinking-block {
  display: grid;
  gap: 6px;
}

.ai-thinking-toggle {
  border: 0;
  background: transparent;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
}

.ai-thinking-triangle {
  display: inline-block;
  transition: transform 0.2s ease;
}

.ai-thinking-triangle.expanded {
  transform: rotate(90deg);
}

.ai-thinking-content {
  margin: 0;
  padding: 8px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
}

.ai-answer-content {
  margin: 0;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}

.ai-message-image {
  width: 100%;
  max-width: 220px;
  border-radius: 10px;
  border: 1px solid var(--color-gray-200);
}

.ai-recommend-list {
  display: grid;
  gap: 8px;
  margin-top: 4px;
}

.ai-recommend-card {
  border: 1px solid var(--color-gray-200);
  border-radius: 10px;
  padding: 8px;
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 8px;
  color: inherit;
  text-decoration: none;
  background: #fff;
}

.ai-recommend-card:hover {
  border-color: rgba(37, 99, 235, 0.35);
  background: rgba(37, 99, 235, 0.03);
}

.ai-recommend-image {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--color-gray-200);
}

.ai-recommend-body {
  display: grid;
  gap: 2px;
}

.ai-recommend-body strong {
  font-size: 13px;
  color: var(--color-gray-800);
}

.ai-recommend-body small {
  font-size: 12px;
  color: var(--color-gray-500);
}

.ai-message span {
  font-size: 11px;
  color: var(--color-gray-400);
}

.ai-message.is-user {
  align-self: flex-end;
  border-color: rgba(37, 99, 235, 0.25);
  background: rgba(37, 99, 235, 0.06);
}

.ai-message.is-assistant {
  align-self: flex-start;
}

.ai-error {
  color: var(--color-danger);
  font-size: 13px;
}

.ai-drawer-footer {
  border-top: 1px solid var(--color-gray-200);
  padding: 12px 16px;
  display: grid;
  gap: 10px;
}

.ai-confirm-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.25);
}

.ai-confirm-bar span {
  font-size: 12px;
  color: #92400e;
  font-weight: 600;
}

.ai-confirm-actions {
  display: inline-flex;
  gap: 8px;
}

.ai-input-row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 8px;
}

.ai-input-row input {
  width: 100%;
}

.ai-upload-row {
  display: grid;
  gap: 8px;
}

.ai-upload-hidden {
  display: none;
}

.ai-upload-preview {
  border: 1px solid var(--color-gray-200);
  border-radius: 10px;
  padding: 8px;
  display: grid;
  grid-template-columns: 42px 1fr auto;
  align-items: center;
  gap: 8px;
}

.ai-upload-preview img {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--color-gray-200);
}

.ai-upload-preview span {
  font-size: 12px;
  color: var(--color-gray-600);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-upload-clear {
  border: 1px solid var(--color-gray-300);
  border-radius: 8px;
  background: #fff;
  color: var(--color-gray-600);
  font-size: 12px;
  padding: 4px 8px;
}
</style>

