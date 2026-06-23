<script setup>
const props = defineProps({
  categories: {
    type: Array,
    default: () => []
  },
  active: {
    type: String,
    default: '推荐'
  }
})

const emit = defineEmits(['change'])

const handleChange = (category) => {
  emit('change', category)
}
</script>

<template>
  <div class="category-tabs">
    <div
      v-for="category in categories"
      :key="category"
      class="tab-item"
      :class="{ active: active === category }"
      @click="handleChange(category)"
    >
      {{ category }}
    </div>
  </div>
</template>

<style scoped>
.category-tabs {
  padding: 16px 0;
  display: flex;
  justify-content: center;
  gap: 28px;
  font-size: 16px;
  color: var(--text-secondary);
  margin-bottom: 20px;
  overflow-x: auto;
}

.tab-item {
  cursor: pointer;
  position: relative;
  transition: all 0.3s;
  white-space: nowrap;
}

.tab-item:hover {
  color: var(--text-main);
}

.tab-item.active {
  color: var(--text-main);
  font-weight: bold;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 50%;
  transform: translateX(-50%);
  width: 28px;
  height: 3px;
  background: var(--primary-red);
  border-radius: 2px;
}
</style>
