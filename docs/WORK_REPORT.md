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

### Малій Олександра Михайлівна

Інфраструктура Kafka, продюсер подій і звіт:
- #25: Kafka (KRaft) у `docker-compose.yml`, залежності, контракт подій `BookingReservedEvent` і `PaymentProcessedEvent` (`docs/EVENT_CONTRACT.md`), топіки через `NewTopic` і `TopicBuilder`. Автостворення топіків у брокері вимкнено, щоб кількість партицій не залежала від порядку старту сервісів.
- #26: `POST /api/v1/bookings` і `GET /api/v1/bookings/{id}`, бронювання із заглушками `FlightGateway` і `PassengerGateway`, Transactional Outbox із `PollingOutboxScheduler` та `BookingEventPublisher`, компенсація збоїв самого бронювання (`409`, `503`, `releaseSeat`).
- #28: звіт `docs/KAFKA_REPORT.md` зі скриншотами: топологія топіків, доказ атомарності запису сутності й події, інтеграційні тести з EmbeddedKafka (перехід у DLT, компенсація), експеримент зі штучною відмовою споживача без дублювання бізнес-ефектів.

### Говорищева Вероніка Максимівна

Обмін подіями між `booking-service` і `payment-service` через Kafka:
- #27: `@KafkaListener` на `booking-events` створює платіж `CHARGE`, ключі подій фіксуються в `processed_events` в одній транзакції з платежем (Idempotent Consumer). Ретраї через `@RetryableTopic` і Dead Letter Topic, з DLT у `booking-service` іде результат `FAILED`. Результат оплати публікується в `payment-events` через Transactional Outbox. Додано `GET /api/v1/payments?bookingId=` і тести з EmbeddedKafka.
- #29: споживач результату оплати в `booking-service`. `SUCCESS` переводить бронювання в `CONFIRMED`, `DECLINED`/`FAILED` звільняє місце через `FlightGateway` і ставить `FAILED` (SAGA-компенсація). Дублікати відсікаються за `eventId` у `processed_events`; збій звільнення місця йде в ретраї та DLT, бронювання при цьому лишається `SEAT_RESERVED`.

## Групове завдання 6

### Малій Олександра Михайлівна

- #36: залежність `micrometer-registry-prometheus` і відкритий `/actuator/prometheus` у всіх п'яти сервісах.
- #37: DistributionSummary `flights.price.economy` і `flights.price.business` з межами бакетів у євро.
- #38: Gauge `outbox.pending.events` у `PollingOutboxScheduler`: скільки подій outbox ще не відправлено в Kafka.
- #42: Prometheus, Prometheus Operator і Alertmanager у кластері через Helm-чарт `kube-prometheus-stack`.
- #43: мітки `app` у `service.yaml` і `ServiceMonitor`: Prometheus опитує всі п'ять сервісів, 10 цілей зі статусом `UP`.
- #44: правило алерту `PaymentHighDeclineRate` і маршрутизація Alertmanager; перехід у `Firing` перевірено під навантаженням.
- #46: Kafka (KRaft) у кластері (`k8s/kafka/`: StatefulSet з PVC 1Gi і Service), `KAFKA_BOOTSTRAP_SERVERS` у ConfigMap `booking-service` і `payment-service`. У `@RetryableTopic` задано `numPartitions = "3"`, тож retry-топіки створюються із трьома партиціями незалежно від порядку старту сервісів.
- Сервіси `flight-service` (рейси, ціни за класами, резервація та звільнення місць) і `customer-service` (пасажири, збережений спосіб оплати) з обробкою помилок через `ProblemDetail` і тестами. У `booking-service` заглушки `StubFlightGateway` і `StubPassengerGateway` замінено на `FlightGatewayImpl` і `PassengerGatewayImpl` поверх `RestClient`.
- #45: звіт `docs/MONITORING_REPORT.md` зі скриншотами метрик усіх типів, targets і спрацювання алерту.

