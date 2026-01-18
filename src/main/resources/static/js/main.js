/**
 * GovSim Parliament - Main Entry Point
 */
import { ParliamentScene } from './ParliamentScene.js';
import { Poller } from './Poller.js';
import { EventParser } from './EventParser.js';

class App {
  constructor() {
    this.scene = null;
    this.poller = new Poller();
    this.eventParser = new EventParser();

    // DOM elements
    this.statusBadge = document.getElementById('statusBadge');
    this.yesBtn = document.getElementById('yesBtn');
    this.noBtn = document.getElementById('noBtn');
    this.voteStats = document.getElementById('voteStats');
    this.yesBar = document.getElementById('yesBar');
    this.noBar = document.getElementById('noBar');
    this.chatLog = document.getElementById('chatLog');
    this.chatName = document.getElementById('chatName');
    this.chatMessage = document.getElementById('chatMessage');
    this.chatSend = document.getElementById('chatSend');
    this.logEl = document.getElementById('log');
    this.tooltip = document.getElementById('tooltip');
    this.speechBubble = document.getElementById('speech-bubble');
    this.transcriptToggle = document.getElementById('transcriptToggle');
    this.logPanel = document.getElementById('log-panel');

    // Data card elements
    this.repCard = document.getElementById('rep-card');
    this.cardClose = document.getElementById('cardClose');
    this.cardName = document.getElementById('cardName');
    this.cardParty = document.getElementById('cardParty');
    this.cardInitials = document.getElementById('cardInitials');
    this.cardActivity = document.getElementById('cardActivity');
    this.cardYesCount = document.getElementById('cardYesCount');
    this.cardNoCount = document.getElementById('cardNoCount');

    this.speechTimeout = null;
    this.transcriptionVisible = true;
    this.hasReceivedStatus = false;
  }

  async init() {
    console.log('Initializing GovSim Parliament...');

    // Initialize Three.js scene
    const container = document.getElementById('scene-container');
    this.scene = new ParliamentScene(container);
    await this.scene.init();

    // Setup scene callbacks
    this.scene.onRepHover = (data, x, y) => this.showTooltip(data, x, y);
    this.scene.onRepClick = (data) => this.showDataCard(data);

    // Fetch representatives and load into scene
    const reps = await this.poller.fetchRepresentatives();
    if (reps.length > 0) {
      await this.scene.loadRepresentatives(reps);
      console.log(`Loaded ${reps.length} representatives`);
    }

    // Setup event parser handlers
    this.eventParser.on('speaking', (data) => this.handleSpeaking(data));
    this.eventParser.on('voting', (data) => this.handleVoting(data));
    this.eventParser.on('decision', (data) => this.handleDecision(data));
    this.eventParser.on('phase', (data) => this.handlePhase(data));
    this.eventParser.on('lobbying', (data) => this.handleLobbying(data));
    this.eventParser.on('meeting', (data) => this.handleMeeting(data));

    // Setup poller handlers
    this.poller.on('log', (lines) => this.handleLogLines(lines));
    this.poller.on('status', (data) => this.handleStatus(data));
    this.poller.on('chat', (messages) => this.handleChatMessages(messages));
    this.poller.on('connection', (connected) => this.handleConnection(connected));

    // Setup UI event handlers
    this.setupUIHandlers();

    // Start polling
    this.poller.start(1000);

    console.log('GovSim Parliament ready');
  }

