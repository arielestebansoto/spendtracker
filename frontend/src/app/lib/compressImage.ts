// OCR reads a receipt well at this size; a raw phone photo is several times larger.
const MAX_DIMENSION = 2500;
const JPEG_QUALITY = 0.85;

/**
 * Re-encodes an image as a JPEG whose longest side is at most MAX_DIMENSION px (never upscaled).
 * Applies the EXIF orientation and drops all EXIF data, GPS location included.
 * Throws if the browser cannot decode the image (for example HEIC outside Safari).
 */
export async function compressImage(file: File): Promise<File> {
  const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });

  try {
    const scale = Math.min(1, MAX_DIMENSION / Math.max(bitmap.width, bitmap.height));
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);

    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;

    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("Canvas 2D context is not available");
    }
    // JPEG has no transparency: paint transparent PNG areas white instead of black.
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);

    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, "image/jpeg", JPEG_QUALITY)
    );
    if (!blob) {
      throw new Error("Could not encode the image");
    }

    return new File([blob], jpegFileName(file.name), { type: "image/jpeg" });
  } finally {
    bitmap.close();
  }
}

function jpegFileName(name: string): string {
  const base = name.replace(/\.[^.]+$/, "") || "receipt";
  return `${base}.jpg`;
}
