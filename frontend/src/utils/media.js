export function extractVideoFrame(videoFile) {
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
      video.currentTime = 1;
    };

    video.onseeked = () => {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      ctx.drawImage(video, 0, 0, video.videoWidth, video.videoHeight);
      cleanup();
      canvas.toBlob(
        (blob) => {
          if (!blob) {
            reject(new Error('无法提取视频帧'));
            return;
          }
          resolve(
            new File([blob], `cover-${Date.now()}.jpg`, {
              type: 'image/jpeg',
            }),
          );
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
