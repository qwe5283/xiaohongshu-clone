<script setup>
import { ref } from 'vue'
import heartIcon from '../../assets/icons/heart.svg?raw'

const emit = defineEmits(['back'])

const activeTab = ref('notes')

const isFollowed = ref(true)

const user = ref({
  name: '紫咲さとり',
  avatar: 'https://picsum.photos/200/200?random=20',
  xhsId: '63571447583',
  ipLocation: '四川',
  bio: '画师修行中\n成分复杂：明日方舟/车万/Ar tonelico/egoist/FF14/想到再加\nB/mhs: 紫咲さとり\n更多见置顶',
  following: 57,
  followers: 400,
  likes: 6840
})

const notes = ref([
  {
    id: 1,
    title: '置顶笔记：角色设计展示',
    coverImage: 'https://picsum.photos/300/400?random=30'
  },
  {
    id: 2,
    title: '新绘制的二次元少女',
    coverImage: 'https://picsum.photos/300/400?random=31'
  },
  {
    id: 3,
    title: '能天使擦手巾\n端午节后正式\n开始发货',
    isTextCard: true
  },
  {
    id: 4,
    title: '大货全部错乱处理中',
    coverImage: 'https://picsum.photos/300/400?random=32'
  },
  {
    id: 5,
    title: '春山漫 - 双角色立绘',
    coverImage: 'https://picsum.photos/300/400?random=33',
    likeCount: 7
  }
])

const toggleFollow = () => {
  isFollowed.value = !isFollowed.value
}
</script>

<template>
  <div class="ml-[164px] flex-1 px-10 max-w-[1600px] bg-white">
    <!-- 顶部搜索栏 -->
    <header class="sticky top-0 bg-white py-7 z-[5]">
      <div class="bg-white border border-[#d9d9d9] rounded-[28px] px-[18px] py-3 flex items-center text-gray-400 max-w-[900px] mx-auto shadow-[0_4px_12px_rgba(0,0,0,0.06)]">
        <input type="text" placeholder="登录探索更多内容" class="border-none outline-none flex-1 text-base bg-transparent placeholder:text-gray-300" />
        <div class="size-8 bg-gray-800 rounded-full flex items-center justify-center cursor-pointer">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" class="size-5 fill-white"><path d="M11.5 3a8.5 8.5 0 0 1 6.613 13.84l3.023 3.024a.9.9 0 1 1-1.272 1.272l-3.024-3.023A8.5 8.5 0 1 1 11.5 3m0 15.2a6.7 6.7 0 1 0 0-13.4 6.7 6.7 0 0 0 0 13.4"/></svg>
        </div>
      </div>
    </header>

    <!-- 个人资料区域 -->
    <section class="bg-white p-10 rounded-2xl mb-16 border border-gray-200">
      <!-- 头像和信息 -->
      <div class="flex gap-10 mb-10 pb-5 border-b border-gray-200">
        <img :src="user.avatar" class="size-[120px] rounded-full object-cover shrink-0" />
        <div class="flex-1">
          <!-- 名字行 -->
          <div class="flex items-center gap-5 mb-2.5">
            <span class="text-2xl font-bold">{{ user.name }}</span>
            <button
              class="border border-primary w-24 px-4 py-2 rounded-[20px] text-sm font-bold cursor-pointer transition-all duration-200"
              :class="isFollowed ? 'bg-white text-primary' : 'bg-primary text-white'"
              @click="toggleFollow"
            >
              {{ isFollowed ? '已关注' : '关注' }}
            </button>
          </div>
          <!-- ID 和 IP -->
          <div class="text-xs text-gray-400 mb-2.5">小红书号：{{ user.xhsId }} | IP属地：{{ user.ipLocation }}</div>
          <!-- 简介 -->
          <div class="text-sm text-gray-500 mb-5 leading-[1.6] whitespace-pre-line">{{ user.bio }}</div>
          <!-- 统计数据 -->
          <div class="flex gap-[30px] text-sm">
            <div><span class="font-bold text-gray-800 mr-1">{{ user.following }}</span>关注</div>
            <div><span class="font-bold text-gray-800 mr-1">{{ user.followers }}</span>粉丝</div>
            <div><span class="font-bold text-gray-800 mr-1">{{ user.likes }}</span>获赞与收藏</div>
          </div>
        </div>
      </div>

      <!-- 标签栏 -->
      <div class="flex justify-center gap-10 mb-[30px]">
        <div
          class="text-base cursor-pointer pb-2.5 transition-colors duration-200"
          :class="activeTab === 'notes' ? 'text-gray-800 font-bold border-b-2 border-gray-800' : 'text-gray-500'"
          @click="activeTab = 'notes'"
        >笔记</div>
        <div
          class="text-base cursor-pointer pb-2.5 transition-colors duration-200"
          :class="activeTab === 'collect' ? 'text-gray-800 font-bold border-b-2 border-gray-800' : 'text-gray-500'"
          @click="activeTab = 'collect'"
        >收藏</div>
      </div>

      <!-- 笔记网格 -->
      <div class="grid grid-cols-5 gap-5">
        <div
          v-for="note in notes"
          :key="note.id"
          class="bg-white rounded-xl overflow-hidden cursor-pointer"
          :class="{ 'flex items-center justify-center h-[200px] border border-gray-100': note.isTextCard }"
        >
          <template v-if="note.isTextCard">
            <div class="text-center font-bold text-lg whitespace-pre-line">{{ note.title }}</div>
          </template>
          <template v-else>
            <div class="m-1 relative overflow-hidden rounded-xl shadow-[0_0_1px_rgba(0,0,0,0.6)] group">
              <img :src="note.coverImage" class="w-full block object-cover transition-transform duration-300 group-hover:scale-105" />
              <div class="absolute inset-0 bg-gradient-to-b from-black/5 to-black/25 opacity-0 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none"></div>
            </div>
            <div class="p-3">
              <div class="text-sm font-medium leading-[1.4] line-clamp-2">{{ note.title }}</div>
              <div v-if="note.likeCount" class="flex justify-end items-center text-xs text-gray-500 mt-2">
                <span class="size-4 [&>svg]:size-4 mr-1" v-html="heartIcon"></span>
                {{ note.likeCount }}
              </div>
            </div>
          </template>
        </div>
      </div>
    </section>
  </div>
</template>
