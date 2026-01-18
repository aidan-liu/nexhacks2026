/**
 * Poller - Handles polling backend endpoints
 */
export class Poller {
  constructor() {
    this.logIndex = 0;
    this.chatIndex = 0;
    this.listeners = {
      log: [],
      status: [],
      chat: [],
      representatives: []
    };
    this.pollingInterval = null;
    this.connected = false;
  }

  /**
   * Add event listener
   */
  on(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event].push(callback);
    }
  }

  /**
   * Remove event listener
   */
  off(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event] = this.listeners[event].filter(cb => cb !== callback);
    }
  }

  /**
   * Emit event to listeners
   */
  emit(event, data) {
    if (this.listeners[event]) {
      this.listeners[event].forEach(callback => callback(data));
    }
  }

  /**
   * Fetch representatives data
   */
  async fetchRepresentatives() {
    try {
      const res = await fetch('/representatives', { cache: 'no-store' });
      if (!res.ok) throw new Error('representatives fetch failed');
      const data = await res.json();
      this.emit('representatives', data);
      return data;
    } catch (error) {
      console.error('Failed to fetch representatives:', error);
      return [];
    }
  }

  /**
   * Fetch logs since last index
   */
  async fetchLogs() {
    try {
      const res = await fetch(`/log?since=${this.logIndex}`, { cache: 'no-store' });
      if (!res.ok) throw new Error('log fetch failed');
      const data = await res.json();

      if (data.lines && data.lines.length > 0) {
        this.emit('log', data.lines);
      }

      this.logIndex = data.nextIndex || this.logIndex;
      this.connected = true;
      return data;
    } catch (error) {
      this.connected = false;
      console.error('Log fetch error:', error);
      return null;
    }
  }

  /**
   * Fetch voting status
   */
  async fetchStatus() {
    try {
      const res = await fetch('/status', { cache: 'no-store' });
      if (!res.ok) throw new Error('status fetch failed');
      const data = await res.json();
      this.emit('status', data);
      this.connected = true;
      return data;
    } catch (error) {
      this.connected = false;
      console.error('Status fetch error:', error);
      return null;
    }
  }

  /**
   * Fetch chat messages
   */
  async fetchChat() {
    try {
      const res = await fetch(`/chat?since=${this.chatIndex}`, { cache: 'no-store' });
      if (!res.ok) throw new Error('chat fetch failed');
      const data = await res.json();

      if (data.messages && data.messages.length > 0) {
        this.emit('chat', data.messages);
      }

      this.chatIndex = data.nextIndex || this.chatIndex;
      return data;
    } catch (error) {
      console.error('Chat fetch error:', error);
      return null;
    }
  }

  /**
   * Submit a vote
   */
  async vote(choice) {
    try {
      const res = await fetch(`/vote?choice=${choice}`, {
        method: 'POST',
        cache: 'no-store'
      });
      return res.ok;
    } catch (error) {
      console.error('Vote error:', error);
      return false;
    }
  }

  /**
   * Send a chat message
   */
  async sendChat(name, message) {
    try {
      const res = await fetch('/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, message }),
        cache: 'no-store'
      });
      return res.ok;
    } catch (error) {
      console.error('Send chat error:', error);
      return false;
    }
  }

  /**
   * Start polling
   */
  start(intervalMs = 1000) {
    if (this.pollingInterval) return;

    // Initial fetch
    this.fetchLogs();
    this.fetchStatus();
    this.fetchChat();

    // Start polling
    this.pollingInterval = setInterval(() => {
      this.fetchLogs();
      this.fetchStatus();
      this.fetchChat();
    }, intervalMs);
  }

  /**
   * Stop polling
   */
  stop() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
      this.pollingInterval = null;
    }
  }

  /**
   * Check if connected
   */
  isConnected() {
    return this.connected;
  }
}
