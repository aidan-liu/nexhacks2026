import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { clone as skeletonClone } from 'three/addons/utils/SkeletonUtils.js';

/**
 * Representative - 3D figure using GLB models
 */
export class Representative {
  // Neutral aesthetic (avoid party coloring)
  static BASE_RING_COLOR = 0xD1D5DB;
  static SPEAKING_COLOR = 0xFFD9B3;
  static FALLBACK_COLOR = 0xB8B8B8;

  static DEFAULT_SCALE = 0.8;
  static PALETTE_SIZE = 256;

  // Shared loader
  static loader = new GLTFLoader();
  
  // Cache for loaded models
  static modelCache = new Map();
  static paletteCache = new Map();

  constructor(data) {
    this.data = data;
    this.id = data.id;
    this.name = data.name;
    this.party = data.party;
    this.ideology = data.ideology;

    // Create container group
    this.group = new THREE.Group();
    this.group.scale.setScalar(Representative.DEFAULT_SCALE);

    // Base position
    this.basePosition = new THREE.Vector3();
    this.targetPosition = new THREE.Vector3();

    // State
    this.isSpeaking = false;
    this.model = null;
    this.speakingGlow = null;
    this.speakingLight = null;
    this.voteSprite = null;
    this.nameSprite = null;
    this.speechSprite = null;
    this._speechCanvas = null;
    this._speechCtx = null;
    this._speechHideTimer = null;

    // Animation state
    this.bobPhase = Math.random() * Math.PI * 2;
    this.bobSpeed = 1.5 + Math.random() * 0.5;
    this.bobAmount = 0.05;
    this.time = 0;

    // Facing / rotation
    this.rotationY = 0;
    this.targetRotationY = 0;
    this.turnSpeed = 6.0; // rad/sec

    // Speaking reasons (sim vs ambient)
    this._speakingReasons = { sim: false, ambient: false };

    // Stable appearance (skin/hair/clothing colors)
    this.appearance = this._pickAppearance();

    // Build the 3D figure
    this._buildFallbackFigure(); // Start with fallback, replace when model loads
    this._addIndicators();
    this._createNameSprite();
    this._createVoteSprite();
    this._createSpeechSprite();
    this._loadModel(); // Try to load GLB model
  }

