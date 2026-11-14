import { cpSync, existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const projectRoot = resolve(scriptDir, '..');
const distDir = resolve(projectRoot, 'dist');
const assetDir = resolve(projectRoot, '..', 'src', 'main', 'assets', 'web-chat');
if (!existsSync(distDir)) {
  console.error('Missing app/web-chat/dist. Run `npm --prefix app/web-chat run build` first.');
  process.exit(1);
}

rmSync(assetDir, { recursive: true, force: true });
mkdirSync(assetDir, { recursive: true });

for (const entry of readdirSync(distDir)) {
  cpSync(resolve(distDir, entry), resolve(assetDir, entry), { recursive: true, force: true });
}
