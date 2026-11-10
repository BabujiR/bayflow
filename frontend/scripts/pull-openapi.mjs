// Downloads the OpenAPI documents of the three services into openapi/*.json.
// The services must be running (docker compose up -d), then run: pnpm api:update
import { mkdir, writeFile } from 'node:fs/promises';

const services = {
  auth: process.env.BAYFLOW_AUTH_URL ?? 'http://localhost:8083',
  appointment: process.env.BAYFLOW_APPOINTMENT_URL ?? 'http://localhost:8081',
  workshop: process.env.BAYFLOW_WORKSHOP_URL ?? 'http://localhost:8082',
};

await mkdir(new URL('../openapi/', import.meta.url), { recursive: true });

for (const [name, baseUrl] of Object.entries(services)) {
  const url = `${baseUrl}/v3/api-docs`;
  let response;
  try {
    response = await fetch(url);
  } catch (error) {
    console.error(`Could not reach ${name} at ${url}. Is it running? (docker compose up -d)`);
    throw error;
  }
  if (!response.ok) {
    throw new Error(`${url} returned ${response.status}`);
  }
  const spec = await response.json();
  await writeFile(new URL(`../openapi/${name}.json`, import.meta.url), JSON.stringify(spec, null, 2) + '\n');
  console.log(`Saved openapi/${name}.json`);
}
