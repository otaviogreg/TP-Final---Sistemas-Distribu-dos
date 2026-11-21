# Plano de ação do grupo — Chat distribuído em tempo real

**Contexto:** Trabalho Prático de Sistemas Distribuídos  
**Objetivo do grupo:** construir uma plataforma de chat com autenticação, conversas privadas e em grupo, persistência, comunicação em tempo real e execução com réplicas e balanceamento de carga.

## 1. Resumo da proposta

Organizaremos o projeto em um monorepositório com dois serviços de backend executáveis e escaláveis de forma independente, além do frontend:

```text
Navegador
   |
   v
Nginx (roteamento HTTP e balanceamento de conexões WebSocket)
   |                         |
   v                         v
Serviço de autenticação     Serviço de chat (2+ réplicas)
   |                         |             |
   v                         v             v
PostgreSQL                 PostgreSQL     Redis Pub/Sub
   (auth_db)                 (chat_db)    (eventos entre réplicas)
```

Adotaremos React no frontend. O serviço de autenticação cuidará do cadastro e login e emitirá tokens JWT. O serviço de chat validará esses tokens, persistirá conversas e mensagens e manterá conexões WebSocket com os clientes. Como uma conversa pode ter participantes conectados a réplicas distintas, usaremos Redis para distribuir eventos entre as instâncias do chat.

Nossa implementação priorizará uma execução e testes reproduzíveis em ambiente local, deixando explícitos os conceitos distribuídos e os limites da arquitetura. Múltiplas réplicas de aplicação não tornam automaticamente o sistema inteiro altamente disponível: uma única instância de PostgreSQL, Redis ou Nginx continua sendo um ponto único de falha. Registramos esse limite e a possibilidade de ampliá-lo na seção 6.

## 2. Ferramentas e decisões

Adotaremos as tecnologias abaixo. No início da implementação, fixaremos versões estáveis e compatíveis entre si para que todos os integrantes executem o mesmo ambiente.

| Tecnologia | Uso no projeto |
|---|---|
| Java 21 LTS | Linguagem dos serviços de backend |
| Spring Boot | Framework e execução dos microsserviços |
| Spring Security e JWT | Autenticação HTTP e autorização de conexões |
| Spring WebSocket com STOMP | Comunicação persistente e eventos de chat |
| Maven | Dependências, compilação e testes do backend |
| React + TypeScript + Vite | Interface web responsiva |
| PostgreSQL | Persistência transacional dos usuários, conversas e mensagens |
| Redis Pub/Sub | Distribuição de eventos entre réplicas do serviço de chat |
| Nginx | Roteamento e balanceamento de HTTP e WebSocket |
| Docker e Docker Compose | Empacotamento e execução local dos componentes |
| JUnit, Mockito e Testcontainers | Testes unitários e de integração do backend |
| Vitest e React Testing Library | Testes unitários de componentes e lógica do frontend |
| k6 | Teste de carga com usuários concorrentes e WebSockets |
| Git e GitHub | Histórico de código, colaboração e URL a incluir no relatório |

### 2.1 Java 21 LTS

Escolhemos Java 21 LTS para implementar os serviços. A versão oferece um runtime maduro e suporte amplo no ecossistema Spring e nas ferramentas de teste.

Essa decisão nos permite concentrar o trabalho nos desafios centrais — distribuição, entrega de mensagens e disponibilidade — aproveitando ferramentas conhecidas e documentação extensa.

### 2.2 Spring Boot

Implementaremos cada serviço como uma aplicação Spring Boot independente, com configuração e processo de execução próprios. Escolhemos o framework por integrar APIs REST, validação, segurança, persistência, WebSockets e testes no mesmo ecossistema.

**Estrutura planejada do repositório:**

```text
/
├── services/
│   ├── auth-service/
│   └── chat-service/
├── frontend/
├── infra/
│   ├── compose.yaml
│   └── nginx/
├── load-tests/
└── README.md
```

Manteremos os serviços no mesmo repositório para facilitar a colaboração e a entrega do trabalho. Ainda assim, cada serviço terá construção, configuração e execução independentes, preservando a separação entre os microsserviços.

### 2.3 Spring Security e JWT

Implementaremos a validação de credenciais e a emissão de JWT assinado no serviço de autenticação. O frontend enviará o token nas chamadas protegidas e ao abrir a conexão WebSocket. O chat verificará assinatura, validade e identidade localmente, sem consultar o serviço de autenticação em cada operação.

