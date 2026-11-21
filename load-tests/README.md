O cenário desta pasta verifica o chat sob concorrência. Ele registra 100 usuários virtuais por padrão, cria 20 grupos e envia mensagens por STOMP/WebSocket enquanto consulta o histórico por HTTP.

## Pré-requisitos

- Docker e Docker Compose ativos.
- k6 instalado no sistema ou disponível em um contêiner.
- Stack iniciada em `http://localhost:8080`.
- Arquivo `.env` configurado na raiz do repositório.

## Executar

Inicie o stack com duas réplicas do serviço de chat:

```powershell
docker compose -f infra/compose.yaml up --build --scale chat-service=2
```

Depois execute:

```powershell
k6 run load-tests/chat-load.js
```

Variáveis úteis:

```text
USER_COUNT=100
GROUP_COUNT=20
MESSAGE_COUNT=5
BASE_URL=http://localhost:8080
WS_URL=ws://localhost:8080/ws
```

Exemplo alterando os limites do cenário:

```powershell
k6 run -e USER_COUNT=100 -e GROUP_COUNT=20 -e MESSAGE_COUNT=5 load-tests/chat-load.js
```

O script cria usuários com emails exclusivos de carga. Para uma execução repetida, ele aceita o registro já existente e realiza login. Os resultados devem ser preservados em CSV ou HTML junto ao relatório do trabalho. O comando não define um SLO que não tenha sido solicitado: ele registra erros HTTP, erros WebSocket, latências e resposta do histórico.