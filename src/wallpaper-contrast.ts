interface Background {
  mode: number; color: string; left: number; top: number;
  width: number; height: number; clientX: number; clientY: number;
}

/** Average linear luminance: medium gray and brighter use black text. */
export function textColorForPixels(pixels: Uint8ClampedArray): 'black' | 'white' {
  if (!pixels.length) return 'black';
  const linear = (value: number) => {
    const s = value / 255;
    return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  let luminance = 0;
  for (let i = 0; i < pixels.length; i += 4) {
    luminance += .2126 * linear(pixels[i]) + .7152 * linear(pixels[i + 1]) + .0722 * linear(pixels[i + 2]);
  }
  return luminance / (pixels.length / 4) < .18 ? 'white' : 'black';
}

/** Sample only the wallpaper under the widget, using the same native layout. */
export function wallpaperTextColor(s: Background, image: HTMLImageElement | null, width: number, height: number): 'black' | 'white' {
  const canvas = document.createElement('canvas');
  canvas.width = 32; canvas.height = 20;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context || width <= 0 || height <= 0) return 'black';
  const x = s.clientX - s.left, y = s.clientY - s.top;
  context.scale(canvas.width / width, canvas.height / height);
  context.translate(-x, -y);
  context.fillStyle = s.color;
  context.fillRect(x, y, width, height);
  if (image) {
    const iw = image.naturalWidth, ih = image.naturalHeight;
    if (s.mode === 1) {
      const pattern = context.createPattern(image, 'repeat');
      if (pattern) { context.fillStyle = pattern; context.fillRect(x, y, width, height); }
    } else {
      const ratio = s.mode === 3 ? Math.min(s.width / iw, s.height / ih)
        : s.mode >= 4 ? Math.max(s.width / iw, s.height / ih) : 1;
      const w = s.mode === 2 ? s.width : iw * ratio;
      const h = s.mode === 2 ? s.height : ih * ratio;
      context.drawImage(image, (s.width - w) / 2, (s.height - h) / 2, w, h);
    }
  }
  return textColorForPixels(context.getImageData(0, 0, canvas.width, canvas.height).data);
}
