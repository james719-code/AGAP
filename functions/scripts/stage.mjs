import { copyFileSync, mkdirSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const source = new URL('../shared/', import.meta.url);
for (const directory of ['../firebase/runtime/shared/', '../supabase/supabase/functions/_shared/']) {
  const target = new URL(directory, import.meta.url);
  mkdirSync(target, { recursive: true });
  for (const file of readdirSync(source).filter((name) => name.endsWith('.mjs'))) {
    copyFileSync(new URL(file, source), new URL(file, target));
  }
  console.log(`Staged shared API in ${fileURLToPath(target)}`);
}