  async _loadModel() {
    // Use suit variants only (representatives) and keep the choice stable per rep
    const modelRand = this._mulberry32((this.appearance?.seed ?? 0) ^ 0x9E3779B9);
    const gender = modelRand() > 0.5 ? 'male' : 'female';
    const variant = 'd';
    this.modelGender = gender;
    const filename = `character-${gender}-${variant}.glb`;
    const path = `/static/assets/models/${filename}`;

    try {
      let gltf;

      // Check cache first
      if (Representative.modelCache.has(filename)) {
        gltf = Representative.modelCache.get(filename);
      } else {
        gltf = await new Promise((resolve, reject) => {
          Representative.loader.load(
            path,
            (data) => resolve(data),
            undefined,
            (error) => {
              console.error('GLTFLoader error:', error);
              reject(error);
            }
          );
        });
        Representative.modelCache.set(filename, gltf);
      }

      // Clone the scene preserving skeletons/skinning (many character GLBs are skinned)
      const newModel = skeletonClone(gltf.scene);

      const bodyPaletteTexture = this._createPaletteTexture(this.appearance, gender, 'body');
      const headPaletteTexture = this._createPaletteTexture(this.appearance, gender, 'head');

      const bodyPaletteMaterial = new THREE.MeshStandardMaterial({
        map: bodyPaletteTexture,
        roughness: 0.85,
        metalness: 0.0,
        skinning: false,
        side: THREE.DoubleSide
      });
      const bodyPaletteSkinnedMaterial = new THREE.MeshStandardMaterial({
        map: bodyPaletteTexture,
        roughness: 0.85,
        metalness: 0.0,
        skinning: true,
        side: THREE.DoubleSide
      });

      const headPaletteMaterial = new THREE.MeshStandardMaterial({
        map: headPaletteTexture,
        roughness: 0.85,
        metalness: 0.0,
        skinning: false,
        side: THREE.DoubleSide
      });
      const headPaletteSkinnedMaterial = new THREE.MeshStandardMaterial({
        map: headPaletteTexture,
        roughness: 0.85,
        metalness: 0.0,
        skinning: true,
        side: THREE.DoubleSide
      });

      // Count meshes and ensure visibility/shadows
      let meshCount = 0;
      newModel.traverse((child) => {
        if (child.isMesh) {
          meshCount++;
          child.castShadow = true;
          child.receiveShadow = true;
          if (child.isSkinnedMesh) child.frustumCulled = false;
          const isHead = String(child.name || '').toLowerCase().includes('head');
          if (isHead) {
            child.material = child.isSkinnedMesh ? headPaletteSkinnedMaterial : headPaletteMaterial;
          } else {
            child.material = child.isSkinnedMesh ? bodyPaletteSkinnedMaterial : bodyPaletteMaterial;
          }
        }
      });

      // Calculate bounding box to properly center the model
      const box = new THREE.Box3().setFromObject(newModel);
      const size = box.getSize(new THREE.Vector3());
      const center = box.getCenter(new THREE.Vector3());

      // Scale based on model size to normalize height (1.5x larger)
      const targetHeight = 2.4;
      const scaleY = size.y > 0 ? targetHeight / size.y : 1;
      newModel.scale.set(scaleY, scaleY, scaleY);

      // Recalculate bounding box AFTER scaling for correct positioning
      const scaledBox = new THREE.Box3().setFromObject(newModel);
      const scaledSize = scaledBox.getSize(new THREE.Vector3());
      const scaledCenter = scaledBox.getCenter(new THREE.Vector3());


      // Position so model sits on the floor (bottom at y=0)
      newModel.position.y = -scaledBox.min.y;
      newModel.position.x = -scaledCenter.x;
      newModel.position.z = -scaledCenter.z;

      // Face forward towards negative Z
      newModel.rotation.y = Math.PI;

      // Make sure the model is visible
      newModel.visible = true;
      newModel.traverse((child) => {
        if (child.isMesh) {
          child.visible = true;
        }
      });

      // Remove the old fallback model if it exists
      if (this.model) {
        this.group.remove(this.model);
      }

      // Add the new GLTF model
      this.model = newModel;
      this.group.add(this.model);

      console.log(`Loaded 3D model for ${this.name}: ${filename} (scale: ${scaleY.toFixed(2)}, meshes: ${meshCount})`);

    } catch (error) {
      console.error(`Failed to load model for ${this.name}:`, error);
      // Fallback already exists, no need to rebuild
    }
  }

  _addIndicators() {
    // Base ring (neutral)
    const geometry = new THREE.RingGeometry(0.3, 0.4, 32);
    const material = new THREE.MeshBasicMaterial({ 
      color: Representative.BASE_RING_COLOR,
      side: THREE.DoubleSide,
      transparent: true,
      opacity: 0.8
    });
    const ring = new THREE.Mesh(geometry, material);
    ring.rotation.x = -Math.PI / 2;
    ring.position.y = 0.02;
    this.group.add(ring);
    
    // Speaking glow (ring around figure)
    const glowGeometry = new THREE.RingGeometry(0.4, 0.6, 32);
    const glowMaterial = new THREE.MeshBasicMaterial({
      color: Representative.SPEAKING_COLOR,
      transparent: true,
      opacity: 0,
      side: THREE.DoubleSide
    });
    this.speakingGlow = new THREE.Mesh(glowGeometry, glowMaterial);
    this.speakingGlow.rotation.x = -Math.PI / 2;
    this.speakingGlow.position.y = 0.02;
    this.group.add(this.speakingGlow);

    // Add speaking light
    this.speakingLight = new THREE.PointLight(Representative.SPEAKING_COLOR, 0, 3);
    this.speakingLight.position.y = 1.0;
    this.group.add(this.speakingLight);
  }

  _buildFallbackFigure() {
    const geometry = new THREE.CylinderGeometry(0.2, 0.2, 0.8, 16);
    const material = new THREE.MeshStandardMaterial({ color: Representative.FALLBACK_COLOR });
    this.model = new THREE.Mesh(geometry, material);
    this.model.position.y = 0.4;
    this.model.castShadow = true;
    this.group.add(this.model);
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
    this.voteGroup = new THREE.Group();
    this.voteGroup.position.y = 1.5;
    this.voteGroup.visible = false;
    this.group.add(this.voteGroup);
  }

