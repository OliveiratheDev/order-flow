# ADR-003 — Eventos de pedido preservam a consistência transacional

## Status

Aceito.

Data: 2026-08-09.

Decisão relacionada: OF-033.

## Contexto

Criação, pagamento e cancelamento de pedidos interessam a auditoria, observabilidade,
notificações e futuras integrações. Chamar esses consumidores diretamente no service mistura
responsabilidades e torna o fluxo difícil de evoluir. Ao mesmo tempo, reserva e restauração
de estoque são invariáveis locais: pedido e estoque não podem ser confirmados separadamente.

Listeners executados depois do commit não conseguem desfazer a transação principal. Uma
falha propagada nessa fase também induziria o cliente a repetir uma operação já confirmada.
Eventos apenas em memória não garantem entrega a processos externos se a aplicação parar
entre o commit do banco e a publicação no broker.

## Decisão

O agregado `CustomerOrder` registra eventos imutáveis e autocontidos para `OrderCreated`,
`OrderPaid` e `OrderCancelled`. O Spring Data publica esses eventos dentro da transação do
repositório, sem o service conhecer listeners.

A auditoria local executa em `BEFORE_COMMIT` e participa da mesma transação. Observadores sem
escrita de negócio, como métricas e logs, executam em `AFTER_COMMIT`; capturam suas próprias
falhas, registram contexto estruturado e incrementam uma métrica de erro.

Reserva e restauração de estoque continuam síncronas na transação do caso de uso por meio de
`OrderCatalogPort`. O adapter pertence a `catalog`, preservando a direção da dependência sem
trocar consistência por desacoplamento aparente.

A publicação externa exigirá uma decisão explícita de confiabilidade. Até que um Outbox seja
adotado, o risco de dual write entre PostgreSQL e broker deve permanecer documentado e
observável.

## Alternativas consideradas

### Chamar consumidores diretamente no service

Foi descartado porque acopla o caso de uso a auditoria, métricas e integrações, dificulta
testes isolados e faz cada novo consumidor alterar a coordenação principal.

### Reservar estoque em listener `AFTER_COMMIT`

Foi descartado porque o pedido poderia ser confirmado antes da reserva, deixando uma janela
de venda sem estoque e exigindo compensação posterior para uma regra que cabe em uma única
transação local.

### Publicar no broker dentro da transação atual

Não resolve atomicidade: a confirmação no broker e o commit PostgreSQL continuam sendo dois
recursos independentes. Essa alternativa será reavaliada com o desenho de mensageria e
Outbox.

## Consequências

### Positivas

- o agregado expressa fatos de negócio sem depender dos consumidores;
- rollback impede auditoria e observadores associados a um fato não confirmado;
- módulos consumidores evoluem sem alterar `OrderService`;
- pedido, estoque e auditoria crítica mantêm uma fronteira transacional clara.

### Negativas

- eventos Spring são locais ao processo e não oferecem durabilidade externa;
- falhas pós-commit exigem alerta e tratamento operacional, pois não há rollback possível;
- publicação futura no broker terá risco de dual write até a adoção de Outbox.

### Neutras

- listeners com escrita após o commit devem abrir uma transação independente;
- eventos precisam permanecer compatíveis e autocontidos para futuros consumidores.
