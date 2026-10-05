# Звіт: міжсервісна взаємодія через REST

## 1. Що ми зробили

Ми реалізували сценарій компенсації за овербукінг. `overbooking-service` отримує список бронювань, завантажує їх із `booking-service` і повертає гроші через `payment-service`. Кожен пункт завдання виконано окремим issue та pull request у спільному репозиторії.

```
клієнт ──POST /api/v1/overbooking/compensations──► overbooking-service
                                                      │
                  ┌───────────────────────────────────┤
                  ▼                                   ▼
   POST /api/v1/bookings/batch             POST /api/v1/payments/refund
         booking-service                          payment-service
   (один запит на весь список)           (окремий запит на кожне бронювання)
```

Ендпоінт компенсації приймає `bookingIds` і повертає для кожного бронювання статус `COMPLETED` або `PENDING`. Спочатку сервіс одним запитом завантажує бронювання, і якщо якогось немає, відповідає 404. Потім для кожного бронювання викликає повернення коштів на суму `priceAtBooking`.

## 2. HTTP-клієнти

Клієнти `BookingClient` і `PaymentClient` описані декларативно через `@PostExchange`, реалізацію створює `HttpServiceProxyFactory` поверх `RestClient`. `RestTemplate` ми не використовуємо. У фабриці `JdkClientHttpRequestFactory` задані таймаути: підключення 2 с, читання 3 с. DTO є record'ами з `@JsonIgnoreProperties(ignoreUnknown = true)`.

Заголовок `X-Correlation-Id` проходить наскрізь: `CorrelationIdFilter` бере його з вхідного запиту (або генерує UUID), кладе в `MDC` і повертає у відповіді, а `CorrelationIdInterceptor` додає його до кожного вихідного запиту.

## 3. Формат помилок

У всіх трьох сервісах `GlobalExceptionHandler` розширює `ResponseEntityExceptionHandler`, тому навіть стандартні помилки Spring повертаються як `ProblemDetail` (`application/problem+json`) з полями `status`, `title`, `detail`, `instance` і `timestamp`. Необроблений виняток дає 500 із загальним текстом. Заголовки й описи написані українською. В `overbooking-service` недоступність залежності дає 503, відхилений запит дає 502, відсутнє бронювання дає 404.

## 4. Захист виклику платіжного сервісу

Виклик повернення коштів винесений у `RefundGateway`, на метод якого навішені три механізми Resilience4j з однією конфігурацією `paymentClient`:

- **Retry:** до трьох спроб, пауза 500 мс подвоюється з кожною спробою, а `randomized-wait-factor=0.5` додає jitter. Повторюються лише мережеві збої та 5xx.
- **Circuit Breaker:** вікно з 10 викликів, відкривається при 50% невдач, у стані `OPEN` лишається 10 с.
- **Semaphore Bulkhead:** не більше 10 одночасних викликів, без очікування.
- **Fallback:** коли спроби вичерпані, запобіжник відкритий або bulkhead переповнений, повертається `CompensationResult` зі статусом `PENDING`.

Ключ ідемпотентності для бронювання детермінований (`UUID.nameUUIDFromBytes("refund:" + bookingId)`), тому всі спроби Retry надсилають однаковий `Idempotency-Key`.

## 5. Пакетний ендпоінт і Idempotency-Key

`POST /api/v1/bookings/batch` у `booking-service` приймає список `ids` і повертає знайдені бронювання одним викликом `findAllById`. Порожній список дає 400.

`POST /api/v1/payments/refund` у `payment-service` вимагає заголовок `Idempotency-Key`. Для нового ключа ми створюємо `Payment` і в тій самій транзакції зберігаємо відповідь у таблиці `idempotency_records`. Повторний запит з тим самим ключем повертає збережену відповідь без нового платежу. Ключ є первинним, тож при двох одночасних запитах програвший відкочується й повертає відповідь переможця.

## 6. Тестування

Тестів: 13 у `booking-service`, 16 у `payment-service`, 26 в `overbooking-service`, усі проходять. Інтеграційні тести з WireMock піднімають повний контекст `overbooking-service` і підставляють WireMock замість справжніх сервісів.

| Сценарій | Що перевіряє |
|---|---|
| A. Шторм помилок | 10 відповідей 500 переводять запобіжник у `OPEN`, 11-й виклик дає `PENDING`, WireMock бачить рівно 10 запитів |
| B. Retry | Відповіді 500, 500, 200 дають `COMPLETED`, три запити з однаковим `Idempotency-Key` |
| C. ProblemDetail | 500 від `booking-service` перетворюється на 503 `application/problem+json` |
| D. Трасування | `X-Correlation-Id` доходить до обох сервісів і повертається у відповіді |
| E. Пакетність | Для трьох бронювань іде один запит до `booking-service` і три до `payment-service` |
