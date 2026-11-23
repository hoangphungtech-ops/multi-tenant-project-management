import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL;
const path = __ENV.API_PATH;
const token = __ENV.ACCESS_TOKEN;
const demo = __ENV.LOCAL_DEMO === 'true';

if (!baseUrl || !path || !path.startsWith('/') || path.startsWith('//') || path.includes('?')) {
  throw new Error('Set BASE_URL and API_PATH (path without query string)');
}
if (demo && (!/^http:\/\/127\.0\.0\.1:\d+$/.test(baseUrl) || !__ENV.DEMO_USER || !__ENV.DEMO_TENANT)) {
  throw new Error('LOCAL_DEMO requires a loopback HTTP URL, DEMO_USER and DEMO_TENANT');
}
if (!demo && (!/^https:\/\/[^/?#@]+$/.test(baseUrl) || !token)) {
  throw new Error('Set an HTTPS BASE_URL and short-lived ACCESS_TOKEN');
}

const headers = demo
  ? { 'X-Demo-User': __ENV.DEMO_USER, 'X-Demo-Tenant': __ENV.DEMO_TENANT }
  : { Authorization: `Bearer ${token}` };

export const options = {
  scenarios: {
    workspace_read: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
    checks: ['rate>0.99'],
  },
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
};

export default function () {
  const response = http.get(`${baseUrl}${path}`, {
    headers,
    redirects: 0,
    responseCallback: http.expectedStatuses(200),
    tags: { name: 'list_workspaces' },
    timeout: '15s',
  });
  check(response, { 'HTTP 200': (r) => r.status === 200 });
  sleep(1);
}
