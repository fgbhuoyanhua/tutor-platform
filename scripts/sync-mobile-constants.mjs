// 移动端常量生成器
//
// 权威源：frontend-common/src/constants/index.ts
// 产物：mobile-app/constants/index.uts
//
// 用法：
//   node scripts/sync-mobile-constants.mjs          # 重新生成
//   node scripts/sync-mobile-constants.mjs --check  # 只校验是否已同步（不一致则退出码 1）
//
// 原理：用 TypeScript 编译器把单一源转成 CommonJS 求值，直接拿到导出的常量对象，
// 再做格式转换。不解析文本、不猜结构，源里加常量本脚本自动跟上。

import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = path.join(ROOT, 'frontend-common', 'src', 'constants', 'index.ts');
const OUT = path.join(ROOT, 'mobile-app', 'constants', 'index.uts');

const requireFromWeb = createRequire(path.join(ROOT, 'web-app', 'package.json'));
const ts = requireFromWeb('typescript');

function loadConstants() {
  const src = fs.readFileSync(SRC, 'utf8');
  const js = ts.transpileModule(src, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
    fileName: SRC,
  }).outputText;
  const mod = { exports: {} };
  new Function('exports', 'require', 'module', js)(mod.exports, requireFromWeb, mod);
  return mod.exports;
}

const C = loadConstants();

function assert(cond, msg) {
  if (!cond) {
    console.error('[失败] ' + msg);
    process.exit(1);
  }
}

assert(Array.isArray(C.TIME_SLOTS) && C.TIME_SLOTS.length > 0, '源里取不到 TIME_SLOTS');
assert(C.ORDER_STATUS_LABELS && Object.keys(C.ORDER_STATUS_LABELS).length >= 6, '源里取不到 ORDER_STATUS_LABELS');
assert(C.ROLE_LABELS && Object.keys(C.ROLE_LABELS).length === 3, '源里取不到 ROLE_LABELS');
assert(C.TUTOR_STATUS_LABELS, '源里取不到 TUTOR_STATUS_LABELS');
assert(C.MESSAGE_TYPE_LABELS, '源里取不到 MESSAGE_TYPE_LABELS');

const q = (s) => "'" + String(s).replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'";

function arrayConst(name, arr, type) {
  const items = arr.map((v) => '  ' + q(v)).join(',\n');
  return `export const ${name}: ${type}[] = [\n${items}\n]\n`;
}

function mapFn(fnName, param, map, fallback) {
  const keys = Object.keys(map).sort((a, b) => Number(a) - Number(b));
  const lines = keys.map((k) => `  if (${param} == ${k}) return ${q(map[k])}`).join('\n');
  return `export function ${fnName}(${param}: number): string {\n${lines}\n  return ${q(fallback)}\n}\n`;
}

const parts = [];
parts.push('// 本文件由 scripts/sync-mobile-constants.mjs 自动生成，请勿手工修改。');
parts.push('// 权威源：frontend-common/src/constants/index.ts');
parts.push('// 改常量请改权威源，然后执行 node scripts/sync-mobile-constants.mjs');
parts.push('');
parts.push(arrayConst('TIME_SLOTS', C.TIME_SLOTS, 'string'));
parts.push('// 金额格式化：与 Web 端 frontend-common/src/utils/format.ts 的 formatPrice 保持一致');
parts.push("export function formatPrice(p: number): string {\n  return '¥' + p.toFixed(2)\n}\n");
parts.push('// 订单状态文案：与后端 OrderDomain.statusText 一致');
parts.push(mapFn('orderStatusLabel', 'status', C.ORDER_STATUS_LABELS, '未知'));
parts.push('// 角色文案');
parts.push(mapFn('ROLE_LABELS_FN', 'role', C.ROLE_LABELS, '未知'));
parts.push('// 家教信息状态文案');
parts.push(mapFn('TUTOR_STATUS_FN', 'status', C.TUTOR_STATUS_LABELS, '未知'));
parts.push('// 消息类型文案');
parts.push(mapFn('MESSAGE_TYPE_FN', 'type', C.MESSAGE_TYPE_LABELS, '未知'));

const text = parts.join('\n');

if (process.argv.includes('--check')) {
  const cur = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8').replace(/\r\n/g, '\n') : '';
  const norm = (s) => s.replace(/\r\n/g, '\n').replace(/\s+/g, ' ').trim();
  if (norm(cur) === norm(text)) {
    console.log('[通过] mobile-app/constants/index.uts 与权威源一致');
    process.exit(0);
  }
  console.error('[不一致] mobile-app/constants/index.uts 与权威源不同步，请执行 node scripts/sync-mobile-constants.mjs');
  process.exit(1);
}

fs.writeFileSync(OUT, text, { encoding: 'utf8' });
console.log('[已生成] ' + OUT);
console.log('  时段 ' + C.TIME_SLOTS.length + ' 个 | 订单状态 ' + Object.keys(C.ORDER_STATUS_LABELS).length + ' 种 | 角色 ' + Object.keys(C.ROLE_LABELS).length + ' 种');
