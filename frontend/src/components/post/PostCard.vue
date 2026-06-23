<script setup>
const props = defineProps({
  post: {
    type: Object,
    required: true
  }
})

const formatLikeCount = (count) => {
  if (count >= 10000) {
    return (count / 10000).toFixed(1) + '万'
  }
  return count.toString()
}
</script>

<template>
  <div class="card" :class="{ 'bg-yellow': post.isTextCard }">
    <!-- 图片卡片 -->
    <div v-if="!post.isTextCard && post.coverImage" class="card-img-wrapper">
      <img :src="post.coverImage" class="card-img" alt="cover" />
    </div>

    <!-- 纯文字卡片 -->
    <div v-if="post.isTextCard" class="card-content text-card-content">
      <div class="card-title" style="font-size: 18px; margin-bottom: 20px;">{{ post.title }}</div>
      <div style="font-size: 40px;">😂</div>
    </div>

    <!-- 普通卡片内容 -->
    <div v-if="!post.isTextCard" class="card-content">
      <div class="card-title">{{ post.title }}</div>
    </div>

    <!-- 底部信息 -->
    <div class="card-footer">
      <div class="user-info">
        <img :src="post.author.avatar" class="avatar-small" />
        <span>{{ post.author.nickname }}</span>
      </div>
      <div class="like-info">
        <span class="icon-svg">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16">
            <path fill="currentColor" fill-opacity=".8" d="M3.256 3.913a3.083 3.083 0 0 0-.003 4.397L8 12.998l4.743-4.684a3.085 3.085 0 0 0 .001-4.4c-.6-.593-1.4-.914-2.233-.914a3.17 3.17 0 0 0-1.91.635L8 4.087l-.601-.452A3.17 3.17 0 0 0 5.489 3c-.834 0-1.634.321-2.233.913m10.19 5.111-4.748 4.69a.996.996 0 0 1-1.397 0L2.549 9.02a4.083 4.083 0 0 1 .004-5.82A4.17 4.17 0 0 1 5.488 2c.907 0 1.787.29 2.512.835A4.17 4.17 0 0 1 10.51 2c1.093 0 2.146.422 2.936 1.202a4.085 4.085 0 0 1 0 5.822"></path>
          </svg>
        </span>
        {{ formatLikeCount(post.likeCount) }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 20px;
  break-inside: avoid;
  border: none;
  box-shadow: none;
  cursor: pointer;
}

.card:hover {
  transform: none;
  box-shadow: none;
}

.card-img-wrapper {
  margin: 4px;
  position: relative;
  overflow: hidden;
  border-radius: 12px;
  box-shadow: 0 0 1px rgba(0,0,0,0.6);
}

.card-img {
  width: 100%;
  display: block;
  object-fit: cover;
  transition: transform 0.3s;
}

.card-img-wrapper::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(to bottom, rgba(0,0,0,0.05), rgba(0,0,0,0.25));
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.card:hover .card-img-wrapper::after {
  opacity: 1;
}

.card-content {
  padding: 12px;
}

.text-card-content {
  padding: 40px 20px;
  text-align: center;
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-secondary);
  padding: 0 12px 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
}

.avatar-small {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
}

.like-info {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
