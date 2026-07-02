import request from './request';

export function chat(payload) {
  return request.post('/ai/chat', payload, {
    timeout: 70000,
  });
}
