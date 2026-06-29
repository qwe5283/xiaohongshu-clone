<script setup>
import { ref, computed, onMounted, watch, inject } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getUserById } from '@/api/auth';
import { getUserPosts } from '@/api/post';
import { getCollectedPosts } from '@/api/collect';
import { getFollowCount, getFollowStatus, toggleFollow } from '@/api/follow';
import { useUserStore } from '@/stores/user';
import { usePostStore } from '@/stores/post';
import { showToast } from '@/utils/toast';
import { adaptPost } from '@/api/post';
import SearchBar from '@/components/layout/SearchBar.vue';
import PageShell from '@/components/layout/PageShell.vue';
import BaseButton from '@/components/common/BaseButton.vue';
import WaterfallPostGrid from '@/components/post/WaterfallPostGrid.vue';
import SearchBarLegacy from "@/components/layout/SearchBarLegacy.vue";

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const postStore = usePostStore();
const openPostDetail = inject('openPostDetail');

// 路由 id 可能是 'me'（已在上层路由守卫解析，但组件内仍需考虑刷新/边界情况）
const userId = computed(() => Number(route.params.id) || null);
const isMe = computed(() => userStore.userInfo?.id === userId.value);

const loading = ref(false);
const user = ref(null);
const activeTab = ref('notes');
const notes = ref([]);
const collects = ref([]);
const notesPageNum = ref(1);
const collectsPageNum = ref(1);
const notesHasMore = ref(true);
const collectsHasMore = ref(true);
const notesLoadingMore = ref(false);
const collectsLoadingMore = ref(false);
const notesError = ref('');
const collectsError = ref('');
const followStats = ref({ followingCount: 0, followersCount: 0 });
const isFollowed = ref(false);

const PAGE_SIZE = 20;

const defaultAvatar =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="%23eee"/><text x="50%" y="55%" text-anchor="middle" font-size="18" fill="%23bbb">U</text></svg>';

const loadProfile = async () => {
  if (!userId.value) return;
  loading.value = true;
  try {
    const [profile, stats] = await Promise.all([
      getUserById(userId.value),
      getFollowCount(userId.value),
    ]);
    user.value = profile;
    followStats.value = stats;
    // 关注状态
    if (userStore.isLoggedIn && !isMe.value) {
      getFollowStatus(userId.value)
        .then((res) => {
          isFollowed.value = !!res.followed;
        })
        .catch(() => {});
    }
    // 默认加载笔记
    await loadNotes(true);
  } catch (e) {
    // request 拦截器已处理错误
  } finally {
    loading.value = false;
  }
};

const loadNotes = async (reset = false) => {
  if (!userId.value) return;
  if (notesLoadingMore.value || (!reset && !notesHasMore.value)) {
    return;
  }

  if (reset) {
    notesPageNum.value = 1;
    notesHasMore.value = true;
    notes.value = [];
  } else {
    notesLoadingMore.value = true;
  }
  notesError.value = '';

  try {
    const page = await getUserPosts(userId.value, {
      pageNum: notesPageNum.value,
      pageSize: PAGE_SIZE,
    });
    const adapted = (page?.records || []).map(adaptPost);
    notes.value = reset ? adapted : [...notes.value, ...adapted];
    postStore.initPosts(adapted);

    const current = Number(page?.current ?? notesPageNum.value);
    const pages = Number(page?.pages ?? 0);
    notesHasMore.value =
      pages > 0 ? current < pages : adapted.length === PAGE_SIZE;
    notesPageNum.value = current + 1;
  } catch (e) {
    notesError.value = e.message || '加载失败';
  } finally {
    notesLoadingMore.value = false;
  }
};

