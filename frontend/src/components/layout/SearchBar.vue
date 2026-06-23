<script setup>
import { ref } from 'vue'

const searchQuery = ref('')

const handleSearch = () => {
  console.log('搜索:', searchQuery.value)
}
</script>

<template>
  <div class="search-bar">
    <svg class="search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
      <path d="M11.5 3a8.5 8.5 0 0 1 6.613 13.84l3.023 3.024a.9.9 0 1 1-1.272 1.272l-3.024-3.023A8.5 8.5 0 1 1 11.5 3m0 15.2a6.7 6.7 0 1 0 0-13.4 6.7 6.7 0 0 0 0 13.4"/>
    </svg>
    <input
      v-model="searchQuery"
      type="text"
      placeholder="登录探索更多内容"
      @keyup.enter="handleSearch"
    />
    <div class="search-btn" @click="handleSearch">
      <svg class="search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
        <path d="M11.5 3a8.5 8.5 0 0 1 6.613 13.84l3.023 3.024a.9.9 0 1 1-1.272 1.272l-3.024-3.023A8.5 8.5 0 1 1 11.5 3m0 15.2a6.7 6.7 0 1 0 0-13.4 6.7 6.7 0 0 0 0 13.4"/>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.search-bar {
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 28px;
  padding: 12px 18px;
  display: flex;
  align-items: center;
  color: var(--text-light);
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
  box-shadow: 0 4px 12px rgba(0,0,0,0.06);
}

.search-bar input {
  border: none;
  outline: none;
  flex: 1;
  margin-left: 10px;
  font-size: 16px;
  background: transparent;
}

.search-bar input::placeholder {
  color: lightgray;
}

.search-icon {
  width: 20px;
  height: 20px;
  fill: currentColor;
}

.search-btn {
  width: 32px;
  height: 32px;
  background: #333;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  margin-left: 8px;
}

.search-btn .search-icon {
  fill: #fff;
}
</style>
