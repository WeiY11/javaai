<template>
  <AppShell v-if="authStore.isLoggedIn" />
  <RouterView v-else />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import AppShell from './components/AppShell.vue'
import { useAuthStore } from './stores/auth.store'

const authStore = useAuthStore()

onMounted(() => {
  document.documentElement.classList.toggle('is-dark', localStorage.getItem('darkMode') === 'true')
})
</script>