const loadCollects = async (reset = false) => {
  if (!userId.value) return;
  if (collectsLoadingMore.value || (!reset && !collectsHasMore.value)) {
    return;
  }

  if (reset) {
    collectsPageNum.value = 1;
    collectsHasMore.value = true;
    collects.value = [];
  } else {
    collectsLoadingMore.value = true;
  }
  collectsError.value = '';

  try {
    const res = await getCollectedPosts(userId.value, {
      pageNum: collectsPageNum.value,
      pageSize: PAGE_SIZE,
    });
    const adapted = (res?.records || []).map(adaptPost);
    collects.value = reset ? adapted : [...collects.value, ...adapted];
    postStore.initPosts(adapted);

    const current = Number(
      res?.current ?? res?.pageNum ?? collectsPageNum.value,
    );
    const pages = Number(res?.pages ?? 0);
    collectsHasMore.value =
      pages > 0 ? current < pages : adapted.length === PAGE_SIZE;
    collectsPageNum.value = current + 1;
  } catch (e) {
    collectsError.value = e.message || '加载失败';
  } finally {
    collectsLoadingMore.value = false;
  }
};

onMounted(loadProfile);

// 路由参数变化时重新加载（他人主页切换）
watch(
  () => route.params.id,
  () => {
    activeTab.value = 'notes';
    notes.value = [];
    collects.value = [];
    notesPageNum.value = 1;
    collectsPageNum.value = 1;
    notesHasMore.value = true;
    collectsHasMore.value = true;
    notesError.value = '';
    collectsError.value = '';
    user.value = null;
    isFollowed.value = false;
    loadProfile();
  },
);

const handleTabChange = async (tab) => {
  activeTab.value = tab;
  if (tab === 'notes' && notes.value.length === 0) {
    await loadNotes(true);
  }
  if (tab === 'collect' && collects.value.length === 0) {
    await loadCollects(true);
  }
};

const handleLoadMore = () => {
  if (activeTab.value === 'notes') {
    loadNotes(false);
  } else {
    loadCollects(false);
  }
};

const handleToggleFollow = async () => {
  if (isMe.value || !user.value) return;
  if (!userStore.isLoggedIn) {
    showToast('请先登录', 'error');
    return;
  }
  const wasFollowed = isFollowed.value;
  isFollowed.value = !wasFollowed;
  // 乐观更新粉丝数
  if (isFollowed.value) {
    followStats.value.followersCount += 1;
  } else {
    followStats.value.followersCount -= 1;
  }
  try {
    await toggleFollow(userId.value);
    showToast(wasFollowed ? '已取消关注' : '关注成功', 'success');
  } catch (e) {
    isFollowed.value = wasFollowed;
    followStats.value.followersCount += wasFollowed ? 1 : -1;
  }
};

const goPostDetail = (postId) => {
  openPostDetail(postId);
};

const handleOpenProfile = (targetUserId) => {
  if (!targetUserId) return;
  router.push({ name: 'user-profile', params: { id: targetUserId } });
};

const formatCount = (num) => {
  if (num == null) return '0';
  if (num >= 10000) return (num / 10000).toFixed(1) + '万';
  return num.toString();
};
</script>

