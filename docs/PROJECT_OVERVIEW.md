# Visão técnica do OrderFlow

## Objetivo

O OrderFlow simula o backend de um e-commerce com foco em decisões esperadas em um time
real: contrato HTTP previsível, regras de domínio protegidas, persistência versionada,
segurança, testes de integração e operação reproduzível.

A aplicação cobre o caminho principal entre catálogo e cobrança:

```mermaid
sequenceDiagram
    actor Cliente
    participant API as OrderFlow API
    participant DB as PostgreSQL
    participant Redis
    participant Gateway as Asaas / Gateway

    Cliente->>API: POST /orders + Idempotency-Key
    API->>Redis: SET NX chave + hash do payload
    API->>DB: bloqueia produtos e reserva estoque
    API->>DB: persiste pedido AWAITING_PAYMENT
    API->>Redis: armazena resposta por 24 h
    API-->>Cliente: 201 Created
    Cliente->>API: POST /payments
    API->>Gateway: cria cobrança + Correlation ID
    Gateway-->>API: pendente, aprovada ou recusada + URL
    API->>DB: persiste pagamento e atualiza pedido
    API-->>Cliente: resultado da cobrança
    Gateway->>API: POST /webhooks/asaas + token
    API->>DB: deduplica evento e bloqueia pagamento
    API->>DB: transiciona pagamento e pedido
    API-->>Gateway: 200 processado/duplicado
    API->>DB: adquire lock distribuído da conciliação
    API->>Gateway: consulta cobranças PENDING antigas
    API->>DB: reaplica a máquina de estados ou marca DIVERGENT
```

## Fronteiras dos módulos

### `catalog`

Mantém categorias, produtos, preço, ativação e estoque. As entidades protegem invariantes;
services coordenam transações e repositories usam consultas parametrizadas. A reserva de
estoque usa bloqueio pessimista para impedir que pedidos concorrentes vendam a mesma unidade.

### `identity`

Registra clientes, autentica com `DaoAuthenticationProvider` e BCrypt e emite JWT HS256. A
API é stateless: cada requisição protegida passa pelo Bearer Token Filter e popula o
`SecurityContext`. O token carrega o papel usado para autorizar endpoints administrativos.

### `order`

Cria o agregado `CustomerOrder`, copia preço para os itens, calcula totais e coordena a
reserva de estoque na mesma transação. O padrão State modela as transições:

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> AWAITING_PAYMENT
    CREATED --> CANCELLED
    AWAITING_PAYMENT --> PAID
    AWAITING_PAYMENT --> CANCELLED
    PAID --> SHIPPED
    PAID --> CANCELLED
    SHIPPED --> DELIVERED
    SHIPPED --> CANCELLED
    DELIVERED --> [*]
    CANCELLED --> [*]
