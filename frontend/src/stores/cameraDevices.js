import { ref } from 'vue'

const STORAGE_KEY = 'wh_camera_devices'

const defaultDevices = [
  {
    id: 'ezviz-live-01',
    name: '海康测试摄像头',
    brand: '海康',
    serialNumber: '',
    verificationCode: '',
    longitude: null,
    latitude: null,
    status: 'ONLINE',
  },
]

export const cameraDevices = ref([])

function normalize(device) {
  return {
    id: device.id || crypto.randomUUID(),
    name: String(device.name || '').trim(),
    brand: '海康',
    serialNumber: String(device.serialNumber || '').trim(),
    verificationCode: String(device.verificationCode || '').trim(),
    longitude: Number.isFinite(Number(device.longitude)) ? Number(device.longitude) : null,
    latitude: Number.isFinite(Number(device.latitude)) ? Number(device.latitude) : null,
    status: device.status === 'OFFLINE' ? 'OFFLINE' : 'ONLINE',
  }
}

export function loadCameraDevices() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
    cameraDevices.value = Array.isArray(saved) ? saved.map(normalize) : defaultDevices.map(normalize)
  } catch {
    cameraDevices.value = defaultDevices.map(normalize)
  }
  return cameraDevices.value
}

export function saveCameraDevices(devices) {
  cameraDevices.value = devices.map(normalize)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(cameraDevices.value))
}

export function addCameraDevice(device) {
  saveCameraDevices([...cameraDevices.value, normalize(device)])
}

export function updateCameraDevice(id, device) {
  saveCameraDevices(cameraDevices.value.map((item) => (item.id === id ? normalize({ ...item, ...device, id }) : item)))
}

export function removeCameraDevice(id) {
  saveCameraDevices(cameraDevices.value.filter((item) => item.id !== id))
}

loadCameraDevices()

window.addEventListener('storage', (event) => {
  if (event.key === STORAGE_KEY) loadCameraDevices()
})
