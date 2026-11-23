import http from 'k6/http';
import { check } from 'k6';
import { BOOKING_URL, JSON_HEADERS, createFlight, createPassenger } from './lib/seed.js';

const PASSENGERS = 20;
const PEAK = Number(__ENV.PEAK_RATE || 60);

export const options = {
  scenarios: {
    booking_stress: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 2000,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '60s', target: 20 },
        { duration: '30s', target: PEAK },
        { duration: '60s', target: PEAK },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.05'],
    checks: ['rate>0.95'],
  },
};

export function setup() {
  const flightId = createFlight(`B${Date.now() % 100000}`, 500000, 50000);
  const passengers = [];
  for (let i = 0; i < PASSENGERS; i++) {
    passengers.push(createPassenger(i));
  }
  return { flightId, passengers };
}

export default function runScenario(data) {
  const passenger = data.passengers[Math.floor(Math.random() * data.passengers.length)];
  const ticketClass = Math.random() < 0.8 ? 'ECONOMY' : 'BUSINESS';

  const res = http.post(
    `${BOOKING_URL}/api/v1/bookings`,
    JSON.stringify({
      flightId: data.flightId,
      passengerId: passenger.id,
      passengerName: passenger.name,
      ticketClass,
    }),
    { ...JSON_HEADERS, tags: { name: 'POST /bookings' } },
  );
  check(res, {
    'booking created (201)': (r) => r.status === 201,
    'booking has SEAT_RESERVED status': (r) => r.status === 201 && r.json('status') === 'SEAT_RESERVED',
  });
}
