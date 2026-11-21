import http from 'k6/http';
import { check, sleep } from 'k6';
import { FLIGHT_URL, createFlight } from './lib/seed.js';

export const options = {
  scenarios: {
    price_read_ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 50 },
        { duration: '60s', target: 50 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  return { flightId: createFlight(`R${Date.now() % 100000}`, 100000, 10000) };
}

export default function (data) {
  const ticketClass = Math.random() < 0.7 ? 'ECONOMY' : 'BUSINESS';
  const res = http.get(`${FLIGHT_URL}/api/v1/flights/${data.flightId}/price?ticketClass=${ticketClass}`, {
    tags: { name: 'GET /flights/{id}/price' },
  });
  check(res, {
    'status is 200': (r) => r.status === 200,
    'price is a positive number': (r) => r.status === 200 && r.json('price') > 0,
  });
  sleep(0.1);
}
