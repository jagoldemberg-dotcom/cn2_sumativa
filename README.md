# Sumativa Cloud Native II - REST + GraphQL + AWS + Azure

Proyecto académico para la actividad **"Añadiendo comunicación REST y GraphQL"**.

## Arquitectura final

```text
Postman / Cliente
       |
       v
AWS EC2
+------------------------------------------------------+
| Docker                                               |
|                                                      |
|  bff-service (Spring Boot :8080)                    |
|      | REST usuarios                                 |
|      +-----------------------------> Azure Function  |
|      |                               users-function  |
|      |                               /api/users/**   |
|      |                                               |
|      | GraphQL roles                                 |
|      +-----------------------------> Azure Function  |
|                                      roles-function  |
|                                      /api/graphql    |
|                                                      |
|  Oracle XE Docker :1521 <----------------------------+
|      USERS / ROLES / USER_ROLES                     |
+------------------------------------------------------+
```

Las Azure Functions acceden a Oracle mediante JDBC usando la IP pública o DNS público de la EC2 y el puerto 1521 autorizado en el Security Group para las IP de salida de Azure Functions.

## Qué se implementó

- `users-function`: Azure Function Java con comunicación **REST** y CRUD de usuarios.
- `roles-function`: Azure Function Java con endpoint **GraphQL** y CRUD de roles.
- `bff-service`: Spring Boot desplegable en Docker. Mantiene una API REST para el cliente y traduce las operaciones de roles a GraphQL.
- `oracle-db`: Oracle XE 21 Slim dockerizado en la misma EC2 que el BFF.
- Corrección de inicialización Oracle: los scripts cambian a `XEPDB1` y al esquema `SUMATIVA` antes de crear tablas y datos.
- `docker-compose.yml`: levanta Oracle + BFF en la EC2.
- `postman/`: colección de evidencia para REST, GraphQL y BFF.
- `GUIA_DESPLIEGUE_AWS_AZURE.md`: despliegue paso a paso.
- `GUIA_VIDEO_EVIDENCIA.md`: orden sugerido para la grabación.

## Endpoints importantes

### users-function - REST

```text
GET    /api/users
GET    /api/users/{id}
POST   /api/users
PUT    /api/users/{id}
DELETE /api/users/{id}
POST   /api/users/{id}/roles/{roleId}
DELETE /api/users/{id}/roles/{roleId}
```

### roles-function - GraphQL

```text
POST /api/graphql
```

Ejemplo:

```json
{
  "query": "query { roles { id name description } }"
}
```

Schema implementado:

```graphql
type Role {
  id: ID!
  name: String!
  description: String
}

type Query {
  roles: [Role!]!
  role(id: ID!): Role
}

type Mutation {
  createRole(name: String!, description: String): Role!
  updateRole(id: ID!, name: String!, description: String): Role
  deleteRole(id: ID!): Boolean!
}
```

### BFF en AWS EC2

```text
GET    /api/health
GET    /api/users
GET    /api/users/{id}
POST   /api/users
PUT    /api/users/{id}
DELETE /api/users/{id}

GET    /api/roles
GET    /api/roles/{id}
POST   /api/roles
PUT    /api/roles/{id}
DELETE /api/roles/{id}
```

Para el cliente el BFF sigue siendo REST. Internamente, usuarios se resuelve vía REST y roles vía GraphQL.

## Prueba local rápida

1. Levantar Oracle:

```bash
docker compose up -d oracle-db
```

2. Users Function:

```bash
cd functions/users-function
cp local.settings.json.example local.settings.json
mvn clean package
mvn azure-functions:run
```

3. Roles Function:

```bash
cd functions/roles-function
cp local.settings.json.example local.settings.json
mvn clean package
mvn azure-functions:run
```

4. BFF:

```bash
docker compose up -d --build bff-service
```

5. Probar:

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/users
curl http://localhost:8080/api/roles
```

## Variables de entorno de producción

Copiar `.env.example` a `.env` en la EC2 y completar:

```text
ORACLE_PASSWORD=...
ORACLE_APP_USER=sumativa
ORACLE_APP_PASSWORD=...
FUNCTIONS_USERS_BASE_URL=https://<users-app>.azurewebsites.net/api
FUNCTIONS_ROLES_GRAPHQL_URL=https://<roles-app>.azurewebsites.net/api/graphql
```

En Azure Functions configurar en ambas apps:

```text
ORACLE_URL=jdbc:oracle:thin:@//<EC2_PUBLIC_IP>:1521/XEPDB1
ORACLE_USER=sumativa
ORACLE_PASSWORD=<ORACLE_APP_PASSWORD>
```

## Despliegue

Seguir `GUIA_DESPLIEGUE_AWS_AZURE.md`.
