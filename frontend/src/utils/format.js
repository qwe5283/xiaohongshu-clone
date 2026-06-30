export const defaultAvatar =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" rx="20" fill="%23eee"/><text x="50%" y="55%" text-anchor="middle" font-size="16" fill="%23bbb">U</text></svg>';

export const defaultThumbnail =
  'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48"><rect width="48" height="48" rx="4" fill="%23f2f2f2"/><text x="50%" y="56%" text-anchor="middle" font-size="11" fill="%23999">笔记</text></svg>';

export function formatRelativeTime(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';

  const diffMs = Date.now() - date.getTime();
  if (diffMs >= 0) {
    const minuteMs = 60 * 1000;
    const hourMs = 60 * minuteMs;
    const dayMs = 24 * hourMs;
    const minutes = Math.floor(diffMs / minuteMs);

    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟前`;

    const hours = Math.floor(diffMs / hourMs);
    if (hours < 24) return `${hours}小时前`;

    const days = Math.floor(diffMs / dayMs);
    if (days <= 7) return `${days}天前`;
  }

  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}

export function formatCompactCount(value) {
  const num = Number(value ?? 0);
  if (num >= 10000) return `${(num / 10000).toFixed(1)}万`;
  return String(num);
}
