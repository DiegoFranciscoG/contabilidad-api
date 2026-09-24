# Despliegue gratuito: Render (API) + Neon (PostgreSQL)

Límites verificados el 2026-09-24 (ver [investigacion.md](investigacion.md), fuentes #15 y #16):

| Servicio | Qué corre | Límites del plan gratuito | Tarjeta |
|---|---|---|---|
| Render (web service, Docker) | La API y el visor web | Se duerme tras 15 min sin tráfico (≈1 min en despertar), 750 h/mes por workspace | No |
| Neon (Free) | PostgreSQL 17 con la vista materializada | 0,5 GB y 100 CU-h por proyecto, scale-to-zero a los 5 min; al exceder se suspende el cómputo sin borrar datos | No |

No se usa Postgres de Render porque la base gratuita expira a los 30 días.

## 1. Base de datos en Neon

1. Crea un proyecto en <https://console.neon.tech> (región cercana a Oregón, por ejemplo `aws-us-west-2`), PostgreSQL 17.
2. En **Connection details** copia host, base, usuario y contraseña. Usa la conexión **directa** (sin `-pooler`): Flyway y `REFRESH MATERIALIZED VIEW` necesitan sesiones normales.
3. La URL JDBC queda así (sin credenciales dentro):
   `jdbc:postgresql://<host-de-neon>/<base>?sslmode=require`

## 2. API en Render

1. Sube el repositorio a GitHub.
2. En Render: **New → Blueprint** y elige el repositorio; Render lee [`render.yaml`](../render.yaml).
3. Completa las variables marcadas `sync: false`:

| Variable | Valor |
|---|---|
| `DB_URL` | La URL JDBC de Neon del paso 1 |
| `DB_USER` / `DB_PASSWORD` | Credenciales de Neon |
| `INTEGRATION_HMAC_SECRET` | `openssl rand -base64 48` (el mismo valor lo usan facturador-sri y nomina-ec) |
| `CORS_ALLOWED_ORIGINS` | La URL pública del servicio, p. ej. `https://contabilidad-api.onrender.com` |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Tu administrador (contraseña de 12+ caracteres) |
| `DEMO_PASSWORD` | Contraseña del usuario de solo lectura `demo@demo.com` que publicarás en el README |

`JWT_SECRET` lo genera Render (`generateValue`). `autoDeployTrigger: checksPass` despliega solo cuando el CI de GitHub pasa.

4. Al primer arranque Flyway crea el esquema, se crea el administrador y se cargan los datos demo del año en curso.
5. Comprueba: `https://<servicio>.onrender.com/actuator/health` → `{"status":"UP"}`.

## 3. Mantenerlo dentro del plan gratuito

- 1 instancia × 24 h × 31 días = 744 h < 750 h por workspace: no despliegues otro servicio gratuito en el mismo workspace si lo quieres siempre disponible.
- El primer request tras 15 minutos tarda ~1 minuto (Render) + ~1 s (Neon despertando).
- `DB_POOL_SIZE=3` mantiene pocas conexiones abiertas en Neon.
- Para cerrar la documentación pública pon `PUBLIC_API_DOCS=false`.
