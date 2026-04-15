<script setup>
import { computed, ref, watch } from 'vue';
import { sendAiMessage } from '../services/ai';

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['close']);

const inputText = ref('');
const orderNo = ref('');
const sending = ref(false);
const errorMessage = ref('');
const pendingToken = ref('');
const messages = ref([]);

const canSubmit = computed(() => !sending.value && !!inputText.value.trim());

const closeDrawer = () => emit('close');

const appendMessage = (role, content) => {
  messages.value.push({
    id: `${Date.now()}-${Math.random()}`,
    role,
    content,
    at: new Date().toLocaleTimeString()
  });
};

const ensureWelcome = () => {
  if (messages.value.length) {
    return;
  }
  appendMessage('assistant', '你好，我是AI客服。你可以告诉我你的问题，涉及退货我会先让你二次确认。');
};

const submit = async () => {
  const text = inputText.value.trim();
  if (!text || sending.value) {
    return;
  }
  errorMessage.value = '';
  appendMessage('user', text);
  inputText.value = '';
  sending.value = true;
  try {
    const result = await sendAiMessage({
      message: text,
      orderNo: orderNo.value.trim(),
      confirmationToken: pendingToken.value,
      confirm: false
    });
    if (result?.reply) {
      appendMessage('assistant', result.reply);
    }
    if (result?.requiresConfirmation && result?.confirmationToken) {
      pendingToken.value = result.confirmationToken;
    }
    if (result?.executed) {
      pendingToken.value = '';
    }
  } catch (error) {
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
      confirm: true
    });
    if (result?.reply) {
      appendMessage('assistant', result.reply);
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
      confirm: false
    });
    if (result?.reply) {
      appendMessage('assistant', result.reply);
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
  }
});
</script>

<template>
  <div v-if="visible" class="ai-drawer-wrapper" @click.self="closeDrawer">
    <aside class="ai-drawer">
      <header class="ai-drawer-header">
        <div>
          <h2>AI客服</h2>
          <p>可咨询问题，或发起退货申请</p>
        </div>
        <button class="btn btn-outline" @click="closeDrawer">关闭</button>
      </header>

      <section class="ai-drawer-order-box">
        <label for="ai-order-no">订单号（可选）</label>
        <input id="ai-order-no" v-model="orderNo" type="text" placeholder="例如 202604150001" />
      </section>

      <section class="ai-drawer-messages">
        <article v-for="message in messages" :key="message.id" class="ai-message" :class="`is-${message.role}`">
          <p>{{ message.content }}</p>
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
        <div class="ai-input-row">
          <input
            v-model="inputText"
            type="text"
            placeholder="请输入你的问题，例如：帮我把这个订单退货"
            @keydown.enter="submit"
          />
          <button class="btn btn-accent" :disabled="!canSubmit" @click="submit">发送</button>
        </div>
      </footer>
    </aside>
  </div>
</template>

<style scoped>
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
  grid-template-columns: 1fr auto;
  gap: 8px;
}

.ai-input-row input {
  width: 100%;
}
</style>

