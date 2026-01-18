/**
 * Representative - 3D figure for parliament members (sphere head + cone body)
 */
export class Representative {
  // Party colors
  static PARTY_COLORS = {
    'Democrat': 0x3B82F6,
    'Republican': 0xEF4444,
    'Democratic Socialist': 0x8B5CF6,
    'authoritarian': 0x6B7280,
    'default': 0x6B7280
  };

  constructor(data) {
    this.data = data;
    this.id = data.id;
    this.name = data.name;
    this.party = data.party;
    this.ideology = data.ideology;

    // Create container group
    this.group = new THREE.Group();

    // Base position
    this.basePosition = new THREE.Vector3();
    this.targetPosition = new THREE.Vector3();

    // State
    this.isSpeaking = false;
    this.voteSprite = null;
    this.nameSprite = null;
    this.speakingGlow = null;

    // Animation state
    this.bobPhase = Math.random() * Math.PI * 2;
    this.bobSpeed = 1.5 + Math.random() * 0.5;
    this.bobAmount = 0.05;
    this.time = 0;

    // Build the 3D figure
    this._buildFigure();
  }

  _buildFigure() {
    const partyColor = Representative.PARTY_COLORS[this.party] || Representative.PARTY_COLORS.default;

    // Create material with party color
    this.material = new THREE.MeshStandardMaterial({
      color: partyColor,
      roughness: 0.6,
      metalness: 0.1
    });

    // Head (sphere)
    const headGeometry = new THREE.SphereGeometry(0.3, 16, 16);
    this.head = new THREE.Mesh(headGeometry, this.material);
    this.head.position.y = 1.0;
    this.head.castShadow = true;
    this.group.add(this.head);

    // Body (inverted cone)
    const bodyGeometry = new THREE.ConeGeometry(0.35, 0.7, 16);
    this.body = new THREE.Mesh(bodyGeometry, this.material);
    this.body.position.y = 0.45;
    this.body.rotation.x = Math.PI; // Invert the cone
    this.body.castShadow = true;
    this.group.add(this.body);

    // Speaking glow (point light, hidden by default)
    this.speakingLight = new THREE.PointLight(0x4dd0e1, 0, 3);
    this.speakingLight.position.y = 0.8;
    this.group.add(this.speakingLight);

    // Create speaking glow mesh (ring around figure)
    const glowGeometry = new THREE.RingGeometry(0.4, 0.6, 32);
    const glowMaterial = new THREE.MeshBasicMaterial({
      color: 0x4dd0e1,
      transparent: true,
      opacity: 0,
      side: THREE.DoubleSide
    });
    this.speakingGlow = new THREE.Mesh(glowGeometry, glowMaterial);
    this.speakingGlow.rotation.x = -Math.PI / 2;
    this.speakingGlow.position.y = 0.02;
    this.group.add(this.speakingGlow);

    // Create name label sprite (hidden by default)
    this._createNameSprite();

    // Create vote indicator sprite container (hidden by default)
    this._createVoteSprite();
  }

