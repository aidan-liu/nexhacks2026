/**
 * ParliamentScene - 3D parliament visualization using Three.js
 * Isometric camera style with soft Monopoly Go-inspired aesthetic
 */
import * as THREE from 'three';
import { Representative } from './Representative.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';

export class ParliamentScene {
  constructor(containerElement) {
    this.containerElement = containerElement;
    this.representatives = new Map(); // id -> Representative
    this.repsByName = new Map(); // name (lowercase) -> Representative

    // Three.js objects
    this.scene = null;
    this.camera = null;
    this.renderer = null;
    this.controls = null;
    this.composer = null;
    this.raycaster = null;
    this.mouse = new THREE.Vector2();

    // Isometric camera config
    this.frustumSize = 24;

    // Scene elements
    this.floor = null;
    this.floorGrid = null;
    this.roomShell = null;
    this.podium = null;
    this.currentSpeaker = null;
    this.repsGroup = null;

    // Minimal isometric room config
    this.gridBlocks = 150;
    this.gridBlockSize = 0.2; // world-units per grid block (150 * 0.2 = 30 units)
    this.roomWallHeight = 2.6;
    this.roomWallThickness = 0.5;
    this.chamberFurniture = null;
    this.defaultZoom = 1.0;

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

    // Ambient behavior (wandering + small conversations)
    this.ambientEnabled = true;
    this._ambientTime = 0;
    this._ambientConversation = null;
    this._nextConversationAt = 6;
    this._ambientPhrases = [
      'Thoughts on this?',
      'Let’s review the details.',
      'I see your point.',
      'We should compromise.',
      'Any concerns?',
      'Let’s take a quick vote.',
      'Can we align on this?',
      'That’s interesting.'
    ];

    // Simulation-driven interactions (meetings / lobbying) queued from backend log events.
    this._simQueue = [];
    this._simActive = null;
    this._simLaneCounter = 0;
  }

  /**
   * Initialize Three.js scene
   */
  async init() {
    const width = this.containerElement.clientWidth;
    const height = this.containerElement.clientHeight;

    // Create scene
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0xf0efe9);

    // Create orthographic camera for isometric view
    const aspect = width / height;
    this.camera = new THREE.OrthographicCamera(
      this.frustumSize * aspect / -2,
      this.frustumSize * aspect / 2,
      this.frustumSize / 2,
      this.frustumSize / -2,
      0.1,
      1000
    );
    // Isometric angle position (equal x, y, z offset for true isometric)
    this.camera.position.set(18, 18, 18);
    this.camera.lookAt(0, 0, 0);
    this.camera.zoom = this.defaultZoom;
    this.camera.updateProjectionMatrix();

    // Create renderer
    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setSize(width, height);
    this.renderer.setPixelRatio(window.devicePixelRatio);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.containerElement.appendChild(this.renderer.domElement);

    // Setup OrbitControls with isometric lock (no rotation, only pan/zoom)
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableRotate = false; // Keep isometric perspective locked
    this.controls.enableZoom = true;
    this.controls.enablePan = true;
    this.controls.minZoom = 0.15;
    this.controls.maxZoom = 3;
    this.controls.target.set(0, 0, 0);

    // Setup post-processing for bloom effect
    this._setupPostProcessing();

    // Create raycaster for mouse interaction
    this.raycaster = new THREE.Raycaster();

    // Setup lighting
    this._setupLighting();

    // Create chamber elements
    this._createFloor();
    this._createPodium();
    this._createChamberFurniture();

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
   * Setup post-processing bloom effect for soft Monopoly Go aesthetic
   */
  _setupPostProcessing() {
    const width = this.containerElement.clientWidth;
    const height = this.containerElement.clientHeight;

    // Create composer
    this.composer = new EffectComposer(this.renderer);

    // Add render pass
    const renderPass = new RenderPass(this.scene, this.camera);
    this.composer.addPass(renderPass);

    // Add subtle bloom pass
    const bloomPass = new UnrealBloomPass(
      new THREE.Vector2(width, height),
      0.25,  // strength - very subtle for soft glow
      0.5,   // radius
      0.4    // threshold - only bright areas glow
    );
    this.composer.addPass(bloomPass);
  }

