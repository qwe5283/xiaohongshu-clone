<script setup>
import { ref, computed, onUnmounted } from 'vue';
import { uploadImage, uploadVideo } from '@/api/upload';
import { createPost, generateTextImage } from '@/api/post';
import { useUserStore } from '@/stores/user';
import { showToast } from '@/utils/toast';
import closeIcon from '../../assets/icons/close.svg?raw';
import BaseButton from '@/components/common/BaseButton.vue';
import BaseInput from '@/components/common/BaseInput.vue';
import BaseModal from '@/components/common/BaseModal.vue';

const emit = defineEmits(['close', 'publish-success']);
const userStore = useUserStore();

// ---- 表单字段 ----
const title = ref('');
const content = ref('');
const imageFiles = ref([]); // File 对象数组
const videoFile = ref(null); // File 对象 | null
const errorMsg = ref('');
const submitting = ref(false);

// ---- 本地预览 URL（用 createObjectURL，删除/卸载时主动释放）----
const imagePreviews = ref([]);

function appendImageFiles(files) {
  const previews = files.map((file) => ({
    file,
    url: URL.createObjectURL(file),
  }));
  imagePreviews.value = [...imagePreviews.value, ...previews];
  imageFiles.value = imagePreviews.value.map((item) => item.file);
}

function revokePreview(preview) {
  if (preview?.url) URL.revokeObjectURL(preview.url);
}

// ---- 文件选择 input 引用 ----
const imageInputRef = ref(null);
const videoInputRef = ref(null);

// ---- 字符计数 ----
const titleRemaining = computed(() => 200 - title.value.length);
const contentRemaining = computed(() => 10000 - content.value.length);

// ---- 校验 ----
function validate() {
  const t = title.value.trim();
  if (!t) {
    errorMsg.value = '请输入笔记标题';
    return false;
  }
  if (t.length > 200) {
    errorMsg.value = '标题不能超过200个字符';
    return false;
  }
  if (content.value.length > 10000) {
    errorMsg.value = '正文不能超过10000个字符';
    return false;
  }
  if (imageFiles.value.length === 0 && !videoFile.value) {
    errorMsg.value = '请至少上传一张图片或一个视频';
    return false;
  }
  errorMsg.value = '';
  return true;
}

// ---- 图片操作 ----
function triggerImageInput() {
  if (imageFiles.value.length >= 9) {
    showToast('最多上传9张图片', 'info');
    return;
  }
  imageInputRef.value?.click();
}

function handleImageChange(e) {
  const files = Array.from(e.target.files || []);
  const remaining = 9 - imageFiles.value.length;
  const toAdd = files.slice(0, remaining);
  appendImageFiles(toAdd);
  // 重置 input 以便重复选择同一文件
  if (imageInputRef.value) imageInputRef.value.value = '';
}

function removeImage(index) {
  revokePreview(imagePreviews.value[index]);
  imagePreviews.value = imagePreviews.value.filter((_, i) => i !== index);
  imageFiles.value = imagePreviews.value.map((item) => item.file);
}

// ---- 视频操作 ----
function triggerVideoInput() {
  videoInputRef.value?.click();
}

function handleVideoChange(e) {
  const files = e.target.files;
  if (files && files.length > 0) {
    videoFile.value = files[0];
  }
  if (videoInputRef.value) videoInputRef.value.value = '';
}

function removeVideo() {
  videoFile.value = null;
}

// ---- 文本配图 ----
const generatingImage = ref(false);

async function handleGenerateImage() {
  const text = title.value.trim();
  if (!text) {
    showToast('请先输入标题', 'info');
    return;
  }
  if (imageFiles.value.length >= 9) {
    showToast('最多上传9张图片', 'info');
    return;
  }
  const genText = text.length > 20 ? text.slice(0, 20) : text;
  generatingImage.value = true;
  try {
    const blob = await generateTextImage(genText);
    const file = new File([blob], `cover-${Date.now()}.png`, {
      type: 'image/png',
    });
    appendImageFiles([file]);
  } catch (e) {
    showToast('生成配图失败，请稍后重试', 'error');
  } finally {
    generatingImage.value = false;
  }
}

