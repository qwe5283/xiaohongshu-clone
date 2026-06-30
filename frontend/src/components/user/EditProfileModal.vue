<script setup>
import { ref, computed, onUnmounted } from 'vue';
import { useUserStore } from '@/stores/user';
import { uploadImage } from '@/api/upload';
import { showToast } from '@/utils/toast';
import closeIcon from '../../assets/icons/close.svg?raw';
import BaseButton from '@/components/common/BaseButton.vue';
import BaseInput from '@/components/common/BaseInput.vue';
import BaseModal from '@/components/common/BaseModal.vue';
import { defaultAvatar } from '@/utils/format';

const emit = defineEmits(['close', 'update-success']);

const userStore = useUserStore();

// ---- 表单字段（预填当前用户信息）----
const info = userStore.userInfo;
const nickname = ref(info?.nickname || '');
const gender = ref(info?.gender ?? 0);
const email = ref(info?.email || '');
const bio = ref(info?.bio || '');

// ---- 头像相关 ----
const avatarPreview = ref(info?.avatar || '');
const avatarFile = ref(null); // 用户新选择的文件
const avatarInputRef = ref(null);

function triggerAvatarInput() {
  avatarInputRef.value?.click();
}

function handleAvatarChange(e) {
  const files = e.target.files;
  if (files && files.length > 0) {
    const file = files[0];
    // 释放旧预览
    if (avatarFile.value?._previewUrl) {
      URL.revokeObjectURL(avatarFile.value._previewUrl);
    }
    avatarFile.value = file;
    avatarFile.value._previewUrl = URL.createObjectURL(file);
    avatarPreview.value = avatarFile.value._previewUrl;
  }
  if (avatarInputRef.value) avatarInputRef.value.value = '';
}

// ---- 字符计数 ----
const nicknameRemaining = computed(() => 20 - nickname.value.length);
const bioRemaining = computed(() => 200 - bio.value.length);

// ---- 状态 ----
const loading = ref(false);
const errorMsg = ref('');

// ---- 性别选项 ----
const genderOptions = [
  { value: 0, label: '未知' },
  { value: 1, label: '男' },
  { value: 2, label: '女' },
];

// ---- 校验 ----
function validate() {
  if (nickname.value.length > 20) {
    errorMsg.value = '昵称不能超过20个字符';
    return false;
  }
  if (bio.value.length > 200) {
    errorMsg.value = '个人简介不能超过200个字符';
    return false;
  }
  errorMsg.value = '';
  return true;
}

// ---- 提交 ----
async function handleSubmit() {
  errorMsg.value = '';
  if (!validate()) return;

  loading.value = true;
  try {
    let avatarUrl = info?.avatar || '';

    // 如果选择了新头像，先上传
    if (avatarFile.value) {
      const res = await uploadImage(avatarFile.value);
      avatarUrl = res.url;
    }

    // 构建更新数据：只传有变化的字段
    const data = {};
    if (nickname.value !== (info?.nickname || '')) {
      data.nickname = nickname.value.trim() || undefined;
    }
    if (gender.value !== (info?.gender ?? 0)) {
      data.gender = gender.value;
    }
    if (email.value !== (info?.email || '')) {
      data.email = email.value.trim() || undefined;
    }
    if (bio.value !== (info?.bio || '')) {
      data.bio = bio.value.trim() || undefined;
    }
    if (avatarUrl !== (info?.avatar || '')) {
      data.avatar = avatarUrl || undefined;
    }

    if (Object.keys(data).length === 0) {
      showToast('没有修改任何信息', 'info');
      emit('close');
      return;
    }

    await userStore.updateProfile(data);
    showToast('保存成功', 'success');
    emit('update-success');
    emit('close');
  } catch (e) {
    errorMsg.value = e.message || '保存失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

function handleClose() {
  if (loading.value) return;
  emit('close');
}

// 清理 objectURL
onUnmounted(() => {
  if (avatarFile.value?._previewUrl) {
    URL.revokeObjectURL(avatarFile.value._previewUrl);
  }
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

    <h2 class="text-xl font-bold text-gray-800 mb-6 text-center">编辑资料</h2>

    <!-- 错误提示 -->
    <div
      v-if="errorMsg"
      class="mb-4 text-sm text-primary text-center bg-red-50 rounded-lg py-2 px-3"
    >
      {{ errorMsg }}
    </div>

    <div class="flex gap-6">
      <!-- 左栏：头像 + 性别 -->
      <div class="flex flex-col items-center gap-5 shrink-0 w-[160px]">
        <!-- 头像 -->
        <div class="flex flex-col items-center gap-3">
          <img
            :src="avatarPreview || defaultAvatar"
            class="size-24 rounded-full object-cover bg-gray-100"
            alt="头像"
          />
          <button
            class="text-sm text-primary cursor-pointer hover:underline"
            @click="triggerAvatarInput"
          >
            更换头像
          </button>
          <input
            ref="avatarInputRef"
            type="file"
            accept="image/*"
            class="hidden"
            @change="handleAvatarChange"
          />
        </div>

        <!-- 性别 -->
        <div class="w-full">
          <label class="block text-sm font-medium text-gray-600 mb-2"
            >性别</label
          >
          <div class="flex gap-1.5">
            <button
              v-for="opt in genderOptions"
              :key="opt.value"
              class="flex-1 py-1.5 rounded-full text-xs font-medium cursor-pointer transition-colors border"
              :class="
                gender === opt.value
                  ? 'bg-primary text-white border-primary'
                  : 'bg-white text-gray-500 border-gray-300 hover:border-primary hover:text-primary'
              "
              @click="gender = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </div>
      </div>

      <!-- 右栏：昵称 + 邮箱 + 简介 + 提交 -->
      <div class="flex-1 flex flex-col gap-4 min-w-0">
        <!-- 昵称 -->
        <div>
          <label class="block text-sm font-medium text-gray-600 mb-1.5"
            >昵称</label
          >
          <BaseInput
            v-model="nickname"
            variant="field"
            type="text"
            maxlength="20"
            placeholder="设置昵称"
          />
          <div class="field-hint mt-1 text-right">
            {{ nicknameRemaining }}
          </div>
        </div>

        <!-- 邮箱 -->
        <div>
          <label class="block text-sm font-medium text-gray-600 mb-1.5"
            >邮箱</label
          >
          <BaseInput
            v-model="email"
            variant="field"
            type="email"
            placeholder="输入邮箱地址"
          />
        </div>

        <!-- 简介 -->
        <div>
          <label class="block text-sm font-medium text-gray-600 mb-1.5"
            >简介</label
          >
          <BaseInput
            v-model="bio"
            variant="field"
            multiline
            maxlength="200"
            rows="3"
            placeholder="介绍一下自己..."
          />
          <div class="field-hint mt-1 text-right">
            {{ bioRemaining }}
          </div>
        </div>

        <!-- 提交 -->
        <BaseButton block :disabled="loading" @click="handleSubmit">
          {{ loading ? '保存中...' : '保存' }}
        </BaseButton>
      </div>
    </div>
  </BaseModal>
</template>
