import { execSync } from 'child_process';
import { readFileSync, existsSync } from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PROJECT_REF = 'mpsqdaptkkasjoepamwl';

// Retrieve token
function getToken() {
  const tokenScript = path.join(__dirname, 'get_token.ps1');
  const token = execSync(`powershell -NoProfile -ExecutionPolicy Bypass -File "${tokenScript}"`, {
    encoding: 'utf8'
  }).trim();
  if (!token) {
    throw new Error('Supabase token could not be retrieved from get_token.ps1');
  }
  return token;
}

const token = getToken();

export async function executeQuery(sql) {
  const url = `https://api.supabase.com/v1/projects/${PROJECT_REF}/database/query`;
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ query: sql })
  });

  const text = await res.text();
  if (!res.ok) {
    throw new Error(`Database query failed (${res.status} ${res.statusText}):\n${text}`);
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

// CLI handler
const args = process.argv.slice(2);
if (args.length > 0) {
  const arg = args[0];
  let sql = '';
  if (arg === '-f' || arg === '--file') {
    const filePath = args[1];
    if (!filePath || !existsSync(filePath)) {
      console.error(`File not found: ${filePath}`);
      process.exit(1);
    }
    sql = readFileSync(filePath, 'utf8');
    console.log(`Executing SQL file: ${filePath} (${sql.length} chars)...`);
  } else {
    sql = arg;
    console.log(`Executing SQL query: ${sql}`);
  }

  const startTime = Date.now();
  try {
    const result = await executeQuery(sql);
    console.log(`Success in ${Date.now() - startTime}ms:`);
    console.log(JSON.stringify(result, null, 2));
  } catch (err) {
    console.error(`Execution failed in ${Date.now() - startTime}ms:`, err.message);
    process.exit(1);
  }
}