// ---- 视频首帧截取 ----
function extractVideoFrame(videoFile) {
  return new Promise((resolve, reject) => {
    const video = document.createElement('video');
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');

    video.preload = 'metadata';
    video.muted = true;
    video.playsInline = true;

    const cleanup = () => {
      video.pause();
      URL.revokeObjectURL(video.src);
    };

    video.onloadeddata = () => {
      // 跳过可能的纯黑首帧
      video.currentTime = 1;
    };

    video.onseeked = () => {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      ctx.drawImage(video, 0, 0, video.videoWidth, video.videoHeight);
      cleanup();
      canvas.toBlob(
        (blob) => {
          if (blob) {
            resolve(
              new File([blob], `cover-${Date.now()}.jpg`, {
                type: 'image/jpeg',
              }),
            );
          } else {
            reject(new Error('无法提取视频帧'));
          }
        },
        'image/jpeg',
        0.9,
      );
    };

    video.onerror = () => {
      cleanup();
      reject(new Error('视频加载失败'));
    };

    video.src = URL.createObjectURL(videoFile);
  });
}

// ---- 提交 ----
async function handleSubmit() {
  errorMsg.value = '';
  if (!userStore.isLoggedIn) {
    showToast('请先登录', 'error');
    return;
  }
  if (!validate()) return;

  submitting.value = true;
  try {
    // 1) 上传图片
    const imageUrls = [];
    for (const file of imageFiles.value) {
      const res = await uploadImage(file);
      imageUrls.push(res.url);
    }

    // 2) 上传视频
    let videoUrl = '';
    if (videoFile.value) {
      const res = await uploadVideo(videoFile.value);
      videoUrl = res.url;
    }

    // 3) 视频首帧作为封面（仅视频 + 无图片时触发）
    let coverImageUrl = '';
    if (videoUrl && imageUrls.length === 0) {
      try {
        const coverFile = await extractVideoFrame(videoFile.value);
        const coverRes = await uploadImage(coverFile);
        coverImageUrl = coverRes.url;
      } catch (e) {
        // 截帧失败不阻塞发布，封面降级为空
        console.warn('视频封面提取失败:', e);
      }
    }

    // 4) 创建笔记
    await createPost({
      title: title.value.trim(),
      content: content.value.trim() || undefined,
      imageUrls: imageUrls.length > 0 ? imageUrls : undefined,
      videoUrl: videoUrl || undefined,
      coverImage: coverImageUrl || undefined,
    });

    showToast('发布成功！', 'success');
    emit('publish-success');
    handleClose();
  } catch (e) {
    // request 拦截器已弹 toast，这里显示在表单下方
    errorMsg.value = e.message || '发布失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}

function handleClose() {
  if (submitting.value) return;
  emit('close');
}

// 清理 objectURL，避免内存泄漏
onUnmounted(() => {
  imagePreviews.value.forEach(revokePreview);
});
</script>

<template>
  <BaseModal
    width-class="w-[640px]"
    panel-class="p-8 max-w-[92%] max-h-[90vh] overflow-y-auto"
    @close="handleClose"
  >
    <!-- 关闭按钮 -->
    <button
      class="absolute top-4 right-4 bg-transparent border-none text-2xl cursor-pointer text-gray-500 p-0 size-8 flex items-center justify-center rounded-full transition-colors duration-300 hover:bg-gray-100"
      @click="handleClose"
    >
      <span class="[&>svg]:size-4 text-gray-500" v-html="closeIcon"></span>
    </button>

    <h2 class="text-xl font-bold text-gray-800 mb-6 text-center">发布笔记</h2>

    <!-- 错误提示 -->
    <div
      v-if="errorMsg"
      class="mb-4 text-sm text-red-500 text-center bg-red-50 rounded-lg py-2 px-3"
    >
      {{ errorMsg }}
    </div>

    <!-- 标题 -->
    <div class="mb-5">
      <label class="block text-sm font-medium text-gray-600 mb-1.5"
        >标题 <span class="text-red-400">*</span></label
      >
      <BaseInput
        v-model="title"
        variant="field"
        type="text"
        maxlength="200"
        placeholder="笔记标题（必填）"
      />
      <div class="text-xs text-gray-400 mt-1 text-right">
        {{ titleRemaining }}
      </div>
      <button
        class="mt-2 text-xs text-primary border border-primary/30 rounded-full px-3 py-1 cursor-pointer hover:bg-red-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        :disabled="generatingImage || !title.trim()"
        @click="handleGenerateImage"
      >
        {{ generatingImage ? '生成中...' : '✨ AI 配图' }}
      </button>
    </div>

    <!-- 正文 -->
    <div class="mb-5">
      <label class="block text-sm font-medium text-gray-600 mb-1.5">正文</label>
      <BaseInput
        v-model="content"
        multiline
        maxlength="10000"
        rows="4"
        placeholder="分享你的想法..."
      />
      <div class="text-xs text-gray-400 mt-1 text-right">
        {{ contentRemaining }}
      </div>
    </div>

    <!-- 图片上传 -->
    <div class="mb-5">
      <label class="block text-sm font-medium text-gray-600 mb-2"
        >图片 <span class="text-xs text-gray-400">(最多9张)</span></label
      >
      <div class="flex flex-wrap gap-2">
        <!-- 已选图片预览 -->
        <div
          v-for="(preview, idx) in imagePreviews"
          :key="idx"
          class="relative size-20 rounded-lg overflow-hidden bg-gray-100 flex-shrink-0"
        >
          <img
            :src="preview.url"
            class="w-full h-full object-cover"
            alt="preview"
          />
          <button
            class="absolute top-0.5 right-0.5 size-5 bg-black/50 text-white rounded-full flex items-center justify-center cursor-pointer text-xs leading-none"
            @click="removeImage(idx)"
          >
            ×
          </button>
        </div>
        <!-- 添加按钮 -->
        <button
          v-if="imageFiles.length < 9"
          class="size-20 rounded-lg border-2 border-dashed border-gray-300 flex items-center justify-center cursor-pointer hover:border-primary hover:bg-red-50 transition-colors flex-shrink-0"
          @click="triggerImageInput"
        >
          <span class="text-2xl text-gray-400">+</span>
        </button>
      </div>
      <input
        ref="imageInputRef"
        type="file"
        accept="image/*"
        multiple
        class="hidden"
        @change="handleImageChange"
      />
    </div>

    <!-- 视频上传 -->
    <div class="mb-6">
      <label class="block text-sm font-medium text-gray-600 mb-2"
        >视频
        <span class="text-xs text-gray-400">(最多1个, 200MB以内)</span></label
      >
      <div
        v-if="videoFile"
        class="flex items-center gap-2 bg-[#F7F7F7] rounded-xl px-4 py-3"
      >
        <span class="text-sm text-gray-700 truncate flex-1">{{
          videoFile.name
        }}</span>
        <button
          class="text-red-400 text-sm cursor-pointer hover:text-red-600 shrink-0"
          @click="removeVideo"
        >
          移除
        </button>
      </div>
      <button
        v-else
        class="flex items-center gap-2 text-sm text-gray-500 bg-[#F7F7F7] rounded-xl px-4 py-3 cursor-pointer hover:bg-[#EEEEEE] transition-colors"
        @click="triggerVideoInput"
      >
        <span class="text-lg">+</span> 选择视频文件
      </button>
      <input
        ref="videoInputRef"
        type="file"
        accept="video/*"
        class="hidden"
        @change="handleVideoChange"
      />
    </div>

    <!-- 提交按钮 -->
    <BaseButton block :disabled="submitting" @click="handleSubmit">
      {{ submitting ? '发布中...' : '发布笔记' }}
    </BaseButton>
  </BaseModal>
</template>
