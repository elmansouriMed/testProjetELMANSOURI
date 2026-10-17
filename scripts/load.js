import http from 'k6/http';
import { check } from 'k6';
export const options = {
  scenarios: { reads: { executor: 'constant-arrival-rate', rate: 100, timeUnit: '1s', duration: '1m', preAllocatedVUs: 20, maxVUs: 100 } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500'] }
};
const base = __ENV.BASE_URL || 'http://localhost:8080';
export default function () {
  const response = http.get(`${base}/api/messages?limit=25`);
  check(response, { 'liste HTTP 200': r => r.status === 200 });
  if (response.status === 200) {
    const items = response.json('items');
    if (items.length) {
      const detail = http.get(`${base}/api/messages/${items[0].id}`);
      check(detail, { 'détail HTTP 200': r => r.status === 200 });
    }
  }
}
