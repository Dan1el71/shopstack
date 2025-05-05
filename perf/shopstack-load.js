import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    catalogue: { executor: 'constant-vus', vus: 10, duration: '30s' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost';

export default function () {
  const products = http.get(`${BASE}/api/product?page=0&size=10`);
  check(products, { 'products 200': (r) => r.status === 200 });

  const categories = http.get(`${BASE}/api/categories`);
  check(categories, { 'categories 200': (r) => r.status === 200 });

  sleep(0.5);
}
