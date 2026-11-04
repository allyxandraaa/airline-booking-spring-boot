# Звіт: безпека кластера

## 1. Що зроблено

- Єдина точка входу: Ingress-контролер ingress-nginx і Ingress `airline-ingress` з маршрутами за шляхами до п'яти сервісів.
- Мережева ізоляція: політика Default Deny для вхідного трафіку в namespace `default` і окремі NetworkPolicy, які дозволяють лише наявні зв'язки: Ingress до сервісів, сервіси між собою, сервіси до своїх баз, Kafka, колектор трейсів, Tempo, Loki, Prometheus і Grafana.
- Обмеження привілеїв подів: сервіси й бази працюють від непривілейованого користувача, без ескалації привілеїв, з файловою системою лише для читання і без токена ServiceAccount.
- Перевірено маршрутизацію через Ingress, блокування прямого доступу до сервісів і баз, запуск подів від non-root і заборону запису поза змонтованими каталогами.

| Вимога | Де доведено |
|---|---|
| Ризики та механізми захисту від них | розділ 2 |
| Ingress маршрутизує запити до сервісів | розділ 5.1 |
| Прямий доступ до баз заблоковано | розділ 5.2 |
| Поди від non-root, без ескалації, read-only файлова система | розділ 5.3 |
| Три сценарії виходу з ладу та їх усунення | розділ 6 |
| Архів проєкту | розділ 7 |

## 2. Ризики та захист

| Ризик | Що могло статися без захисту | Механізм захисту | Де описано |
|---|---|---|---|
| Доступ до будь-якого сервісу з будь-якого пода кластера | скомпрометований под викликає внутрішні API напряму, наприклад створює бронювання або рефанд в обхід Ingress | Default Deny для вхідного трафіку; до сервісів пускає лише Ingress-контролер, Prometheus і сервіси-клієнти з контракту | `k8s/security/default-deny.yaml`, `k8s/<сервіс>/networkpolicy.yaml`, `networkpolicy-internal.yaml` |
| Доступ до чужої бази даних | сервіс або сторонній под читає чи змінює дані іншого контексту, порушуючи ізоляцію даних з архітектури | до `postgres-<сервіс>:5432` пускає лише под `app: <сервіс>` | `k8s/<сервіс>/postgres-networkpolicy.yaml` |
| Доступ до Kafka та стеку спостережуваності з довільного пода | підроблені події в `booking-events` або `payment-events`, сміття в Tempo і Loki | до Kafka пускає лише `booking-service` і `payment-service`; до колектора лише сервіси; до Tempo і Loki лише колектор, агент логів і Grafana | `k8s/kafka/networkpolicy.yaml`, `k8s/observability/networkpolicy.yaml` |
| Виконання коду від root у контейнері | втеча з контейнера або зміна файлів образу після компрометації застосунку | `runAsNonRoot: true`, `runAsUser: 10001` для сервісів і `70` для Postgres, `allowPrivilegeEscalation: false` | `k8s/<сервіс>/deployment.yaml`, `postgres-statefulset.yaml` |
| Зміна файлів у працюючому контейнері | підміна jar або бібліотек, закріплення зловмисника в поді | `readOnlyRootFilesystem: true`; для запису лишаються тільки `emptyDir` у `/tmp` (і `/var/run/postgresql` для баз) та диск даних бази | ті самі маніфести |
| Використання токена ServiceAccount для звернень до API Kubernetes | під з'ясовує структуру кластера, читає Secret або змінює ресурси | `automountServiceAccountToken: false`: сервіси й бази не потребують API Kubernetes | ті самі маніфести |

## 3. Мережа

### Ingress

Ingress `airline-ingress` (`k8s/ingress/ingress.yaml`, `ingressClassName: nginx`) маршрутизує запити за префіксом шляху:

| Шлях | Сервіс |
|---|---|
| `/api/v1/passengers` | `customer-service:8085` |
| `/api/v1/flights`, `/api/v1/reservations` | `flight-service:8081` |
| `/api/v1/bookings` | `booking-service:8082` |
| `/api/v1/payments` | `payment-service:8083` |
| `/api/v1/overbooking` | `overbooking-service:8084` |

Зовні видно лише контролер у namespace `ingress-nginx`; Service сервісів лишаються типу `ClusterIP`.

### NetworkPolicy

Політика `default-deny-ingress` з порожнім `podSelector` забороняє весь вхідний трафік до подів у `default`. Решта політик додають дозволи лише для зв'язків, які існували до цього:

| Призначення | Порт | Дозволено від |
|---|---|---|
| `customer-service` | 8085 | Ingress-контролер, `booking-service`, Prometheus |
| `flight-service` | 8081 | Ingress-контролер, `booking-service`, Prometheus |
| `booking-service` | 8082 | Ingress-контролер, `overbooking-service`, Prometheus |
| `payment-service` | 8083 | Ingress-контролер, `overbooking-service`, Prometheus |
| `overbooking-service` | 8084 | Ingress-контролер, Prometheus |
| `postgres-<сервіс>` | 5432 | лише `app: <сервіс>` |
| `kafka` | 9092 | `booking-service`, `payment-service` |
| `otel-collector` | 4318 | п'ять сервісів |
| `tempo` | 4317, 3200 | `otel-collector`, Grafana |
| `loki` | 3100 | `otel-log-agent`, Grafana |

Prometheus і Grafana працюють у namespace `monitoring`, тому для них використано `namespaceSelector` разом із `podSelector` за міткою `app.kubernetes.io/name`. Вихідний трафік не обмежувався: політики мають лише `policyTypes: [Ingress]`.

## 4. securityContext подів

| Параметр | Сервіси (`deployment.yaml`) | Бази (`postgres-statefulset.yaml`) | Навіщо |
|---|---|---|---|
| `runAsNonRoot` | `true` | `true` | kubelet відмовиться запускати контейнер від root |
| `runAsUser` / `runAsGroup` | `10001` (користувач `spring` з Dockerfile) | `70` (користувач `postgres` в образі `postgres:16-alpine`) | процес не має прав root усередині контейнера |
| `fsGroup` | не потрібен | `70` | диск даних PVC доступний на запис користувачу бази |
| `allowPrivilegeEscalation` | `false` | `false` | заборона setuid-підвищення привілеїв |
| `readOnlyRootFilesystem` | `true` | `true` | файли образу незмінні під час роботи |
| `emptyDir` | `/tmp` | `/tmp`, `/var/run/postgresql` | Spring Boot і Tomcat пишуть тимчасові файли в `/tmp`; Postgres тримає там тимчасові файли і unix-сокет |
| `automountServiceAccountToken` | `false` | `false` | у поді немає `/var/run/secrets/kubernetes.io/serviceaccount` |

Значення `runAsUser: 10001` збігається з інструкцією `USER 10001:10001` у Dockerfile кожного сервісу, тому образи не змінювалися.

## 5. Перевірка захисту

### 5.1. Ingress маршрутизує запити

Після `kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8080:80` запити за кожним шляхом дійшли до свого сервісу: `POST /api/v1/flights`, `POST /api/v1/passengers` і `POST /api/v1/bookings` повернули створені об'єкти, `GET /api/v1/payments?bookingId=` повернув оплату, а невідомий шлях дав `404` від nginx.

![Ingress: запити за шляхами доходять до сервісів](screenshots/security-01-ingress-routes.png)

### 5.2. Прямий доступ заблоковано

З тимчасового пода (`kubectl run tmp --rm -it --image=curlimages/curl`) запит `curl -m 3 http://booking-service:8082/actuator/health` завершився таймаутом, хоча через Ingress той самий сервіс відповідає. Под із міткою `app: customer-service` не підключається до чужої бази: `nc -zv -w 3 postgres-booking 5432` завершується таймаутом, тоді як `customer-service` зі своєю базою працює (под `Ready`).