```

Transições não previstas retornam `422 Unprocessable Content`. Cancelamentos restauram o
estoque quando aplicável, e o campo JPA `@Version` detecta atualizações concorrentes do
pedido. O agregado registra fatos imutáveis `OrderCreated`, `OrderPaid` e `OrderCancelled`;
o service coordena o caso de uso, mas não conhece seus consumidores.

O módulo `order` acessa o estoque por `OrderCatalogPort`. O adapter fica em `catalog`, que
continua sendo o proprietário da entidade `Product`, do bloqueio pessimista e da invalidação
do cache. O item do pedido guarda apenas o identificador e o snapshot comercial necessários,
sem importar entidades do catálogo.

### `payment`

É a exceção arquitetural deliberada. O domínio e as portas não conhecem Spring, REST, JPA
ou o fornecedor. A aplicação depende de três contratos de saída:

- `PaymentRepositoryPort` para persistência;
- `OrderPaymentPort` para consultar e atualizar o pedido;
- `PaymentGatewayPort` para executar a cobrança.

Os adaptadores disponíveis são determinístico aprovado, determinístico recusado e HTTP.
Essa substituição é feita por profiles, sem alterar a regra de negócio.

### `shared`

Concentra apenas preocupações realmente transversais:

- tradução de exceções para `ProblemDetail`;
- paginação independente do tipo do Spring Data;
- cache e OpenAPI;
- idempotência com Redis e AOP;
- envelope e topologia comuns de mensageria;
- filtros de Correlation ID e contexto de usuário nos logs.

## Persistência e transações

O schema é propriedade do Flyway e o Hibernate executa apenas `validate`. As migrations são:

| Versão | Responsabilidade |
|---|---|
| V1 | categorias e produtos |
| V2 | usuários e papéis |
| V3 | pedidos, itens, estados e versionamento |
| V4 | pagamentos e unicidade por pedido |
| V5 | CPF/CNPJ de cobrança do cliente e URL externa do pagamento |
| V6 | auditoria/deduplicação de webhooks e status de pagamento estornado |
| V7 | status divergente, índice de conciliação e tabela de locks distribuídos |
| V8 | auditoria transacional dos eventos de domínio de pedido |

`spring.jpa.open-in-view=false` força o carregamento necessário dentro do service e evita
consultas acidentais durante a serialização. Relações são lazy; queries específicas e batching
controlam a quantidade de acessos ao banco.

Na criação do pedido, produtos são consultados com lock pessimista. Estoque, pedido e itens
são alterados na mesma transação; uma falha provoca rollback. O preço unitário é copiado para
`order_item`, preservando o histórico mesmo que o catálogo mude depois.

Eventos registrados pelo agregado são publicados pelo repositório Spring Data. A auditoria
usa `BEFORE_COMMIT`: se não puder ser gravada, pedido e auditoria sofrem rollback juntos. As
métricas usam `AFTER_COMMIT`, portanto somente observam fatos confirmados. Falhas nesse
listener pós-commit são capturadas, registradas com contexto e contabilizadas em
`orderflow.order.event_listener.failures`, sem devolver erro ao cliente depois que a
transação já foi confirmada.

A reserva e a restauração de estoque permanecem síncronas na transação do caso de uso. Um
listener `AFTER_COMMIT` seria inadequado para essa invariável: permitiria confirmar um pedido
sem confirmar sua reserva. A publicação em broker e o risco de dual write são tratados na
evolução de mensageria, sem enfraquecer a consistência local.

## Mensageria RabbitMQ

A topologia é declarada por beans Spring AMQP e recriada pela aplicação quando aponta para
um broker vazio. Exchanges, filas e bindings são duráveis. O exchange `order.events` é do
tipo `topic`: notificações assinam chaves específicas e auditoria assina `order.#`.

```mermaid
flowchart TD
    events["order.events<br/>topic"]
    created["notification.order-created<br/>order.created"]
    paid["notification.order-paid<br/>order.paid"]
    audit["audit.queue<br/>order.#"]
    dlx["order.events.dlx<br/>direct"]
    dlq["order.events.dlq"]
    events --> created
    events --> paid
    events --> audit
    created -. rejeição .-> dlx
    paid -. rejeição .-> dlx
    audit -. rejeição .-> dlx
    dlx -->|dead| dlq
```

As três filas de trabalho apontam para a mesma DLX; a DLQ não aponta para si mesma. Mensagens
usam JSON pelo `Jackson2JsonMessageConverter` de Jackson 2, sem serialização binária Java. O
envelope comum contém `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId` e
`payload`.

O adaptador de saída recebe `OrderCreated`, `OrderPaid` e `OrderCancelled` em `AFTER_COMMIT`,
converte cada fato em payload externo v1 e publica com delivery mode persistente. Confirms
correlacionados verificam a aceitação pelo broker. `mandatory` e publisher returns tornam
visível uma routing key sem binding. Falhas não induzem o cliente a repetir uma transação já
confirmada: identificadores do evento, versão, routing key e correlação ficam no log sem o
payload, e a métrica `orderflow.messaging.order_event.publications` registra sucesso/falha
por tipo.

Isso não fecha o dual write. O banco pode confirmar e o processo cair antes de publicar. A
decisão atual reduz a janela, mede falhas conhecidas e mantém Outbox como evolução registrada
no ADR-004. O consumo idempotente continua obrigatório porque confirmações e retries oferecem
entrega *at-least-once*, não *exactly-once*.

O módulo `notification` fecha o fluxo de `OrderPaid`. O listener AMQP só valida o contrato,
propaga correlação e cria um comando. O service transacional disputa a PK composta
`processed_event(event_id, consumer)` e persiste a entrega simulada. Duas instâncias podem
passar pela aplicação ao mesmo tempo, mas apenas uma vence o `INSERT`; a outra reconhece a
duplicata depois que a primeira transação termina.