  _createNameSprite() {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 64;
    const ctx = canvas.getContext('2d');

    // Background
    ctx.fillStyle = 'rgba(255, 255, 255, 0.9)';
    ctx.roundRect(0, 16, 256, 40, 8);
    ctx.fill();

    // Border
    ctx.strokeStyle = '#d1d5db';
    ctx.lineWidth = 2;
    ctx.roundRect(0, 16, 256, 40, 8);
    ctx.stroke();

    // Text
    ctx.fillStyle = '#1a1a1a';
    ctx.font = 'bold 20px monospace';
    ctx.textAlign = 'center';
    ctx.fillText(this._getShortName(), 128, 44);

    const texture = new THREE.CanvasTexture(canvas);
    const spriteMaterial = new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      opacity: 0
    });
    this.nameSprite = new THREE.Sprite(spriteMaterial);
    this.nameSprite.position.y = 1.6;
    this.nameSprite.scale.set(1.5, 0.4, 1);
    this.group.add(this.nameSprite);
  }

  _createVoteSprite() {
    // Create container for vote indicator
    this.voteGroup = new THREE.Group();
    this.voteGroup.position.y = 1.5;
    this.voteGroup.visible = false;
    this.group.add(this.voteGroup);
  }

  _getShortName() {
    const parts = this.name.split(' ');
    return parts.length > 1 ? parts[parts.length - 1] : this.name;
  }

  /**
   * Set position
   */
  setPosition(x, y, z) {
    this.basePosition.set(x, y, z);
    this.targetPosition.set(x, y, z);
    this.group.position.set(x, y, z);
  }

  /**
   * Move to position with animation
   */
  moveTo(x, y, z) {
    this.targetPosition.set(x, y, z);
  }

  /**
   * Return to base position
   */
  returnToBase() {
    this.targetPosition.copy(this.basePosition);
  }

  /**
   * Set speaking state
   */
  setSpeaking(speaking) {
    this.isSpeaking = speaking;
    if (speaking) {
      this.speakingLight.intensity = 1;
    } else {
      this.speakingLight.intensity = 0;
      this.speakingGlow.material.opacity = 0;
    }
  }

  /**
   * Show vote indicator
   */
  showVote(vote) {
    const isYes = vote === 'YES';
    const color = isYes ? '#22C55E' : '#EF4444';
    const symbol = isYes ? '\u2713' : '\u2717';

    // Clear previous vote sprite
    while (this.voteGroup.children.length > 0) {
      this.voteGroup.remove(this.voteGroup.children[0]);
    }

    // Create vote indicator sprite
    const canvas = document.createElement('canvas');
    canvas.width = 64;
    canvas.height = 64;
    const ctx = canvas.getContext('2d');

    // Background circle
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(32, 32, 28, 0, Math.PI * 2);
    ctx.fill();

    // Symbol
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 36px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(symbol, 32, 34);

    const texture = new THREE.CanvasTexture(canvas);
    const spriteMaterial = new THREE.SpriteMaterial({ map: texture, transparent: true });
    const voteSprite = new THREE.Sprite(spriteMaterial);
    voteSprite.scale.set(0.5, 0.5, 1);
    this.voteGroup.add(voteSprite);
    this.voteGroup.visible = true;

    // Hide after delay
    setTimeout(() => {
      this.hideVote();
    }, 3000);
  }

  /**
   * Hide vote indicator
   */
  hideVote() {
    this.voteGroup.visible = false;
  }

  /**
   * Show name label
   */
  showName() {
    if (this.nameSprite) {
      this.nameSprite.material.opacity = 1;
    }
  }

  /**
   * Hide name label
   */
  hideName() {
    if (this.nameSprite) {
      this.nameSprite.material.opacity = 0;
    }
  }

  /**
   * Update animation
   */
  update(delta) {
    this.time += delta;

    // Idle bob animation
    this.bobPhase += this.bobSpeed * delta;
    const bobOffset = Math.sin(this.bobPhase) * this.bobAmount;

    // Move towards target
    const currentPos = this.group.position;
    const dx = this.targetPosition.x - currentPos.x;
    const dy = this.targetPosition.y - currentPos.y;
    const dz = this.targetPosition.z - currentPos.z;
    const moveSpeed = 3 * delta;

    if (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01) {
      currentPos.x += dx * moveSpeed;
      currentPos.z += dz * moveSpeed;
      currentPos.y = this.targetPosition.y + bobOffset;
    } else {
      currentPos.y = this.targetPosition.y + bobOffset;
    }

    // Speaking glow pulse
    if (this.isSpeaking && this.speakingGlow) {
      const pulse = 0.3 + Math.sin(this.time * 3) * 0.2;
      this.speakingGlow.material.opacity = pulse;

      // Also pulse the light
      this.speakingLight.intensity = 0.5 + Math.sin(this.time * 3) * 0.3;
    }

    // Vote indicator float animation
    if (this.voteGroup.visible) {
      this.voteGroup.position.y = 1.5 + Math.sin(this.time * 2) * 0.1;
    }
  }

  /**
   * Get ideology score for sorting
   */
  getIdeologyScore() {
    return (this.ideology?.econ || 0) + (this.ideology?.social || 0);
  }

  /**
   * Destroy the figure
   */
  destroy() {
    // Dispose geometries and materials
    this.head.geometry.dispose();
    this.body.geometry.dispose();
    this.material.dispose();

    if (this.nameSprite) {
      this.nameSprite.material.map.dispose();
      this.nameSprite.material.dispose();
    }

    this.speakingGlow.geometry.dispose();
    this.speakingGlow.material.dispose();
  }
}
