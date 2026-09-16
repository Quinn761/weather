import { ref } from 'vue'
import {
  createCameraDevice,
  deleteCameraDevice,
  listCameraDevices,
  updateCameraDevice as updateCameraDeviceRequest,
} from '@/api/camera'

// Device data is shared through the protected backend API, never localStorage.
export const cameraDevices = ref([])

export async function loadCameraDevices() {
  cameraDevices.value = await listCameraDevices()
  return cameraDevices.value
}

export async function addCameraDevice(device) {
  const created = await createCameraDevice(device)
  cameraDevices.value = [created, ...cameraDevices.value]
  return created
}

export async function updateCameraDevice(id, device) {
  const updated = await updateCameraDeviceRequest(id, device)
  cameraDevices.value = cameraDevices.value.map((item) => (item.id === id ? updated : item))
  return updated
}

export async function removeCameraDevice(id) {
  await deleteCameraDevice(id)
  cameraDevices.value = cameraDevices.value.filter((item) => item.id !== id)
}