  _createSpeechSprite() {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 128;
    const ctx = canvas.getContext('2d');
    this._speechCanvas = canvas;
    this._speechCtx = ctx;

    const texture = new THREE.CanvasTexture(canvas);
    const spriteMaterial = new THREE.SpriteMaterial({ map: texture, transparent: true, opacity: 0 });
    this.speechSprite = new THREE.Sprite(spriteMaterial);
    this.speechSprite.position.y = 2.2;
    this.speechSprite.scale.set(2.2, 1.1, 1);
    this.group.add(this.speechSprite);
  }

  _hashStringToUint32(str) {
    let hash = 2166136261;
    for (let i = 0; i < str.length; i++) {
      hash ^= str.charCodeAt(i);
      hash = Math.imul(hash, 16777619);
    }
    return hash >>> 0;
  }

  _mulberry32(seed) {
    return function () {
      let t = seed += 0x6D2B79F5;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  _pickAppearance() {
    const seed = this._hashStringToUint32(`${this.id}:${this.name}`);
    const rand = this._mulberry32(seed);

    // Keep appearances realistic: a few skin tones + natural hair colors + suits.
    // Note: We intentionally model "skin tones" only (no demographic labeling).
    const skinTones = [
      { name: 'porcelain', color: '#FFFFFF' },
      { name: 'veryLight', color: '#f8e2d3' },
      { name: 'light', color: '#f4cdb1' },
      { name: 'medium', color: '#d9a07a' },
      { name: 'tan', color: '#c8875f' },
      { name: 'deep', color: '#7a4a33' }
    ];

    const hairColors = [
      { name: 'blonde', color: '#d3b06a' },
      { name: 'brown', color: '#5a3727' },
      { name: 'ginger', color: '#c65a2b' },
      { name: 'black', color: '#161616' }
    ];

    const suitColors = [
      { name: 'navy', color: '#1f2a44' },
      { name: 'charcoal', color: '#2d3138' },
      { name: 'black', color: '#111827' },
      { name: 'slate', color: '#334155' },
      { name: 'brown', color: '#3b2a23' }
    ];

    const shirt = { name: 'white', color: '#f8fafc' };

    const pick = (arr) => arr[Math.floor(rand() * arr.length)];
    const look = {
      skin: pick(skinTones),
      hair: pick(hairColors),
      outfit: pick(suitColors),  // suit color
      accent: shirt              // shirt (tie, if present, will be subtle)
    };
    const shoes = { name: 'shoes', color: '#000000' };
    return { seed, ...look, shoes };
  }

  _createPaletteTexture(appearance, layout = 'male', mode = 'body') {
    const key = `${layout}|${mode}|${appearance.skin.color}|${appearance.hair.color}|${appearance.outfit.color}|${appearance.accent.color}|${appearance.shoes.color}`;
    const cached = Representative.paletteCache.get(key);
    if (cached) return cached;

    const canvas = document.createElement('canvas');
    canvas.width = Representative.PALETTE_SIZE;
    canvas.height = Representative.PALETTE_SIZE;
    const ctx = canvas.getContext('2d');

    const w = canvas.width;
    const h = canvas.height;

    const hexToRgb = (hex) => {
      const s = String(hex).replace('#', '').trim();
      const full = s.length === 3 ? s.split('').map((c) => c + c).join('') : s;
      const n = parseInt(full, 16);
      return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
    };

    const shadeRgb = (rgb, shade) => {
      const s = Math.max(0, Math.min(1.25, shade));
      if (s <= 1) {
        return {
          r: Math.round(rgb.r * s),
          g: Math.round(rgb.g * s),
          b: Math.round(rgb.b * s)
        };
      }
      const t = Math.max(0, Math.min(1, s - 1));
      return {
        r: Math.round(rgb.r + (255 - rgb.r) * t),
        g: Math.round(rgb.g + (255 - rgb.g) * t),
        b: Math.round(rgb.b + (255 - rgb.b) * t)
      };
    };

    const base = {
      skin: hexToRgb(appearance.skin.color),
      hair: hexToRgb(appearance.hair.color),
      outfit: hexToRgb(appearance.outfit.color),
      accent: hexToRgb(appearance.accent.color),
      shoes: hexToRgb(appearance.shoes.color)
    };

    // Columns observed in the GLBs' UVs (px centers): 24, 56, 152, 184, 248
    // Role mapping by mesh analysis:
    // - head:    248 -> skin, 184 -> hair
    // - body:    24/56 -> suit/shirt, 152 -> shoes, 248 -> skin (hands)
    // Boundaries between those centers: 40, 104, 168, 216
    const b0 = 40;
    const b1 = 104;
    const b2 = 168;
    const b3 = 216;

    const imgData = ctx.createImageData(w, h);
    const data = imgData.data;

    for (let y = 0; y < h; y++) {
      // Prefer lighter shades toward the top (works with typical palette UV usage)
      const v = 1 - y / (h - 1);
      const shade = 0.72 + v * 0.38; // ~0.72..1.10

      const accent = shadeRgb(base.accent, shade);
      const outfit = shadeRgb(base.outfit, shade);
      const skin = shadeRgb(base.skin, shade);
      const hair = shadeRgb(base.hair, shade);
      const shoes = shadeRgb(base.shoes, shade);

      // Eyes + mouth should always be black. Those facial features live in the first columns on the head mesh.
      const feature = { r: 0, g: 0, b: 0 };

      // Male/female GLBs swap suit/shirt columns. Keep both in suits, with a consistent shirt.
      const col0 = mode === 'head' ? feature : (layout === 'female' ? accent : outfit);
      const col1 = mode === 'head' ? feature : (layout === 'female' ? outfit : accent);

      for (let x = 0; x < w; x++) {
        // 0..b0: suit, b0..b1: shirt, b1..b2: shoes, b2..b3: hair, b3..end: skin
        let c = outfit;
        if (x < b0) c = col0;
        else if (x < b1) c = col1;
        else if (x < b2) c = shoes;
        else if (x < b3) c = hair;
        else c = skin;

        const idx = (y * w + x) * 4;
        data[idx + 0] = c.r;
        data[idx + 1] = c.g;
        data[idx + 2] = c.b;
        data[idx + 3] = 255;
      }
    }

    ctx.putImageData(imgData, 0, 0);

    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.flipY = false;
    texture.magFilter = THREE.NearestFilter;
    texture.minFilter = THREE.NearestFilter;
    texture.generateMipmaps = false;
    texture.needsUpdate = true;
    Representative.paletteCache.set(key, texture);
    return texture;
  }

  _getShortName() {
    const parts = this.name.split(' ');
    return parts.length > 1 ? parts[parts.length - 1] : this.name;
  }

  setPosition(x, y, z) {
    this.basePosition.set(x, y, z);
    this.targetPosition.set(x, y, z);
    this.group.position.set(x, y, z);
  }

  moveTo(x, y, z) {
    this.targetPosition.set(x, y, z);
  }

  returnToBase() {
    this.targetPosition.copy(this.basePosition);
  }

  isSimSpeaking() {
    return Boolean(this._speakingReasons?.sim);
  }

  setSpeaking(speaking, reason = 'sim') {
    if (!this._speakingReasons) this._speakingReasons = { sim: false, ambient: false };
    if (!(reason in this._speakingReasons)) this._speakingReasons[reason] = false;

    this._speakingReasons[reason] = Boolean(speaking);
    this.isSpeaking = Object.values(this._speakingReasons).some(Boolean);

    const active = this.isSpeaking;
    if (this.speakingLight) {
        this.speakingLight.intensity = active ? 1 : 0;
    }
    if (this.speakingGlow) {
        this.speakingGlow.material.opacity = active ? 0.3 : 0;
    }
  }

  say(text, durationMs = 2200) {
    if (!this.speechSprite || !this._speechCtx || !this._speechCanvas) return;

    if (this._speechHideTimer) {
      clearTimeout(this._speechHideTimer);
      this._speechHideTimer = null;
    }

    // Clear
    this._speechCtx.clearRect(0, 0, this._speechCanvas.width, this._speechCanvas.height);

    // Bubble
    this._speechCtx.fillStyle = 'rgba(255, 255, 255, 0.92)';
    this._speechCtx.strokeStyle = '#d1d5db';
    this._speechCtx.lineWidth = 3;
    this._speechCtx.roundRect(10, 10, 236, 90, 16);
    this._speechCtx.fill();
    this._speechCtx.stroke();

    // Text
    this._speechCtx.fillStyle = '#111827';
    this._speechCtx.font = 'bold 20px monospace';
    this._speechCtx.textAlign = 'center';
    this._speechCtx.textBaseline = 'middle';

    const maxChars = 22;
    const words = String(text).split(/\s+/).filter(Boolean);
    const lines = [];
    let line = '';
    for (const word of words) {
      const next = line ? `${line} ${word}` : word;
      if (next.length <= maxChars) {
        line = next;
      } else {
        if (line) lines.push(line);
        line = word;
      }
      if (lines.length >= 2) break;
    }
    if (line && lines.length < 2) lines.push(line);
    const y0 = lines.length === 1 ? 55 : 45;
    lines.slice(0, 2).forEach((l, idx) => this._speechCtx.fillText(l, 128, y0 + idx * 26));

    this.speechSprite.material.map.needsUpdate = true;
    this.speechSprite.material.opacity = 1;

    this._speechHideTimer = setTimeout(() => {
      if (this.speechSprite) this.speechSprite.material.opacity = 0;
      this._speechHideTimer = null;
    }, durationMs);
  }

  faceTowards(x, z) {
    const dx = x - this.group.position.x;
    const dz = z - this.group.position.z;
    const len = Math.hypot(dx, dz);
    if (len < 1e-5) return;
    const dirX = dx / len;
    const dirZ = dz / len;
    // Our models are rotated to face -Z inside the group, so add PI.
    this.targetRotationY = Math.atan2(dirX, dirZ) + Math.PI;
  }

  showVote(vote) {
    const isYes = vote === 'YES';
    const color = isYes ? '#22C55E' : '#EF4444';
    const symbol = isYes ? '\u2713' : '\u2717';

    while (this.voteGroup.children.length > 0) {
      this.voteGroup.remove(this.voteGroup.children[0]);
    }

    const canvas = document.createElement('canvas');
    canvas.width = 64;
    canvas.height = 64;
    const ctx = canvas.getContext('2d');

    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(32, 32, 28, 0, Math.PI * 2);
    ctx.fill();

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

    setTimeout(() => {
      this.hideVote();
    }, 3000);
  }

  hideVote() {
    this.voteGroup.visible = false;
  }

  showName() {
    if (this.nameSprite) {
      this.nameSprite.material.opacity = 1;
    }
  }

  hideName() {
    if (this.nameSprite) {
      this.nameSprite.material.opacity = 0;
    }
  }

  update(delta) {
    this.time += delta;

    this.bobPhase += this.bobSpeed * delta;
    const bobOffset = Math.sin(this.bobPhase) * this.bobAmount;

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

    if (this.isSpeaking && this.speakingGlow) {
      const pulse = 0.3 + Math.sin(this.time * 3) * 0.2;
      this.speakingGlow.material.opacity = pulse;
      if (this.speakingLight) {
          this.speakingLight.intensity = 0.5 + Math.sin(this.time * 3) * 0.3;
      }
    }

    if (this.voteGroup.visible) {
      this.voteGroup.position.y = 1.5 + Math.sin(this.time * 2) * 0.1;
    }

    // Smooth facing rotation
    let diff = this.targetRotationY - this.rotationY;
    diff = (diff + Math.PI) % (Math.PI * 2);
    if (diff < 0) diff += Math.PI * 2;
    diff -= Math.PI;
    const maxStep = this.turnSpeed * delta;
    const step = Math.abs(diff) <= maxStep ? diff : Math.sign(diff) * maxStep;
    this.rotationY += step;
    this.group.rotation.y = this.rotationY;
  }

  getIdeologyScore() {
    return (this.ideology?.econ || 0) + (this.ideology?.social || 0);
  }

  destroy() {
    // Basic cleanup
    if (this.model) {
        this.group.remove(this.model);
    }
    if (this.nameSprite) {
      this.nameSprite.material.map.dispose();
      this.nameSprite.material.dispose();
    }
    if (this.speechSprite) {
      this.speechSprite.material.map.dispose();
      this.speechSprite.material.dispose();
    }
    if (this.speakingGlow) {
        this.speakingGlow.geometry.dispose();
        this.speakingGlow.material.dispose();
    }
  }
}