Essa escolha reduz a dependência síncrona entre os serviços. Não incluiremos senhas nem informações secretas no token. Armazenaremos senhas somente como hashes produzidos por um algoritmo apropriado, como BCrypt, e manteremos os segredos de assinatura em configuração externa, fora do Git.

Separaremos autenticação de autorização: antes de enviar uma mensagem ou assinar um tópico, o chat verificará se o usuário pertence à conversa. O servidor obterá a identidade do remetente a partir do token, sem confiar em um `senderId` enviado pelo cliente.

### 2.4 WebSocket com STOMP

Usaremos HTTP para login, cadastro e consulta do histórico. Para a troca em tempo real, o frontend abrirá uma conexão WebSocket persistente. Escolhemos STOMP para organizar publicação e assinatura por destinos e tornar explícito o contrato entre frontend e backend.

Definiremos um destino de publicação para enviar mensagens a uma conversa e destinos de assinatura para receber atualizações. Fixaremos seus nomes e os formatos dos eventos junto com os endpoints, antes de integrar frontend e backend.

**Regras que implementaremos:**

- validaremos o JWT ao abrir a conexão;
- conferiremos a participação do usuário ao publicar e assinar uma conversa;
- validaremos conteúdo e tamanho das mensagens;
- trataremos reconexões e carregaremos o histórico persistido;
- usaremos um identificador único por mensagem para o frontend poder ignorar duplicatas;
- manteremos em mente que cada conexão WebSocket termina em uma única réplica.

### 2.5 React, TypeScript e Vite

Escolhemos React para estruturar a interface em componentes — lista de conversas, histórico, formulário de envio e participantes. Usaremos TypeScript para tipar contratos de mensagens e estados da interface, e Vite para desenvolvimento e geração do pacote estático.

Manteremos a interface inicial focada nos fluxos exigidos, sem adicionar uma biblioteca visual extensa ou funcionalidades fora do escopo. Implementaremos um layout responsivo com lista de conversas, histórico e envio de mensagens.

O cliente WebSocket manterá a conexão autenticada e atualizará a conversa ativa ao receber eventos. Ao abrir uma conversa ou recuperar a conexão, consultaremos o histórico por HTTP para reconciliar mensagens persistidas com eventos eventualmente perdidos durante a desconexão.

### 2.6 PostgreSQL

Adotaremos PostgreSQL como fonte persistente de verdade. A modelagem relacional facilita manter a integridade entre conversas, participantes e mensagens e recuperar o histórico em ordem. Para a carga mínima de 100 usuários e 20 grupos, começaremos com um servidor PostgreSQL.

Para preservar a propriedade dos dados por serviço, manteremos bancos separados (`auth_db` e `chat_db`) no mesmo servidor durante o desenvolvimento. O serviço de autenticação administrará contas e credenciais. O chat armazenará IDs de usuário como referências lógicas, sem consultar ou alterar diretamente as tabelas de autenticação.

Modelaremos inicialmente as seguintes entidades:

- **Usuário:** identificador, nome de exibição, e-mail ou nome de login e hash da senha (propriedade do serviço de autenticação).
- **Conversa:** identificador, tipo (privada ou grupo), nome opcional do grupo e data de criação.
- **Participante:** identificador da conversa, identificador do usuário e data de entrada.
- **Mensagem:** identificador, conversa, remetente, conteúdo e data/hora de criação.

Representaremos conversas privadas como conversas com dois participantes e grupos como conversas com mais participantes. Consultaremos o histórico no banco, sem depender somente da memória das réplicas. Cada serviço aplicará migrações versionadas sobre os dados que administra.

### 2.7 Redis Pub/Sub

Uma conexão WebSocket termina em uma única réplica do chat. Sem coordenação, uma mensagem processada por uma réplica não chegaria aos participantes conectados a outra. Usaremos Redis Pub/Sub para distribuir eventos de mensagens e permitir que cada réplica os entregue às conexões locais interessadas.

Implementaremos o fluxo nesta ordem:

1. validar o remetente, a conversa e a participação;
2. persistir a mensagem no PostgreSQL;
3. publicar um evento contendo o ID da mensagem e os dados necessários para a entrega;
4. cada réplica inscrita encaminhar o evento aos clientes locais da conversa.

