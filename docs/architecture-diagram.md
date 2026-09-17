# Diagrama de Arquitectura Final

```mermaid
flowchart LR
    Client["Postman / Cliente"] -->|REST| BFF["AWS EC2\nBFF Spring Boot\nDocker :8080"]

    BFF -->|REST| UsersFn["Azure Function\nusers-function\n/api/users/**"]
    BFF -->|GraphQL POST| RolesFn["Azure Function\nroles-function\n/api/graphql"]

    UsersFn -->|JDBC :1521| DB[("Oracle XE\nDocker en EC2\nXEPDB1 / SUMATIVA")]
    RolesFn -->|JDBC :1521| DB

    subgraph AWS["AWS Academy - EC2"]
      BFF
      DB
    end

    subgraph Azure["Microsoft Azure"]
      UsersFn
      RolesFn
    end
```

## Responsabilidades

- **BFF**: orquesta las llamadas y no accede directamente a Oracle.
- **users-function**: implementa REST para usuarios y asignación de roles.
- **roles-function**: implementa GraphQL para roles.
- **Oracle XE**: se ejecuta como contenedor en la misma máquina virtual EC2 que el BFF.
- **Postman**: se usa para evidenciar llamadas REST y GraphQL.
