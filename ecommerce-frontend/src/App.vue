<script setup>
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import CartFloatingButton from './components/CartFloatingButton.vue';
import CartDrawer from './components/CartDrawer.vue';
import AiFloatingButton from './components/AiFloatingButton.vue';
import AiChatDrawer from './components/AiChatDrawer.vue';

const route = useRoute();
const cartDrawerVisible = ref(false);
const aiDrawerVisible = ref(false);

const canShowCartEntry = computed(() => route.path !== '/login' && !!localStorage.getItem('token'));

const openCartDrawer = () => {
  cartDrawerVisible.value = true;
};

const closeCartDrawer = () => {
  cartDrawerVisible.value = false;
};

const openAiDrawer = () => {
  aiDrawerVisible.value = true;
};

const closeAiDrawer = () => {
  aiDrawerVisible.value = false;
};
</script>

<template>
  <router-view />
  <CartFloatingButton v-if="canShowCartEntry" @open="openCartDrawer" />
  <AiFloatingButton v-if="canShowCartEntry" @open="openAiDrawer" />
  <CartDrawer :visible="cartDrawerVisible" @close="closeCartDrawer" />
  <AiChatDrawer :visible="aiDrawerVisible" @close="closeAiDrawer" />
</template>

