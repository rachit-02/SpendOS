// Load test for DEVELOPMENT_PLAN.md Phase 16: 100+ concurrent users, API median < 200 ms,
// dashboard < 2 s. Run against a private stack with RATE_LIMIT_ENABLED=false:
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:3000 grafana/k6 run - < load/k6-core-api.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = (__ENV.BASE_URL || 'http://localhost:3000') + '/api/v1';
// One account per virtual user by default, so users do not share cached results.
const ACCOUNTS = Number(__ENV.DEMO_ACCOUNTS || 100);

export const options = {
  setupTimeout: '900s',
  scenarios: {
    users: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 100 },
        { duration: '60s', target: 100 },
        { duration: '15s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:api}': ['med<200', 'p(95)<1000'],
    'http_req_duration{name:dashboard}': ['p(95)<2000'],
    // Results are cached per user for 5 minutes (UserDataCache), so each user's first pass is the
    // uncached cost. Report cold and warm separately instead of letting cache hits hide it.
    'http_req_duration{kind:api,cache:cold}': ['med<1000', 'p(95)<2000'],
    'http_req_duration{kind:api,cache:warm}': ['med<200', 'p(95)<1000'],
    'http_req_duration{name:dashboard,cache:cold}': ['p(95)<2000'],
  },
};

// Each demo account holds about six months of synthetic data, like a real user.
export function setup() {
  const tokens = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const res = http.post(`${BASE}/auth/demo`, null, { tags: { kind: 'setup' } });
    check(res, { 'demo account created': (r) => r.status === 201 });
    tokens.push(res.json('data.accessToken'));
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const cache = __ITER === 0 ? 'cold' : 'warm';
  const params = (name) => ({ headers: { Authorization: `Bearer ${token}` }, tags: { kind: 'api', name, cache } });

  const dashboard = http.get(`${BASE}/dashboard`, params('dashboard'));
  check(dashboard, { 'dashboard 200': (r) => r.status === 200 });
  sleep(1);
  check(http.get(`${BASE}/transactions?page=1&pageSize=20`, params('transactions')), { 'transactions 200': (r) => r.status === 200 });
  sleep(1);
  check(http.get(`${BASE}/insights`, params('insights')), { 'insights 200': (r) => r.status === 200 });
  sleep(1);
  check(http.get(`${BASE}/budgets`, params('budgets')), { 'budgets 200': (r) => r.status === 200 });
  sleep(1);
}
