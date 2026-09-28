# Reactive Distributed SAGA Orchestrator with Quarkus & Mutiny

Sistema distribuido de orquestación de transferencias monetarias de alta concurrencia basado en el Patrón SAGA Orquestado, implementado con Quarkus 3.39.5, SmallRye Mutiny (programación reactiva no bloqueante) y PostgreSQL / H2.

El sistema garantiza consistencia eventual y atomicidad distribuida entre microservicios autónomos mediante transacciones compensatorias en orden inverso (LIFO - Last In, First Out), evitando bloqueos pesados de base de datos distribuidos (2PC) y manteniendo el Event Loop de Vert.x completamente libre de operaciones bloqueantes.

---

## 1. Arquitectura y Patrones de Diseño

El sistema implementa una arquitectura orientada a microservicios desacoplados donde un orquestador central coordina el ciclo de vida de la transacción mediante llamadas HTTP reactivas asíncronas (Uni).

                              [ Cliente / Bruno / Swagger ]
                                            │
                                            ▼
                           ┌──────────────────────────────────┐
                           │    saga-orchestrator (8080)      │
                           │  - Máquina de Estados SAGA       │
                           │  - Tabla: saga_transaction       │
                           │  - Tabla: transaction_audit      │
                           └───────┬──────────┬─────────┬─────┘
                                   │          │         │
                 1. Débito (HTTP)  │          │         │ 3. Notificación (HTTP)
                                   ▼          │         ▼
                    ┌──────────────────────┐  │  ┌──────────────────────┐
                    │account-service (8081)│  │  │notification-service   │
                    │- Balance débito/rev. │  │  │(8083)                │
                    │- Base de Datos       │  │  │- Emisión comprobante │
                    └──────────────────────┘  │  └──────────────────────┘
                                              │
                            2. Crédito (HTTP) │
                                              ▼
                               ┌──────────────────────┐
                               │payment-service (8082)│
                               │- Abono en cuenta     │
                               │- Base de Datos       │
                               └──────────────────────┘

### Patrones Clave Implementados
* Saga Pattern (Orchestration): El orquestador es el único punto de control que conoce la secuencia de ejecución (Execute) y la secuencia de compensación (Compensate).
* LIFO Compensation: Si el Paso 3 (Notificación) falla, el orquestador compensa primero el Paso 2 (revierte crédito) y finalmente el Paso 1 (revierte débito).
* Reactive Worker Offloading: Las lecturas y escrituras JPA/Panache bloqueantes hacia la base de datos se ejecutan fuera del Event Loop de Vert.x usando explícitamente runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).
* State Machine & Idempotency: Cada transacción se persiste en estado PENDING, transicionando atómicamente a COMPLETED o COMPENSATED con su traza de auditoría.

---

## 2. Tecnologías y Versiones

* Lenguaje: Java 21 LTS (compatible con Java 25)
* Framework: Quarkus 3.39.5
* Librería Reactiva: SmallRye Mutiny
* ORM: Hibernate ORM con Panache (JPA)
* Bases de Datos:
  * Producción / Desarrollo: PostgreSQL Serverless (Neon Cloud)
  * Pruebas Unitarias / CI: H2 Database en memoria (%test)
* Especificación API: SmallRye OpenAPI 3.1 & Swagger UI
* Herramientas de Testing: JUnit 5, Mockito (con @InjectMock), REST-Assured y Bruno CLI

---

## 3. Microservicios y Componentes

| Microservicio | Puerto | Base de Datos | Rol en la SAGA |
|---|---|---|---|
| saga-orchestrator | 8080 | PostgreSQL (saga_db) | Orquestador central, auditoría, compensación y streaming SSE. |
| account-service | 8081 | PostgreSQL (account_db) | Débito de saldos y reversa de débitos en cuenta origen. |
| payment-service | 8082 | PostgreSQL (payment_db) | Acreditación y reversa de créditos en cuenta destino. |
| notification-service | 8083 | En memoria / Mock | Emisión de comprobantes digitales y punto de inyección de fallas. |

---

## 4. Configuración de Base de Datos y Credenciales

### Entornos y Perfiles
* Producción / Dev: Utiliza PostgreSQL hosteado en Neon. Requiere las variables de entorno inyectadas en tiempo de ejecución.
* Test (%test): Configurado en application.properties con jdbc:h2:mem:orchestrator_test;DB_CLOSE_DELAY=-1 y generación drop-and-create, permitiendo tests independientes sin dependencias de red.

### Variables de Entorno Requeridas
Nunca se versionan contraseñas en Git. Los servicios leen estas variables del sistema operativo o del archivo .env local:

export DB_JDBC_URL="jdbc:postgresql://<neon-host>.neon.tech/<database>?sslmode=require"
export DB_USERNAME="<usuario_db>"
export DB_PASSWORD="<contraseña_db>"

---

## 5. Endpoints y Catálogo de APIs