PostgreSQL será a fonte durável; Pub/Sub será o meio de notificação em tempo real. Como Pub/Sub não é uma fila durável e não garante reentrega durante indisponibilidade do Redis, implementaremos a recuperação de mensagens pelo histórico no PostgreSQL. Registraremos esse limite e, se a avaliação exigir entrega durável de eventos durante falhas do broker, planejaremos um incremento com uma fila durável.

### 2.8 Nginx

Usaremos Nginx como ponto de entrada para encaminhar requisições HTTP e WebSocket aos serviços correspondentes. Configuraremos os cabeçalhos de upgrade e conexão exigidos por WebSocket. O balanceador distribuirá novas conexões entre réplicas; cada conexão permanecerá associada à réplica que a aceitou.

Validaremos a configuração com conexões WebSocket reais, pois distribuir requisições REST não comprova o balanceamento dessas conexões. Na demonstração inicial, declararemos as réplicas explicitamente ou configuraremos a descoberta de instâncias de forma compatível com o Nginx escolhido.

### 2.9 Docker e Docker Compose

Usaremos Docker para empacotar serviços e dependências em ambientes reproduzíveis. Com Docker Compose, iniciaremos frontend, serviços, PostgreSQL, Redis e Nginx sem exigir instalação manual de cada componente no computador de quem avaliar o trabalho.

Usaremos Compose para comparar a execução com uma e com múltiplas réplicas e simular a parada de uma instância. Não o trataremos, por si só, como uma plataforma de alta disponibilidade.

### 2.10 Ferramentas de teste

- **JUnit e Mockito:** usaremos para testar regras isoladas, como validação de login, autorização de participantes e envio de mensagens.
- **Testcontainers:** usaremos PostgreSQL e Redis reais nos testes de integração, evitando depender somente de mocks para validar persistência e comunicação com o broker.
- **Vitest e React Testing Library:** usaremos para testar interações essenciais do frontend, como login, renderização de mensagens e estados da conexão.
- **k6:** usaremos para simular no mínimo 100 usuários virtuais autenticados, conectados e trocando mensagens em pelo menos 20 grupos. Registraremos erros, taxa de mensagens e latências observadas.

## 3. Fluxos que guiarão a implementação

### Login e conexão

1. O usuário envia credenciais ao serviço de autenticação.
2. O serviço valida a conta e retorna um JWT.
3. O frontend guarda o token somente no contexto apropriado da aplicação e o envia nas chamadas autorizadas.
4. O frontend abre a conexão WebSocket com o chat autenticando o handshake ou o mecanismo de conexão acordado.
5. O chat valida o token e associa a conexão ao identificador do usuário.

### Envio e recebimento de mensagem

1. O frontend publica conteúdo e identificador da conversa.
2. O chat deriva o remetente da identidade autenticada e valida que ele participa da conversa.
3. O chat grava a mensagem no PostgreSQL e obtém seu ID e horário do servidor.
4. O chat publica o evento no Redis.
5. As réplicas encaminham a mensagem aos participantes conectados localmente.
6. O frontend atualiza a tela. Na reconexão, ele busca mensagens persistidas para completar eventuais lacunas.

### Grupos

O serviço de chat criará grupos, manterá participantes e validará inclusão/remoção conforme as regras definidas. Mensagens de grupo serão endereçadas à conversa, não a uma lista de destinatários arbitrária enviada pelo cliente. Uma conversa privada seguirá o mesmo modelo de persistência e entrega, com exatamente dois participantes.

## 4. Plano de ação por incrementos

Em cada incremento, manteremos o sistema executável e demonstraremos o comportamento concluído. Seguiremos a ordem abaixo para definir contratos e preparar a base antes de integrar regras, interface e distribuição.

### Incremento 0 — Fechar o escopo e preparar o repositório

**Atividades do grupo**

- Definiremos as operações de usuário: cadastro, login e nome de exibição. Só incluiremos recuperação de senha se ela for exigida.
- Definiremos os contratos iniciais dos endpoints REST e dos eventos STOMP.
- Definiremos o modelo de conversa privada e em grupo, as regras de participação e a ordenação do histórico.
- Criaremos a estrutura do monorepositório e registraremos as decisões no README.
- Fixaremos versões compatíveis das ferramentas e estabeleceremos convenções de configuração.

**Critério de aceite**

- O repositório explica como iniciar o projeto e quais componentes existirão.
- Os contratos mínimos estão definidos antes de frontend e serviços evoluírem com formatos incompatíveis.

