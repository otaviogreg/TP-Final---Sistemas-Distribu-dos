# Contratos da API

## Autenticação

Base local direta: `http://localhost:8081`. Pelo gateway: `http://localhost:8080`.

### `POST /api/v1/auth/register`

Cadastro público. Requer:

```json
{
  "displayName": "Ana Silva",
  "email": "ana@example.com",
  "password": "senha-forte-123"
}
```

Retorna `201` com `id`, `displayName`, `email` e `createdAt`. A senha nunca é retornada.

### `POST /api/v1/auth/login`

Login público. Requer `email` e `password`; retorna `accessToken`, `tokenType` (`Bearer`), `expiresIn` em segundos e o perfil público do usuário.

Todas as rotas abaixo exigem `Authorization: Bearer <accessToken>`.

### `GET /api/v1/users/me`

Retorna o perfil público associado ao token.

### `GET /api/v1/users?query=ana`

Busca até 50 participantes por nome de exibição ou e-mail. Retorna somente `id` e `displayName`.

### `POST /api/v1/users/validate`

Uso interno do chat para confirmar IDs antes de criar conversas:

```json
{
  "userIds": ["0d1828b5-1e4c-44d0-babc-5af54ec437cf"]
}
```

Retorna `204` se todos os IDs existem ou `404` se algum não existir.

## Conversas e histórico

Base local direta: `http://localhost:8082`. Pelo gateway: `http://localhost:8080`.

### `POST /api/v1/conversations`

Cria conversa. O usuário do token é incluído automaticamente. Para conversa privada, `participantIds` deve conter exatamente um outro usuário:

```json
{
  "type": "PRIVATE",
  "participantIds": ["0d1828b5-1e4c-44d0-babc-5af54ec437cf"]
}
```

Para grupo, é obrigatório fornecer nome e ao menos um outro participante:

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

Retorna `201` com `id`, `type`, `name`, `createdBy`, `participantIds`, `createdAt` e `updatedAt`.

### `GET /api/v1/conversations`

Retorna as conversas do usuário autenticado, ordenadas por atividade recente.

### `GET /api/v1/conversations/{conversationId}/messages?page=0&size=50`

Retorna uma página de histórico. `size` aceita de 1 a 100; a página `0` contém as mensagens mais recentes, ordenadas cronologicamente dentro da página. A resposta contém `messages`, `page`, `size` e `hasNext`.

## WebSocket/STOMP

Conexão: `/ws`. Envie `Authorization: Bearer <accessToken>` no quadro STOMP `CONNECT`.

| Operação | Destino |
|---|---|
| Assinar mensagens | `/topic/conversations/{conversationId}` |
| Enviar mensagem | `/app/conversations/{conversationId}/messages` |

Corpo de envio:

```json
{
  "content": "Olá!"
}
```

Evento entregue:

```json
{
  "id": "5b2eb195-bb3e-4675-97cd-834adf426bd9",
  "conversationId": "b64d88f5-77fc-4dcf-aa93-a5bb3c5a1113",
  "senderId": "0d1828b5-1e4c-44d0-babc-5af54ec437cf",
  "content": "Olá!",
  "createdAt": "2026-11-20T14:00:00Z"
}
```

O cliente não envia `senderId`; o serviço o obtém da identidade autenticada. Envio e assinatura são permitidos somente a participantes da conversa.

## Erros comuns

| Status | Significado |
|---|---|
| `400` | JSON ou campos inválidos, mensagem vazia, conversa privada/grupo malformado |
| `401` | Token ausente, inválido ou expirado |
| `404` | Conversa inacessível/inexistente ou participante não encontrado |
| `409` | E-mail já cadastrado no serviço de autenticação |

Erros REST são retornados como `ProblemDetail`; em mensagens STOMP, violações de autorização são rejeitadas no canal de entrada.