  setupUIHandlers() {
    // Vote buttons
    this.yesBtn.addEventListener('click', async () => {
      await this.poller.vote('yes');
      this.yesBtn.disabled = true;
      this.noBtn.disabled = true;
    });

    this.noBtn.addEventListener('click', async () => {
      await this.poller.vote('no');
      this.yesBtn.disabled = true;
      this.noBtn.disabled = true;
    });

    // Chat input
    this.chatSend.addEventListener('click', () => this.sendChat());
    this.chatMessage.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') this.sendChat();
    });

    // Transcription toggle button
    this.transcriptToggle.addEventListener('click', () => this.toggleTranscription());

    // Data card close button
    this.cardClose.addEventListener('click', () => this.hideDataCard());
  }

  toggleTranscription() {
    this.transcriptionVisible = !this.transcriptionVisible;

    if (this.transcriptionVisible) {
      // Show transcription
      this.logPanel.classList.remove('hidden');
      this.speechBubble.classList.remove('hidden');
      this.transcriptToggle.textContent = 'Hide Transcription';
      this.transcriptToggle.classList.remove('active');
    } else {
      // Hide transcription
      this.logPanel.classList.add('hidden');
      this.speechBubble.classList.add('hidden');
      this.transcriptToggle.textContent = 'Show Transcription';
      this.transcriptToggle.classList.add('active');
    }
  }

  async sendChat() {
    const name = this.chatName.value.trim() || 'Guest';
    const message = this.chatMessage.value.trim();
    if (!message) return;

    await this.poller.sendChat(name, message);
    this.chatMessage.value = '';
  }

  handleLogLines(lines) {
    // Add to console log
    lines.forEach(line => {
      this.logEl.textContent += line + '\n';
    });
    this.logEl.scrollTop = this.logEl.scrollHeight;

    // Parse for events
    this.eventParser.parseLines(lines);

    // Update connection status
    if (!this.hasReceivedStatus) {
      this.statusBadge.textContent = this.poller.isConnected() ? 'Connected' : 'Disconnected';
    }
  }

  handleConnection(connected) {
    if (!connected) {
      this.statusBadge.textContent = 'Disconnected';
      this.statusBadge.classList.remove('voting');
      return;
    }

    if (!this.hasReceivedStatus) {
      this.statusBadge.textContent = 'Connected';
    }
  }

  handleStatus(data) {
    const open = !!data.open;
    const total = data.yes + data.no;
    this.hasReceivedStatus = true;

    // Update status badge
    if (open) {
      this.statusBadge.textContent = 'Voting Open';
      this.statusBadge.classList.add('voting');
    } else {
      this.statusBadge.textContent = 'Voting Closed';
      this.statusBadge.classList.remove('voting');
    }

    // Update buttons
    this.yesBtn.disabled = !open;
    this.noBtn.disabled = !open;

    // Update stats
    this.voteStats.textContent = `YES: ${data.yes} | NO: ${data.no} | Total: ${total}`;

    // Update tally bar
    if (total > 0) {
      const yesPercent = (data.yes / total) * 100;
      const noPercent = (data.no / total) * 100;
      this.yesBar.style.width = `${yesPercent}%`;
      this.noBar.style.width = `${noPercent}%`;
    } else {
      this.yesBar.style.width = '0%';
      this.noBar.style.width = '0%';
    }
  }

  handleChatMessages(messages) {
    messages.forEach(msg => {
      const div = document.createElement('div');
      div.className = 'message';
      div.innerHTML = `<span class="name">${this.escapeHtml(msg.name)}:</span> ${this.escapeHtml(msg.message)}`;
      this.chatLog.appendChild(div);
    });
    this.chatLog.scrollTop = this.chatLog.scrollHeight;
  }

  handleSpeaking(data) {
    if (this.scene) {
      this.scene.handleSpeaking(data.name, data.content);
    }

    // Show speech bubble (if transcription is visible)
    if (this.transcriptionVisible) {
      this.showSpeechBubble(data.content);
    }
  }

  handleVoting(data) {
    if (this.scene) {
      this.scene.handleVoting(data.name, data.vote);
    }
  }

  handleDecision(data) {
    // Could trigger a visual celebration/defeat animation
    console.log('Decision:', data.passed ? 'PASSED' : 'FAILED');
  }

  handlePhase(data) {
    console.log('Phase change:', data.phase);
  }

  handleLobbying(data) {
    if (this.scene) {
      this.scene.handleLobbying?.(data.lobbyist, data.target, data.message || '');
    }
  }

  handleMeeting(data) {
    if (this.scene) {
      this.scene.handleMeeting?.(data.a, data.b, data.aCommittee, data.bCommittee);
    }
  }

  showTooltip(data, x, y) {
    if (!data) {
      this.tooltip.classList.add('hidden');
      return;
    }

    const partyClass = data.party.replace(/\s+/g, '');
    const issues = data.petIssues ? data.petIssues.join(', ') : '';
    const committee = data.committeeName ? String(data.committeeName) : '';

    this.tooltip.innerHTML = `
      <div class="name">${this.escapeHtml(data.name)}</div>
      <div class="party ${partyClass}">${this.escapeHtml(data.party)}</div>
      ${committee ? `<div class="issues">Committee: ${this.escapeHtml(committee)}</div>` : ''}
      <div class="issues">Focus: ${this.escapeHtml(issues)}</div>
    `;

    // Position tooltip
    const rect = this.tooltip.getBoundingClientRect();
    let posX = x + 15;
    let posY = y - rect.height / 2;

    // Keep on screen
    if (posX + rect.width > window.innerWidth) {
      posX = x - rect.width - 15;
    }
    if (posY < 10) posY = 10;
    if (posY + rect.height > window.innerHeight - 10) {
      posY = window.innerHeight - rect.height - 10;
    }

    this.tooltip.style.left = `${posX}px`;
    this.tooltip.style.top = `${posY}px`;
    this.tooltip.classList.remove('hidden');
  }

  showSpeechBubble(content) {
    if (!content || !this.transcriptionVisible) return;

    const bubbleContent = this.speechBubble.querySelector('.bubble-content');
    bubbleContent.textContent = content.substring(0, 150) + (content.length > 150 ? '...' : '');

    // Position near top center
    const centerX = window.innerWidth / 2;
    this.speechBubble.style.left = `${centerX - 160}px`;
    this.speechBubble.style.top = '160px';
    this.speechBubble.classList.remove('hidden');

    // Auto-hide
    if (this.speechTimeout) {
      clearTimeout(this.speechTimeout);
    }
    this.speechTimeout = setTimeout(() => {
      this.speechBubble.classList.add('hidden');
    }, 5000);
  }

  escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  /**
   * Show representative data card
   */
  showDataCard(data) {
    if (!data) return;

    // Set name
    this.cardName.textContent = data.name;

    // Set party with proper class
    const partyClass = data.party.replace(/\s+/g, '');
    this.cardParty.textContent = data.party;
    this.cardParty.className = `party-badge ${partyClass || 'default'}`;

    // Set initials
    const names = data.name.split(' ');
    const initials = names.length >= 2
      ? (names[0][0] + names[names.length - 1][0]).toUpperCase()
      : (names[0][0] + (names[0][1] || '')).toUpperCase();
    this.cardInitials.textContent = initials;

    // Update voting stats (placeholder - would need real data)
    const yesCount = Math.floor(Math.random() * 50) + 10;
    const noCount = Math.floor(Math.random() * 30) + 5;
    this.cardYesCount.textContent = yesCount;
    this.cardNoCount.textContent = noCount;

    // Set recent activity
    const issues = data.petIssues || ['No specific issues'];
    const committee = data.committeeName ? `<p>Committee: ${this.escapeHtml(String(data.committeeName))}</p>` : '';
    this.cardActivity.innerHTML = `${committee}<p>Focus: ${this.escapeHtml(issues.join(', '))}</p>`;

    // Show card
    this.repCard.classList.remove('hidden');
    this.repCard.classList.add('visible');
  }

  /**
   * Hide representative data card
   */
  hideDataCard() {
    this.repCard.classList.remove('visible');
    this.repCard.classList.add('hidden');
    // Also reset camera view
    if (this.scene) {
      this.scene.resetView();
    }
  }
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', async () => {
  const app = new App();
  try {
    await app.init();
  } catch (error) {
    console.error('Failed to initialize:', error);
  }
});