<template>
  <PageShell>
    <!-- 顶部搜索栏 -->
    <header class="sticky top-0 bg-white py-7 z-[5]">
      <SearchBarLegacy />
    </header>

    <!-- 加载中 -->
    <div v-if="loading" class="flex flex-col items-center py-20 text-gray-400">
      <span
        class="inline-block size-8 border-2 border-gray-300 border-t-primary rounded-full animate-spin mb-3"
      ></span>
      加载中...
    </div>

    <template v-else-if="user">
      <!-- 个人资料区域 -->
      <section class="bg-white p-10 rounded-2xl mb-16">
        <!-- 头像和信息 -->
        <div class="flex justify-center">
          <div class="flex gap-10 mb-10 pb-5">
            <img
              :src="user.avatar || defaultAvatar"
              class="size-40 rounded-full object-cover shrink-0"
            />
            <div class="flex-1">
              <!-- 名字行 -->
              <div class="flex items-center gap-5 mb-2.5 w-[480px]">
                <span class="text-2xl font-bold">{{
                  user.nickname || user.username
                }}</span>
                <BaseButton
                  v-if="!isMe"
                  class="w-24 ml-auto"
                  :variant="isFollowed ? 'outline' : 'primary'"
                  size="sm"
                  @click="handleToggleFollow"
                >
                  {{ isFollowed ? '已关注' : '关注' }}
                </BaseButton>
                <BaseButton
                  v-else
                  class="w-24 ml-auto"
                  variant="outline"
                  size="sm"
                >
                  编辑资料
                </BaseButton>
              </div>
              <!-- ID -->
              <div class="text-xs text-gray-400 mb-2.5">
                小红书号：{{ user.id }}
              </div>
              <!-- 简介 -->
              <div
                class="text-sm text-gray-500 mb-5 leading-[1.6] whitespace-pre-line"
              >
                {{ user.bio || '这个人很懒，还没有写简介～' }}
              </div>
              <!-- 统计数据 -->
              <div class="flex gap-5 text-sm text-gray-500 mb-5">
                <div>
                  <span class="font-bold text-gray-800 mr-1">{{
                    formatCount(user.followingCount)
                  }}</span
                  >关注
                </div>
                <div>
                  <span class="font-bold text-gray-800 mr-1">{{
                    formatCount(user.followersCount)
                  }}</span
                  >粉丝
                </div>
                <div>
                  <span class="font-bold text-gray-800 mr-1">{{
                    formatCount(user.likeAndCollectCount)
                  }}</span
                  >获赞与收藏
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 标签栏 -->
        <div class="flex justify-center gap-10 mb-[30px]">
          <div
            class="text-base cursor-pointer pb-2.5 transition-colors duration-200"
            :class="
              activeTab === 'notes'
                ? 'text-gray-800 font-bold border-b-2 border-gray-800'
                : 'text-gray-500'
            "
            @click="handleTabChange('notes')"
          >
            笔记
          </div>
          <div
            class="text-base cursor-pointer pb-2.5 transition-colors duration-200"
            :class="
              activeTab === 'collect'
                ? 'text-gray-800 font-bold border-b-2 border-gray-800'
                : 'text-gray-500'
            "
            @click="handleTabChange('collect')"
          >
            收藏
          </div>
        </div>

        <div
          v-if="activeTab === 'notes' && notesError"
          class="flex flex-col items-center py-10 text-gray-400"
        >
          <div class="mb-3">{{ notesError }}</div>
          <button
            class="bg-primary text-white px-5 py-2 rounded-full text-sm cursor-pointer"
            @click="loadNotes(true)"
          >
            重试
          </button>
        </div>

        <WaterfallPostGrid
          v-if="activeTab === 'notes' && !notesError"
          :posts="notes"
          :loading-more="notesLoadingMore"
          :has-more="notesHasMore"
          enable-load-more
          empty-text="暂无笔记"
          @open="goPostDetail"
          @open-profile="handleOpenProfile"
          @load-more="handleLoadMore"
        />

        <div
          v-if="activeTab === 'collect' && collectsError"
          class="flex flex-col items-center py-10 text-gray-400"
        >
          <div class="mb-3">{{ collectsError }}</div>
          <button
            class="bg-primary text-white px-5 py-2 rounded-full text-sm cursor-pointer"
            @click="loadCollects(true)"
          >
            重试
          </button>
        </div>

        <WaterfallPostGrid
          v-if="activeTab === 'collect' && !collectsError"
          :posts="collects"
          :loading-more="collectsLoadingMore"
          :has-more="collectsHasMore"
          enable-load-more
          empty-text="暂无收藏"
          @open="goPostDetail"
          @open-profile="handleOpenProfile"
          @load-more="handleLoadMore"
        />
      </section>
    </template>

    <div v-else class="flex flex-col items-center py-20 text-gray-400">
      用户加载失败
    </div>
  </PageShell>
</template>
