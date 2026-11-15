import http from 'k6/http';
import { check, sleep } from 'k6';

// To integrate with TV2 after API path and auth have been finalized:
// PowerShell:
// $env:BASE_URL='http://localhost:8080'; $env:API_PATH='/api/v1/projects';
// $env:ACCESS_TOKEN='<SHORT-LIVED TEST JWT>'; k6 run k6/api-load.js
// Only run on a test system with permission. Never commit a real token.
export const options = {
  stages: [
    { duration: '15s', target: 5 },  // warmup
    { duration: '2m', target: 5 },   // measurement
    { duration: '15s', target: 0 }, // cooldown
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'], // suggested goal, NOT a verified course requirement
    checks: ['rate>0.99'],
  },
};

const baseUrl = __ENV.BASE_URL;
const path = __ENV.API_PATH;
const token = __ENV.ACCESS_TOKEN;
if (!baseUrl || !path || !token) throw new Error('Set BASE_URL, API_PATH and ACCESS_TOKEN');

export default function () {
  const response = http.get(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { test_case: 'read_projects' },
    timeout: '15s',
  });
  check(response, { 'HTTP 200': r => r.status === 200 });
  sleep(1);
}
