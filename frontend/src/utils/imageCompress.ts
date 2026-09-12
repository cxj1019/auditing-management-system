/**
 * 图片压缩：拍照/扫描件上传前在浏览器端用 canvas 重采样 + JPEG 质量压缩
 * - 仅处理 jpg/jpeg/png/webp，PDF 等其他文件原样返回
 * - 原图长边不超过 maxEdge 且体积小于 skipBelowKB 时直接跳过（无损上传）
 * - 长边 2000px + 质量 0.75 下发票文字可清晰辨认，单张约 200~500KB
 */
export interface CompressOptions {
  /** 缩放后允许的最大长边（像素） */
  maxEdge?: number
  /** JPEG 质量 0~1 */
  quality?: number
  /** 小于该体积（KB）不压缩 */
  skipBelowKB?: number
}

const IMAGE_MIME = ['image/jpeg', 'image/png', 'image/webp']

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片读取失败'))
    }
    img.src = url
  })
}

/** 按选项压缩图片文件；不符合压缩条件的原样返回 */
export async function compressImage(file: File, options?: CompressOptions): Promise<File> {
  const maxEdge = options?.maxEdge ?? 2000
  const quality = options?.quality ?? 0.75
  const skipBelowKB = options?.skipBelowKB ?? 400

  if (!IMAGE_MIME.includes(file.type)) return file
  if (file.size <= skipBelowKB * 1024) return file

  let img: HTMLImageElement
  try {
    img = await loadImage(file)
  } catch {
    return file
  }

  const scale = Math.min(1, maxEdge / Math.max(img.naturalWidth, img.naturalHeight))
  if (scale === 1 && file.type === 'image/jpeg') {
    // 尺寸不超且本身就是 JPEG：交给质量压缩（PNG 转 JPEG 可白拿大部分收益）
    if (file.size <= 800 * 1024) return file
  }

  const canvas = document.createElement('canvas')
  canvas.width = Math.round(img.naturalWidth * scale)
  canvas.height = Math.round(img.naturalHeight * scale)
  const ctx = canvas.getContext('2d')
  if (!ctx) return file
  // PNG 透明底转 JPEG 时填白，避免票面变黑
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height)

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
  if (!blob) return file
  // 压缩结果反而更大（极少见的高效原图）就不压缩
  if (blob.size >= file.size) return file

  const baseName = file.name.replace(/\.[^.]+$/, '')
  return new File([blob], `${baseName}.jpg`, { type: 'image/jpeg', lastModified: Date.now() })
}
