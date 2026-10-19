const fs = require('node:fs/promises');
const path = require('node:path');
const net = require('node:net');
const { spawn } = require('node:child_process');

const root = __dirname;
const java = process.env.SUGARRUSH_JAVA || 'java';
const children = new Set();
let stopping = false;
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function sources(folder) {
  const result = [];
  for (const entry of await fs.readdir(folder, { withFileTypes: true })) {
    const file = path.join(folder, entry.name);
    if (entry.isDirectory()) result.push(...(await sources(file)));
    else if (entry.name.endsWith('.java')) result.push(file);
  }
  return result;
}

function spawnOwned(command, args, options = {}) {
  const child = spawn(command, args, {
    cwd: root,
    stdio: 'inherit',
    detached: process.platform !== 'win32',
    ...options,
  });
  children.add(child);
  child.once('close', () => children.delete(child));
  return child;
}

function finish(child, name) {
  return new Promise((resolve, reject) => {
    child.once('error', (error) => reject(new Error(`${name} could not start: ${error.message}`)));
    child.once('exit', (code) =>
      code === 0 ? resolve() : reject(new Error(`${name} stopped with exit code ${code}.`)),
    );
  });
}

function npmCommand(command, cwd, env = process.env) {
  return spawnOwned(`npm ${command}`, [], { shell: true, cwd, env });
}

async function freePort(start = 8080) {
  for (let port = start; port < start + 100; port++) {
    const available = await new Promise((resolve, reject) => {
      const probe = net.createServer();
      probe.once('error', (error) => (error.code === 'EADDRINUSE' ? resolve(false) : reject(error)));
      probe.listen(port, '127.0.0.1', () => probe.close(() => resolve(true)));
    });
    if (available) return port;
  }
  throw new Error('No available local backend port.');
}

async function waitForBackend(child, port) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null || child.signalCode !== null)
      throw new Error('Java stopped before the backend was ready.');
    try {
      const health = await fetch(`http://127.0.0.1:${port}/api/health`, {
        signal: AbortSignal.timeout(2000),
      });
      const state = await health.json();
      const login = await fetch(`http://127.0.0.1:${port}/api/firebase-session`, { signal: AbortSignal.timeout(2000) });
      if (health.ok && state.status === 'UP' && login.status === 405) return;
      throw new Error('The backend is missing the single-login endpoint.');
    } catch (error) {
      if (error.message.includes('missing the single-login')) throw error;
    }
    await delay(250);
  }
  throw new Error('Java did not become ready within 30 seconds.');
}

async function stopChild(child) {
  if (!child.pid || child.exitCode !== null || child.signalCode !== null) return;
  if (process.platform === 'win32') {
    await new Promise((resolve) => {
      const killer = spawn('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
      killer.once('error', resolve);
      killer.once('exit', resolve);
    });
  } else {
    try {
      process.kill(-child.pid, 'SIGTERM');
    } catch {}
  }
}

async function shutdown(code = 0) {
  if (stopping) return;
  stopping = true;
  await Promise.all([...children].map(stopChild));
  process.exit(code);
}

async function start() {
  const sourceFiles = await sources(path.join(root, 'backend', 'src'));
  const output = path.join(root, 'backend', 'out');
  await fs.mkdir(output, { recursive: true });
  console.log('Compiling the Java backend...');
  await finish(
    spawnOwned(java, [
      '-m',
      'jdk.compiler/com.sun.tools.javac.Main',
      '--release',
      '17',
      '--add-modules',
      'jdk.httpserver',
      '-d',
      output,
      ...sourceFiles,
    ]),
    'Java compilation',
  );

  const ui = path.join(root, 'ui');
  try {
    await fs.access(path.join(ui, 'node_modules', '.bin', process.platform === 'win32' ? 'vite.cmd' : 'vite'));
  } catch {
    console.log('Installing frontend dependencies...');
    await finish(npmCommand('install --include=dev', ui), 'Dependency installation');
  }

  const port = await freePort(8080);
  const envFile = await fs.readFile(path.join(ui, '.env.local'), 'utf8').catch(() => '');
  const databaseUrl = process.env.VITE_FIREBASE_DATABASE_URL || envFile.match(/^VITE_FIREBASE_DATABASE_URL=(.*)$/m)?.[1]?.trim()?.replace(/^['"]|['"]$/g, '');
  const backend = spawnOwned(java, [
    '--add-modules',
    'jdk.httpserver',
    '-cp',
    output,
    'sugarrush.presentation.http.ApiServer',
    process.env.SUGARRUSH_SEED || 'backend/config/demo.properties',
    String(port),
  ], { env: { ...process.env, SUGARRUSH_DATABASE_URL: databaseUrl || '' } });
  backend.once('error', (error) => {
    console.error(error.message);
    shutdown(1);
  });
  backend.once('exit', (code) => {
    if (!stopping) {
      console.error('Java stopped.');
      shutdown(code || 1);
    }
  });
  await waitForBackend(backend, port);
  console.log(`Java is ready on port ${port}. Starting the dashboard and opening your browser...`);
  const frontend = npmCommand('run dev -- --open', ui, {
    ...process.env,
    SUGARRUSH_API_URL: `http://127.0.0.1:${port}`,
  });
  frontend.once('error', (error) => {
    console.error(error.message);
    shutdown(1);
  });
  frontend.once('exit', (code) => {
    if (!stopping) shutdown(code || 0);
  });
  console.log('Keep this task running. Ctrl+C stops both servers.');
}

if (require.main === module) {
  process.on('SIGINT', () => shutdown(0));
  process.on('SIGTERM', () => shutdown(0));
  start().catch((error) => {
    console.error(error.message);
    shutdown(1);
  });
}
module.exports = { freePort, waitForBackend, sources };
