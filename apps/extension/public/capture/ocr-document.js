import { containsSensitiveText, MAX_TEXT_CHARS } from './policy.js';
let busy = false;
chrome.runtime.onMessage.addListener((message, sender, respond) => {
  if (sender.id !== chrome.runtime.id || sender.tab || (sender.url && sender.url !== chrome.runtime.getURL('service-worker.js')) || message?.type !== 'LOCAL_OCR' || message.target !== 'offscreen') return;
  if (busy) { respond({ success: false, error: { code: 'OCR_BUSY', retryable: true } }); return; }
  busy = true;
  (async () => {
    let worker;
    let bitmap;
    let canvas;
    try {
      if (typeof message.image !== 'string' || !message.image.startsWith('data:image/png;base64,') || message.image.length > 48_000_000) throw new Error('INVALID_CAPTURE');
      const bytes = Uint8Array.from(atob(message.image.slice(22)), character => character.charCodeAt(0));
      bitmap = await createImageBitmap(new Blob([bytes], { type: 'image/png' }));
      if (bitmap.width * bitmap.height > 32_000_000) throw new Error('CAPTURE_TOO_LARGE');
      canvas = document.createElement('canvas');
      canvas.width = bitmap.width; canvas.height = bitmap.height;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(bitmap, 0, 0);
      const sx = bitmap.width / message.viewport.width, sy = bitmap.height / message.viewport.height;
      ctx.fillStyle = '#000';
      for (const mask of message.masks) ctx.fillRect(mask.x * sx, mask.y * sy, mask.width * sx, mask.height * sy);
      const sample = ctx.getImageData(0, 0, canvas.width, canvas.height).data;
      let bright = 0;
      for (let i = 0; i < sample.length; i += 4 * 64) if (sample[i] + sample[i + 1] + sample[i + 2] > 24) bright++;
      if (bright === 0) return { success: false, error: { code: 'BLACK_SCREEN', retryable: false } };
      worker = await globalThis.Tesseract.createWorker(['kor', 'eng'], 1, {
        workerPath: chrome.runtime.getURL('ocr/worker.min.js'), corePath: chrome.runtime.getURL('ocr/core'),
        langPath: chrome.runtime.getURL('ocr/langs'), workerBlobURL: false, cacheMethod: 'none',
      });
      const { data } = await worker.recognize(canvas);
      canvas.width = 0; canvas.height = 0;
      const text = data.text.trim();
      if (containsSensitiveText(text)) return { success: false, error: { code: 'PRIVACY_BLOCKED', retryable: false } };
      if (!text) return { success: false, error: { code: 'EMPTY_CAPTURE', retryable: false } };
      if (text.length > MAX_TEXT_CHARS) return { success: false, error: { code: 'INCOMPLETE_REPRESENTATION', retryable: false } };
      const confidence = data.confidence / 100;
      if (!Number.isFinite(confidence) || confidence < 0 || confidence > 1) throw new Error('INVALID_OCR_RESULT');
      return { success: true, data: { text, confidence } };
    } finally {
      bitmap?.close();
      if (canvas) { canvas.width = 0; canvas.height = 0; }
      if (worker) await worker.terminate();
      busy = false;
    }
  })().then(respond, () => respond({ success: false, error: { code: 'OCR_FAILED', retryable: true } }));
  return true;
});
