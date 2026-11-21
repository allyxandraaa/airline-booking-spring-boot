import http from 'k6/http';
import { check } from 'k6';
import { FLIGHT_URL, JSON_HEADERS, createFlight } from './lib/seed.js';

const FLIGHTS = 10;

export const options = {
  scenarios: {
    seat_reservation_open_model: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
  thresholds: {
    'http_req_duration{name:POST /flights/{id}/reservations}': ['p(95)<500'],
    'http_req_duration{name:DELETE /reservations/{id}}': ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    dropped_iterations: ['count<1'],
  },
};

export function setup() {
  const tag = Date.now() % 100000;
  const flightIds = [];
  for (let i = 0; i < FLIGHTS; i++) {
    flightIds.push(createFlight(`W${tag}${i}`, 100000, 10000));
  }
  return { flightIds };
}

export default function (data) {
  const flightId = data.flightIds[Math.floor(Math.random() * data.flightIds.length)];
  const ticketClass = Math.random() < 0.8 ? 'ECONOMY' : 'BUSINESS';

  const reserve = http.post(
    `${FLIGHT_URL}/api/v1/flights/${flightId}/reservations`,
    JSON.stringify({ ticketClass }),
    { ...JSON_HEADERS, tags: { name: 'POST /flights/{id}/reservations' } },
  );
  const reserved = check(reserve, {
    'reservation created (201)': (r) => r.status === 201,
    'reservationId returned': (r) => r.status === 201 && !!r.json('reservationId'),
  });
  if (!reserved) {
    return;
  }

  const release = http.del(`${FLIGHT_URL}/api/v1/reservations/${reserve.json('reservationId')}`, null, {
    tags: { name: 'DELETE /reservations/{id}' },
  });
  check(release, { 'reservation released (204)': (r) => r.status === 204 });
}
