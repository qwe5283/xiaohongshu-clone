<script setup>
const props = defineProps({
  categories: {
    type: Array,
    default: () => [],
  },
  active: {
    type: String,
    default: '推荐',
  },
});

const emit = defineEmits(['change']);

const handleChange = (category) => {
  emit('change', category);
};
</script>

<template>
  <div
    class="py-4 flex justify-center gap-7 text-base text-gray-500 mb-5 overflow-x-hidden"
  >
    <div
      v-for="category in categories"
      :key="category"
      class="cursor-pointer relative transition-all duration-300 whitespace-nowrap hover:text-gray-800"
      :class="{ 'text-gray-800 font-bold tab-active': active === category }"
      @click="handleChange(category)"
    >
      {{ category }}
    </div>
  </div>
</template>

<style scoped>
.tab-active::after {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 50%;
  transform: translateX(-50%);
  width: 28px;
  height: 3px;
  background: #ff2442;
  border-radius: 2px;
}
</style>
