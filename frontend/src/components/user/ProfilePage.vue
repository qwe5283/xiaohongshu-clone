<script setup>
import { ref, computed, onMounted, watch, inject } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getUserById } from '@/api/auth';
import { getUserPosts } from '@/api/post';
import { getCollectedPosts } from '@/api/collect';
import { getLikedPosts } from '@/api/like';
import { getFollowCount } from '@/api/follow';
import { useUserStore } from '@/stores/user';
import { usePostStore } from '@/stores/post';
import SearchBar from '@/components/layout/SearchBar.vue';
import PageShell from '@/components/layout/PageShell.vue';
import BaseButton from '@/components/common/BaseButton.vue';
import LoadingState from '@/components/common/LoadingState.vue';
import ErrorState from '@/components/common/ErrorState.vue';
import WaterfallPostGrid from '@/components/post/WaterfallPostGrid.vue';
import EditProfileModal from '@/components/user/EditProfileModal.vue';
import maleIcon from '../../assets/icons/male.svg?raw';
import femaleIcon from '../../assets/icons/female.svg?raw';
import { defaultAvatar, formatCompactCount } from '@/utils/format';
import { usePaginatedPosts } from '@/composables/usePaginatedPosts';
import { useFollowToggle } from '@/composables/useFollowToggle';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const postStore = usePostStore();
const openPostDetail = inject('openPostDetail');
const displayRoute = inject('displayRoute', null);
const pageRoute = computed(() => displayRoute?.value || route);

// 路由 id 可能是 'me'（已在上层路由守卫解析，但组件内仍需考虑刷新/边界情况）
const userId = computed(() => Number(pageRoute.value.params.id) || null);
const isMe = computed(() => userStore.userInfo?.id === userId.value);

const loading = ref(false);
const user = ref(null);
const activeTab = ref('notes');
const followStats = ref({ followingCount: 0, followersCount: 0 });
const showEditModal = ref(false);
const { isFollowed, loadFollowStatus, toggleFollow } = useFollowToggle(
  userStore,
  {
    onOptimisticChange: (nextFollowed) => {
      followStats.value.followersCount += nextFollowed ? 1 : -1;
    },
    onRollback: (wasFollowed) => {
      followStats.value.followersCount += wasFollowed ? 1 : -1;
    },
  },
);

const PAGE_SIZE = 20;

const notesList = usePaginatedPosts(
  (params) => getUserPosts(userId.value, params),
  {
    pageSize: PAGE_SIZE,
    onItemsLoaded: (items) => postStore.initPosts(items),
  },
);
const collectsList = usePaginatedPosts(
  (params) => getCollectedPosts(userId.value, params),
  {
    pageSize: PAGE_SIZE,
    onItemsLoaded: (items) => postStore.initPosts(items),
  },
);
const likesList = usePaginatedPosts(
  (params) => getLikedPosts(userId.value, params),
  {
    pageSize: PAGE_SIZE,
    onItemsLoaded: (items) => postStore.initPosts(items),
  },
);

const listByTab = {
  notes: notesList,
  collect: collectsList,
  likes: likesList,
};

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
    if (!isMe.value) loadFollowStatus(userId.value);
    // 默认加载笔记
    await notesList.load(true);
  } catch (e) {
    // request 拦截器已处理错误
  } finally {
    loading.value = false;
  }
};

onMounted(loadProfile);

// 路由参数变化时重新加载（他人主页切换）
watch(
  () => pageRoute.value.params.id,
  () => {
    activeTab.value = 'notes';
    Object.values(listByTab).forEach((list) => list.resetState());
    user.value = null;
    isFollowed.value = false;
    loadProfile();
  },
);

const handleTabChange = async (tab) => {
  activeTab.value = tab;
  const list = listByTab[tab];
  if (list && list.items.value.length === 0) {
    await list.load(true);
  }
};

const handleLoadMore = () => {
  listByTab[activeTab.value]?.load(false);
};

const handleToggleFollow = async () => {
  if (isMe.value || !user.value) return;
  await toggleFollow(userId.value);
};

const handleEditSuccess = () => {
  // 编辑成功后刷新当前页面用户信息
  if (isMe.value && userStore.userInfo) {
    user.value = { ...user.value, ...userStore.userInfo };
  }
};

const goPostDetail = (postId) => {
  openPostDetail(postId);
};

const handleOpenProfile = (targetUserId) => {
  if (!targetUserId) return;
  router.push({ name: 'user-profile', params: { id: targetUserId } });
};

