# Reactive Distributed SAGA Orchestrator with Quarkus & Mutiny

Este proyecto es una plataforma bancaria distribuida que gestiona transferencias de dinero entre cuentas usando microservicios desacoplados. Cuando un usuario solicita mover saldo de una cuenta A a una cuenta B, el sistema no bloquea las bases de datos de forma tradicional; en su lugar, utiliza un Orquestador SAGA Reactivo que coordina 3 pasos secuenciales:

1. Débito: Retira el dinero de la cuenta de origen.
2. Crédito: Deposita el dinero en la cuenta de destino.
3. Comprobante: Emite una notificación digital de la operación.

¿Qué sucede si algo falla? (Rollback LIFO):
Si el servicio de notificaciones se cae o rechaza la emisión, el sistema ejecuta una compensación automática e inmediata en orden inverso: revierte primero el abono en la cuenta de destino y luego restaura el dinero en la cuenta de origen. De este modo, el sistema garantiza consistencia contable exacta sin dejar saldos colgados ni inconsistencias entre servicios.

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
* Reactive Worker Offloading: Las lecturas y escrituras JPA/Panache hacia la base de datos se delegan al pool de workers mediante runSubscriptionOn(Infrastructure.getDefaultWorkerPool()) para no bloquear el Event Loop de Vert.x.
* State Machine & Idempotency: Cada transacción se persiste en estado PENDING, transicionando atómicamente a COMPLETED o COMPENSATED con su correspondiente registro de auditoría.

---

## 2. Tecnologías y Versiones

* Lenguaje: Java 25 (OpenJDK / Oracle GraalVM compatible)
* Framework: Quarkus 3.39.5
* Librería Reactiva: SmallRye Mutiny
* ORM & Persistencia: Hibernate ORM con Panache (JPA)
* Bases de Datos:
  * Producción / Desarrollo: PostgreSQL Serverless en la nube (Neon Cloud)
  * Pruebas Unitarias / CI: Base de datos H2 en memoria (%test)
* Documentación de APIs: SmallRye OpenAPI 3.1 y Swagger UI
* Herramientas de Validación: JUnit 5, Mockito, REST-Assured y Bruno CLI

---

## 3. Microservicios y Componentes

| Microservicio | Puerto Local | Base de Datos | Rol en la Arquitectura |
|---|---|---|---|
| saga-orchestrator | 8080 | PostgreSQL (saga_db) | Orquestador central, auditoría, compensación y streaming SSE. |
| account-service | 8081 | PostgreSQL (account_db) | Débito de saldos y reversa de débitos en cuenta origen. |
| payment-service | 8082 | PostgreSQL (payment_db) | Acreditación y reversa de créditos en cuenta destino. |
| notification-service | 8083 | En memoria / Mock | Emisión de comprobantes digitales y punto de inyección de fallas. |

---

## 4. Configuración de Base de Datos y Credenciales

### Entornos y Perfiles de Base de Datos
* Perfil de Pruebas (%test):
  Configurado en application.properties con jdbc:h2:mem:orchestrator_test;DB_CLOSE_DELAY=-1 y estrategia drop-and-create. Permite que las pruebas unitarias y de integración en CI se ejecuten de forma aislada y veloz sin requerir red externa ni credenciales reales.
* Perfil de Producción / Desarrollo (prod, dev):
  Se conecta a un clúster serverless de PostgreSQL provisto por Neon Cloud utilizando canales cifrados con TLS (sslmode=require).

### Ubicación y Gestión de Credenciales
Por estándares de seguridad, ninguna credencial se guarda en el código fuente. Se inyectan mediante variables de entorno en el sistema o mediante un archivo .env en la raíz (ignorado por Git):

* Variable de URL de conexión:
  DB_JDBC_URL=jdbc:postgresql://<neon-subdomain>.neon.tech/<database>?sslmode=require
* Variable de Usuario:
  DB_USERNAME=<usuario_neon>
* Variable de Contraseña:
  DB_PASSWORD=<password_neon>

---

## 5. Endpoints y Catálogo de APIs

### Swagger UI y Documentación Interactiva
La documentación interactiva OpenAPI/Swagger se encuentra activa en el microservicio orquestador (8080):

* URL en GitHub Codespaces:
  https://crispy-guacamole-9g655v7rpx9hx775-8080.app.github.dev/q/swagger-ui
* URL en Localhost:
  http://localhost:8080/q/swagger-ui
* Especificación OpenAPI (JSON):
  https://crispy-guacamole-9g655v7rpx9hx775-8080.app.github.dev/q/openapi

### Catálogo de Rutas (saga-orchestrator)

1. Transferencia Exitosa (Happy Path)
* Método y Ruta: POST /api/transfers
* Header: Content-Type: application/json
* Payload de Solicitud:
  {
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 50.00,
    "simulateNotificationFailure": false
  }
* Respuesta Esperada (200 OK):
  {
    "sagaId": "219f8c8f-36e3-423b-8f61-30774743f10d",
    "status": "SUCCESS",
    "message": "Transferencia completada exitosamente bajo orquestación SAGA."
  }

2. Transferencia con Falla y Compensación (Rollback LIFO)
* Método y Ruta: POST /api/transfers
* Header: Content-Type: application/json
* Payload de Solicitud:
  {
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 50.00,
    "simulateNotificationFailure": true
  }