  /**
   * Create semi-circular floor with soft colors
   */
  _createFloor() {
    const size = this.gridBlocks * this.gridBlockSize;

    // Flat grey floor base
    const floorGeometry = new THREE.PlaneGeometry(size, size, 1, 1);
    const floorMaterial = new THREE.MeshStandardMaterial({
      color: 0xd6d6d6,
      roughness: 0.95,
      metalness: 0.0
    });

    this.floor = new THREE.Mesh(floorGeometry, floorMaterial);
    this.floor.rotation.x = -Math.PI / 2;
    this.floor.position.y = 0;
    this.floor.receiveShadow = true;
    this.scene.add(this.floor);

    // 300x300 isometric grid overlay
    this.floorGrid = new THREE.GridHelper(
      size,
      this.gridBlocks,
      0x8b8b8b, // center lines
      0xb0b0b0  // grid lines
    );
    this.floorGrid.position.y = 0.01;
    this.scene.add(this.floorGrid);

    // Minimal room shell (low walls) to read as an interior
    this.roomShell = new THREE.Group();

    const wallMaterial = new THREE.MeshStandardMaterial({
      color: 0xbcbcbc,
      roughness: 0.92,
      metalness: 0.0
    });

    const half = size / 2;
    const h = this.roomWallHeight;
    const t = this.roomWallThickness;

    const wallNorth = new THREE.Mesh(new THREE.BoxGeometry(size + t * 2, h, t), wallMaterial);
    wallNorth.position.set(0, h / 2, -half - t / 2);
    wallNorth.castShadow = true;
    wallNorth.receiveShadow = true;

    const wallSouth = new THREE.Mesh(new THREE.BoxGeometry(size + t * 2, h, t), wallMaterial);
    wallSouth.position.set(0, h / 2, half + t / 2);
    wallSouth.castShadow = true;
    wallSouth.receiveShadow = true;

    const wallWest = new THREE.Mesh(new THREE.BoxGeometry(t, h, size + t * 2), wallMaterial);
    wallWest.position.set(-half - t / 2, h / 2, 0);
    wallWest.castShadow = true;
    wallWest.receiveShadow = true;

    const wallEast = new THREE.Mesh(new THREE.BoxGeometry(t, h, size + t * 2), wallMaterial);
    wallEast.position.set(half + t / 2, h / 2, 0);
    wallEast.castShadow = true;
    wallEast.receiveShadow = true;

    this.roomShell.add(wallNorth, wallSouth, wallWest, wallEast);
    this.scene.add(this.roomShell);
  }