### Swagger UI
Disponible en el orquestador mientras esté en ejecución:
* URL: http://localhost:8080/q/swagger-ui/ (o vía URL de Codespaces con puerto 8080)
* OpenAPI Spec: http://localhost:8080/q/openapi

### Rutas Principales (saga-orchestrator)

1. Ejecutar Transferencia (Orquestación SAGA)
* Método / URL: POST /api/transfers
* Headers: Content-Type: application/json
* Body (Happy Path):
  {
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 75.00,
    "simulateNotificationFailure": false
  }
* Respuesta Exitosa (200 OK):
  {
    "sagaId": "219f8c8f-36e3-423b-8f61-30774743f10d",
    "status": "SUCCESS",
    "message": "Transferencia completada exitosamente bajo orquestación SAGA."
  }
* Body (Prueba de Rollback LIFO):
  {
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 75.00,
    "simulateNotificationFailure": true
  }
* Respuesta con Compensación (409 Conflict):
  {
    "sagaId": "219f8c8f-36e3-423b-8f61-30774743f10d",
    "status": "FAILED_AND_COMPENSATED",
    "message": "Fallo reactivo. La compensación SAGA restauró saldos y comprobantes vía HTTP (Reactivo Uni)."
  }

2. Consultas y Auditoría
* GET /api/accounts: Consulta de saldos delegada vía HTTP hacia account-service.
* GET /api/sagas: Historial de transacciones y estados finales (COMPLETED, COMPENSATED).
* GET /api/audits: Trazabilidad detallada de cada paso y compensación ejecutada.
* GET /api/transfers/stream: Flujo en vivo de eventos reactivos vía Server-Sent Events (Multi<String>).

---

## 6. Pruebas de API con Bruno

La carpeta bruno-collection/ contiene la colección de pruebas versionada.

### Estructura de la Colección
bruno-collection/
├── bruno.json
├── collection.bru
├── environments/
│   ├── Localhost.bru
│   └── Codespaces.bru
├── 01-Saga-Orchestrator/
└── 02-Direct-Microservices-Testing/

### Cómo ejecutar la colección
1. Abre Bruno y selecciona Open Collection.
2. Selecciona la carpeta bruno-collection/ de este proyecto.
3. Elige el entorno:
   * Localhost: Si ejecutas los servicios en tu máquina local.
   * Codespaces: Si ejecutas sobre GitHub Codespaces. Configura tu token personal en Environments -> Configure para túneles protegidos.

---

## 7. Pipeline de CI/CD (GitHub Actions)

El repositorio cuenta con un pipeline completamente automatizado en .github/workflows/ci-cd.yml que se ejecuta en cada push o pull_request a la rama main.

           [ git push origin main ]
                      │
                      ▼
   ┌─────────────────────────────────────┐
   │        FASE CI (Continuous Int.)    │
   │ - Setup Java 21 Temurin             │
   │ - Ejecución mvn test (4 servicios)  │
   │ - Empaquetado Fast-JAR de apps      │
   │ - Generación de Artefactos          │
   └──────────────────┬──────────────────┘
                      │ (Solo si CI pasa 100%)
                      ▼
   ┌─────────────────────────────────────┐
   │        FASE CD (Continuous Dep.)    │
   │ - Descarga de binarios Fast-JAR     │
   │ - Despliegue en vivo en background  │
   │ - Health Check de puertos y APIs    │
   │ - Entorno Activo durante 5 minutos  │
   │ - Apagado controlado de servicios   │
   └─────────────────────────────────────┘

### ¿Qué sucede exactamente en cada Push?
1. Validación Estricta: Se compilan y corren todas las pruebas unitarias y de integración reactivas de los cuatro módulos (account-service, payment-service, notification-service, saga-orchestrator). Si algún test falla, el pipeline aborta la ejecución inmediatamente.
2. Generación de Binarios: Si los tests son exitosos, se construyen los ejecutables Fast-JAR optimizados de Quarkus.
3. Despliegue Efímero: El Job de CD levanta los cuatro servicios en puertos dedicados (8080, 8081, 8082, 8083), verifica que respondan a los health checks y los mantiene activos durante 5 minutos continuos (300 segundos) para permitir validaciones de humo o pruebas automatizadas de integración.
4. Apagado y Limpieza Automática: Transcurridos los 5 minutos, el pipeline envía señales de terminación a los procesos Java y destruye el entorno efímero de forma limpia.

---

## 8. Guía de Ejecución Local

### Prerrequisitos
* Java 21 o superior
* Git

### Pasos para levantar la plataforma completa

Abre cuatro terminales independientes y ejecuta en cada una:

# Terminal 1 - Microservicio de Cuentas
cd account-service
./mvnw quarkus:dev

# Terminal 2 - Microservicio de Pagos
cd payment-service
./mvnw quarkus:dev

# Terminal 3 - Microservicio de Notificaciones
cd notification-service
./mvnw quarkus:dev

# Terminal 4 - Orquestador SAGA
cd saga-orchestrator
./mvnw quarkus:dev

### Ejecutar la suite de pruebas unitarias
./mvnw test -f saga-orchestrator/pom.xml