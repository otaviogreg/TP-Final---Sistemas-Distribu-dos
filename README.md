# TP-Final---Sistemas-Distribu-dos

Implementação de um chat distribuído em tempo real para o Trabalho Prático de Sistemas Distribuídos.

## Estrutura

- `services/auth-service`: cadastro, login, emissão e validação de JWT.
- `services/chat-service`: serviço de conversas e mensagens (em implementação).
- `frontend`: interface web (em implementação).
- `infra`: execução local, banco de dados, Redis e balanceamento (em implementação).
- `load-tests`: cenários de carga (em implementação).
- `plano-implementacao-chat-distribuido.md`: decisões arquiteturais e plano de execução do grupo.

## Estado da implementação

O serviço de autenticação implementa cadastro e login, persistência de contas no PostgreSQL, emissão de JWT, consulta do usuário autenticado, validação de entrada e testes unitários. Os demais componentes ainda são estrutura inicial.

Consulte [services/auth-service/README.md](services/auth-service/README.md) para configurar e executar o serviço de autenticação.

Para executar o serviço e o banco localmente com Docker Compose, copie `.env.example` para `.env`, configure `AUTH_DB_PASSWORD` e gere `AUTH_JWT_SECRET_BASE64` conforme as instruções do README do serviço. Em seguida, execute `docker compose -f infra/compose.yaml up --build`.