  /**
   * Create podium platform with soft colors
   */
  _createPodium() {
    // Podium base - wood
    const podiumGeometry = new THREE.BoxGeometry(2, 0.3, 1);
    const podiumMaterial = new THREE.MeshStandardMaterial({ color: 0xB0773A, roughness: 0.85, metalness: 0.0 });
    this.podium = new THREE.Mesh(podiumGeometry, podiumMaterial);
    this.podium.position.copy(this.podiumPosition);
    this.podium.position.y = 0.15;
    this.podium.castShadow = true;
    this.podium.receiveShadow = true;
    this.scene.add(this.podium);

    // Podium accent - lighter wood trim
    const accentGeometry = new THREE.BoxGeometry(2.1, 0.05, 1.1);
    const accentMaterial = new THREE.MeshStandardMaterial({ color: 0xD3A46B, roughness: 0.7, metalness: 0.0 });
    const accent = new THREE.Mesh(accentGeometry, accentMaterial);
    accent.position.copy(this.podiumPosition);
    accent.position.y = 0.32;
    this.scene.add(accent);

    // "SPEAKER" label using a sprite - soft gray text
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 64;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#9CA3AF';  // Soft gray
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

  _createChamberFurniture() {
    if (this.chamberFurniture) {
      this.scene.remove(this.chamberFurniture);
    }

    const group = new THREE.Group();

    const size = this.gridBlocks * this.gridBlockSize;
    const half = size / 2;

    const wood = new THREE.MeshStandardMaterial({ color: 0xB0773A, roughness: 0.82, metalness: 0.0 });
    const woodDark = new THREE.MeshStandardMaterial({ color: 0x7D4E25, roughness: 0.9, metalness: 0.0 });
    const carpet = new THREE.MeshStandardMaterial({ color: 0xCFCFCF, roughness: 0.98, metalness: 0.0 });

    // Central carpet area (covers the grid slightly so the room reads cleaner)
    const carpetMesh = new THREE.Mesh(new THREE.PlaneGeometry(Math.min(size * 0.55, 22), Math.min(size * 0.6, 24)), carpet);
    carpetMesh.rotation.x = -Math.PI / 2;
    carpetMesh.position.set(0, 0.02, 3.2);
    carpetMesh.receiveShadow = true;
    group.add(carpetMesh);

    // Speaker dais backdrop (simple wood slab)
    const daisWidth = 10;
    const daisDepth = 2.4;
    const daisBase = new THREE.Mesh(new THREE.BoxGeometry(daisWidth, 0.35, daisDepth), woodDark);
    daisBase.position.set(0, 0.175, this.podiumPosition.z - 1.6);
    daisBase.castShadow = true;
    daisBase.receiveShadow = true;
    group.add(daisBase);

    const daisTop = new THREE.Mesh(new THREE.BoxGeometry(daisWidth, 0.14, daisDepth), wood);
    daisTop.position.set(0, 0.35 + 0.07, this.podiumPosition.z - 1.6);
    daisTop.castShadow = true;
    daisTop.receiveShadow = true;
    group.add(daisTop);

    // Benches/desks per row (aligned to current rep placement logic)
    const rowSizes = [5, 5, 4, 3, 2];
    const aisleGap = 0.8;
    const deskHeight = 0.32;
    const deskDepth = 0.7;
    const deskYOffset = 0.16;
    const deskZOffset = 0.55;
    const padding = 1.1;

    for (let rowIdx = 0; rowIdx < rowSizes.length; rowIdx++) {
      const rowSize = rowSizes[rowIdx];
      const zRow = this.seatingStartZ + rowIdx * this.rowSpacing;
      const zDesk = zRow + deskZOffset;

      const leftStartX = -aisleGap;
      const rightStartX = aisleGap;

      const leftMaxX = leftStartX - 0.5 * this.seatSpacing;
      const leftMinX = leftStartX - (rowSize - 0.5) * this.seatSpacing;
      const leftWidth = Math.min(Math.abs(leftMaxX - leftMinX) + padding, half * 2 - 2);
      const leftCenterX = (leftMaxX + leftMinX) / 2;

      const rightMinX = rightStartX + 0.5 * this.seatSpacing;
      const rightMaxX = rightStartX + (rowSize - 0.5) * this.seatSpacing;
      const rightWidth = Math.min(Math.abs(rightMaxX - rightMinX) + padding, half * 2 - 2);
      const rightCenterX = (rightMaxX + rightMinX) / 2;

      const deskBaseGeo = new THREE.BoxGeometry(1, deskHeight, deskDepth);
      const deskTrimGeo = new THREE.BoxGeometry(1, 0.12, deskDepth + 0.1);

      const leftDeskBase = new THREE.Mesh(deskBaseGeo, woodDark);
      leftDeskBase.scale.x = leftWidth;
      leftDeskBase.position.set(leftCenterX, deskYOffset, zDesk);
      leftDeskBase.castShadow = true;
      leftDeskBase.receiveShadow = true;

      const leftDeskTrim = new THREE.Mesh(deskTrimGeo, wood);
      leftDeskTrim.scale.x = leftWidth;
      leftDeskTrim.position.set(leftCenterX, deskYOffset + deskHeight / 2 + 0.06, zDesk);
      leftDeskTrim.castShadow = true;
      leftDeskTrim.receiveShadow = true;

      const rightDeskBase = new THREE.Mesh(deskBaseGeo, woodDark);
      rightDeskBase.scale.x = rightWidth;
      rightDeskBase.position.set(rightCenterX, deskYOffset, zDesk);
      rightDeskBase.castShadow = true;
      rightDeskBase.receiveShadow = true;

      const rightDeskTrim = new THREE.Mesh(deskTrimGeo, wood);
      rightDeskTrim.scale.x = rightWidth;
      rightDeskTrim.position.set(rightCenterX, deskYOffset + deskHeight / 2 + 0.06, zDesk);
      rightDeskTrim.castShadow = true;
      rightDeskTrim.receiveShadow = true;

      group.add(leftDeskBase, leftDeskTrim, rightDeskBase, rightDeskTrim);
    }

    this.chamberFurniture = group;
    this.scene.add(this.chamberFurniture);
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
   * Handle mouse click - focus camera on representative
   */
  _onMouseClick(event) {
    if (this.hoveredRep) {
      // Focus camera on clicked representative
      this.focusOnRepresentative(this.hoveredRep);
      // Call registered click callback
      if (this.onRepClick) {
        this.onRepClick(this.hoveredRep.data);
      }
    } else {
      // Click on empty space resets the view
      this.resetView();
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

    // Initialize ambient metadata per rep
    for (const rep of this.representatives.values()) {
      rep._ambient = {
        inConversation: false,
        nextWanderAt: this._ambientTime + 1 + Math.random() * 4
      };
    }
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
   * Handle speaking event with cinematic camera focus
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

      // Cinematic camera focus on speaker
      this.focusOnRepresentative(rep);
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
   * Clear all speaking states and reset camera
   */
  clearSpeaking() {
    if (this.currentSpeaker) {
      this.currentSpeaker.setSpeaking(false);
      this.currentSpeaker.returnToBase();
      this.currentSpeaker = null;
    }
    // Reset camera to default isometric view
    this.resetView();
  }

  /**
   * Cinematic camera transition - focus on a representative
   * @param {Representative} rep - The representative to focus on
   */
  focusOnRepresentative(rep) {
    if (!rep || !rep.group) return;

    const worldPos = new THREE.Vector3();
    rep.group.getWorldPosition(worldPos);

    // Calculate camera offset for cinematic close-up
    const offset = 10;
    const targetCameraPos = {
      x: worldPos.x + offset,
      y: worldPos.y + offset,
      z: worldPos.z + offset
    };

    // Animate camera position
    gsap.to(this.camera.position, {
      x: targetCameraPos.x,
      y: targetCameraPos.y,
      z: targetCameraPos.z,
      duration: 1.5,
      ease: "power2.inOut"
    });

    // Animate controls target to focus on the representative
    gsap.to(this.controls.target, {
      x: worldPos.x,
      y: worldPos.y,
      z: worldPos.z,
      duration: 1.5,
      ease: "power2.inOut"
    });
  }

  /**
   * Reset camera to default isometric view
   */
  resetView() {
    // Default isometric position
    const defaultPos = { x: 18, y: 18, z: 18 };
    const defaultCenter = { x: 0, y: 0, z: 0 };

    // Animate back to default position
    gsap.to(this.camera.position, {
      x: defaultPos.x,
      y: defaultPos.y,
      z: defaultPos.z,
      duration: 1.2,
      ease: "power2.inOut"
    });

    gsap.to(this.camera, {
      zoom: this.defaultZoom,
      duration: 1.2,
      ease: "power2.inOut",
      onUpdate: () => this.camera.updateProjectionMatrix()
    });

    // Animate controls target back to center
    gsap.to(this.controls.target, {
      x: defaultCenter.x,
      y: defaultCenter.y,
      z: defaultCenter.z,
      duration: 1.2,
      ease: "power2.inOut"
    });
  }

  /**
   * Animation loop
   */
  _animate() {
    requestAnimationFrame(() => this._animate());

    const delta = this.clock.getDelta();
    this._ambientTime += delta;

    // Update controls
    if (this.controls) {
      this.controls.update();
    }

    this._updateSimInteractions(delta);

    if (this.ambientEnabled) {
      this._updateAmbient(delta);
    }

    // Update all representatives
    for (const rep of this.representatives.values()) {
      rep.update(delta);
    }

    // Use composer for post-processing
    if (this.composer) {
      this.composer.render();
    } else {
      this.renderer.render(this.scene, this.camera);
    }
  }

  handleLobbying(lobbyistName, targetName, message = '') {
    this._simQueue.push({
      type: 'lobbying',
      lobbyistName,
      targetName,
      message
    });
  }

  handleMeeting(aName, bName, aCommittee, bCommittee) {
    this._simQueue.push({
      type: 'meeting',
      aName,
      bName,
      aCommittee,
      bCommittee
    });
  }

  _updateSimInteractions(delta) {
    // Keep any active sim-driven interaction alive; otherwise start the next queued one.
    if (!this._simActive) {
      while (this._simQueue.length > 0 && !this._simActive) {
        const next = this._simQueue.shift();
        this._startSimInteraction(next);
      }
      return;
    }

    const sim = this._simActive;
    if (!sim?.a || !sim?.b) {
      this._endSimInteraction();
      return;
    }

    // Keep them facing each other.
    sim.a.faceTowards?.(sim.b.group.position.x, sim.b.group.position.z);
    sim.b.faceTowards?.(sim.a.group.position.x, sim.a.group.position.z);

    if (this._ambientTime >= sim.endsAt) {
      this._endSimInteraction();
      return;
    }

    // Meeting has two short turns.
    if (sim.type === 'meeting' && this._ambientTime >= sim.nextUtterAt) {
      if (sim.stage === 0) {
        sim.a.setSpeaking?.(true, 'sim');
        sim.b.setSpeaking?.(false, 'sim');
        sim.a.say?.(sim.aLine, 2600);
        sim.stage = 1;
        sim.nextUtterAt = this._ambientTime + 2.8;
      } else if (sim.stage === 1) {
        sim.a.setSpeaking?.(false, 'sim');
        sim.b.setSpeaking?.(true, 'sim');
        sim.b.say?.(sim.bLine, 2600);
        sim.stage = 2;
        sim.nextUtterAt = this._ambientTime + 3.0;
      } else {
        // After both have spoken, keep them idle until end.
        sim.a.setSpeaking?.(false, 'sim');
        sim.b.setSpeaking?.(false, 'sim');
        sim.nextUtterAt = sim.endsAt + 999;
      }
    }
  }

  _startSimInteraction(evt) {
    if (!evt) return;

    let a = null;
    let b = null;
    let aLine = '';
    let bLine = '';

    if (evt.type === 'lobbying') {
      a = this.findRepByName(evt.lobbyistName);
      b = this.findRepByName(evt.targetName);
      aLine = evt.message && evt.message.trim() ? evt.message.trim() : 'Can we talk for a second?';
    } else if (evt.type === 'meeting') {
      a = this.findRepByName(evt.aName);
      b = this.findRepByName(evt.bName);
      aLine = `I’m on ${evt.aCommittee}.`;
      bLine = `I’m on ${evt.bCommittee}.`;
    } else {
      return;
    }

    if (!a || !b) return;
    if (a === b) return;

    // If the floor has a current speaker, avoid clobbering them.
    if (a === this.currentSpeaker || b === this.currentSpeaker) return;

    // If either rep is currently in an ambient conversation, end it to avoid fights over control.
    if (this._ambientConversation && (this._ambientConversation.a === a || this._ambientConversation.b === a ||
        this._ambientConversation.a === b || this._ambientConversation.b === b)) {
      this._endAmbientConversation(this._ambientConversation);
    }

    if (!a._ambient) a._ambient = { inConversation: false, nextWanderAt: this._ambientTime + 2 };
    if (!b._ambient) b._ambient = { inConversation: false, nextWanderAt: this._ambientTime + 2 };
    a._ambient.inConversation = true;
    b._ambient.inConversation = true;

    // Choose a visible "lobby zone" on the main carpet so movement is obvious.
    const lane = this._simLaneCounter % 5; // 0..4
    const row = Math.floor(this._simLaneCounter / 5) % 2; // 0..1
    this._simLaneCounter++;

    const centerX = (lane - 2) * 2.4;
    const centerZ = 3.2 + row * 1.6;
    const center = new THREE.Vector3(centerX, 0, centerZ);

    const sep = 0.8;
    const aMeet = center.clone().add(new THREE.Vector3(-sep, 0, 0));
    const bMeet = center.clone().add(new THREE.Vector3(sep, 0, 0));
    a.moveTo(aMeet.x, 0, aMeet.z);
    b.moveTo(bMeet.x, 0, bMeet.z);

    // Start speaking immediately for lobbying; meeting speaks in two turns.
    if (evt.type === 'lobbying') {
      a.setSpeaking?.(true, 'sim');
      b.setSpeaking?.(false, 'sim');
      a.say?.(aLine, 5200);
    } else {
      a.setSpeaking?.(false, 'sim');
      b.setSpeaking?.(false, 'sim');
    }

    this._simActive = {
      type: evt.type,
      a,
      b,
      aLine,
      bLine,
      stage: 0,
      nextUtterAt: this._ambientTime + (evt.type === 'meeting' ? 1.0 : 999),
      endsAt: this._ambientTime + (evt.type === 'meeting' ? 9.0 : 10.0)
    };
  }

  _endSimInteraction() {
    const sim = this._simActive;
    if (!sim) return;
    const { a, b } = sim;
    if (a) {
      a.setSpeaking?.(false, 'sim');
      a.returnToBase?.();
      if (a._ambient) a._ambient.inConversation = false;
    }
    if (b) {
      b.setSpeaking?.(false, 'sim');
      b.returnToBase?.();
      if (b._ambient) b._ambient.inConversation = false;
    }
    this._simActive = null;
  }

  _updateAmbient(delta) {
    this._updateAmbientConversation();
    this._updateAmbientWander();
  }

  _updateAmbientWander() {
    for (const rep of this.representatives.values()) {
      if (!rep?._ambient) continue;
      if (rep._ambient.inConversation) continue;
      if (rep.isSimSpeaking?.() || rep === this.currentSpeaker) continue;

      if (this._ambientTime < rep._ambient.nextWanderAt) continue;

      // Wander around their seat
      const r = 1.6;
      const angle = Math.random() * Math.PI * 2;
      const radius = Math.random() * r;
      const x = rep.basePosition.x + Math.cos(angle) * radius;
      const z = rep.basePosition.z + Math.sin(angle) * radius;
      rep.moveTo(x, rep.basePosition.y, z);
      rep.faceTowards?.(x, z);

      rep._ambient.nextWanderAt = this._ambientTime + 1.2 + Math.random() * 3.0;
    }
  }

  _endAmbientConversation(convo, returnToSeats = true) {
    if (!convo) return;
    const { a, b } = convo;
    if (a) {
      a.setSpeaking?.(false, 'ambient');
      if (returnToSeats) a.returnToBase?.();
      if (a._ambient) a._ambient.inConversation = false;
    }
    if (b) {
      b.setSpeaking?.(false, 'ambient');
      if (returnToSeats) b.returnToBase?.();
      if (b._ambient) b._ambient.inConversation = false;
    }
    this._ambientConversation = null;
    this._nextConversationAt = this._ambientTime + 8 + Math.random() * 12;
  }

  _startAmbientConversation(a, b) {
    if (!a || !b) return;
    if (!a._ambient) a._ambient = { inConversation: false, nextWanderAt: this._ambientTime + 2 };
    if (!b._ambient) b._ambient = { inConversation: false, nextWanderAt: this._ambientTime + 2 };

    a._ambient.inConversation = true;
    b._ambient.inConversation = true;

    // Meet near the midpoint between their seats
    const aPos = a.basePosition.clone();
    const bPos = b.basePosition.clone();
    const mid = aPos.clone().add(bPos).multiplyScalar(0.5);

    const dir = bPos.clone().sub(aPos);
    dir.y = 0;
    const len = dir.length();
    if (len > 1e-5) dir.multiplyScalar(1 / len);

    const perp = new THREE.Vector3(-dir.z, 0, dir.x);
    const sep = 0.7;
    const aMeet = mid.clone().add(perp.clone().multiplyScalar(sep));
    const bMeet = mid.clone().add(perp.clone().multiplyScalar(-sep));

    a.moveTo(aMeet.x, 0, aMeet.z);
    b.moveTo(bMeet.x, 0, bMeet.z);

    this._ambientConversation = {
      a,
      b,
      endsAt: this._ambientTime + 10 + Math.random() * 10,
      nextUtterAt: this._ambientTime + 1.0 + Math.random() * 1.5
    };
  }

  _updateAmbientConversation() {
    const convo = this._ambientConversation;

    if (!convo) {
      // If the simulation is actively orchestrating 1:1 interactions, don't start new ambient ones.
      if (this._simActive || (this._simQueue && this._simQueue.length > 0)) {
        return;
      }
      if (this._ambientTime < this._nextConversationAt) return;
      const candidates = [...this.representatives.values()].filter((rep) => {
        if (!rep?._ambient) return false;
        if (rep._ambient.inConversation) return false;
        if (rep.isSimSpeaking?.() || rep === this.currentSpeaker) return false;
        return true;
      });
      if (candidates.length < 2) {
        this._nextConversationAt = this._ambientTime + 3 + Math.random() * 6;
        return;
      }

      const a = candidates[Math.floor(Math.random() * candidates.length)];
      let b = a;
      for (let tries = 0; tries < 8 && b === a; tries++) {
        b = candidates[Math.floor(Math.random() * candidates.length)];
      }
      if (b === a) {
        this._nextConversationAt = this._ambientTime + 3 + Math.random() * 6;
        return;
      }

      this._startAmbientConversation(a, b);
      return;
    }

    const { a, b, endsAt, nextUtterAt } = convo;
    if (!a || !b) {
      this._endAmbientConversation(convo);
      return;
    }

    // If the simulation takes control, stop ambient conversation quickly
    if (a.isSimSpeaking?.() || b.isSimSpeaking?.() || a === this.currentSpeaker || b === this.currentSpeaker) {
      this._endAmbientConversation(convo);
      return;
    }

    // Keep them facing each other
    a.faceTowards?.(b.group.position.x, b.group.position.z);
    b.faceTowards?.(a.group.position.x, a.group.position.z);

    if (this._ambientTime >= endsAt) {
      this._endAmbientConversation(convo);
      return;
    }

    if (this._ambientTime >= nextUtterAt) {
      const speaker = Math.random() > 0.5 ? a : b;
      const listener = speaker === a ? b : a;
      const phrase = this._ambientPhrases[Math.floor(Math.random() * this._ambientPhrases.length)];

      speaker.setSpeaking?.(true, 'ambient');
      listener.setSpeaking?.(false, 'ambient');
      speaker.say?.(phrase, 2600 + Math.random() * 1600);

      convo.nextUtterAt = this._ambientTime + 2.6 + Math.random() * 2.4;
    }
  }

  /**
   * Handle window resize
   */
  _handleResize() {
    const width = this.containerElement.clientWidth;
    const height = this.containerElement.clientHeight;
    const aspect = width / height;

    // Update orthographic camera frustum
    this.camera.left = this.frustumSize * aspect / -2;
    this.camera.right = this.frustumSize * aspect / 2;
    this.camera.top = this.frustumSize / 2;
    this.camera.bottom = this.frustumSize / -2;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);

    // Update composer size
    if (this.composer) {
      this.composer.setSize(width, height);
    }
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