Falhas transitórias têm três tentativas totais com backoff exponencial. Falhas de conversão ou
contrato recebem rejeição sem requeue e seguem para a DLQ declarada no broker. A lógica não
consulta `OrderCreated` nem o estado atual do pedido, portanto não presume ordenação entre
eventos. MDC é restaurado ao final para impedir vazamento de correlação entre mensagens da
mesma thread.

## Idempotência

`POST /api/v1/orders` é protegido por `@Idempotent`:

1. a chave, o usuário e o hash SHA-256 do payload formam a identidade da tentativa;
2. Redis executa `SET NX` com TTL curto para reivindicar o processamento;
3. concorrência sobre a mesma tentativa recebe conflito enquanto ela está em andamento;
4. após sucesso, status, corpo e `Location` ficam retidos por 24 horas;
5. a repetição do mesmo payload devolve `200` com o corpo original;
6. reutilizar a chave com outro payload devolve `422`;
7. se Redis estiver indisponível, o endpoint falha fechado em vez de prometer idempotência
   sem conseguir garanti-la.

## Segurança

```mermaid
flowchart LR
    request[Requisição] --> jwt[BearerTokenAuthenticationFilter]
    jwt --> decoder[JwtDecoder HS256]
    decoder --> context[SecurityContext]
    context --> authorization[Regras de endpoint e Method Security]
    authorization --> controller[Controller]
```

- JWT tem issuer `orderflow` e expiração padrão de 15 minutos;
- o segredo deve possuir ao menos 32 bytes;
- o servidor não cria sessão e CSRF fica desabilitado para a API stateless;
- 401 significa autenticação ausente ou inválida; 403 significa usuário autenticado sem papel;
- produção não cria administrador automaticamente;
- DTOs e erros não retornam hash de senha, stack trace, SQL ou nomes de constraints.

## Erros

O `GlobalExceptionHandler` traduz falhas para `application/problem+json`, seguindo RFC 9457.
Os principais tipos incluem validação, recurso inexistente, regra de negócio, transição inválida,
conflito de idempotência, autenticação e autorização. Erros inesperados retornam uma mensagem
segura e um identificador de correlação; o detalhe técnico permanece no log.

## Observabilidade

Cada requisição recebe `X-Correlation-Id`. Valores seguros enviados pelo cliente são
preservados; caso contrário, a API gera um UUID. O identificador aparece na resposta, no MDC,
nos erros e na chamada ao gateway HTTP.

Os profiles `dev` e `docker` mantêm texto legível e exibem `correlationId`, `userId` e pares
chave-valor do SLF4J. O profile `prod` usa JSON Logstash nativo do Spring Boot, que transforma
MDC e `addKeyValue` em campos consultáveis. O executor dedicado do Asaas copia e restaura o
MDC entre threads; jobs agendados criam uma correlação por execução e listeners restauram o
contexto anterior ao finalizar.

O Actuator expõe somente `health`, `info`, `metrics`, `prometheus` e `circuitbreakers`.
Nos profiles Docker e produção, a porta de gerenciamento `9090` fica separada da API e
acessível apenas pela rede interna. O profile `prod` escreve logs estruturados em JSON no
formato Logstash.

O Prometheus coleta a aplicação a cada 15 segundos e avalia regras para indisponibilidade,
taxa de erro HTTP, latência p95, circuit breaker aberto e DLQ não vazia. O Grafana recebe por
provisionamento a fonte Prometheus e o dashboard operacional. Métricas próprias observam
eventos confirmados e adapters de infraestrutura; nenhuma instrumentação foi adicionada ao
domínio ou aos services de pedido e pagamento.

As tags são enumerações ou valores constantes: status do pedido, gateway, operação, motivo
de falha e tipo de divergência. Identificadores, mensagens de exceção, URLs e dados de
cliente nunca viram tags. A latência do gateway publica histogramas para cálculo de p50,
p95 e p99; falhas usam contador separado por gateway e motivo.

## Profiles

| Profile | Uso |
|---|---|
| `dev` | aplicação na máquina; PostgreSQL `5433` e Redis `6380` |
| `docker` | aplicação dentro do Compose local; bootstrap de admin habilitável |
| `prod` | configuração externa, logs JSON, Swagger e bootstrap desativados |
| `payment-declined` | simula recusa do gateway para testes manuais |
| `payment-http` | ativa o adaptador REST e exige URL/chave do fornecedor |
| `payment-asaas` | ativa o adapter Asaas e exige `ASAAS_API_KEY` |

