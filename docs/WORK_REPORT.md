# Звіт про виконану роботу

## Групове завдання 2

### Малій Олександра Михайлівна

Налаштування репозиторію та бази даних:
- Структура монорепозиторію — 5 незалежних сервісів
- Створення 5 окремих баз даних у Postgres (по одній на сервіс)

### Говорищева Вероніка Максимівна

Усе завдання з `.env`:
- `.env.example` для кожного сервісу
- Конфігурація підключення до БД у `application.properties`

### Давиденко Георгій Андрійович

Усе завдання з Docker:
- `Dockerfile` для кожного сервісу
- `docker-compose.yml` для запуску всього стенду

## Групове завдання 3

### Малій Олександра Михайлівна

Перенесення баз даних у Kubernetes та звітність:
- Структура каталогу `docs/` для всієї документації проєкту
- `StatefulSet`, `Service` і `Secret` для кожної Postgres-бази в `k8s/`
- Звіт з розгортання (`docs/KUBERNETES_REPORT.md`) зі скріншотами роботи кластера

### Говорищева Вероніка Максимівна

Конфігурація сервісів у Kubernetes:
- `ConfigMap` і `Secret` для кожного з 5 сервісів у `k8s/`: порт, адреса БД і користувач у ConfigMap, пароль окремо в Secret
- Liveness/readiness probes через Actuator (`/actuator/health/liveness`, `/readiness`) в `application.properties` усіх сервісів

## Групове завдання 4

### Малій Олександра Михайлівна

Код сервісів:
- #9: типізовані HTTP-клієнти `overbooking-service` на `RestClient` з таймаутами 2 і 3 с та DTO з Tolerant Reader. Також інтерцептор, що додає `X-Correlation-Id` до вихідних запитів.
- #10: єдиний `ProblemDetail` (RFC 9457) в усіх трьох сервісах з заголовками й описами помилок. В `overbooking-service` додатково обробка збоїв залежностей (503 і 502).
- #11: пакетний ендпоінт `POST /api/v1/bookings/batch` у `booking-service`. Він повертає бронювання за списком id одним запитом до бази.
- #12: `POST /api/v1/payments/refund` у `payment-service` із захистом від дублювання через `Idempotency-Key`. Відповідь зберігається за ключем у PostgreSQL, а повторний запит повертає її без нового платежу.
- Розкладка ресурсів `booking`, `payment` та `compensation` на публічний інтерфейс і приватну реалізацію в `internal`.

### Говорищева Вероніка Максимівна

Стійкість `overbooking-service` до збоїв залежностей:
- #13: ендпоінт `POST /api/v1/overbooking/compensations`. Бронювання підтягуються одним пакетним запитом, для кожного виконується refund з детермінованим `Idempotency-Key`. Фільтр `CorrelationIdFilter` прокидає `X-Correlation-Id` з вхідного запиту у вихідні.
- #14: Circuit Breaker, Retry з експоненційним backoff і jitter, Semaphore Bulkhead на `RefundGateway`. При вичерпанні спроб або відкритому колі компенсація отримує статус `PENDING`, а не помилку.
- #15: інтеграційні тести з WireMock: шторм помилок переводить коло в `OPEN`, ретраї з тим самим ключем, `ProblemDetail` 503, наскрізний `X-Correlation-Id`, пакетність. Тести `overbooking-service` увімкнено в CI.

## Групове завдання 5

### Говорищева Вероніка Максимівна

Обмін подіями між `booking-service` і `payment-service` через Kafka:
- #27: `@KafkaListener` на `booking-events` створює платіж `CHARGE`, ключі подій фіксуються в `processed_events` в одній транзакції з платежем (Idempotent Consumer). Ретраї через `@RetryableTopic` і Dead Letter Topic, з DLT у `booking-service` іде результат `FAILED`. Результат оплати публікується в `payment-events` через Transactional Outbox. Додано `GET /api/v1/payments?bookingId=` і тести з EmbeddedKafka.
- #29: споживач результату оплати в `booking-service`. `SUCCESS` переводить бронювання в `CONFIRMED`, `DECLINED`/`FAILED` звільняє місце через `FlightGateway` і ставить `FAILED` (SAGA-компенсація). Дублікати відсікаються за `eventId` у `processed_events`; збій звільнення місця йде в ретраї та DLT, бронювання при цьому лишається `SEAT_RESERVED`.
