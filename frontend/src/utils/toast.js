// 极简 toast 工具：固定定位的轻量提示，无第三方依赖
// 用法：import { showToast } from '@/utils/toast'; showToast('登录成功', 'success')

let container = null;

function ensureContainer() {
  if (container && document.body.contains(container)) return container;
  container = document.createElement('div');
  container.style.cssText =
    'position:fixed;top:24px;left:50%;transform:translateX(-50%);z-index:9999;display:flex;flex-direction:column;align-items:center;gap:8px;pointer-events:none;';
  document.body.appendChild(container);
  return container;
}

const COLOR = {
  success: '#52c41a',
  error: '#ff2442',
  info: '#333',
};

/**
 * 显示一条轻量提示
 * @param {string} message 文案
 * @param {'success'|'error'|'info'} type 类型
 * @param {number} duration 持续毫秒
 */
export function showToast(message, type = 'info', duration = 2500) {
  if (!message) return;
  const root = ensureContainer();
  const el = document.createElement('div');
  el.textContent = message;
  el.style.cssText = `max-width:80vw;padding:10px 20px;border-radius:20px;background:${COLOR[type] || COLOR.info};color:#fff;font-size:14px;line-height:1.4;box-shadow:0 4px 12px rgba(0,0,0,0.15);opacity:0;transition:opacity .25s ease, transform .25s ease;transform:translateY(-8px);word-break:break-all;`;
  root.appendChild(el);
  // 入场
  requestAnimationFrame(() => {
    el.style.opacity = '1';
    el.style.transform = 'translateY(0)';
  });
  // 出场
  const timer = setTimeout(() => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(-8px)';
    setTimeout(() => el.remove(), 260);
  }, duration);
  // 悬停暂停（轻量实现：不做）
  el._timer = timer;
}
