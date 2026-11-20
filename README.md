# TP-Final---Sistemas-Distribu-dos

Implementação de um chat distribuído em tempo real para o Trabalho Prático de Sistemas Distribuídos.

## Estrutura

- `services/auth-service`: cadastro, login, emissão e validação de JWT.
- `services/chat-service`: criação/listagem de conversas, histórico, envio STOMP/WebSocket e distribuição de eventos via Redis.
- `frontend`: aplicação React/TypeScript para autenticação, conversas e mensagens em tempo real.
- `infra`: execução local, banco de dados, Redis e balanceamento (em implementação).
- `load-tests`: cenários de carga (em implementação).
- `plano-implementacao-chat-distribuido.md`: decisões arquiteturais e plano de execução do grupo.

## Estado da implementação

O serviço de autenticação implementa cadastro e login, persistência de contas no PostgreSQL, emissão de JWT, consulta do usuário autenticado, validação de entrada e testes unitários. O serviço de chat implementa conversas privadas e em grupo, histórico paginado, autorização por participação, WebSocket/STOMP e propagação de eventos por Redis.

Consulte [services/auth-service/README.md](services/auth-service/README.md) para configurar e executar o serviço de autenticação.
Consulte [services/chat-service/README.md](services/chat-service/README.md) para os endpoints do chat, o protocolo WebSocket e a execução integrada.

Para executar o stack e os bancos localmente com Docker Compose, copie `.env.example` para `.env`, configure as senhas de banco e gere `AUTH_JWT_SECRET_BASE64` conforme as instruções do README do serviço de chat. Em seguida, execute `docker compose -f infra/compose.yaml up --build`.

Com os serviços ativos, instale as dependências e inicie a interface em outro terminal:

```powershell
npm --prefix frontend install
npm --prefix frontend run dev
```

Acesse `http://localhost:5173`. Durante o desenvolvimento, o Vite encaminha as chamadas HTTP e WebSocket ao gateway local em `http://localhost:8080`. Para validar o frontend, execute `npm --prefix frontend test` e `npm --prefix frontend run build`.