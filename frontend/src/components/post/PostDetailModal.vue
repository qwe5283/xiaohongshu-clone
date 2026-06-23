<script setup>
import { ref, onMounted } from 'vue'

const props = defineProps({
  postId: {
    type: Number,
    required: true
  }
})

const emit = defineEmits(['close'])

// 模拟笔记详情数据
const post = ref({
  id: props.postId,
  title: '求秒睡教程',
  content: '一直睡眠都不好，现在已经到每天在床上硬躺3h都睡不着的程度了。。。咋办啊。。。甚至现在午休都睡不着我求了 #睡不着 #想睡个好觉 #我的命也是命 #睡不着一点',
  coverImage: 'https://picsum.photos/600/800?random=50',
  author: {
    nickname: 'AAA_方糕批发商61酱',
    avatar: 'https://picsum.photos/50/50?random=60'
  },
  createTime: '05-22',
  likeCount: 2839,
  collectCount: 1465,
  commentCount: 491
})

const comments = ref([
  {
    id: 1,
    author: {
      nickname: 'AAA_方糕批发商61酱',
      avatar: 'https://picsum.photos/30/30?random=60',
      isAuthor: true
    },
    content: '昨天晚上再创新高，5点都没睡着。。。'
  },
  {
    id: 2,
    author: {
      nickname: 'yang 🌿',
      avatar: 'https://picsum.photos/30/30?random=61',
      isAuthor: false
    },
    content: '强烈推荐黄芪麦冬枸杞红枣煮水喝，每次煮二十分钟，我一直失眠，只喝了两天晚上就倒头大睡了，见效特别快'
  }
])

const commentText = ref('')

const handleClose = () => {
  emit('close')
}

const handleComment = () => {
  if (commentText.value.trim()) {
    console.log('发表评论:', commentText.value)
    commentText.value = ''
  }
}
</script>

