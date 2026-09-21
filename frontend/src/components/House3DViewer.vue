<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import type { HouseTypeGeometry } from '../api/types'
import { ROOM_COLOR, bbox } from '../utils/geometry'

/**
 * 参数化 3D 户型视图（spec 002 FR-16~17、docs/04 §3.3）。
 * 策略：有 GLB 模型优先加载；失败或无模型即用 rooms(x,y,w,h,ceiling) 生成盒体——
 * 因此"AI 生成 3D 初始化代码"（I4）替换的是同一套 buildScene 逻辑，二者可对照演示。
 * 卸载时 dispose 全部几何/材质，保证答辩现场长时间操作不泄漏。
 */
const props = defineProps<{ geo: HouseTypeGeometry; modelUrl?: string; mode?: 'orbit' | 'walk' }>()

const host = ref<HTMLElement | null>(null)
const status = ref<'idle' | 'loading' | 'ready' | 'fallback'>('idle')
const renderer = shallowRef<THREE.WebGLRenderer | null>(null)
const disposables: (THREE.BufferGeometry | THREE.Material | THREE.Texture)[] = []

let scene: THREE.Scene, camera: THREE.PerspectiveCamera, controls: OrbitControls, raf = 0

function track<T extends THREE.BufferGeometry | THREE.Material | THREE.Texture>(x: T): T {
  disposables.push(x)
  return x
}

function buildScene(geo: HouseTypeGeometry) {
  const g = new THREE.Group()
  const ceiling = geo.ceiling || 2.8
  const b = bbox(geo)
  const cx = b.minX + b.w / 2
  const cz = b.minY + b.h / 2

  const floor = new THREE.Mesh(
    track(new THREE.PlaneGeometry(b.w, b.h)),
    track(new THREE.MeshLambertMaterial({ color: 0xf2f3f5, side: THREE.DoubleSide })),
  )
  floor.rotation.x = -Math.PI / 2
  floor.position.set(cx, 0, cz)
  g.add(floor)

  geo.rooms.forEach((r) => {
    const box = new THREE.Mesh(
      track(new THREE.BoxGeometry(r.w, ceiling, r.h)),
      track(
        new THREE.MeshLambertMaterial({
          color: ROOM_COLOR[r.category] ?? '#999999', transparent: true, opacity: 0.14,
        }),
      ),
    )
    box.position.set(r.x + r.w / 2, ceiling / 2, r.y + r.h / 2)
    g.add(box)
    const edges = new THREE.LineSegments(
      track(new THREE.EdgesGeometry(box.geometry)),
      track(new THREE.LineBasicMaterial({ color: 0x22303f, transparent: true, opacity: 0.55 })),
    )
    edges.position.copy(box.position)
    g.add(edges)

    // 家具占位（厨卫洁具/床）只给最低限度的体块，避免把演示变成建模秀
    if (['KITCHEN', 'BATH'].includes(r.category)) {
      const counter = new THREE.Mesh(
        track(new THREE.BoxGeometry(Math.min(1.8, r.w * 0.6), 0.8, Math.min(0.6, r.h * 0.3))),
        track(new THREE.MeshLambertMaterial({ color: 0xbfd3e0 })),
      )
      counter.position.set(r.x + 0.5, 0.4, r.y + 0.4)
      g.add(counter)
    }
  })
  return g
}

function init() {
  const el = host.value
  if (!el || !props.geo) return
  const w = el.clientWidth
  const h = el.clientHeight || 420
  renderer.value = new THREE.WebGLRenderer({ antialias: true, alpha: true })
  renderer.value.setSize(w, h)
  renderer.value.setPixelRatio(Math.min(2, window.devicePixelRatio))
  el.appendChild(renderer.value.domElement)

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0xfafafa)
  scene.add(new THREE.AmbientLight(0xffffff, 0.85))
  const dir = new THREE.DirectionalLight(0xffffff, 0.6)
  dir.position.set(8, 14, 6)
  scene.add(dir)

  camera = new THREE.PerspectiveCamera(45, w / h, 0.1, 200)
  const b = bbox(props.geo)
  camera.position.set(b.minX + b.w / 2, b.h * 1.15, b.minY + b.h * 2.1)
  controls = new OrbitControls(camera, renderer.value.domElement)
  controls.enableDamping = true
  controls.target.set(b.minX + b.w / 2, 1, b.minY + b.h / 2)

  renderer.value.render(scene, camera)
  const loop = () => {
    controls.update()
    renderer.value?.render(scene, camera)
    raf = requestAnimationFrame(loop)
  }
  loop()
}

async function loadGeometry() {
  status.value = 'loading'
  try {
    if (props.modelUrl) {
      const gltf = await new GLTFLoader().loadAsync(props.modelUrl)
      scene.add(gltf.scene)
      status.value = 'ready'
      return
    }
    throw new Error('no-model')
  } catch {
    // FR-17：回落参数化；FR-21：任何失败都不白屏
    scene?.add(buildScene(props.geo))
    status.value = 'fallback'
  }
}

function dispose() {
  cancelAnimationFrame(raf)
  disposables.forEach((d) => d.dispose())
  disposables.length = 0
  controls?.dispose()
  renderer.value?.dispose()
  renderer.value?.domElement.remove()
  renderer.value = null
}

onMounted(async () => {
  init()
  await loadGeometry()
})
watch(() => props.geo, async () => {
  if (!scene) return
  scene.children.filter((o) => o.type === 'Group').forEach((o) => scene.remove(o))
  await loadGeometry()
})
onBeforeUnmount(dispose)
</script>

<template>
  <div class="viewer">
    <div ref="host" class="host" />
    <el-alert v-if="status === 'fallback'" class="tip" type="info" :closable="false" show-icon
      title="使用参数化生成的 3D 体块（无精模或模型加载失败）。可拖动旋转、滚轮缩放。" />
    <el-alert v-else-if="status === 'loading'" class="tip" type="info" :closable="false" title="3D 资源加载中…" />
  </div>
</template>

<style scoped>
.viewer { position: relative; }
.host { width: 100%; height: 420px; background: #fafafa; border-radius: 8px; }
.tip { position: absolute; left: 12px; bottom: 12px; max-width: 60%; }
</style>
