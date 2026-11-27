const apiBase = import.meta.env.VITE_API_URL?.replace(/\/$/, '') || '';

fetch(`${apiBase}/api/mode`, { cache: 'no-store' })
  .then(async (response) => {
    if (!response.ok) throw new Error('The order server is unavailable.');
    const mode = await response.json();
    return mode.demo ? import('./demoMain.jsx') : import('./main.jsx');
  })
  .catch(() => {
    document.getElementById('root').textContent = 'The order server is unavailable. Please try again shortly.';
  });