![NetworkPolicy: таймаут при прямому зверненні до сервісу й чужої бази](screenshots/security-02-direct-access-blocked.png)

### 5.3. Поди без root і без запису

| Перевірка | Команда | Результат |
|---|---|---|
| користувач сервісу | `kubectl exec deploy/booking-service -- id -u` | `10001` |
| користувач бази | `kubectl exec postgres-booking-0 -- id -u` | `70` |
| запис у файлову систему образу | `kubectl exec deploy/booking-service -- touch /app/x` | `Read-only file system` |
| запис поза каталогами бази | `kubectl exec postgres-booking-0 -- touch /usr/x` | `Read-only file system` |
| токен ServiceAccount | `kubectl exec deploy/booking-service -- ls /var/run/secrets/kubernetes.io/serviceaccount` | `No such file or directory` |

Після `kubectl rollout restart` усі поди сервісів і баз повернулися в `Ready`, `pg_isready` проходить, бронювання створюються як раніше.

![securityContext: id, спроба запису і відсутній токен ServiceAccount](screenshots/security-03-pod-hardening.png)

## 6. Три сценарії виходу з ладу та їх усунення

### Сценарій 1. Новий зв'язок без NetworkPolicy

Після Default Deny будь-який новий виклик між подами блокується мовчки: клієнт отримує таймаут, у логах сервера нічого немає. Так станеться, коли з'явиться новий сервіс або новий виклик, наприклад `overbooking-service` почне звертатися до `flight-service`.

Ознаки: `ResourceAccessException` або `503` у клієнта, сервер `Ready` і без помилок, `kubectl run tmp` з потрібною міткою теж отримує таймаут.

Усунення: додати правило в `networkpolicy-internal.yaml` сервера з `podSelector` за міткою клієнта й портом, застосувати, перевірити з'єднання з пода клієнта. Правило: кожен новий зв'язок у коді супроводжується NetworkPolicy у тому самому PR.

### Сценарій 2. Контейнер пише туди, де файлова система лише для читання

Якщо застосунок почне писати в інший каталог (кеш бібліотеки, новий тимчасовий файл, журнал на диск), под не стартує або падає під час роботи з `Read-only file system`, а Deployment лишається з частиною старих реплік.

Ознаки: `CrashLoopBackOff`, у `kubectl logs --previous` помилка `java.nio.file.FileSystemException: ... Read-only file system`; для бази `could not create lock file` або помилка сокета.

Усунення: з'ясувати шлях із логу, додати ще один `emptyDir` і `volumeMount` на цей каталог, не вимикаючи `readOnlyRootFilesystem`. Якщо під час оновлення поди не піднімаються, `kubectl rollout undo deployment/<сервіс>` повертає попередню версію, бо `rollout` не видаляє старі репліки, доки нові не стануть `Ready`.

### Сценарій 3. Недоступний Ingress-контролер

Усі зовнішні запити проходять через один под `ingress-nginx-controller`. Якщо його видалено, він упав або для нього не вистачило ресурсів, зовнішній доступ зникає повністю, хоча всі сервіси здорові й між собою спілкуються.

Ознаки: `connection refused` або `502`/`504` на вході, `kubectl -n ingress-nginx get pods` показує под не в `Running`, при цьому `kubectl get pods` у `default` без змін.

Усунення: перевірити події пода контролера (`kubectl -n ingress-nginx describe pod`), перезапустити його або перевстановити контролер; для перевірки сервісів під час інциденту використати `kubectl port-forward` до потрібного Service. Щоб сценарій не повторювався, контролеру задають дві репліки й запити на ресурси.

## 7. Архів

Архів для здачі містить маніфести `k8s/` (у тому числі `k8s/ingress/`, `k8s/security/`, NetworkPolicy й оновлені Deployment і StatefulSet), цей звіт і скріншоти `docs/screenshots/security-*.png`. Секретів в архіві немає: `k8s/*/secret.yaml` і `postgres-secret.yaml` містять лише демонстраційні значення для локального кластера.
