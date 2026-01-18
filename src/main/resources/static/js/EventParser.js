/**
 * EventParser - Parses log events to trigger animations
 */
export class EventParser {
  constructor() {
    this.listeners = {
      speaking: [],
      voting: [],
      decision: [],
      phase: [],
      lobbying: []
    };
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
   * Emit event
   */
  emit(event, data) {
    if (this.listeners[event]) {
      this.listeners[event].forEach(cb => cb(data));
    }
  }

  /**
   * Parse a log line and emit appropriate events
   */
  parseLine(line) {
    // Detect speaking events
    // Patterns: "[NAME] speaks:", "NAME:", "[NAME] argues:", etc.
    const speakingPatterns = [
      /^\[([^\]]+)\]\s*(speaks|argues|states|responds|replies|says):/i,
      /^(?:Rep\.|Representative)\s+([^:]+):/i,
      /^([A-Z][a-z]+\s+[A-Z][a-z]+)(?:\s+\([^)]+\))?\s*:/
    ];

    for (const pattern of speakingPatterns) {
      const match = line.match(pattern);
      if (match) {
        const speakerName = match[1].trim();
        const content = line.substring(match[0].length).trim();
        this.emit('speaking', {
          name: speakerName,
          content: content.substring(0, 200) // Limit content length
        });
        return;
      }
    }

    // Detect voting events
    // Patterns: "NAME votes YES/NO", "[NAME] casts vote: YES/NO"
    const votePatterns = [
      /^\[?([^\]]+)\]?\s+votes?\s+(YES|NO|YEA|NAY)/i,
      /^\[?([^\]]+)\]?\s+casts?\s+(?:a\s+)?vote:?\s*(YES|NO|YEA|NAY)/i
    ];

    for (const pattern of votePatterns) {
      const match = line.match(pattern);
      if (match) {
        const voterName = match[1].trim();
        const vote = match[2].toUpperCase();
        const isYes = vote === 'YES' || vote === 'YEA';
        this.emit('voting', {
          name: voterName,
          vote: isYes ? 'YES' : 'NO'
        });
        return;
      }
    }

    // Detect final decision
    const decisionPatterns = [
      /bill\s+(passed|failed|approved|rejected)/i,
      /final\s+(?:vote|decision|result):\s*(passed|failed|approved|rejected)/i,
      /(?:motion|resolution)\s+(passes|fails|carries)/i
    ];

    for (const pattern of decisionPatterns) {
      const match = line.match(pattern);
      if (match) {
        const result = match[1].toLowerCase();
        const passed = ['passed', 'approved', 'passes', 'carries'].includes(result);
        this.emit('decision', { passed });
        return;
      }
    }

    // Detect phase changes
    const phasePatterns = [
      /=+\s*(?:PHASE|STAGE|ROUND)[\s:]+([^=]+)\s*=+/i,
      /(?:entering|beginning|starting)\s+(?:the\s+)?([^.]+)\s+(?:phase|stage)/i,
      /\[([^\]]+)\s+(?:PHASE|STAGE)\]/i
    ];

    for (const pattern of phasePatterns) {
      const match = line.match(pattern);
      if (match) {
        this.emit('phase', { phase: match[1].trim() });
        return;
      }
    }

    // Detect lobbying
    const lobbyPatterns = [
      /^\[?([^\]]+)\]?\s+(?:lobbies|approaches|persuades)\s+\[?([^\]]+)\]?/i
    ];

    for (const pattern of lobbyPatterns) {
      const match = line.match(pattern);
      if (match) {
        this.emit('lobbying', {
          lobbyist: match[1].trim(),
          target: match[2].trim()
        });
        return;
      }
    }
  }

  /**
   * Parse multiple log lines
   */
  parseLines(lines) {
    lines.forEach(line => this.parseLine(line));
  }
}
