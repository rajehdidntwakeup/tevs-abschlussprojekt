const NODE_URLS = [
  'http://localhost:8443',
  'http://localhost:8444',
  'http://localhost:8445',
];

const TIMEOUT_MS = 3000;

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
  throw lastError || new Error('All nodes unreachable');
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