Profiles de gateway complementam o profile de ambiente, por exemplo
`SPRING_PROFILES_ACTIVE=docker,payment-asaas` no Sandbox ou
`SPRING_PROFILES_ACTIVE=prod,payment-asaas` em produção.

## Resiliência da cobrança Asaas

O adapter compõe funcionalmente `TimeLimiter → CircuitBreaker → Retry → HTTP`. A composição
fica fora do domínio e usa circuitos independentes para criação, consulta e cancelamento.
O pool dedicado é limitado para impedir que lentidão externa consuma todas as threads da API.

O `POST /payments` não recebe retry porque o Asaas não documenta uma chave de idempotência
para criação. Em falha inconclusiva, o adapter faz uma consulta segura por
`externalReference`; somente consultas recebem até 3 tentativas com backoff exponencial.
Se ainda não houver confirmação, a aplicação persiste o pagamento sem `externalId`, em
`PENDING`, mantém o pedido em `AWAITING_PAYMENT` e informa honestamente que a cobrança será
processada depois. Esse registro é a entrada da conciliação periódica.

## Conciliação periódica

O job executa por padrão a cada 15 minutos e seleciona no máximo 200 pagamentos `PENDING`
associados a pedidos `AWAITING_PAYMENT`, com idade entre 10 minutos e 7 dias. Esses limites,
o cron e os tempos do lock são configuráveis por variáveis de ambiente.

ShedLock coordena as instâncias pela tabela PostgreSQL `shedlock` usando o relógio do banco.
O gateway é consultado sem manter uma transação aberta; depois, uma transação curta bloqueia
o pagamento, confirma que ele continua pendente e aplica a transição. Isso torna a execução
idempotente diante de concorrência com webhook ou outra instância.

Cobrança aprovada paga o pedido; recusada cancela o pedido e restaura estoque; ainda pendente
apenas atualiza identificador/URL. Cobrança ausente gera alerta operacional e não muda dados.
Valor ausente ou diferente e identificador conflitante mudam o pagamento para `DIVERGENT`,
sem alterar pedido ou estoque. Logs estruturados incluem pagamento, pedido, estados, ação e
Correlation ID. Actuator publica as métricas `orderflow.payment.reconciliation.executions`,
`duration`, `checked`, `corrections`, `divergences`, `not_found` e `errors`.

## Webhook financeiro

O endpoint `POST /api/v1/webhooks/asaas` é público apenas no sentido de não exigir JWT:
ele compara em tempo constante o header `asaas-access-token` com
`ASAAS_WEBHOOK_TOKEN`. O corpo é lido como bytes antes da desserialização e persistido em
`webhook_event_log.payload` (`JSONB`).

O Template Method final controla autenticação, registro, deduplicação, transação e
auditoria; handlers de confirmação, recusa e estorno implementam somente parse/processamento.
A constraint única de `event_id` protege inclusive entregas concorrentes. Valor e
`externalReference` são comparados com o pagamento local antes de qualquer transição.

## Estratégia de testes

- entidades e State: testes unitários de invariantes e transições;
- services: JUnit 5 e Mockito para coordenação isolada;
- repositories: integração com PostgreSQL 16 real;
- idempotência: integração com Redis 7, incluindo repetição e concorrência;
- contrato HTTP: MockMvc com autenticação, autorização, validação e fluxo completo;
- migrations: Flyway parte de schema vazio em Testcontainers;
- scheduler: dois provedores ShedLock no mesmo PostgreSQL comprovam exclusão mútua;
- eventos de domínio: PostgreSQL real comprova execução no commit, ausência no rollback e
  auditoria das transições criado, pago e cancelado;
- mensageria: RabbitMQ e PostgreSQL reais comprovam topologia, roteamento, consumo idempotente,
  retry, DLQ, cabeçalhos `x-death` e precisão do contrato JSON;
- testes assíncronos: Awaitility observa condições com limite de dez segundos, sem
  `Thread.sleep`;
- qualidade: JaCoCo exige pelo menos 70% de linhas no `verify`.

## Decisões e limites

As justificativas arquiteturais ficam em [`docs/adr`](adr/README.md). A baseline usa RabbitMQ
como broker, mantém um gateway de pagamento simulado como padrão e entrega observabilidade
local com Prometheus e Grafana. Refresh token e envio SMTP não fazem parte do escopo atual.
Separar em microsserviços só será reconsiderado diante dos gatilhos observáveis registrados
no ADR-001.
