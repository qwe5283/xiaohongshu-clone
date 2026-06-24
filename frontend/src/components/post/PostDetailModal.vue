<script setup>
import { ref } from 'vue'
import heartIcon from '../../assets/icons/heart.svg?raw'
import starIcon from '../../assets/icons/star.svg?raw'
import commentIcon from '../../assets/icons/comment.svg?raw'

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
  coverImage: 'https://picsum.photos/1920/1080?random=50',
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
  <div class="fixed inset-0 bg-black/30 z-[100] flex justify-center items-center">
    <div class="absolute top-5 left-5 text-white text-2xl cursor-pointer z-[101] bg-black/50 size-8 rounded-full flex items-center justify-center" @click="handleClose">X</div>
    <div class="w-[1000px] h-[700px] bg-white rounded-2xl flex overflow-hidden shadow-[0_10px_30px_rgba(0,0,0,0.2)]">
      <!-- 左侧图片 -->
      <div class="flex-[1.2] bg-black flex items-center justify-center relative">
        <img :src="post.coverImage" alt="Detail Image" class="max-w-full max-h-full object-contain" />
      </div>

      <!-- 右侧内容 -->
      <div class="flex-1 flex flex-col p-6">
        <!-- 作者信息 -->
        <div class="flex justify-between items-center mb-5">
          <div class="flex items-center gap-2.5">
            <img :src="post.author.avatar" class="size-9 rounded-full" />
            <span class="text-sm font-medium">{{ post.author.nickname }}</span>
          </div>
          <button class="bg-primary text-white border-none px-6 py-2 rounded-[20px] text-sm font-bold cursor-pointer">关注</button>
        </div>

        <!-- 内容区域 -->
        <div class="flex-1 overflow-y-auto">
          <div class="text-lg font-bold mb-3">{{ post.title }}</div>
          <div class="text-sm leading-[1.6] text-gray-800 mb-3">{{ post.content }}</div>
          <div class="text-xs text-gray-400 mb-5">编辑于 {{ post.createTime }}</div>

          <div class="text-xs text-gray-400 mb-2.5">
            共 {{ post.commentCount }} 条评论
          </div>

          <!-- 评论列表 -->
          <div class="border-t border-gray-200 pt-5">
            <div v-for="comment in comments" :key="comment.id" class="mb-5">
              <div class="flex items-center gap-2 mb-1.5">
                <img :src="comment.author.avatar" class="size-6 rounded-full" />
                <span class="text-xs text-gray-500">
                  {{ comment.author.nickname }}
                  <span v-if="comment.author.isAuthor" class="bg-[#fee] text-red-500 px-1 py-0.5 rounded text-[10px]">作者</span>
                </span>
              </div>
              <div class="text-sm ml-8">{{ comment.content }}</div>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div class="border-t border-gray-200 pt-4 flex items-center gap-4">
          <input
            v-model="commentText"
            type="text"
            class="flex-1 bg-gray-100 border-none rounded-[20px] px-4 py-2.5 text-sm outline-none"
            placeholder="说点什么..."
            @keyup.enter="handleComment"
          />
          <div class="flex gap-4 text-gray-500">
            <div class="flex items-center gap-1 text-sm cursor-pointer">
              <span class="size-5 [&>svg]:size-5" v-html="heartIcon"></span>
              {{ post.likeCount }}
            </div>
            <div class="flex items-center gap-1 text-sm cursor-pointer">
              <span class="size-5 [&>svg]:size-5" v-html="starIcon"></span>
              {{ post.collectCount }}
            </div>
            <div class="flex items-center gap-1 text-sm cursor-pointer">
              <span class="size-5 [&>svg]:size-5" v-html="commentIcon"></span>
              {{ post.commentCount }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
