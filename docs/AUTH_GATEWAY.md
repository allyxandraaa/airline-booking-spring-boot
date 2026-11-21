# Єдина точка входу, автентифікація та Rate Limiter

Реалізовано за лекцією 8: Spring Cloud Gateway як єдина точка входу, Keycloak як спільне джерело автентифікації (OIDC), шлюз перевіряє JWT як Resource Server, Redis зберігає лічильники Rate Limiter.

## Схема

```
клієнт ──► Keycloak (реєстрація, логін, видача JWT)
   │
   └─ Bearer JWT ──► gateway-service ──► passenger / flight / booking / payment / overbooking
                       │  перевірка підпису й iss (JWKS Keycloak)
                       │  RequestRateLimiter (Redis), ключ = sub із JWT
                       └─ 401 без токена, 429 при перевищенні ліміту
```

## Що де лежить

| Файл | Призначення |
|---|---|
| `gateway-service/` | Spring Cloud Gateway (WebFlux, Spring Cloud 2025.1.2), маршрути в `application.yml` |
| `gateway-service/.../GatewaySecurityConfig.java` | `SecurityWebFilterChain`: відкрито лише `/actuator/health/**` і `/actuator/prometheus`, решта потребує JWT |
| `gateway-service/.../RateLimiterConfig.java` | `userKeyResolver`: ім'я принципала, інакше `anonymous` |
| `keycloak/airline-realm.json` | realm `airline`: самореєстрація за email, логін, публічний клієнт `airline-client` |
| `docker-compose.yml` | сервіси `keycloak`, `redis`, `gateway-service` |
| `k8s/gateway-service`, `k8s/keycloak`, `k8s/redis` | маніфести кластера; `NetworkPolicy` сервісів приймає трафік лише від шлюзу |

## Запуск у compose

1. Скопіюйте `keycloak/.env.example` у `keycloak/.env` і `gateway-service/.env.example` у `gateway-service/.env`, задайте пароль адміністратора Keycloak.
2. `docker compose up -d --build`. Шлюз слухає `localhost:8090`, Keycloak `localhost:8180`.

## Сценарій демо

Реєстрація: відкрити у браузері
`http://localhost:8180/realms/airline/protocol/openid-connect/registrations?client_id=airline-client&response_type=code&redirect_uri=http://localhost:8090/&scope=openid`
і заповнити форму.

Логін і токен (пряма видача для зручності демо, у клієнта увімкнено `directAccessGrantsEnabled`):

```powershell
curl -s -X POST http://localhost:8180/realms/airline/protocol/openid-connect/token -d "client_id=airline-client" -d "grant_type=password" -d "username=<email>" -d "password=<пароль>"
```

Виклик через шлюз із токеном з поля `access_token`:

```powershell
curl -i -H "Authorization: Bearer <токен>" http://localhost:8090/api/v1/flights/<id>/price?ticketClass=ECONOMY
```

Без токена шлюз повертає `401`. Понад 10 запитів на секунду від одного користувача (сплеск до 20) отримують `429`.

## Чому `iss` і JWKS налаштовано окремо

Токен видається за адресою, яку бачить клієнт (`KC_HOSTNAME=http://localhost:8180`), і саме її `iss` перевіряє шлюз (`KEYCLOAK_ISSUER_URI`). Ключі шлюз бере з внутрішньої адреси `keycloak:8080` (`KEYCLOAK_JWK_SET_URI`), бо з контейнера `localhost` не веде до Keycloak. Лекція прямо попереджає про цю розбіжність.

## Обмеження, які варто знати на захисті

- Сервіси за шлюзом самі JWT не перевіряють (на відміну від повного Zero Trust з лекції). Ззовні кластера єдиний шлях до API це Ingress, що веде на шлюз (сервіси мають лише `ClusterIP`). Зсередини кластера прямий виклик сервісу блокує `NetworkPolicy` (`default-deny` плюс дозвіл лише для `gateway-service`): перевірено, що `payment-service` і чужий под не підключаються до `flight-service` і `redis`, а `gateway-service` і дозволені сервіси підключаються. Токен самі сервіси не перевіряють. У compose політик немає, а порти сервісів опубліковані для налагодження й k6.
- Користувач Keycloak і пасажир у `passenger-service` не пов'язані автоматично: профіль пасажира створюється окремим викликом після логіну.
- Ролей у realm немає: шлюз перевіряє лише, що токен дійсний, без розмежування прав.
- Прямий grant `password` і `redirectUris: *` призначені лише для локального демо. Keycloak працює в режимі `start-dev` із вбудованою базою, зареєстровані користувачі зникають разом із контейнером.
- У k8s `KC_HOSTNAME` і `KEYCLOAK_ISSUER_URI` вказують на `http://localhost:8088` (порт для `kubectl port-forward` до Ingress), тож зміна способу доступу вимагає зміни обох.

## Розгортання в Kubernetes

```powershell
kubectl apply -f k8s/redis
kubectl apply -f k8s/keycloak
kubectl apply -f k8s/gateway-service
kubectl apply -f k8s/ingress
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8088:80
```

- Образи збираються з `docker compose build` і тегуються `<сервіс>:1.1` (`docker tag airline-booking-spring-boot-gateway-service gateway-service:1.1`). Вузол кластера має власне сховище образів і не оновлює вже відомий тег, тому нова версія потребує нового тегу, який вказаний у `deployment.yaml`.
- `NetworkPolicy` кожного сервісу пускає лише `gateway-service`, тож прямий виклик сервісу з іншого pod блокується.
- Kubernetes додає в кожен pod змінні `<СЕРВІС>_PORT=tcp://...` для сервісів з однойменними іменами (`REDIS_PORT`, `KEYCLOAK_PORT`), тому порт Redis у `application.yml` заданий константою, а не змінною середовища.
