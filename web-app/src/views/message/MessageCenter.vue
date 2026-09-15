<template>
  <div class="chat-layout">
    <!-- 会话列表 -->
    <div class="session-pane" :class="{ hidden: activeOtherId }">
      <div class="pane-title">会话列表</div>
      <div v-if="sessions.length === 0" class="empty">还没有对话，去"找家教"联系老师吧</div>
      <div
        v-for="s in sessions"
        :key="s.otherId"
        class="session-item"
        :class="{ active: s.otherId === activeOtherId }"
        @click="openSession(s)"
      >
        <el-avatar :size="40">{{ (s.otherName || '?')[0] }}</el-avatar>
        <div class="session-body">
          <div class="session-top">
            <span class="session-name">{{ s.otherName }}</span>
            <span class="session-time">{{ shortTime(s.lastTime) }}</span>
          </div>
          <div class="session-last">
            {{ s.lastContent || '' }}
            <el-badge v-if="s.unread > 0" :value="s.unread" class="unread" />
          </div>
        </div>
      </div>
    </div>

    <!-- 聊天区 -->
    <div class="chat-pane" v-loading="loadingMsgs">
      <template v-if="activeOtherId">
        <div class="chat-header">
          <span class="back" @click="backToList">← 会话列表</span>
          <span class="chat-title">与 {{ activeName }} 的对话</span>
        </div>
        <div class="msg-stream" ref="streamRef">
          <div v-if="msgs.length === 0" class="empty">开始你们的第一次对话吧</div>
          <div v-for="m in msgs" :key="m.id" class="msg-row" :class="mine(m) ? 'me' : 'other'">
            <div class="bubble">
              <div class="bubble-content">{{ m.content }}</div>
              <div class="bubble-time">{{ formatDateTime(m.createTime) }}</div>
            </div>
          </div>
        </div>
        <div class="chat-input">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            placeholder="输入消息，Enter 发送，Shift+Enter 换行"
            @keydown.enter.exact.prevent="send"
          />
          <el-button type="primary" :disabled="!draft.trim()" :loading="sending" @click="send">发送</el-button>
        </div>
      </template>
      <div v-else class="chat-empty">
        <el-empty description="选择左侧会话，或从老师主页点击发消息" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  listConversations,
  listConversation,
  sendMessage,
  formatDateTime,
  type ConversationVO,
  type MessageEntity,
} from '@tutor-platform/frontend-common';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const sessions = ref<ConversationVO[]>([]);
const activeOtherId = ref<number | null>(null);
const activeName = ref('');
const msgs = ref<MessageEntity[]>([]);
const draft = ref('');
const sending = ref(false);
const loadingMsgs = ref(false);
const streamRef = ref<HTMLElement | null>(null);

function mine(m: MessageEntity): boolean {
  return m.fromId === userStore.user?.userId;
}

function shortTime(t: string | null): string {
  if (!t) return '';
  return formatDateTime(t).substring(5, 16);
}

async function loadSessions() {
  sessions.value = await listConversations();
}

async function openSession(s: ConversationVO) {
  activeOtherId.value = s.otherId;
  activeName.value = s.otherName;
  await loadMsgs();
}

async function loadMsgs() {
  if (!activeOtherId.value) return;
  loadingMsgs.value = true;
  try {
    msgs.value = await listConversation(activeOtherId.value);
    await nextTick();
    streamRef.value?.scrollTo({ top: streamRef.value.scrollHeight });
    // 会话页内未读已在后端自动已读，刷新列表角标
    await loadSessions();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loadingMsgs.value = false;
  }
}

async function send() {
  const content = draft.value.trim();
  if (!content || !activeOtherId.value) return;
  sending.value = true;
  try {
    await sendMessage(activeOtherId.value, content);
    draft.value = '';
    await loadMsgs();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    sending.value = false;
  }
}

function backToList() {
  activeOtherId.value = null;
  activeName.value = '';
}

onMounted(async () => {
  await loadSessions();
  // 支持从老师主页 ?uid=2&name=王老师 直达对话
  const uid = Number(route.query.uid);
  if (uid && !Number.isNaN(uid)) {
    activeOtherId.value = uid;
    activeName.value = (route.query.name as string) || `用户${uid}`;
    await loadMsgs();
  }
});
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: calc(100vh - 110px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}
.session-pane {
  width: 280px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}
.pane-title {
  padding: 14px 16px;
  font-weight: 600;
  border-bottom: 1px solid #f0f0f0;
}
.session-item {
  display: flex;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  align-items: center;
}
.session-item:hover,
.session-item.active {
  background: #f0f7ff;
}
.session-body {
  flex: 1;
  min-width: 0;
}
.session-top {
  display: flex;
  justify-content: space-between;
}
.session-name {
  font-weight: 600;
  font-size: 14px;
}
.session-time {
  font-size: 11px;
  color: #909399;
}
.session-last {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}
.unread {
  margin-left: 6px;
}
.chat-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-header {
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  gap: 12px;
  align-items: center;
}
.back {
  color: #409eff;
  cursor: pointer;
}
.chat-title {
  font-weight: 600;
}
.msg-stream {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f7f8fa;
}
.msg-row {
  display: flex;
  margin-bottom: 12px;
}
.msg-row.me {
  justify-content: flex-end;
}
.bubble {
  max-width: 60%;
}
.bubble-content {
  padding: 8px 12px;
  border-radius: 10px;
  background: #fff;
  word-break: break-word;
}
.me .bubble-content {
  background: #409eff;
  color: #fff;
}
.bubble-time {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}
.me .bubble-time {
  text-align: right;
}
.chat-input {
  padding: 10px 14px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.chat-input .el-textarea {
  flex: 1;
}
.empty,
.chat-empty {
  color: #909399;
  font-size: 13px;
  padding: 24px;
  text-align: center;
}
@media (max-width: 768px) {
  .session-pane {
    width: 100%;
  }
  .session-pane.hidden {
    display: none;
  }
}
</style>
