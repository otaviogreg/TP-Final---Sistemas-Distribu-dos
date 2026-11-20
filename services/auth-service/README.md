# Serviço de autenticação

Serviço responsável pelo cadastro, login e consulta dos dados do usuário autenticado. O serviço persiste as contas no PostgreSQL e emite JWTs assinados com HMAC SHA-256.

## Requisitos

- JDK 21
- Maven 3.9 ou compatível
- PostgreSQL acessível pelo serviço, com o banco `auth_db` criado

O Flyway cria a tabela de usuários ao iniciar o serviço. O Hibernate valida o esquema, mas não cria nem altera tabelas automaticamente.

## Configuração

Configure estas variáveis antes de iniciar o serviço:

| Variável | Obrigatória | Padrão | Descrição |
|---|---:|---|---|
| `AUTH_SERVICE_PORT` | Não | `8081` | Porta HTTP do serviço |
| `AUTH_DB_URL` | Não | `jdbc:postgresql://localhost:5432/auth_db` | URL JDBC do banco de autenticação |
| `AUTH_DB_USERNAME` | Não | `postgres` | Usuário do PostgreSQL |
| `AUTH_DB_PASSWORD` | Não | `postgres` | Senha do PostgreSQL |
| `AUTH_JWT_ISSUER` | Não | `tp-chat-auth` | Emissor incluído nos tokens |
| `AUTH_JWT_SECRET_BASE64` | Sim | — | Chave HMAC codificada em Base64, com pelo menos 32 bytes antes da codificação |
| `AUTH_JWT_EXPIRATION_SECONDS` | Não | `3600` | Validade do token em segundos |
| `AUTH_CORS_ALLOWED_ORIGINS` | Não | `http://localhost:5173` | Origens permitidas, separadas por vírgula |

Para executar com Docker Compose, copie `.env.example` para `.env` na raiz do repositório, defina `AUTH_DB_PASSWORD` e gere uma chave aleatória para `AUTH_JWT_SECRET_BASE64`. No PowerShell, gere a chave com:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:AUTH_JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Configure as variáveis do banco conforme seu PostgreSQL e mantenha a chave fora do repositório. A chave precisa ser a mesma em todas as réplicas do serviço e no serviço de chat que validar os tokens. Não reutilize uma chave de desenvolvimento em produção.

Com Docker instalado, inicie o banco e o serviço a partir da raiz do repositório:

```powershell
docker compose -f infra/compose.yaml up --build
```

O Compose publica a API em `http://localhost:8081` e mantém os dados do PostgreSQL no volume `auth-db-data`.

## Executar

Com o PostgreSQL iniciado, o banco `auth_db` criado e as variáveis configuradas:

```powershell
cd services/auth-service
mvn spring-boot:run
```

Execute os testes unitários com:

```powershell
mvn test
```

O endpoint de saúde fica disponível em `GET /actuator/health`.

## API implementada

### Cadastro

`POST /api/v1/auth/register`

```json
{
  "displayName": "Ana Silva",
  "email": "ana@example.com",
  "password": "senha-com-no-minimo-8-caracteres"
}
```

Retorna `201 Created` com os dados públicos do usuário. E-mails são normalizados para minúsculas. Uma tentativa de cadastrar um e-mail já usado retorna `409 Conflict`. A senha nunca é incluída na resposta e é persistida como hash BCrypt.

### Login

`POST /api/v1/auth/login`

```json
{
  "email": "ana@example.com",
  "password": "senha-com-no-minimo-8-caracteres"
}
```

Retorna `200 OK` com `accessToken`, `tokenType`, `expiresIn` e os dados públicos do usuário. Credenciais inválidas retornam `401 Unauthorized` com uma mensagem genérica.

### Usuário autenticado

`GET /api/v1/users/me`

Requer o cabeçalho `Authorization: Bearer <accessToken>` e retorna os dados públicos do titular do token. A rota é protegida pelo Spring Security Resource Server e valida assinatura, emissor e expiração do JWT.

### Buscar participantes

`GET /api/v1/users?query=ana`

Requer autenticação e retorna até 50 usuários com correspondência por nome de exibição ou e-mail. A resposta contém apenas ID e nome de exibição para permitir seleção de participantes sem expor os e-mails.

### Validar participantes

`POST /api/v1/users/validate`

Requer autenticação e recebe `{"userIds":["<uuid>"]}`. Retorna `204 No Content` quando todos os IDs existem e `404 Not Found` caso contrário. O chat usa esta operação antes de persistir uma conversa.

## Testes

Os testes unitários cobrem normalização de e-mail, prevenção de cadastro duplicado, login válido e rejeição de credenciais inválidas. O teste de JWT verifica emissão e validação com a chave e o emissor configurados.
