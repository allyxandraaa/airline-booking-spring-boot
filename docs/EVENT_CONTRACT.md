# Контракт подій Kafka

Єдине джерело правди про обмін подіями між `booking-service` та `payment-service`. Зміни контракту вносяться тут і в обидві копії відповідних record.

## Потік SAGA

Подієвий обмін реалізує кроки 6–7 сценарію 1 та сценарій 2 з `docs/ARCHITECTURE_PROPOSAL.md` (хореографічна SAGA):

```
booking-service                                   payment-service
  PENDING → ціна → резервація → SEAT_RESERVED
  ── PaymentRequestedEvent ──(booking-events)──▶   списання (SUCCESS / DECLINED)
                                                   збій після ретраїв → DLT → статус FAILED
  ◀── PaymentResultEvent ────(payment-events)──
  SUCCESS            → CONFIRMED
  DECLINED / FAILED  → звільнити місце (release-seat) → FAILED
```

Кожна подія пишеться в Outbox сервісу-продюсера в тій самій транзакції, що й зміна його даних. Споживачі ідемпотентні за `eventId`.

## Топологія

| Параметр | `booking-events` | `payment-events` |
|---|---|---|
| Призначення | запит на оплату | результат оплати |
| Продюсер / власник топіка | `booking-service` (`KafkaTopicConfig`) | `payment-service` (`KafkaTopicConfig`) |
| Споживач | `payment-service` | `booking-service` |
| Партиції / репліки | 3 / 1 | 3 / 1 |
| Ключ повідомлення | `bookingId` (UUID як `String`) | `bookingId` (UUID як `String`) |
| Значення | JSON, `PaymentRequestedEvent` | JSON, `PaymentResultEvent` |
| Група споживача | `payment-service-group` | `booking-service-group` |
| Retry-топіки | `booking-events-retry-1000`, `booking-events-retry-2000` | `payment-events-retry-1000`, `payment-events-retry-2000` |
| Dead Letter Topic | `booking-events-dlt` | `payment-events-dlt` |

Retry-топіки й DLT створює `@RetryableTopic` у споживача. Заголовок `X-Correlation-Id` (необов'язковий) продюсер додає, якщо він є в MDC. Споживач топіка лише підписується на нього й не оголошує його.

## `PaymentRequestedEvent` (`booking-events`)

Крок 6 сценарію 1: запит на списання коштів. Публікується, коли ціна зафіксована, а місце зарезервоване (статус `SEAT_RESERVED`).

```json
{
  "eventId":         "0b0f7c9e-6a53-4f3b-9c1a-2d3a8f1b7e11",
  "bookingId":       "5d1e2a64-8b0c-4a8f-9a77-0c1f4f3d9b21",
  "paymentMethodId": "a8c3b6f0-1d2e-4c55-8e90-7b6a5d4c3b2a",
  "amount":          120.50,
  "occurredAt":      "2026-10-11T18:30:00Z"
}
```

| Поле | Тип | Опис |
|---|---|---|
| `eventId` | UUID | Унікальний ідентифікатор події; збігається з `id` запису outbox і є ключем дедуплікації у споживача |
| `bookingId` | UUID | Бронювання, яке оплачується |
| `paymentMethodId` | UUID | Емульований спосіб оплати з Passenger Context (платіжні дані не передаються) |
| `amount` | decimal | Сума до списання, `> 0`; дорівнює `priceAtBooking` |
| `occurredAt` | ISO-8601, UTC | Момент створення події |

## `PaymentResultEvent` (`payment-events`)

Результат оплати для SAGA-компенсації.

```json
{
  "eventId":    "7c2d9e10-3b4a-4f6e-8a21-5e9d0c7b1f33",
  "bookingId":  "5d1e2a64-8b0c-4a8f-9a77-0c1f4f3d9b21",
  "paymentId":  "9f8e7d6c-5b4a-4321-8fed-cba987654321",
  "status":     "SUCCESS",
  "reason":     null,
  "occurredAt": "2026-10-11T18:30:02Z"
}
```

| Поле | Тип | Опис |
|---|---|---|
| `eventId` | UUID | Унікальний ідентифікатор події; збігається з `id` запису outbox `payment-service`, ключ дедуплікації у `booking-service` |
| `bookingId` | UUID | Бронювання, до якого належить результат |
| `paymentId` | UUID, nullable | Створений платіж; `null` для `FAILED`, коли платіж не вдалося зафіксувати |
| `status` | enum | `SUCCESS`, `DECLINED` (бізнес-результат симуляції) або `FAILED` (запит не вдалося обробити: ретраї вичерпані, подія пішла в DLT) |
| `reason` | string, nullable | Пояснення для `DECLINED`/`FAILED` |
| `occurredAt` | ISO-8601, UTC | Момент створення події |

Реакція `booking-service`: `SUCCESS` → `CONFIRMED`; `DECLINED` або `FAILED` → звільнення місця й статус `FAILED`.

## Правила

- **Обов'язковість полів:** усі поля обов'язкові, крім `paymentId` і `reason` в `PaymentResultEvent`. Record перевіряє це в компактному конструкторі.
- **Еволюція схеми:** додаємо лише необов'язкові поля; наявні не перейменовуємо й не видаляємо.
- **Класи в різних пакетах:** кожен record дубльований у двох сервісах (`com.example.booking_service.booking` і `com.example.payment_service.payment`), спільного модуля немає. Тому type-заголовки Spring Kafka вимкнені:
  - продюсер: `spring.kafka.producer.properties.spring.json.add.type.headers=false`;
  - споживач: `spring.json.use.type.headers=false` і `spring.json.value.default.type=<повне ім'я локального record>`.
- **Серіалізація:** ключ — `StringSerializer`/`StringDeserializer`; значення — `JacksonJsonSerializer`/`JacksonJsonDeserializer` (Jackson 3, як у Spring Boot 4). Споживач обгортає десеріалізатор у `ErrorHandlingDeserializer`, щоб зламане повідомлення не блокувало партицію.
- **Адреса брокера:** змінна `KAFKA_BOOTSTRAP_SERVERS`. У `docker-compose` — `kafka:9092`; для сервісу, запущеного локально через `./gradlew bootRun`, за замовчуванням `localhost:29092`.
- **Обмеження:** якщо `payment-service` не відповів узагалі (подія не дійшла й не потрапила в DLT), бронювання лишається в `SEAT_RESERVED`; таймаутів і sweeper-а поки немає.
