const NODE_URLS = [
  'https://localhost:8443',
  'https://localhost:8444',
  'https://localhost:8445',
];

const TIMEOUT_MS = 3000;

export class AllNodesUnreachable extends Error {
  constructor(cause) {
    super('All nodes unreachable');
    this.cause = cause;
    this.name = 'AllNodesUnreachable';
  }
}

async function fetchWithFailover(path, options = {}) {
  let lastError;
  for (const base of NODE_URLS) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const res = await fetch(`${base}${path}`, {
        ...options,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          ...(options.headers || {}),
        },
      });
      clearTimeout(timer);
      return res;
    } catch (err) {
      clearTimeout(timer);
      lastError = err;
    }
  }
  throw new AllNodesUnreachable(lastError);
}

export async function getAllStatuses() {
  const res = await fetchWithFailover('/api/status');
  if (!res.ok) throw new Error('Failed to fetch statuses');
  return res.json();
}

export async function getStatus(username) {
  const res = await fetchWithFailover(`/api/status/${encodeURIComponent(username)}`);
  if (!res.ok) throw new Error('Status not found');
  return res.json();
}

export async function saveStatus(payload) {
  const res = await fetchWithFailover('/api/status', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error('Failed to save status');
  return res.json();
}

export async function deleteStatus(username) {
  const res = await fetchWithFailover(`/api/status/${encodeURIComponent(username)}`, {
    method: 'DELETE',
  });
  if (!res.ok) throw new Error('Failed to delete status');
}