* Respuesta Esperada (409 Conflict):
  {
    "sagaId": "219f8c8f-36e3-423b-8f61-30774743f10d",
    "status": "FAILED_AND_COMPENSATED",
    "message": "Fallo reactivo. La compensación SAGA restauró saldos y comprobantes vía HTTP (Reactivo Uni)."
  }

3. Consultas de Estado y Auditoría
* Consultar Cuentas Consolidadas:
  GET /api/accounts
  Consulta a través del orquestador el estado y saldo de todas las cuentas delegando la petición a account-service.
* Consultar Historial de Transacciones SAGA:
  GET /api/sagas
  Lista todas las sagas procesadas, sus identificadores UUID, estados finales (COMPLETED o COMPENSATED) y mensajes de error si los hubo.
* Consultar Trazabilidad y Auditoría:
  GET /api/audits
  Muestra el log cronológico de cada paso ejecutado (DEBITED, CREDITED, RECEIPT_ISSUED) y los pasos de compensación revertidos.
* Streaming de Eventos Reactivos (SSE):
  GET /api/transfers/stream
  Transmite en tiempo real eventos de transferencias mediante Server-Sent Events con Multi<String>.

---

## 6. Pruebas Automatizadas de API con Bruno

La carpeta bruno-collection/ contiene todas las pruebas parametrizadas para validar el comportamiento del clúster.

### Estructura de la Colección
bruno-collection/
├── bruno.json
├── collection.bru
├── environments/
│   ├── Localhost.bru
│   └── Codespaces.bru
├── 01-Saga-Orchestrator/
│   ├── 01-Execute-Transfer-Success.bru
│   ├── 02-Execute-Transfer-Failure-Compensation.bru
│   ├── 03-List-Accounts.bru
│   ├── 04-List-Sagas-History.bru
│   ├── 05-List-Audits.bru
│   └── 06-Stream-Saga-Events-SSE.bru
└── 02-Direct-Microservices-Testing/
    ├── Account-Service/
    ├── Payment-Service/
    └── Notification-Service/

### Instrucciones de Uso en Bruno
1. Abre la aplicación de escritorio de Bruno.
2. Haz clic en Open Collection y selecciona la carpeta bruno-collection/ del repositorio.
3. En la esquina superior derecha, selecciona el entorno:
   * Codespaces: Apunta a las URLs públicas generadas por GitHub (https://crispy-guacamole-...app.github.dev).
   * Localhost: Apunta a las direcciones locales directas (http://localhost:8080, etc.).

---

## 7. Pipeline de CI/CD (GitHub Actions)

El archivo .github/workflows/ci-cd.yml automatiza la integración continua y el despliegue efímero en cada push o pull_request a la rama main.

                    [ git push origin main ]
                               │
                               ▼
        ┌─────────────────────────────────────────────┐
        │        ETAPA 1: Integración Continua (CI)   │
        │ - Configuración de entorno Java 25          │
        │ - Ejecución de pruebas unitarias (mvn test) │
        │ - Empaquetado Fast-JAR de la aplicación     │
        └──────────────────────┬──────────────────────┘
                               │ (Aprobado sin errores)
                               ▼
        ┌─────────────────────────────────────────────┐
        │        ETAPA 2: Despliegue Continuo (CD)    │
        │ - Localización y arranque de quarkus-run.jar│
        │ - Verificación de Health Check (/q/openapi) │
        │ - Entorno activo durante 5 minutos (300s)   │
        │ - Apagado controlado automático del runner  │
        └─────────────────────────────────────────────┘

### Comportamiento del Pipeline ante un Push
1. Validación Automática de Tests: Al subir cambios, GitHub Actions descarga el JDK 25 y corre la suite completa de pruebas unitarias. Si una sola aserción falla, el pipeline se detiene inmediatamente con código de error, previniendo despliegues rotos.
2. Construcción del Binario: Si los tests son satisfactorios, genera el paquete de ejecución optimizado en target/quarkus-app/quarkus-run.jar.
3. Despliegue Efímero de 5 Minutos: El runner arranca la aplicación en background, valida la conectividad en el puerto 8080 y mantiene los servicios encendidos exactamente 5 minutos (300 segundos) para permitir verificaciones en vivo o smoke tests.
4. Terminación y Limpieza: Cumplido el tiempo, el runner envía una señal de apagado ordenado (kill) a los procesos Java y libera los recursos de cómputo automáticamente.

---

## 8. Guía de Ejecución en Entorno Local

### Prerrequisitos
* Java 25 (o Java 21 configurando el compilador correspondiente)
* Git instalado

### Puesta en Marcha de los Microservicios
Para probar el flujo distribuido de forma local, abre cuatro pestañas de terminal y ejecuta los comandos correspondientes:

# Terminal 1: Account Service (Puerto 8081)
cd account-service
./mvnw quarkus:dev

# Terminal 2: Payment Service (Puerto 8082)
cd payment-service
./mvnw quarkus:dev

# Terminal 3: Notification Service (Puerto 8083)
cd notification-service
./mvnw quarkus:dev

# Terminal 4: Saga Orchestrator (Puerto 8080)
cd saga-orchestrator
./mvnw quarkus:dev

### Ejecución de Pruebas Unitarias
Para correr la suite de pruebas unitarias localmente:
./mvnw test