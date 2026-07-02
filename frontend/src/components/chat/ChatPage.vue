<script setup>
import { computed, nextTick, ref } from 'vue';
import { chat } from '@/api/ai';
import { useUserStore } from '@/stores/user';
import { useUiStore } from '@/stores/ui';
import { showToast } from '@/utils/toast';
import sidebarToggleIcon from '@/assets/icons/ai-sidebar-toggle.svg?raw';
import newChatIcon from '@/assets/icons/ai-new-chat.svg?raw';
import sendIcon from '@/assets/icons/ai-send.svg?raw';

const userStore = useUserStore();
const uiStore = useUiStore();

const inputValue = ref('');
const messages = ref([]);
const loading = ref(false);
const messageListRef = ref(null);

const suggestions = [
  '想找人少景美的地方，怎么挑目的地？',
  '帮我规划一套每周3次的居家有氧运动方案',
  '怎么通过穿搭让整个人看起来更有气场？',
];

const formatMessageLines = (content) =>
  String(content || '')
    .split('\n')
    .map((line, index) => {
      const trimmed = line.trim();
      const isBullet = trimmed.startsWith('• ');
      return {
        id: `${index}-${line}`,
        text: isBullet ? trimmed.slice(2) : line,
        isBullet,
        isBlank: trimmed.length === 0,
      };
    });

const hasMessages = computed(() => messages.value.length > 0);
const pageTitle = computed(() => {
  const firstUserMessage = messages.value.find((message) => message.role === 'user');
  if (!firstUserMessage) return '';
  return firstUserMessage.content.length > 14
    ? `${firstUserMessage.content.slice(0, 14)}...`
    : firstUserMessage.content;
});

const scrollToBottom = async () => {
  await nextTick();
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  }
};

const submitMessage = async (preset) => {
  const content = (preset ?? inputValue.value).trim();
  if (!content || loading.value) return;

  if (!userStore.isLoggedIn) {
    showToast('请先登录后使用AI助手', 'error');
    uiStore.openLoginModal();
    return;
  }

  messages.value.push({
    id: `${Date.now()}-user`,
    role: 'user',
    content,
  });
  inputValue.value = '';
  loading.value = true;
  await scrollToBottom();

  try {
    const response = await chat({
      message: content,
      systemPrompt: '你是一个AI助手，回答要简洁、友好、实用。',
    });
    messages.value.push({
      id: `${Date.now()}-assistant`,
      role: 'assistant',
      content: response?.answer || '我暂时没有生成有效回复，请换个问题试试。',
    });
  } catch (error) {
    messages.value.push({
      id: `${Date.now()}-assistant-error`,
      role: 'assistant',
      content: error.message || 'AI服务暂时不可用，请稍后重试。',
      isError: true,
    });
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
};

const handleKeydown = (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    submitMessage();
  }
};

const startNewChat = () => {
  messages.value = [];
  inputValue.value = '';
};
</script>

