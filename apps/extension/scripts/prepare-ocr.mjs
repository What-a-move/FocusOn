import { createRequire } from 'node:module';
import { cp, mkdir, readdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const destination = path.join(root, 'public/ocr');
const tesseract = path.dirname(require.resolve('tesseract.js/package.json'));
const core = path.dirname(createRequire(path.join(tesseract, 'package.json')).resolve('tesseract.js-core/package.json'));
await rm(destination, { recursive: true, force: true });
await mkdir(path.join(destination, 'core'), { recursive: true });
await mkdir(path.join(destination, 'langs'), { recursive: true });
for (const name of ['tesseract.min.js', 'worker.min.js']) await cp(path.join(tesseract, 'dist', name), path.join(destination, name));
for (const name of await readdir(core)) {
  if (/^tesseract-core.*\.(?:wasm|js)$/.test(name)) await cp(path.join(core, name), path.join(destination, 'core', name));
}
for (const lang of ['eng', 'kor']) {
  const data = path.dirname(require.resolve(`@tesseract.js-data/${lang}/package.json`));
  await cp(path.join(data, '4.0.0_best_int', `${lang}.traineddata.gz`), path.join(destination, 'langs', `${lang}.traineddata.gz`));
  await cp(path.join(data, 'README.md'), path.join(destination, `README-${lang}.md`));
}
await cp(path.join(tesseract, 'LICENSE.md'), path.join(destination, 'LICENSE-tesseract.md'));
