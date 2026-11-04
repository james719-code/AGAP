import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runInNewContext } from 'node:vm';
import test from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/components/Beams/capabilities.ts', import.meta.url), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2023 },
});

function checkCapabilities(options = {}) {
  const { width, memory, cores, reducedMotion, webgl, throws } = {
    width: 1280, memory: 8, cores: 8, reducedMotion: false, webgl: true, throws: false, ...options,
  };
  let probes = 0;
  let released = 0;
  const context = {
    exports: {},
    navigator: { deviceMemory: memory, hardwareConcurrency: cores },
    window: { innerWidth: width, matchMedia: () => ({ matches: reducedMotion }) },
    document: {
      createElement() {
        probes++;
        if (throws) throw new Error('Graphics unavailable');
        return {
          getContext: () => webgl ? { getExtension: () => ({ loseContext: () => released++ }) } : null,
        };
      },
    },
  };
  runInNewContext(outputText, context);
  return { supported: context.exports.supports3DBeams(), probes, released };
}

test('mobile, constrained devices, and reduced motion skip WebGL initialization', () => {
  for (const options of [{ width: 767 }, { memory: 4 }, { cores: 4 }, { reducedMotion: true }]) {
    assert.deepEqual(checkCapabilities(options), { supported: false, probes: 0, released: 0 });
  }
});

test('capable devices release the temporary graphics context', () => {
  assert.deepEqual(checkCapabilities(), { supported: true, probes: 1, released: 1 });
  assert.equal(checkCapabilities({ memory: undefined, cores: undefined }).supported, true);
});

test('unavailable or blocked graphics use the fallback', () => {
  assert.equal(checkCapabilities({ webgl: false }).supported, false);
  assert.equal(checkCapabilities({ throws: true }).supported, false);
});