### Incremento 1 — Base executável e infraestrutura local

**Atividades do grupo**

- Criaremos os projetos independentes `auth-service` e `chat-service` e a aplicação React.
- Configuraremos Docker Compose para iniciar PostgreSQL, Redis e os serviços.
- Prepararemos os bancos de cada serviço e a configuração externa de credenciais e chaves.
- Implementaremos health checks e logs com identificador do serviço e da instância.
- Configuraremos Nginx para o roteamento inicial e as conexões WebSocket.

**Critério de aceite**

- Um comando documentado inicia os componentes básicos.
- Os serviços reportam saúde e conseguem acessar somente os recursos de que precisam.
- Nenhum segredo real é armazenado no código ou em arquivos versionados.

### Incremento 2 — Autenticação e identidade

**Atividades do grupo**

- Implementaremos cadastro e login no serviço de autenticação.
- Persistiremos usuários e hashes de senha no `auth_db`.
- Emitiremos e validaremos JWT com expiração e chave configurada externamente.
- Protegeremos as rotas e criaremos testes unitários e de integração.
- Documentaremos como criar usuários de teste.

**Critério de aceite**

- Comprovaremos que credenciais válidas autenticam e que credenciais inválidas são rejeitadas sem revelar se a conta ou a senha estava errada.
- Senhas não aparecem em texto puro no banco nem nos logs.
- O serviço de chat consegue validar um token emitido pelo serviço de autenticação.

### Incremento 3 — Modelo de conversa e persistência

**Atividades do grupo**

- Implementaremos entidades e migrações de conversas, participantes e mensagens no `chat_db`.
- Implementaremos criação e listagem de conversas privadas e grupos.
- Implementaremos inclusão e listagem de participantes segundo as regras definidas.
- Implementaremos gravação e consulta paginada do histórico.
- Validaremos que remetentes e destinatários pertencem à conversa.

**Critério de aceite**

- Mensagens e metadados (grupo, participantes, remetente e data/hora) persistem e são recuperáveis após reiniciar o serviço.
- Usuários não conseguirão consultar nem publicar em conversas das quais não participam.

### Incremento 4 — Chat em tempo real em uma instância

**Atividades do grupo**

- Implementaremos a conexão autenticada e os destinos STOMP para publicar e assinar conversas.
- Persistiremos cada mensagem antes de anunciar sua entrega.
- Implementaremos a consulta de histórico no frontend e os estados de conexão e reconexão.
- Cobriremos conversas privadas e em grupo com testes de integração.

**Critério de aceite**

- Dois usuários conectados receberão mensagens sem atualizar a página.
- Entregaremos mensagens privadas e de grupo somente aos participantes autorizados.
- As mensagens permanecerão no histórico após reiniciarmos o serviço.

### Incremento 5 — Interface utilizável

**Atividades do grupo**

- Implementaremos telas responsivas de cadastro, login e chat.
- Exibiremos a lista de conversas, o nome do grupo ou participante, o histórico e os horários.
- Implementaremos envio de mensagens, estados de carregamento e erro e indicação da conexão.
- Atualizaremos o histórico após reconexões.
- Adicionaremos testes dos fluxos centrais da interface.

**Critério de aceite**

- Usuários conseguirão autenticar, abrir ou criar conversas conforme as regras, enviar mensagens e consultar o histórico pela interface.
- A interface continuará compreensível em telas menores e durante falhas de conexão.

### Incremento 6 — Comunicação entre réplicas e balanceamento

**Atividades do grupo**

- Integraremos Redis Pub/Sub ao serviço de chat.
- Faremos cada réplica entregar às próprias conexões os eventos originados em qualquer réplica.
- Configuraremos Nginx para balancear requisições HTTP e conexões WebSocket.
- Executaremos pelo menos duas instâncias do chat e verificaremos a distribuição das conexões.
- Incluiremos o identificador da réplica nos logs para observar o caminho percorrido.

**Critério de aceite**

- Participantes conectados a réplicas diferentes receberão a mesma mensagem em tempo real.
- O histórico permanecerá correto quando distribuirmos conexões entre réplicas.
- Comprovaremos o balanceamento de WebSockets, além do balanceamento de requisições HTTP.

**Estado atual da implementação**

