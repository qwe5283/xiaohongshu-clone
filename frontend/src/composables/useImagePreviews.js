import { ref, onUnmounted } from 'vue';

export function useImagePreviews(maxCount = 9) {
  const imagePreviews = ref([]);
  const imageFiles = ref([]);

  function syncFiles() {
    imageFiles.value = imagePreviews.value.map((item) => item.file);
  }

  function appendImageFiles(files) {
    const remaining = maxCount - imagePreviews.value.length;
    const previews = files.slice(0, remaining).map((file) => ({
      file,
      url: URL.createObjectURL(file),
    }));
    imagePreviews.value = [...imagePreviews.value, ...previews];
    syncFiles();
  }

  function revokePreview(preview) {
    if (preview?.url) URL.revokeObjectURL(preview.url);
  }

  function removeImage(index) {
    revokePreview(imagePreviews.value[index]);
    imagePreviews.value = imagePreviews.value.filter((_, i) => i !== index);
    syncFiles();
  }

  onUnmounted(() => {
    imagePreviews.value.forEach(revokePreview);
  });

  return {
    imagePreviews,
    imageFiles,
    appendImageFiles,
    removeImage,
  };
}
