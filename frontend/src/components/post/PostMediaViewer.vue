<script setup>
import { computed, ref, watch } from 'vue';
import chevronLeftIcon from '../../assets/icons/chevron-left.svg?raw';
import chevronRightIcon from '../../assets/icons/chevron-right.svg?raw';

const props = defineProps({
  mediaItems: {
    type: Array,
    default: () => [],
  },
  currentIndex: {
    type: Number,
    default: 0,
  },
});

const emit = defineEmits(['update:currentIndex']);
const slideDirection = ref('next');
const hovering = ref(false);

const currentMediaItem = computed(() => props.mediaItems[props.currentIndex]);
const mediaTransitionName = computed(() =>
  slideDirection.value === 'next' ? 'media-slide-next' : 'media-slide-prev',
);
const currentMediaKey = computed(() => {
  if (!currentMediaItem.value) return 'empty';
  return `${props.currentIndex}-${currentMediaItem.value.type}-${currentMediaItem.value.url}`;
});

watch(
  () => props.currentIndex,
  (index, previousIndex) => {
    if (previousIndex === undefined || index === previousIndex) return;
    slideDirection.value = index > previousIndex ? 'next' : 'prev';
  },
);

function setIndex(index) {
  const lastIndex = props.mediaItems.length - 1;
  emit('update:currentIndex', Math.max(0, Math.min(lastIndex, index)));
}

function prev() {
  setIndex(props.currentIndex - 1);
}

function next() {
  setIndex(props.currentIndex + 1);
}
</script>

<template>
  <div
    class="flex-[1.2] bg-black flex items-center justify-center relative select-none overflow-hidden"
    @mouseenter="hovering = true"
    @mouseleave="hovering = false"
  >
    <template v-if="mediaItems.length && currentMediaItem">
      <Transition :name="mediaTransitionName">
        <div
          :key="currentMediaKey"
          class="absolute inset-0 flex items-center justify-center"
        >
          <video
            v-if="currentMediaItem.type === 'video'"
            :src="currentMediaItem.url"
            class="w-full h-full object-contain"
            controls
            autoplay
            loop
          ></video>
          <img
            v-else
            :src="currentMediaItem.url"
            alt="Detail Image"
            class="w-full h-full object-contain"
          />
        </div>
      </Transition>

      <div
        v-if="mediaItems.length > 1"
        class="absolute top-3 right-3 z-10 px-2.5 py-1 bg-black/40 text-white text-xs rounded-full select-none transition-opacity duration-200"
        :class="hovering ? 'opacity-100' : 'opacity-0'"
      >
        {{ currentIndex + 1 }}/{{ mediaItems.length }}
      </div>

      <button
        v-if="mediaItems.length > 1"
        class="absolute left-3 top-1/2 z-10 -translate-y-1/2 w-10 h-10 bg-black/40 text-white rounded-full flex items-center justify-center cursor-pointer hover:bg-black/60 transition-opacity duration-200"
        :class="hovering ? 'opacity-100' : 'opacity-0'"
        @click="prev"
      >
        <span class="w-4 h-4 [&>svg]:w-4 [&>svg]:h-4" v-html="chevronLeftIcon"></span>
      </button>
      <button
        v-if="mediaItems.length > 1"
        class="absolute right-3 top-1/2 z-10 -translate-y-1/2 w-10 h-10 bg-black/40 text-white rounded-full flex items-center justify-center cursor-pointer hover:bg-black/60 transition-opacity duration-200"
        :class="hovering ? 'opacity-100' : 'opacity-0'"
        @click="next"
      >
        <span class="w-4 h-4 [&>svg]:w-4 [&>svg]:h-4" v-html="chevronRightIcon"></span>
      </button>

      <div
        v-if="mediaItems.length > 1"
        class="absolute bottom-4 left-1/2 z-10 -translate-x-1/2 flex gap-2"
      >
        <span
          v-for="(item, idx) in mediaItems"
          :key="idx"
          class="w-2 h-2 rounded-full cursor-pointer"
          :class="idx === currentIndex ? 'bg-white' : 'bg-white/40'"
          @click="setIndex(idx)"
        ></span>
      </div>
    </template>
    <div v-else class="text-white">暂无图片</div>
  </div>
</template>

<style scoped>
.media-slide-next-enter-active,
.media-slide-next-leave-active,
.media-slide-prev-enter-active,
.media-slide-prev-leave-active {
  transition:
    transform 0.56s ease,
    opacity 0.56s ease;
}

.media-slide-next-enter-from {
  opacity: 0.85;
  transform: translateX(100%);
}

.media-slide-next-leave-to {
  opacity: 0.85;
  transform: translateX(-100%);
}

.media-slide-prev-enter-from {
  opacity: 0.85;
  transform: translateX(-100%);
}

.media-slide-prev-leave-to {
  opacity: 0.85;
  transform: translateX(100%);
}
</style>