### Говорищева Вероніка Максимівна

Бізнес-метрики Micrometer у трьох сервісах:
- #39: Counter `payments.processed.total` з тегом `status` (`SUCCESS`/`DECLINED`) у `PaymentServiceImpl.charge`. Інкремент стоїть у кінці методу, щоб відкочена транзакція не потрапляла в лічильник. Серія `payments_processed_total` живить правило алерту `PaymentHighDeclineRate`.
- #40: Timer `bookings.confirmation.duration` у `PaymentResultServiceImpl.apply`: час від створення бронювання до підтвердження оплати, записується лише для бронювань, що перейшли в `CONFIRMED`.
- #41: LongTaskTimer `overbooking.compensation` навколо `CompensationServiceImpl.compensate` зі стопом у `finally`, тож активна задача не зависає при винятку. Тест перевіряє `activeTasks` під час виконання і після нього.

## Групове завдання 7

### Малій Олександра Михайлівна

Інфраструктура трасування й збору логів у кластері, експорт спанів із сервісів і наскрізна перевірка:
- #59: Grafana Tempo (`k8s/observability/tempo.yaml`): StatefulSet з PVC 1Gi для сховища трейсів і Service на портах 3200 та 4317.
- #60: Grafana Loki (`k8s/observability/loki.yaml`): StatefulSet з PVC 1Gi, прийом логів по OTLP, `service.name` стає міткою `service_name`.
- #61: OpenTelemetry Collector (`k8s/observability/otel-collector.yaml`): прийом OTLP, tail sampling (помилки, повільні трейси, `/api/v1/bookings`, решта 1%) і експорт у Tempo. Ключ `uri` у політиці лишено, бо Spring Boot 4.1 кладе URI-шаблон саме в `uri`.
- #62: агент збору логів `otel-log-agent` (DaemonSet з RBAC): читає логи подів і відправляє їх у Loki.
- #63: `spring-boot-starter-opentelemetry` і налаштування OTLP у всіх п'яти сервісах, `OTEL_TRACES_ENDPOINT` у ConfigMap. Експорт метрик і логів через OTLP вимкнено, бо метрики збирає Prometheus, а логи агент зі stdout. Спани booking-, flight- і customer-service потрапляють в один трейс.
- #68: наскрізний сценарій у кластері: розгортання `k8s/observability/` і Grafana з джерелами Tempo та Loki, бронювання через booking, flight, customer і payment в одному трейсі, збій `flight-service` із переходом від спана з помилкою до рядка логу в Loki, перевірка політик семплінгу. Скриншоти `docs/screenshots/tracing-01…05` і виправлення назв спанів у `docs/TRACING_REPORT.md` за тим, що показує Tempo.

### Говорищева Вероніка Максимівна

Логи та трасування в сервісах, джерела даних Grafana:
- #64: JSON-логи в усіх п'яти сервісах через `logstash-logback-encoder` і `logback-spring.xml`: один рядок на запис із полями `app`, `traceId`, `spanId`. У ConfigMap вимкнено `show-sql`, бо SQL простим текстом ламав JSON.
- #65: передача `traceparent` через outbox у `booking-service`. При збереженні `OutboxRecord` фіксується контекст поточного HTTP-спана, а `PollingOutboxScheduler` відновлює його перед відправкою, тому продюсерський спан стає частиною трейса запиту, а не окремим трейсом.
- #66: те саме для `payment-service`: `OutboxEvent` зберігає `traceparent` спана слухача, `OutboxRelay` відновлює його перед публікацією результату в `payment-events`. Клас `OutboxTracing` і юніт-тест на запис і відновлення на реальному OpenTelemetry SDK.
- #67: `k8s/monitoring/grafana-values.yaml` з джерелами Tempo і Loki: зі спана відкриваються логи з тим самим `traceId`, а поле `traceId` у рядку логу Loki є посиланням на трейс у Tempo.
