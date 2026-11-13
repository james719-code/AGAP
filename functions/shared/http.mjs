export class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export const now = () => new Date().toISOString();
export const json = (body, status = 200) => Response.json(body, { status });
export const noContent = () => new Response(null, { status: 204 });
export const marks = (values) => values.map(() => '?').join(', ');

export function requireRow(row, message = 'Record not found') {
  if (!row) throw new HttpError(404, message);
  return row;
}

export function id(value) {
  if (!/^\d+$/.test(String(value)) || !Number.isSafeInteger(Number(value)) || Number(value) < 1) {
    throw new HttpError(400, 'Invalid record ID');
  }
  return Number(value);
}

export function pageNumber(value, fallback, min, max) {
  if (value == null) return fallback;
  const number = Number(value);
  if (!/^\d+$/.test(value) || !Number.isSafeInteger(number) || number < min || number > max) {
    throw new HttpError(400, 'Invalid pagination');
  }
  return number;
}

export function oneOf(value, allowed, field) {
  if (!allowed.includes(value)) throw new HttpError(400, `Invalid ${field}`);
  return value;
}

export function string(value, field, max = 255, optional = false) {
  if (typeof value !== 'string' || value.length > max || (!optional && !value.trim())) {
    throw new HttpError(400, `Invalid ${field}`);
  }
  return value.trim();
}

export function number(value, field, min, max, integer = false) {
  if (!Number.isFinite(value) || value < min || value > max || (integer && !Number.isInteger(value))) {
    throw new HttpError(400, `Invalid ${field}`);
  }
  return value;
}

export function boolean(value, field) {
  if (typeof value !== 'boolean') throw new HttpError(400, `Invalid ${field}`);
  return Number(value);
}

export async function readBody(request) {
  if (!request.headers.get('content-type')?.toLowerCase().startsWith('application/json')) {
    throw new HttpError(415, 'Expected a JSON body');
  }
  if (!request.body) throw new HttpError(400, 'Expected a JSON body');
  const reader = request.body.getReader();
  const decoder = new TextDecoder();
  let bytes = 0;
  let text = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    bytes += value.length;
    if (bytes > 65_536) {
      await reader.cancel();
      throw new HttpError(413, 'Request body is too large');
    }
    text += decoder.decode(value, { stream: true });
  }
  try {
    const value = JSON.parse(text + decoder.decode());
    if (value && typeof value === 'object' && !Array.isArray(value)) return value;
  } catch {
    // The caller receives the same error for malformed JSON and non-objects.
  }
  throw new HttpError(400, 'Expected a JSON object');
}

export const query = (db, sql, args = []) => db.execute({ sql, args });
export const rows = async (db, sql, args) => (await query(db, sql, args)).rows;
export const first = async (db, sql, args) => (await rows(db, sql, args))[0];

export function withBooleans(row, ...fields) {
  const result = { ...row };
  for (const field of fields) result[field] = Boolean(row[field]);
  return result;
}
