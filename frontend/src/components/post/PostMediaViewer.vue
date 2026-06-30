<script setup>
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
    class="flex-[1.2] bg-black flex items-center justify-center relative select-none"
  >
    <template v-if="mediaItems.length">
      <video
        v-if="mediaItems[currentIndex].type === 'video'"
        :src="mediaItems[currentIndex].url"
        class="max-w-full max-h-full object-contain"
        controls
        autoplay
        loop
      ></video>
      <img
        v-else
        :src="mediaItems[currentIndex].url"
        alt="Detail Image"
        class="w-full h-full object-contain"
      />

      <button
        v-if="mediaItems.length > 1"
        class="absolute left-3 top-1/2 -translate-y-1/2 w-10 h-10 bg-black/40 text-white rounded-full flex items-center justify-center cursor-pointer hover:bg-black/60 text-2xl"
        @click="prev"
      >
        ‹
      </button>
      <button
        v-if="mediaItems.length > 1"
        class="absolute right-3 top-1/2 -translate-y-1/2 w-10 h-10 bg-black/40 text-white rounded-full flex items-center justify-center cursor-pointer hover:bg-black/60 text-2xl"
        @click="next"
      >
        ›
      </button>

      <div
        v-if="mediaItems.length > 1"
        class="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2"
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
