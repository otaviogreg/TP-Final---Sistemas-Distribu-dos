# Plano de testes implementado

## Testes unitários

Executar em cada serviço:

```powershell
cd services/auth-service
mvn test

cd ../chat-service
mvn test
```

- Auth: normalização de e-mail, prevenção de duplicatas, emissão de token, rejeição de credenciais inválidas e campos inválidos.
- Chat: validação das regras de conversa, persistência de mensagem, autorização de participação e rejeição de destinos STOMP não permitidos.

## Integração do chat

O teste `RedisMessageIntegrationTest` usa Testcontainers com PostgreSQL e Redis. Ele inicia o serviço em uma porta aleatória, gera um JWT de teste, persiste uma conversa, conecta via STOMP, envia uma mensagem, verifica a entrega do evento e consulta o histórico persistido por HTTP. Também verifica acesso sem token e tentativa de leitura por um usuário que não participa.

Requisitos: Maven e Docker com daemon ativo. Sem Docker, Testcontainers ignora esse teste; nesse ambiente ficam sem validação real as integrações PostgreSQL/Redis e o fluxo WebSocket ponta a ponta.

## Cenários manuais com Compose

1. Criar duas contas via `/api/v1/auth/register` e fazer login para obter seus tokens/IDs.
2. Pesquisar o segundo usuário com `GET /api/v1/users?query=...`.
3. Criar uma conversa privada e um grupo via `POST /api/v1/conversations`.
4. Consultar a lista e o histórico de cada usuário; verificar que um usuário externo recebe `404`.
5. Conectar dois clientes STOMP ao mesmo destino de conversa e verificar envio/recebimento em tempo real.
6. Subir duas instâncias do chat com `docker compose -f infra/compose.yaml up --build --scale chat-service=2`, conectar cada participante a uma instância via gateway e confirmar a entrega por Redis Pub/Sub.

## Limites atuais

- Não há ainda script k6 nem evidência de 100 usuários simultâneos e 20 grupos.
- Redis Pub/Sub não mantém mensagens para consumidores desconectados; o PostgreSQL mantém o histórico para recuperação.
- O Compose executa PostgreSQL, Redis e Nginx como instâncias únicas; a entrega local demonstra escalabilidade da aplicação, não alta disponibilidade completa das dependências.