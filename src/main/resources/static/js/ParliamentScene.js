/**
 * ParliamentScene - 3D parliament visualization using Three.js
 */
import { Representative } from './Representative.js';

export class ParliamentScene {
  constructor(containerElement) {
    this.containerElement = containerElement;
    this.representatives = new Map(); // id -> Representative
    this.repsByName = new Map(); // name (lowercase) -> Representative

    // Three.js objects
    this.scene = null;
    this.camera = null;
    this.renderer = null;
    this.raycaster = null;
    this.mouse = new THREE.Vector2();

    // Scene elements
    this.floor = null;
    this.podium = null;
    this.currentSpeaker = null;
    this.repsGroup = null;

    // Layout config
    this.podiumPosition = new THREE.Vector3(0, 0.5, -3);
    this.seatingStartZ = 0;
    this.rowSpacing = 1.8;
    this.seatSpacing = 1.4;

    // Animation
    this.clock = new THREE.Clock();

    // Callbacks
    this.onRepHover = null;
    this.onRepClick = null;
    this.hoveredRep = null;
  }

  /**
   * Initialize Three.js scene
   */
  async init() {
    const width = this.containerElement.clientWidth;
    const height = this.containerElement.clientHeight;

    // Create scene
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xe8e8e8);

    // Create camera
    this.camera = new THREE.PerspectiveCamera(50, width / height, 0.1, 1000);
    this.camera.position.set(0, 8, 12);
    this.camera.lookAt(0, 0, 0);

    // Create renderer
    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(window.devicePixelRatio);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.containerElement.appendChild(this.renderer.domElement);

    // Create raycaster for mouse interaction
    this.raycaster = new THREE.Raycaster();

    // Setup lighting
    this._setupLighting();

    // Create chamber elements
    this._createFloor();
    this._createPodium();

    // Create representatives container group
    this.repsGroup = new THREE.Group();
    this.scene.add(this.repsGroup);

    // Setup event listeners
    this._setupEventListeners();

    // Start animation loop
    this._animate();

