# artagram-payment

Microservicio responsable de **pagos y suscripciones**: checkout, webhook
del proveedor de pago, cuotas de plan (megabytes/obras). Extraído de
`artagram-users/src/main/java/com/artagram/users/{billing,service/SuscripcionService.java,...}`
— facturación tiene un perfil de seguridad y ciclo de vida distinto al de
perfil de usuario (secretos de webhook, proveedor externo), así que no
pertenece junto a `artagram-users`.

## Puerto y base de datos

- Puerto local: `8089`.
- Base de datos propia: `db_payment` (Postgres) — **no** tiene FK a
  `usuarios`: esa tabla vive en la base de datos de `artagram-users`. El
  estado premium se sincroniza localmente vía el evento `user.registered`
  (ver `PaymentUserEventListener`), no con una consulta en vivo.
- RabbitMQ: consume `user.registered`/`user.deleted` para sembrar/limpiar
  su propio estado; publica `subscription.updated` al aprobar un pago, con
  el **mismo payload** que antes publicaba `artagram-users`, para que
  `artagram-canvas` y cualquier otro consumidor existente sigan
  funcionando sin cambios.

## Qué se portó de `artagram-users` y qué cambió

- `common.*`/`auth.*` — copiados tal cual de `artagram-canvas` (mismas
  convenciones de trazabilidad, manejo de errores, CORS y validación JWT
  RS256 manual, sin Spring Security).
- `SuscripcionService`/`Suscripcion`/`UsoCuenta`/sus repositorios — portados
  de `artagram-users`, sin dependencia de `UsuarioRepository`: el campo
  `premium` ya no vive en una entidad `Usuario` ajena, se deriva de
  `plan == "PRO" && estado == "ACTIVA"` en la propia `Suscripcion`.
- `SuscripcionController` (`/api/suscripcion`: `GET /actual`,
  `POST /checkout`, `POST /confirmar?tx=...`) — mismas rutas que antes, para
  no romper el frontend.
- **Fix de diseño deliberado**: en el código original, `MercadoPagoProvider`
  decidía Fake-vs-real internamente mirando si el access token estaba en
  blanco (con `@Primary` + un fallback a `FakePagoProvider` dentro del
  propio bean) — el env var `PAGO_PROVEEDOR` existía en `application.yml`
  pero nunca se leía en código. Aquí la selección la hace Spring vía
  `@ConditionalOnProperty(prefix="app.pago", name="proveedor", ...)`, el
  mismo patrón que usan `StorageProvider`/`EmailService` en el resto del
  workspace: la existencia del bean ES la selección. Sigue siendo un stub
  (sin llamadas reales al SDK de MercadoPago).
- **Nuevo** (no existía nada parecido antes): `PagoWebhookController`
  (`POST /api/pagos/webhook/mercadopago`) y `MercadoPagoWebhookVerifier`
  (HMAC-SHA256 contra `MERCADOPAGO_WEBHOOK_SECRET`, verificación
  simplificada — el formato real de MercadoPago usa un manifiesto
  distinto). El único flujo de confirmación que existía antes era el
  `confirmar` manual/autenticado por JWT, que se mantiene para el proveedor
  Fake y para pruebas manuales.
- `InternalPaymentController` (`GET /internal/payment/{usuarioId}/plan`) —
  renombrado de `/internal/users/{id}/plan`.

## Variables de entorno

Ver `.env.example`. Las relevantes de pagos: `PAGO_PROVEEDOR` (`fake` por
defecto, o `mercadopago`), `MP_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET`.

## Cómo correrlo en local

```bash
docker compose -f ../artagram-infra/compose.yml -f ../artagram-infra/compose.services.yml \
  up -d --build artagram-payment
curl -s http://localhost:8089/actuator/health
```

Requiere que `db_payment` exista en el Postgres compartido — ver el paso
manual documentado en el plan de implementación (el init-script de
`artagram-infra` solo crea bases nuevas en un volumen Postgres recién
creado).

## Fuera de alcance de esta migración

`artagram-users` sigue teniendo el código viejo de `billing`/
`SuscripcionService` — queda como código muerto hasta el corte del gateway
y la verificación de este servicio; borrarlo es un cambio aparte.
