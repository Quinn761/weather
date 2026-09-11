export function listRoboflowPlots(result) {
  const output = result?.outputs?.[0] || {}
  const geographic = collectGeographicParcels(output)
  if (geographic.length) {
    return geographic.map((item) => ({
      coordinates: item.coordinates,
      className: item.className,
      confidence: item.confidence,
    }))
  }
  return listPixelPlots(output)
}

function listPixelPlots(output) {
  const pack = output?.predictions
  const predictions = Array.isArray(pack?.predictions) ? pack.predictions : []
  const width = Number(pack?.image?.width) || 256
  const height = Number(pack?.image?.height) || 256
  return predictions
    .map((item) => {
      const box = predictionBox(item)
      const mask = decodeMask(item?.rle_mask, width, height)
      const ring = maskToRing(mask, width, height) || (box ? boxToRing(box) : null)
      if (!ring || ring.length < 3) return null
      return {
        ring: simplifyRing(ring),
        imageWidth: width,
        imageHeight: height,
        className: item.class || 'agricultural field',
        confidence: Number(item.confidence) || 0,
      }
    })
    .filter(Boolean)
}

function collectGeographicParcels(output) {
  const fromList = Array.isArray(output?.parcel_geographic_polygons)
    ? output.parcel_geographic_polygons.map((item) => ({
        className: item?.class_name || 'agricultural field',
        confidence: Number(item?.confidence) || 0,
        coordinates: normalizeLonLatRing(item?.coordinates),
      }))
    : []
  if (fromList.some((item) => item.coordinates.length >= 3)) {
    return fromList.filter((item) => item.coordinates.length >= 3)
  }
  const features = output?.parcel_geojson?.features
  if (!Array.isArray(features)) return []
  return features
    .map((feature) => ({
      className: feature?.properties?.class_name || 'agricultural field',
      confidence: Number(feature?.properties?.confidence) || 0,
      coordinates: normalizeLonLatRing(feature?.geometry?.coordinates?.[0]),
    }))
    .filter((item) => item.coordinates.length >= 3)
}

function normalizeLonLatRing(points) {
  if (!Array.isArray(points)) return []
  const ring = []
  for (const point of points) {
    const lon = Number(point?.[0])
    const lat = Number(point?.[1])
    if (!Number.isFinite(lon) || !Number.isFinite(lat)) continue
    const prev = ring[ring.length - 1]
    if (prev && prev[0] === lon && prev[1] === lat) continue
    ring.push([lon, lat])
  }
  if (ring.length >= 4) {
    const first = ring[0]
    const last = ring[ring.length - 1]
    if (first[0] === last[0] && first[1] === last[1]) ring.pop()
  }
  return ring
}


function predictionBox(item) {
  const width = Number(item?.width)
  const height = Number(item?.height)
  const x = Number(item?.x)
  const y = Number(item?.y)
  if (![width, height, x, y].every(Number.isFinite) || width < 2 || height < 2) return null
  return { x, y, width, height }
}

function boxToRing(box) {
  const left = box.x - box.width / 2
  const top = box.y - box.height / 2
  const right = left + box.width
  const bottom = top + box.height
  return [
    [left, top],
    [right, top],
    [right, bottom],
    [left, bottom],
  ]
}

function decodeMask(rle, width, height) {
  const counts = rle?.counts
  const size = rle?.size
  if (typeof counts !== 'string' || !counts || !Array.isArray(size)) return null
  const h = Number(size[0]) || height
  const w = Number(size[1]) || width
  const runs = decodeCocoCounts(counts)
  const mask = new Uint8Array(h * w)
  let index = 0
  let value = 0
  for (const run of runs) {
    const next = Math.min(mask.length, index + Math.max(0, run))
    if (value) mask.fill(1, index, next)
    index = next
    value ^= 1
    if (index >= mask.length) break
  }
  return { data: mask, width: w, height: h }
}

function decodeCocoCounts(text) {
  const counts = []
  let p = 0
  while (p < text.length) {
    let x = 0
    let k = 0
    let more = 1
    while (more && p < text.length) {
      const c = text.charCodeAt(p) - 48
      p += 1
      x |= (c & 31) << (5 * k)
      more = c & 32
      k += 1
      if (!more && (c & 16)) x |= -1 << (5 * k)
    }
    if (counts.length > 2) x += counts[counts.length - 2]
    counts.push(x)
  }
  return counts
}

function maskToRing(mask) {
  if (!mask) return null
  const { data, width, height } = mask
  const at = (x, y) => x >= 0 && y >= 0 && x < width && y < height && data[x * height + y] === 1
  let startX = -1
  let startY = -1
  for (let y = 0; y < height && startX < 0; y += 1) {
    for (let x = 0; x < width; x += 1) {
      if (at(x, y)) {
        startX = x
        startY = y
        break
      }
    }
  }
  if (startX < 0) return null
  const dx = [1, 1, 0, -1, -1, -1, 0, 1]
  const dy = [0, 1, 1, 1, 0, -1, -1, -1]
  const ring = []
  let x = startX
  let y = startY
  let dir = 0
  const limit = width * height
  for (let step = 0; step < limit; step += 1) {
    ring.push([x, y])
    let found = false
    for (let i = 0; i < 8; i += 1) {
      const nextDir = (dir + 6 + i) % 8
      const nx = x + dx[nextDir]
      const ny = y + dy[nextDir]
      if (!at(nx, ny)) continue
      x = nx
      y = ny
      dir = nextDir
      found = true
      break
    }
    if (!found) break
    if (x === startX && y === startY) break
  }
  return ring.length >= 3 ? ring : null
}

function simplifyRing(ring, epsilon = 1.4) {
  if (ring.length <= 8) return ring
  const simplified = rdp(ring, epsilon)
  return simplified.length >= 3 ? simplified : ring
}

function rdp(points, epsilon) {
  if (points.length < 3) return points
  let maxDist = 0
  let index = 0
  const first = points[0]
  const last = points[points.length - 1]
  for (let i = 1; i < points.length - 1; i += 1) {
    const dist = perpendicularDistance(points[i], first, last)
    if (dist > maxDist) {
      index = i
      maxDist = dist
    }
  }
  if (maxDist <= epsilon) return [first, last]
  const left = rdp(points.slice(0, index + 1), epsilon)
  const right = rdp(points.slice(index), epsilon)
  return left.slice(0, -1).concat(right)
}

function perpendicularDistance(point, start, end) {
  const dx = end[0] - start[0]
  const dy = end[1] - start[1]
  if (dx === 0 && dy === 0) return Math.hypot(point[0] - start[0], point[1] - start[1])
  const t = ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / (dx * dx + dy * dy)
  const projX = start[0] + t * dx
  const projY = start[1] + t * dy
  return Math.hypot(point[0] - projX, point[1] - projY)
}