    return this;
  }

  /**
   * Setup scene lighting
   */
  _setupLighting() {
    // Ambient light for base illumination
    const ambient = new THREE.AmbientLight(0xffffff, 0.6);
    this.scene.add(ambient);

    // Main directional light (sun-like)
    const directional = new THREE.DirectionalLight(0xffffff, 0.8);
    directional.position.set(5, 10, 7);
    directional.castShadow = true;
    directional.shadow.mapSize.width = 2048;
    directional.shadow.mapSize.height = 2048;
    directional.shadow.camera.near = 0.5;
    directional.shadow.camera.far = 50;
    directional.shadow.camera.left = -15;
    directional.shadow.camera.right = 15;
    directional.shadow.camera.top = 15;
    directional.shadow.camera.bottom = -15;
    this.scene.add(directional);

    // Fill light from the front
    const fillLight = new THREE.DirectionalLight(0xffffff, 0.3);
    fillLight.position.set(-3, 5, 8);
    this.scene.add(fillLight);
  }

  /**
   * Create semi-circular floor
   */
  _createFloor() {
    // Main floor - semi-circular shape
    const floorShape = new THREE.Shape();
    const radius = 10;

    // Create semi-circle path
    floorShape.absarc(0, 0, radius, 0, Math.PI, false);
    floorShape.lineTo(-radius, 0);

    const floorGeometry = new THREE.ShapeGeometry(floorShape, 32);
    const floorMaterial = new THREE.MeshStandardMaterial({
      color: 0xd4d4d4,
      roughness: 0.8,
      metalness: 0.1
    });

    this.floor = new THREE.Mesh(floorGeometry, floorMaterial);
    this.floor.rotation.x = -Math.PI / 2;
    this.floor.position.y = 0;
    this.floor.receiveShadow = true;
    this.scene.add(this.floor);

    // Add floor rings for depth/row indication
    for (let i = 1; i <= 4; i++) {
      const ringRadius = 2 + i * 2;
      const ringGeometry = new THREE.RingGeometry(ringRadius - 0.05, ringRadius + 0.05, 32, 1, 0, Math.PI);
      const ringMaterial = new THREE.MeshBasicMaterial({
        color: 0xc0c0c0,
        side: THREE.DoubleSide
      });
      const ring = new THREE.Mesh(ringGeometry, ringMaterial);
      ring.rotation.x = -Math.PI / 2;
      ring.position.y = 0.01;
      this.scene.add(ring);
    }

    // Center aisle line
    const aisleGeometry = new THREE.PlaneGeometry(0.1, radius);
    const aisleMaterial = new THREE.MeshBasicMaterial({ color: 0xc0c0c0 });
    const aisle = new THREE.Mesh(aisleGeometry, aisleMaterial);
    aisle.rotation.x = -Math.PI / 2;
    aisle.position.set(0, 0.01, -radius / 2);
    this.scene.add(aisle);
  }

  /**
   * Create podium platform
   */
  _createPodium() {
    // Podium base
    const podiumGeometry = new THREE.BoxGeometry(2, 0.3, 1);
    const podiumMaterial = new THREE.MeshStandardMaterial({
      color: 0x9ca3af,
      roughness: 0.5,
      metalness: 0.2
    });
    this.podium = new THREE.Mesh(podiumGeometry, podiumMaterial);
    this.podium.position.copy(this.podiumPosition);
    this.podium.position.y = 0.15;
    this.podium.castShadow = true;
    this.podium.receiveShadow = true;
    this.scene.add(this.podium);

    // Podium accent (cyan border)
    const accentGeometry = new THREE.BoxGeometry(2.1, 0.05, 1.1);
    const accentMaterial = new THREE.MeshStandardMaterial({
      color: 0x4dd0e1,
      emissive: 0x4dd0e1,
      emissiveIntensity: 0.3
    });
    const accent = new THREE.Mesh(accentGeometry, accentMaterial);
    accent.position.copy(this.podiumPosition);
    accent.position.y = 0.32;
    this.scene.add(accent);

    // "SPEAKER" label using a sprite
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 64;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#6b7280';
    ctx.font = 'bold 24px monospace';
    ctx.textAlign = 'center';
    ctx.fillText('SPEAKER', 128, 40);

    const texture = new THREE.CanvasTexture(canvas);
    const labelMaterial = new THREE.SpriteMaterial({ map: texture, transparent: true });
    const label = new THREE.Sprite(labelMaterial);
    label.position.copy(this.podiumPosition);
    label.position.y = 0.6;
    label.scale.set(2, 0.5, 1);
    this.scene.add(label);
  }

  /**
   * Setup event listeners
   */
  _setupEventListeners() {
    window.addEventListener('resize', () => this._handleResize());

    this.containerElement.addEventListener('mousemove', (e) => this._onMouseMove(e));
    this.containerElement.addEventListener('click', (e) => this._onMouseClick(e));
  }

  /**
   * Handle mouse move for hover detection
   */
  _onMouseMove(event) {
    const rect = this.containerElement.getBoundingClientRect();
    this.mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    this.mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    // Raycast to find hovered representative
    this.raycaster.setFromCamera(this.mouse, this.camera);

    const intersectObjects = [];
    for (const rep of this.representatives.values()) {
      intersectObjects.push(rep.group);
    }

    const intersects = this.raycaster.intersectObjects(intersectObjects, true);

    if (intersects.length > 0) {
      // Find the representative that was hit
      let hitObject = intersects[0].object;
      while (hitObject.parent && !hitObject.userData.repId) {
        hitObject = hitObject.parent;
      }

      if (hitObject.userData.repId) {
        const rep = this.representatives.get(hitObject.userData.repId);
        if (rep && rep !== this.hoveredRep) {
          // Unhover previous
          if (this.hoveredRep) {
            this.hoveredRep.hideName();
          }
          // Hover new
          this.hoveredRep = rep;
          rep.showName();

          if (this.onRepHover) {
            const screenPos = this._getScreenPosition(rep.group.position);
            this.onRepHover(rep.data, screenPos.x, screenPos.y);
          }
        }
      }
    } else {
      // No hover
      if (this.hoveredRep) {
        this.hoveredRep.hideName();
        this.hoveredRep = null;
        if (this.onRepHover) {
          this.onRepHover(null);
        }
      }
    }
  }

  /**
   * Handle mouse click
   */
  _onMouseClick(event) {
    if (this.hoveredRep && this.onRepClick) {
      this.onRepClick(this.hoveredRep.data);
    }
  }

  /**
   * Convert 3D position to screen coordinates
   */
  _getScreenPosition(position) {
    const vector = position.clone();
    vector.project(this.camera);

    const rect = this.containerElement.getBoundingClientRect();
    return {
      x: (vector.x * 0.5 + 0.5) * rect.width + rect.left,
      y: (-vector.y * 0.5 + 0.5) * rect.height + rect.top
    };
  }

  /**
   * Load representatives and create 3D figures
   */
  async loadRepresentatives(repsData) {
    // Sort by ideology for left/right seating
    const sorted = [...repsData].sort((a, b) => {
      const scoreA = (a.ideology?.econ || 0) + (a.ideology?.social || 0);
      const scoreB = (b.ideology?.econ || 0) + (b.ideology?.social || 0);
      return scoreA - scoreB; // Left (negative) to Right (positive)
    });

    // Split into left and right sides
    const midpoint = Math.floor(sorted.length / 2);
    const leftSide = sorted.slice(0, midpoint);
    const rightSide = sorted.slice(midpoint);

    // Arrange representatives
    const aisleGap = 0.8;

    // Arrange left side
    this._arrangeSection(leftSide, -aisleGap, -1);

    // Arrange right side
    this._arrangeSection(rightSide, aisleGap, 1);

    console.log(`Loaded ${repsData.length} representatives in 3D`);
  }

  _arrangeSection(reps, startX, direction) {
    // Create rows with decreasing seats (amphitheater style)
    const rowSizes = [5, 5, 4, 3, 2];
    let repIndex = 0;

    for (let rowIdx = 0; rowIdx < rowSizes.length && repIndex < reps.length; rowIdx++) {
      const rowSize = Math.min(rowSizes[rowIdx], reps.length - repIndex);
      const z = this.seatingStartZ + rowIdx * this.rowSpacing;

      for (let seatIdx = 0; seatIdx < rowSize && repIndex < reps.length; seatIdx++) {
        const repData = reps[repIndex];
        const rep = new Representative(repData);

        // Calculate x position (spread outward from aisle)
        const offset = (seatIdx + 0.5) * this.seatSpacing * direction;
        const x = startX + offset;

        rep.setPosition(x, 0, z);
        rep.group.userData.repId = repData.id;

        this.repsGroup.add(rep.group);
        this.representatives.set(repData.id, rep);
        this.repsByName.set(repData.name.toLowerCase(), rep);

        repIndex++;
      }
    }
  }

  /**
   * Find representative by name (fuzzy match)
   */
  findRepByName(name) {
    const normalized = name.toLowerCase().trim();

    // Exact match
    if (this.repsByName.has(normalized)) {
      return this.repsByName.get(normalized);
    }

    // Partial match (last name)
    for (const [key, rep] of this.repsByName) {
      if (key.includes(normalized) || normalized.includes(key.split(' ').pop())) {
        return rep;
      }
    }

    // Try matching just the last name
    for (const [key, rep] of this.repsByName) {
      const lastName = key.split(' ').pop();
      if (lastName === normalized || normalized.includes(lastName)) {
        return rep;
      }
    }

    return null;
  }

  /**
   * Handle speaking event
   */
  handleSpeaking(speakerName, content) {
    // Clear previous speaker
    if (this.currentSpeaker) {
      this.currentSpeaker.setSpeaking(false);
      this.currentSpeaker.returnToBase();
    }

    // Find new speaker
    const rep = this.findRepByName(speakerName);
    if (rep) {
      rep.setSpeaking(true);
      this.currentSpeaker = rep;

      // Move speaker towards podium
      const targetPos = this.podiumPosition.clone();
      targetPos.y = 0;
      targetPos.z += 1.5;
      rep.moveTo(targetPos.x, targetPos.y, targetPos.z);
    }
  }

  /**
   * Handle voting event
   */
  handleVoting(voterName, vote) {
    const rep = this.findRepByName(voterName);
    if (rep) {
      rep.showVote(vote);
    }
  }

  /**
   * Clear all speaking states
   */
  clearSpeaking() {
    if (this.currentSpeaker) {
      this.currentSpeaker.setSpeaking(false);
      this.currentSpeaker.returnToBase();
      this.currentSpeaker = null;
    }
  }

  /**
   * Animation loop
   */
  _animate() {
    requestAnimationFrame(() => this._animate());

    const delta = this.clock.getDelta();

    // Update all representatives
    for (const rep of this.representatives.values()) {
      rep.update(delta);
    }

    this.renderer.render(this.scene, this.camera);
  }

  /**
   * Handle window resize
   */
  _handleResize() {
    const width = this.containerElement.clientWidth;
    const height = this.containerElement.clientHeight;

    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  }

  /**
   * Destroy the scene
   */
  destroy() {
    if (this.renderer) {
      this.renderer.dispose();
      this.containerElement.removeChild(this.renderer.domElement);
    }
  }
}
