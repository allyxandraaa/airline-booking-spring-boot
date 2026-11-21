import http from 'k6/http';
import { fail } from 'k6';

export const FLIGHT_URL = __ENV.FLIGHT_URL || 'http://flight-service:8081';
export const PASSENGER_URL = __ENV.PASSENGER_URL || 'http://passenger-service:8085';
export const BOOKING_URL = __ENV.BOOKING_URL || 'http://booking-service:8082';

export const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export function createFlight(tag, economySeats, businessSeats) {
  const body = {
    flightNumber: `LT${tag}`,
    origin: 'KBP',
    destination: 'WAW',
    departureTime: '2027-06-01T10:00:00Z',
    totalSeatsEconomy: economySeats,
    totalSeatsBusiness: businessSeats,
    basePriceEconomy: 120.0,
    basePriceBusiness: 250.0,
    overbookingLimitPercent: 10,
  };
  const res = http.post(`${FLIGHT_URL}/api/v1/flights`, JSON.stringify(body), JSON_HEADERS);
  if (res.status !== 201) {
    fail(`flight seed failed: ${res.status} ${res.body}`);
  }
  return res.json('id');
}

export function createPassenger(index) {
  const body = {
    fullName: `Load Tester ${index}`,
    email: `load.tester.${Date.now()}.${index}@example.com`,
    phone: '+380501234567',
    documentNumber: `LT${Date.now()}${index}`,
  };
  const res = http.post(`${PASSENGER_URL}/api/v1/passengers`, JSON.stringify(body), JSON_HEADERS);
  if (res.status !== 201) {
    fail(`passenger seed failed: ${res.status} ${res.body}`);
  }
  return { id: res.json('id'), name: body.fullName };
}