<template>
  <main class="ml-41 min-h-screen bg-white text-[#2f2f2f]">
    <div class="relative min-h-screen overflow-hidden">
      <header class="absolute left-8 top-10 z-10 flex items-center gap-6">
        <button class="ai-icon-button" aria-label="收起侧边栏">
          <span class="ai-nav-icon" v-html="sidebarToggleIcon"></span>
        </button>
        <button
          v-if="hasMessages"
          class="ai-icon-button"
          aria-label="新建对话"
          @click="startNewChat"
        >
          <span class="ai-nav-icon" v-html="newChatIcon"></span>
        </button>
      </header>

      <section v-if="!hasMessages" class="ai-empty-state">
        <div class="ai-orb" aria-hidden="true"></div>
        <h1>想一起探索些什么?</h1>

        <form class="ai-compose ai-compose-empty" @submit.prevent="submitMessage()">
          <textarea
            v-model="inputValue"
            rows="2"
            maxlength="4000"
            placeholder="衣柜里衣服很多，但总感觉没衣服穿，怎么解决?"
            @keydown="handleKeydown"
          ></textarea>
          <div class="ai-compose-footer">
            <button class="ai-plus-button" type="button" aria-label="添加">
              +
            </button>
            <button
              class="ai-send-button"
              type="submit"
              aria-label="发送"
              :disabled="!inputValue.trim() || loading"
            >
              <span class="ai-send-icon" v-html="sendIcon"></span>
            </button>
          </div>
        </form>

        <div class="ai-suggestions">
          <button
            v-for="suggestion in suggestions"
            :key="suggestion"
            type="button"
            @click="submitMessage(suggestion)"
          >
            {{ suggestion }}
          </button>
        </div>
      </section>

      <section v-else class="ai-chat-state">
        <h1 class="ai-chat-title">{{ pageTitle }}</h1>

        <div ref="messageListRef" class="ai-message-list">
          <div
            v-for="message in messages"
            :key="message.id"
            :class="[
              'ai-message-row',
              message.role === 'user' ? 'ai-message-row-user' : 'ai-message-row-assistant',
            ]"
          >
            <div
              :class="[
                'ai-message',
                message.role === 'user' ? 'ai-message-user' : 'ai-message-assistant',
                { 'ai-message-error': message.isError },
              ]"
            >
              <template v-if="message.role === 'assistant'">
                <div
                  v-for="line in formatMessageLines(message.content)"
                  :key="line.id"
                  :class="[
                    'ai-message-line',
                    {
                      'ai-message-line-bullet': line.isBullet,
                      'ai-message-line-blank': line.isBlank,
                    },
                  ]"
                >
                  <span v-if="line.isBullet" class="ai-bullet-dot"></span>
                  <span>{{ line.text }}</span>
                </div>
              </template>
              <template v-else>
                {{ message.content }}
              </template>
            </div>
          </div>
          <div v-if="loading" class="ai-message-row ai-message-row-assistant">
            <div class="ai-message ai-message-assistant ai-thinking">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>

        <form class="ai-compose ai-compose-docked" @submit.prevent="submitMessage()">
          <textarea
            v-model="inputValue"
            rows="2"
            maxlength="4000"
            placeholder="搜索或者输入任何问题"
            @keydown="handleKeydown"
          ></textarea>
          <div class="ai-compose-footer">
            <button class="ai-plus-button" type="button" aria-label="添加">
              +
            </button>
            <button
              class="ai-send-button"
              type="submit"
              aria-label="发送"
              :disabled="!inputValue.trim() || loading"
            >
              <span class="ai-send-icon" v-html="sendIcon"></span>
            </button>
          </div>
        </form>
      </section>
    </div>
  </main>
</template>

<style scoped>
.ai-icon-button {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #3d3d3d;
  background: transparent;
  border: 0;
  padding: 0;
  cursor: pointer;
}

.ai-nav-icon {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.ai-empty-state {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10vh 40px 15vh;
}

.ai-empty-state h1 {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 0 0 34px;
  font-size: 28px;
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: 0;
  color: #2e2e2e;
}

.ai-orb {
  position: absolute;
  width: 600px;
  height: 310px;
  top: 45%;
  left: 50%;
  transform: translate(-50%, -20%);
  background: radial-gradient(circle, rgba(119, 229, 213, 0.16), rgba(255, 255, 255, 0) 68%);
  pointer-events: none;
}

.ai-empty-state h1::before {
  content: '';
  width: 42px;
  height: 42px;
  border-radius: 18px 18px 18px 6px;
  background: #9cf1e4;
  display: inline-block;
}

.ai-compose {
  width: min(100%, 920px);
  min-height: 118px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.98);
}

.ai-compose-empty {
  border: 1.5px solid #6ee0d4;
  border-radius: 24px;
  padding: 20px 22px 18px;
}

.ai-compose-docked {
  position: fixed;
  left: calc(164px + 33vw - 120px);
  right: 10vw;
  bottom: 18px;
  width: auto;
  min-height: 112px;
  border-radius: 24px;
  padding: 18px 22px 16px;
  box-shadow: 0 8px 26px rgba(76, 214, 198, 0.12);
}

