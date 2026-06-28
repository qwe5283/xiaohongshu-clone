<script setup>
defineProps({
  widthClass: {
    type: String,
    default: 'w-[400px]',
  },
  panelClass: {
    type: String,
    default: '',
  },
});

const emit = defineEmits(['close']);
</script>

<template>
  <div
    class="fixed inset-0 bg-black/30 z-[100] flex justify-center items-center"
    @click.self="emit('close')"
  >
    <div
      class="relative bg-white rounded-2xl shadow-[0_10px_30px_rgba(0,0,0,0.2)]"
      :class="[widthClass, panelClass]"
    >
      <slot />
    </div>
  </div>
</template>
