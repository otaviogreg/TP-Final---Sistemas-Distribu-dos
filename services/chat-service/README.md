# Serviço de chat

Serviço responsável por criar e listar conversas, persistir mensagens e entregá-las em tempo real. O serviço valida JWTs emitidos pelo `auth-service`; mantém conversas e mensagens no seu próprio banco PostgreSQL; e usa Redis Pub/Sub para propagar eventos entre instâncias.

## Requisitos

- JDK 21 e Maven 3.9 ou compatível para execução local
- Docker e Docker Compose para iniciar o stack recomendado e executar testes de integração
- PostgreSQL e Redis acessíveis pelo serviço

As tabelas são criadas pelo Flyway. O Hibernate valida o esquema ao iniciar e não cria ou altera tabelas.

## Executar o stack

Na raiz do repositório, copie `.env.example` para `.env`, defina senhas de banco e gere uma chave JWT Base64 aleatória com pelo menos 32 bytes. Em PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:AUTH_JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Grave o valor gerado em `AUTH_JWT_SECRET_BASE64` no `.env` local. Use exatamente o mesmo valor para o serviço de autenticação e para o chat. Não versione `.env` nem reutilize uma chave local em produção.

Suba o ambiente com:

```powershell
docker compose -f infra/compose.yaml up --build
```

O Nginx disponibiliza a API em `http://localhost:8080`. PostgreSQL de autenticação fica exposto localmente na porta `5432`; PostgreSQL do chat, na `5433`; Redis, na `6379`. O serviço de chat não é publicado diretamente no host: o acesso passa pelo gateway.

Para executar só o chat diretamente com Maven, configure `CHAT_DB_URL`, `CHAT_DB_USERNAME`, `CHAT_DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `AUTH_JWT_ISSUER` e `AUTH_JWT_SECRET_BASE64`, e rode:

```powershell
cd services/chat-service
mvn spring-boot:run
```

## API HTTP

As rotas, exceto health check, exigem `Authorization: Bearer <accessToken>`, usando o token retornado pelo `auth-service`.

### Criar conversa

`POST /api/v1/conversations`

O usuário autenticado é incluído automaticamente. `participantIds` contém os IDs dos outros participantes.

Conversa privada (um participante adicional):

```json
{
  "type": "PRIVATE",
  "participantIds": ["0d1828b5-1e4c-44d0-babc-5af54ec437cf"]
}
```

Grupo:

```json
{
  "type": "GROUP",
  "name": "Equipe",
  "participantIds": [
    "0d1828b5-1e4c-44d0-babc-5af54ec437cf",
    "d58a819c-57dd-4f4a-9ea1-3d6c3e4eea29"
  ]
}
```

O serviço rejeita IDs repetidos, o próprio ID do usuário na lista, conversa privada que não tenha exatamente duas pessoas, grupo sem nome e participantes além do limite de 100 pessoas. Antes de persistir, o chat valida os IDs adicionais no serviço de autenticação usando o token do usuário que fez a requisição. A busca autenticada `GET /api/v1/users?query=ana` no auth-service retorna até 50 opções com ID e nome de exibição para selecionar participantes.

### Listar conversas

`GET /api/v1/conversations`

Retorna as conversas das quais o usuário autenticado participa, ordenadas pela atividade mais recente. Os dados incluem tipo, nome, criador, participantes e datas de criação/atualização.

### Adicionar participantes a um grupo

`POST /api/v1/conversations/{conversationId}/participants`

Somente o criador de uma conversa do tipo `GROUP` pode adicionar pessoas. O serviço valida os IDs no serviço de autenticação, rejeita participantes já existentes e mantém o limite de 100 pessoas no grupo. Conversas privadas não podem receber participantes adicionais.

```json
{
  "participantIds": ["0d1828b5-1e4c-44d0-babc-5af54ec437cf"]
}
```

Uma resposta `200 OK` retorna a conversa atualizada com a lista de participantes. Membros comuns recebem `403 Forbidden`; usuários que não participam da conversa recebem `404 Not Found`.

### Consultar histórico

`GET /api/v1/conversations/{conversationId}/messages?page=0&size=50`

Retorna mensagens da conversa em ordem cronológica dentro da página, junto com os metadados de paginação. A página `0` contém as mensagens mais recentes (até 100 por página). Usuários que não participam da conversa recebem `404 Not Found`, sem exposição da existência de conversas privadas.

## WebSocket/STOMP

O endpoint é `/ws`. O token é enviado no quadro STOMP `CONNECT` pelo cabeçalho nativo `Authorization: Bearer <accessToken>`.

- **Assinar uma conversa:** `/topic/conversations/{conversationId}`
- **Enviar uma mensagem:** `/app/conversations/{conversationId}/messages`
- **Corpo enviado:** `{"content":"Olá!"}`
- **Evento entregue:** objeto com `id`, `conversationId`, `senderId`, `content` e `createdAt`.

O servidor deriva o remetente do token e verifica a participação tanto ao enviar quanto ao assinar. Não aceite `senderId` fornecido pelo cliente como identidade.

## Persistência e distribuição entre réplicas

O PostgreSQL é a fonte durável para conversas, participantes e mensagens. O serviço grava e confirma a transação antes de publicar o evento Redis. Cada réplica recebe o evento e o envia às conexões WebSocket locais.

Redis Pub/Sub não guarda eventos para consumidores desconectados. Se o Redis ou uma réplica estiver indisponível, a interface deve buscar o histórico persistido após reconectar. A entrega em tempo real entre réplicas depende de todas compartilharem a mesma instância Redis, banco do chat, chave e emissor JWT.

## Testes

```powershell
mvn test
```

Os testes unitários cobrem criação de conversas e validação/envio de mensagens. O teste de integração usa PostgreSQL e Redis em Testcontainers para iniciar o serviço, autenticar, criar uma conversa, enviar uma mensagem pelo WebSocket, receber o evento Redis e ler a mensagem persistida pela API. Docker precisa estar em execução; o teste de integração é ignorado quando não há daemon Docker.