<template>
  <div class="modal-overlay">
    <div class="close-modal" @click="handleClose">X</div>
    <div class="modal-content">
      <!-- 左侧图片 -->
      <div class="modal-left">
        <img :src="post.coverImage" alt="Detail Image" />
      </div>

      <!-- 右侧内容 -->
      <div class="modal-right">
        <!-- 作者信息 -->
        <div class="modal-header">
          <div class="modal-user">
            <img :src="post.author.avatar" />
            <span class="modal-user-name">{{ post.author.nickname }}</span>
          </div>
          <button class="btn-follow">关注</button>
        </div>

        <!-- 内容区域 -->
        <div class="modal-body">
          <div class="modal-title">{{ post.title }}</div>
          <div class="modal-text">{{ post.content }}</div>
          <div class="modal-date">编辑于 {{ post.createTime }}</div>

          <div style="font-size: 12px; color: #999; margin-bottom: 10px;">
            共 {{ post.commentCount }} 条评论
          </div>

          <!-- 评论列表 -->
          <div class="comments-section">
            <div v-for="comment in comments" :key="comment.id" class="comment-item">
              <div class="comment-header">
                <img :src="comment.author.avatar" />
                <span class="comment-name">
                  {{ comment.author.nickname }}
                  <span v-if="comment.author.isAuthor" class="author-tag">作者</span>
                </span>
              </div>
              <div class="comment-text">{{ comment.content }}</div>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div class="modal-footer">
          <input
            v-model="commentText"
            type="text"
            class="input-comment"
            placeholder="说点什么..."
            @keyup.enter="handleComment"
          />
          <div class="action-icons">
            <div class="action-item">
              <span class="icon-svg">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16">
                  <path fill="currentColor" fill-opacity=".8" d="M3.256 3.913a3.083 3.083 0 0 0-.003 4.397L8 12.998l4.743-4.684a3.085 3.085 0 0 0 .001-4.4c-.6-.593-1.4-.914-2.233-.914a3.17 3.17 0 0 0-1.91.635L8 4.087l-.601-.452A3.17 3.17 0 0 0 5.489 3c-.834 0-1.634.321-2.233.913m10.19 5.111-4.748 4.69a.996.996 0 0 1-1.397 0L2.549 9.02a4.083 4.083 0 0 1 .004-5.82A4.17 4.17 0 0 1 5.488 2c.907 0 1.787.29 2.512.835A4.17 4.17 0 0 1 10.51 2c1.093 0 2.146.422 2.936 1.202a4.085 4.085 0 0 1 0 5.822"></path>
                </svg>
              </span>
              {{ post.likeCount }}
            </div>
            <div class="action-item">
              <span class="icon-svg">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                  <path fill="currentColor" d="M18.865 19.503a1.81 1.81 0 0 1-.737 1.649 1.82 1.82 0 0 1-1.8.196L12.2 19.546a.5.5 0 0 0-.4 0l-4.127 1.802a1.82 1.82 0 0 1-1.801-.196 1.81 1.81 0 0 1-.737-1.65l.452-4.384a.5.5 0 0 0-.127-.386l-2.994-3.32a1.81 1.81 0 0 1-.377-1.77c.2-.615.713-1.077 1.347-1.213l4.404-.945a.5.5 0 0 0 .326-.235l2.265-3.853a1.82 1.82 0 0 1 3.138 0l2.265 3.853a.5.5 0 0 0 .326.235l4.404.945a1.808 1.808 0 0 1 .97 2.984l-2.994 3.319a.5.5 0 0 0-.127.386zm-1.662-5.977 2.994-3.32q.004-.004.003-.007-.001-.006-.013-.01l-4.404-.945a2.3 2.3 0 0 1-1.5-1.083l-2.266-3.853Q12.014 4.302 12 4.3q-.015.002-.017.008L9.718 8.161a2.3 2.3 0 0 1-1.5 1.083l-4.405.945q-.012.004-.013.01 0 .003.003.008l2.994 3.32a2.3 2.3 0 0 1 .58 1.776l-.452 4.384q-.001.001.005.01l.01.003h.002q.006 0 .01-.002l4.128-1.801a2.3 2.3 0 0 1 1.84 0l4.127 1.801.012.002.01-.004q.008-.008.006-.009l-.452-4.384a2.3 2.3 0 0 1 .58-1.777"></path>
                </svg>
              </span>
              {{ post.collectCount }}
            </div>
            <div class="action-item">
              <span class="icon-svg">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                  <path fill="currentColor" fill-rule="evenodd" d="m3.925 17.006 1.528-.95c.308.496.389 1.11.199 1.681L5.09 19.42l.237-.044c.44-.08.903-.162 1.299-.223.337-.053.784-.118 1.098-.118.459 0 .968.138 1.255.216l.077.02c.7.187 1.61.429 2.943.429 4.234 0 7.7-3.45 7.7-7.7a7.7 7.7 0 1 0-14.247 4.056zM2.917 20.25a.95.95 0 0 0 .882 1.25q.018 0 .036-.003c.252-.051 3.3-.662 3.89-.662.208 0 .492.076.868.176.739.197 1.833.489 3.407.489 5.225 0 9.5-4.253 9.5-9.5a9.5 9.5 0 1 0-17.576 5.006c.03.049.038.108.02.162z" clip-rule="evenodd"></path>
                </svg>
              </span>
              {{ post.commentCount }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.3);
  z-index: 100;
  display: flex;
  justify-content: center;
  align-items: center;
}

.modal-content {
  width: 1000px;
  height: 700px;
  background: #fff;
  border-radius: 16px;
  display: flex;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0,0,0,0.2);
}

.modal-left {
  flex: 1.2;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.modal-left img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.modal-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 24px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.modal-user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.modal-user img {
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.modal-user-name {
  font-size: 14px;
  font-weight: 500;
}

.modal-body {
  flex: 1;
  overflow-y: auto;
}

.modal-title {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 12px;
}

.modal-text {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-main);
  margin-bottom: 12px;
}

.modal-date {
  font-size: 12px;
  color: var(--text-light);
  margin-bottom: 20px;
}

.comments-section {
  border-top: 1px solid var(--border-color);
  padding-top: 20px;
}

.comment-item {
  margin-bottom: 20px;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.comment-header img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
}

.comment-name {
  font-size: 12px;
  color: var(--text-secondary);
}

.author-tag {
  background: #fee;
  color: red;
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 10px;
}

.comment-text {
  font-size: 14px;
  margin-left: 32px;
}

.modal-footer {
  border-top: 1px solid var(--border-color);
  padding-top: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.input-comment {
  flex: 1;
  background: #f2f2f2;
  border: none;
  border-radius: 20px;
  padding: 10px 16px;
  font-size: 14px;
  outline: none;
}

.action-icons {
  display: flex;
  gap: 16px;
  font-size: 20px;
  color: var(--text-secondary);
}

.action-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  cursor: pointer;
}

.close-modal {
  position: absolute;
  top: 20px;
  left: 20px;
  color: #fff;
  font-size: 24px;
  cursor: pointer;
  z-index: 101;
  background: rgba(0,0,0,0.5);
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