- O frontend de produção foi empacotado em uma imagem Docker própria e pode ser alcançado pelo mesmo gateway que autenticação, API e WebSocket.
- Docker Compose inicia o frontend, autenticação e chat com health checks e aguarda esses componentes antes de iniciar o Nginx.
- Os serviços HTTP incluem o cabeçalho `X-Application-Instance`, permitindo relacionar respostas às réplicas que as processaram.
- O diretório `load-tests` contém o cenário k6 para autenticação, 100 usuários virtuais, 20 grupos e mensagens pelo protocolo STOMP/WebSocket.

### Incremento 7 — Teste de carga e comportamento sob falhas

**Atividades do grupo**

- Criaremos um cenário k6 com no mínimo 100 usuários simultâneos e 20 grupos.
- Faremos os usuários autenticarem e enviarem e receberem mensagens durante um período definido.
- Registraremos taxa de sucesso, erros, latências percentis, uso de recursos e distribuição por réplica.
- Repetiremos o cenário com uma réplica indisponível e verificaremos reconexão, consulta do histórico e continuidade de novas conexões.
- Testaremos indisponibilidades temporárias de Redis e PostgreSQL e registraremos o comportamento observado.
- Corrigiremos gargalos relacionados aos critérios do trabalho e repetiremos os testes.

**Critério de aceite**

- Tornaremos os cenários exigidos reproduzíveis e incluiremos os resultados no relatório.
- Diferenciaremos falhas recuperadas de falhas que interrompem o serviço e não afirmaremos disponibilidade que não tenhamos demonstrado.
- Preservaremos mensagens já confirmadas como persistidas no banco; mensagens associadas a eventos perdidos durante falha do broker poderão ser recuperadas pelo histórico.

### Incremento 8 — Entrega e relatório

**Atividades do grupo**

- Revisaremos as instruções de execução, a arquitetura, os contratos e a configuração.
- Prepararemos comandos para iniciar o ambiente, criar usuários, executar testes e iniciar a carga.
- Produziremos o relatório de até 10 páginas com decisões, diagramas, resultados e limitações.
- Incluiremos a URL do repositório GitHub e prepararemos uma demonstração baseada nos estudos de caso.

**Critério de aceite**

- Uma pessoa que não participou do desenvolvimento conseguirá executar os fluxos e testes seguindo o README.
- O relatório cobrirá os requisitos e apresentará evidências dos testes, sem exceder o limite de páginas.

## 5. Estratégia de teste e evidências

| Camada | O que verificar | Evidência a guardar |
|---|---|---|
| Unitária | Regras de credenciais, autorização, criação de grupos e validação de mensagens | Relatório do JUnit/Vitest e casos cobertos |
| Integração | Persistência no PostgreSQL, validação de JWT, publicação/consumo Redis e contratos dos serviços | Resultado automatizado usando dependências reais de teste |
| Aplicação ponta a ponta | Login, conexão WebSocket, envio, recebimento e histórico pela interface | Roteiro reproduzível e capturas ou logs |
| Concorrência/carga | 100 usuários simultâneos em pelo menos 20 grupos, com troca de mensagens | Script k6, configuração, duração e resultados |
| Resiliência | Parada de réplica e indisponibilidade de dependências | Sequência do teste, comportamento esperado/observado e limites |

Antes de executar a carga, definiremos e registraremos a duração, a frequência de envio e o conteúdo dos cenários. O enunciado fixa usuários e grupos, mas não fixa uma meta numérica de latência; por isso, apresentaremos os valores medidos sem atribuir ao trabalho um SLO que não foi solicitado.

## 6. Alta disponibilidade: escopo e extensão

**O que demonstraremos na entrega base:** balanceamento de novas conexões, mais de uma réplica da aplicação, entrega de eventos entre réplicas, recuperação de histórico e comportamento ao parar uma réplica do serviço.

**Limite da entrega base:** não garantiremos continuidade total se falhar o host ou uma instância única de Nginx, PostgreSQL ou Redis. Health checks e reinício de containers ajudam a detectar ou recuperar processos, mas não substituem redundância nem failover de dados.

Se concluirmos, junto à avaliação dos requisitos, que precisamos cobrir também falhas de infraestrutura, planejaremos um incremento adicional após validar o núcleo funcional:

1. executaremos mais de uma instância do ponto de entrada ou demonstraremos o failover desse componente;
2. configuraremos PostgreSQL replicado, definiremos um procedimento de promoção/failover e validaremos a recuperação;
3. configuraremos Redis com redundância apropriada, mantendo explícito que Pub/Sub não é histórico durável;
4. repetiremos os testes de falha e registraremos os RTO/RPO observados, sem afirmar valores não medidos.