.ai-compose textarea {
  width: 100%;
  min-height: 42px;
  resize: none;
  border: 0;
  outline: none;
  padding: 0;
  color: #333333;
  font-size: 14px;
  line-height: 1.5;
  background: transparent;
}

.ai-compose textarea::placeholder {
  color: #bebfc4;
}

.ai-compose-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 34px;
}

.ai-plus-button,
.ai-send-button {
  border: 0;
  padding: 0;
  background: transparent;
  cursor: pointer;
  color: #333333;
  line-height: 1;
}

.ai-plus-button {
  font-size: 24px;
  font-weight: 300;
}

.ai-send-button {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #303136;
  color: #ffffff;
  font-size: 22px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.ai-send-button:disabled {
  cursor: not-allowed;
}

.ai-send-icon {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.ai-suggestions {
  width: min(100%, 720px);
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 18px 22px;
  margin-top: 48px;
}

.ai-suggestions button {
  min-height: 44px;
  border: 0;
  border-radius: 15px;
  padding: 0 24px;
  color: #3f4349;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 10px 30px rgba(32, 85, 81, 0.04);
  font-size: 14px;
  white-space: nowrap;
  cursor: pointer;
}

.ai-chat-state {
  min-height: 100vh;
  padding: 28px 11vw 158px 24vw;
}

.ai-chat-title {
  margin: 0;
  text-align: center;
  font-size: 18px;
  line-height: 1.2;
  font-weight: 800;
  color: #222222;
}

.ai-message-list {
  height: calc(100vh - 158px);
  overflow-y: auto;
  padding: 82px 0 18px;
  scrollbar-width: none;
}

.ai-message-list::-webkit-scrollbar {
  display: none;
}

.ai-message-row {
  display: flex;
  width: 100%;
  margin-bottom: 42px;
}

.ai-message-row-user {
  justify-content: flex-end;
}

.ai-message-row-assistant {
  justify-content: flex-start;
}

.ai-message {
  max-width: min(650px, 70%);
  color: #414141;
  font-size: 14px;
  line-height: 1.75;
  letter-spacing: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.ai-message-user {
  max-width: 320px;
  padding: 10px 18px;
  border-radius: 18px;
  background: #fbfbfb;
  color: #3f3f3f;
  line-height: 1.45;
}

.ai-message-assistant {
  padding-top: 2px;
}

.ai-message-error {
  color: #b42318;
}

.ai-message-line {
  min-height: 1.75em;
}

.ai-message-line-blank {
  min-height: 1.15em;
}

.ai-message-line-bullet {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.ai-bullet-dot {
  flex: 0 0 auto;
  width: 5px;
  height: 5px;
  margin-top: 13px;
  border-radius: 50%;
  background: #63dccc;
}

.ai-thinking {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 16px 0;
}

.ai-thinking span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #70dccf;
  animation: aiPulse 1s infinite ease-in-out;
}

.ai-thinking span:nth-child(2) {
  animation-delay: 0.16s;
}

.ai-thinking span:nth-child(3) {
  animation-delay: 0.32s;
}

@keyframes aiPulse {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-4px);
  }
}

@media (max-width: 960px) {
  .ai-empty-state {
    padding: 100px 22px 60px;
  }

  .ai-empty-state h1 {
    font-size: 21px;
    gap: 12px;
  }

  .ai-empty-state h1::before {
    width: 34px;
    height: 34px;
    border-radius: 15px 15px 15px 5px;
  }

  .ai-compose {
    min-height: 112px;
  }

  .ai-compose-docked {
    left: 184px;
    right: 20px;
  }

  .ai-chat-state {
    padding: 28px 24px 154px 24px;
  }

  .ai-message {
    max-width: 82%;
    font-size: 14px;
  }

  .ai-suggestions button {
    width: 100%;
    white-space: normal;
    font-size: 14px;
  }
}
</style>
