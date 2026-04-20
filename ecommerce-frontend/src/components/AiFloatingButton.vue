<script setup>
const emit = defineEmits(['open']);

const openAi = () => emit('open');
</script>

<template>
  <button class="ai-floating-button" type="button" @click="openAi" title="打开AI客服">
    <span class="ai-floating-icon">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M4 12a8 8 0 0 1 16 0"></path>
        <path d="M4 12v4a2 2 0 0 0 2 2h1"></path>
        <path d="M20 12v4a2 2 0 0 1-2 2h-1"></path>
        <path d="M7 20h3"></path>
        <path d="M16 20h-3"></path>
        <path d="M8 16c.6 1 1.7 1.7 3 1.7s2.4-.7 3-1.7"></path>
      </svg>
    </span>
    <span class="ai-floating-label">AI客服</span>
  </button>
</template>

<style scoped>
.ai-floating-button {
  position: fixed;
  right: 18px;
  top: calc(50% + 84px);
  z-index: var(--z-fixed);
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 72px;
  padding: 12px 12px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.12);
  color: var(--color-gray-700);
  backdrop-filter: blur(16px);
}

.ai-floating-button:hover {
  transform: translateX(-2px);
  box-shadow: 0 14px 30px rgba(37, 99, 235, 0.16);
  color: var(--color-accent);
}

.ai-floating-icon {
  display: grid;
  place-items: center;
  color: var(--color-gray-600);
}

.ai-floating-label {
  font-size: 12px;
  font-weight: 700;
  writing-mode: vertical-rl;
  letter-spacing: 0.08em;
  color: var(--color-gray-600);
}

@media (max-width: 768px) {
  .ai-floating-button {
    right: 10px;
    top: calc(50% + 64px);
    min-width: 60px;
    padding: 10px;
  }

  .ai-floating-label {
    writing-mode: horizontal-tb;
    letter-spacing: 0;
  }
}
</style>