Essa extensão eleva o custo operacional. Decidiremos se a incluiremos antes da fase de resiliência, considerando o tempo disponível e a interpretação do requisito de alta disponibilidade.

## 7. Riscos e decisões em aberto

| Risco ou decisão | Resposta prevista pelo grupo |
|---|---|
| Evento Pub/Sub pode não chegar durante indisponibilidade do Redis | Persistiremos antes de publicar, recuperaremos o histórico após reconexões e documentaremos o limite; se a avaliação exigir entrega de eventos durante falhas, planejaremos um broker durável |
| Dados de identidade e chat podem ficar acoplados | Manteremos bancos separados e usaremos IDs de usuário como referências lógicas no chat |
| WebSockets podem concentrar usuários em uma réplica | Mediremos a distribuição das conexões e validaremos o balanceamento com Nginx |
| A carga pode não ser reproduzível | Versionaremos o script k6 e documentaremos sua configuração e o ambiente de execução |
| Réplicas da aplicação não cobrem falha do banco/broker | Explicaremos a diferença entre alta disponibilidade da camada de aplicação e do sistema ponta a ponta e decidiremos sobre o incremento adicional |
| Token ou senha podem ser expostos | Armazenaremos senhas como hashes, manteremos o segredo JWT fora do código, evitaremos credenciais nos logs e validaremos autorização no chat |
| Escopo crescer com recursos de mensageria não pedidos | Manteremos o foco em cadastro/login, conversas privadas e em grupo, histórico, tempo real, réplicas e testes exigidos |

## 8. Mapeamento dos requisitos do trabalho

Os IDs abaixo são uma identificação de planejamento para facilitar rastreabilidade; não substituem IDs fornecidos no enunciado.

| ID | Requisito | Incrementos | Evidência de conclusão |
|---|---|---|---|
| REQ-01 | Separar autenticação/usuário e chat em ao menos dois serviços independentes | 1, 2, 3 | Diretórios, processos, configurações e bancos de propriedade dos serviços |
| REQ-02 | Permitir cadastro/login e autenticar operações | 2, 4, 5 | Testes de autenticação, JWT e fluxo pela interface |
| REQ-03 | Trocar mensagens em tempo real por comunicação persistente/push | 4, 5, 6 | Testes WebSocket em uma e em múltiplas réplicas |
| REQ-04 | Suportar conversa privada e envio para vários participantes/grupos | 3, 4, 5 | Testes de autorização, grupos e entrega aos membros |
| REQ-05 | Persistir mensagens, nome do grupo, participantes e data/hora | 3, 4 | Migrações e testes de persistência/histórico |
| REQ-06 | Ter interface de chat responsiva com conversas e mensagens | 5 | Fluxos funcionais e verificação em telas menores |
| REQ-07 | Executar com réplicas e balanceamento de carga | 1, 6 | Compose, Nginx, logs por réplica e teste de distribuição |
| REQ-08 | Cobrir lógica central com testes unitários e integração | 2, 3, 4, 5 | Relatórios automatizados de testes |
| REQ-09 | Simular pelo menos 100 usuários simultâneos e 20 grupos | 7 | Script k6 versionado e relatório de execução |
| REQ-10 | Entregar relatório de até 10 páginas com decisões, avaliação e URL do código | 8 | Relatório final e repositório acessível |

## 9. Sequência de execução do grupo

1. **Definiremos contratos e estrutura** antes de dividir o desenvolvimento entre frontend e backend.
2. **Subiremos a infraestrutura e os serviços vazios** para validar o ambiente local.
3. **Implementaremos autenticação**, que fornecerá identidade para as operações seguintes.
4. **Implementaremos modelo, autorização e persistência do chat** antes do envio em tempo real.
5. **Adicionaremos WebSocket em uma instância** e validaremos o fluxo funcional de ponta a ponta.
6. **Construiremos a interface** sobre os contratos já testados.
7. **Adicionaremos Redis, réplicas e Nginx** para implementar comunicação entre instâncias e balanceamento.
8. **Executaremos os testes de carga e falhas**, corrigiremos os problemas encontrados e registraremos as evidências.
9. **Consolidaremos documentação, relatório e demonstração**.

Essa sequência nos permitirá verificar cada entrega e integrar as réplicas depois de validar o fluxo básico de mensagens.
