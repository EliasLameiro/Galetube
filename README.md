# Galetube

Plataforma de vídeos (backend). Proyecto personal en desarrollo.

## Stack

- Java 21, Spring Boot 4, Spring Security (JWT), JPA
- MySQL 8.4 con migraciones Flyway
- SeaweedFS como almacenamiento S3 (subidas y descargas con URLs firmadas)
- RabbitMQ
- Tests con Testcontainers y CI en GitHub Actions

## Arranque

Requisitos: Java 21 y Docker.

```bash
cp .env.example .env        # rellenar los valores marcados
docker compose up -d
cd backend
./mvnw spring-boot:run
```

Tests: `./mvnw verify` (necesita Docker).

## API

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/register` | Registro |
| POST | `/api/auth/login` | Login, devuelve JWT |
| GET | `/api/users/me` | Usuario actual |
| POST | `/api/users/me/avatar/upload-url` | URL firmada para subir avatar |
| PUT | `/api/users/me/avatar` | Confirmar avatar |
| DELETE | `/api/users/me/avatar` | Quitar avatar |
| GET | `/api/users/{id}/avatar` | Ver avatar |
| POST | `/api/videos` | Crear vídeo y obtener URL de subida |
| GET | `/api/videos` | Listado público |
| GET | `/api/videos/mine` | Mis vídeos |
| GET | `/api/videos/{id}` | Detalle |
| PATCH | `/api/videos/{id}` | Editar |
| DELETE | `/api/videos/{id}` | Borrar (también el fichero) |
| POST | `/api/videos/{id}/upload-url` | Renovar URL de subida |
| POST | `/api/videos/{id}/upload-complete` | Confirmar subida |

## Estado

Hecho:

- Autenticación con JWT y roles
- Gestión de vídeos y subida directa al almacenamiento
- Avatares de usuario

Pendiente:

- Reproducción de vídeos (no hay endpoint que devuelva la URL del vídeo)
- Procesamiento de vídeos mediante RabbitMQ (conectado, sin uso todavía)
- Likes, comentarios y suscripciones (módulo `interaction` vacío)
- Frontend

## Licencia

[MIT](LICENSE)