const retryActiveList = () => listByTab[activeTab.value]?.load(true);
</script>

<template>
  <PageShell>
    <!-- 顶部搜索栏 -->
    <header class="sticky top-0 bg-white py-4 z-5">
      <SearchBar variant="compact" />
    </header>

    <!-- 加载中 -->
    <div v-if="loading" class="py-20">
      <LoadingState size="lg" />
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
                  @click="showEditModal = true"
                >
                  编辑资料
                </BaseButton>
              </div>
              <!-- ID -->
              <div class="field-hint mb-2.5">小红书号：{{ user.id }}</div>
              <!-- 简介 -->
              <div
                class="text-sm text-gray-500 mb-2.5 leading-[1.6] whitespace-pre-line"
              >
                {{ user.bio || '这个人很懒，还没有写简介～' }}
              </div>
              <!-- 性别 -->
              <div
                v-if="user.gender && user.gender !== 0"
                class="size-5 rounded-full bg-surface-hover mb-2.5 flex items-center justify-center shrink-0"
              >
                <span
                  class="size-4 [&>svg]:size-4"
                  v-html="user.gender === 1 ? maleIcon : femaleIcon"
                ></span>
              </div>
              <!-- 统计数据 -->
              <div class="flex gap-5 text-sm text-gray-500 mb-5">
                <div>
                  <span class="font-bold text-gray-800 mr-1">{{
                    formatCompactCount(user.followingCount)
                  }}</span
                  >关注
                </div>
                <div>
                  <span class="font-bold text-gray-800 mr-1">{{
                    formatCompactCount(user.followersCount)
                  }}</span
                  >粉丝
                </div>
                <div>
                  <span class="font-bold text-gray-800 mr-1">{{
                    formatCompactCount(user.likeAndCollectCount)
                  }}</span
                  >获赞与收藏
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 标签栏 -->
        <div class="flex justify-center gap-2 mb-[30px]">
          <div
            class="tab-pill"
            :class="activeTab === 'notes' ? 'tab-pill-active' : 'text-gray-500'"
            @click="handleTabChange('notes')"
          >
            笔记
          </div>
          <div
            class="tab-pill"
            :class="
              activeTab === 'collect' ? 'tab-pill-active' : 'text-gray-500'
            "
            @click="handleTabChange('collect')"
          >
            收藏
          </div>
          <div
            v-if="isMe"
            class="tab-pill"
            :class="activeTab === 'likes' ? 'tab-pill-active' : 'text-gray-500'"
            @click="handleTabChange('likes')"
          >
            点赞
          </div>
        </div>

        <ErrorState
          v-if="activeTab === 'notes' && notesList.error.value"
          :message="notesList.error.value"
          @retry="retryActiveList"
        />

        <WaterfallPostGrid
          v-if="activeTab === 'notes' && !notesList.error.value"
          :posts="notesList.items.value"
          :loading-more="notesList.loadingMore.value"
          :has-more="notesList.hasMore.value"
          enable-load-more
          empty-text="暂无笔记"
          @open="goPostDetail"
          @open-profile="handleOpenProfile"
          @load-more="handleLoadMore"
        />

        <ErrorState
          v-if="activeTab === 'collect' && collectsList.error.value"
          :message="collectsList.error.value"
          @retry="retryActiveList"
        />

        <WaterfallPostGrid
          v-if="activeTab === 'collect' && !collectsList.error.value"
          :posts="collectsList.items.value"
          :loading-more="collectsList.loadingMore.value"
          :has-more="collectsList.hasMore.value"
          enable-load-more
          empty-text="暂无收藏"
          @open="goPostDetail"
          @open-profile="handleOpenProfile"
          @load-more="handleLoadMore"
        />

        <ErrorState
          v-if="activeTab === 'likes' && likesList.error.value"
          :message="likesList.error.value"
          @retry="retryActiveList"
        />

        <WaterfallPostGrid
          v-if="activeTab === 'likes' && !likesList.error.value"
          :posts="likesList.items.value"
          :loading-more="likesList.loadingMore.value"
          :has-more="likesList.hasMore.value"
          enable-load-more
          empty-text="暂无点赞"
          @open="goPostDetail"
          @open-profile="handleOpenProfile"
          @load-more="handleLoadMore"
        />
      </section>
    </template>

    <div v-else class="state-panel-column">用户加载失败</div>

    <!-- 编辑资料弹窗 -->
    <EditProfileModal
      v-if="showEditModal"
      @close="showEditModal = false"
      @update-success="handleEditSuccess"
    />
  </PageShell>
</template>
