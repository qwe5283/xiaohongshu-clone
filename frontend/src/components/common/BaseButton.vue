<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (value) => ['primary', 'outline', 'ghost'].includes(value),
  },
  block: {
    type: Boolean,
    default: false,
  },
  size: {
    type: String,
    default: 'md',
    validator: (value) => ['sm', 'md'].includes(value),
  },
});
</script>

<template>
  <button
    class="inline-flex items-center justify-center rounded-3xl text-base font-bold cursor-pointer transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
    :class="[
      size === 'sm' ? 'px-4 py-2 text-sm' : 'p-3',
      variant === 'primary' && 'bg-primary text-white border border-primary',
      variant === 'outline' && 'bg-white text-primary border border-primary',
      variant === 'ghost' &&
        'bg-transparent text-gray-500 border border-transparent p-0 hover:bg-gray-100',
      block && 'w-full',
    ]"
  >
    <slot />
  </button>
</template>
