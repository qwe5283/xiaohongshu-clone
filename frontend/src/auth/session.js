const TOKEN_KEY = 'xhs_token';
const AUTH_EXPIRED_EVENT = 'xhs:auth-expired';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function notifyAuthExpired(message) {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(
    new CustomEvent(AUTH_EXPIRED_EVENT, {
      detail: { message },
    }),
  );
}

export function onAuthExpired(handler) {
  if (typeof window === 'undefined') return () => {};
  const listener = (event) => handler(event.detail?.message);
  window.addEventListener(AUTH_EXPIRED_EVENT, listener);
  return () => window.removeEventListener(AUTH_EXPIRED_EVENT, listener);
}
