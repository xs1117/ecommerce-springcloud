<script setup>
const emit = defineEmits(['open']);

const openAi = () => emit('open');
</script>

<template>
  <button class="ai-floating-button" type="button" @click="openAi" title="打开AI客服">
    <span class="ai-floating-icon">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M4 6a2 2 0 0 1 2-2h9l5 5v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z"></path>
        <path d="M9 12h6"></path>
        <path d="M9 16h4"></path>
      </svg>
    </span>
    <span class="ai-floating-label">AI客服</span>
  </button>
</template>

<style scoped>
.ai-floating-button {
  position: fixed;
  right: 18px;
  top: calc(50% + 120px);
  z-index: var(--z-fixed);
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 72px;
  padding: 12px 12px;
  border-radius: 20px;
  border: 1px solid rgba(37, 99, 235, 0.22);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.13);
  color: var(--color-accent);
  backdrop-filter: blur(16px);
}

.ai-floating-button:hover {
  transform: translateX(-2px);
  box-shadow: 0 18px 36px rgba(37, 99, 235, 0.2);
}

.ai-floating-icon {
  display: grid;
  place-items: center;
}

.ai-floating-label {
  font-size: 12px;
  font-weight: 700;
  writing-mode: vertical-rl;
  letter-spacing: 0.08em;
}

@media (max-width: 768px) {
  .ai-floating-button {
    right: 10px;
    top: calc(50% + 92px);
    min-width: 60px;
    padding: 10px;
  }

  .ai-floating-label {
    writing-mode: horizontal-tb;
    letter-spacing: 0;
  }
}
</style>

