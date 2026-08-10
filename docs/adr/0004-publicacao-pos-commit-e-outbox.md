# ADR-004 — Publicação pós-commit com Outbox como evolução

## Status

Aceito.

Data: 2026-08-09.

Decisão relacionada: OF-051.

## Contexto

Eventos de pedido precisam chegar ao RabbitMQ sem anunciar transações que sofreram rollback.
PostgreSQL e RabbitMQ são recursos independentes: não existe commit atômico entre ambos na
arquitetura atual. Publicar antes do commit pode anunciar um pedido inexistente; publicar
depois deixa uma janela em que o pedido existe, mas a mensagem ainda não foi entregue.

Transação distribuída XA aumentaria acoplamento, duração de locks e complexidade operacional
de forma desproporcional para o projeto. O Outbox elimina o dual write do caminho crítico ao
gravar estado e evento na mesma transação local, mas exige tabela, relay, retenção e operação
próprios.

## Decisão

Os eventos `OrderCreated`, `OrderPaid` e `OrderCancelled` serão publicados por um adaptador de
saída acionado com `@TransactionalEventListener(AFTER_COMMIT)`. O domínio e o service não
dependem de RabbitMQ.

Mensagens terão envelope versionado, delivery mode persistente, publisher confirm
correlacionado e `mandatory` com publisher returns. O listener aguardará a confirmação por
tempo limitado. Falhas serão absorvidas depois de registradas com identificadores do evento,
versão, routing key, correlação e métrica; o payload não será copiado para logs e não será
devolvido erro ao cliente depois que o commit já ocorreu.

Aceitamos explicitamente a janela residual entre commit e publicação. O Outbox Pattern é a
evolução recomendada quando a perda dessa mensagem deixar de ser aceitável: pedido e evento
serão gravados atomicamente no PostgreSQL, e um relay publicará eventos pendentes com garantia
*at-least-once*.

## Alternativas consideradas

### Publicar antes ou dentro da transação do pedido

Foi descartado porque a mensagem pode chegar aos consumidores antes de um rollback local,
produzindo efeitos para um pedido inexistente. Manter a transação aberta enquanto aguarda o
broker também prolonga locks sem fornecer atomicidade real.

### Usar transação distribuída XA

Foi descartado pelo custo operacional, acoplamento entre recursos e suporte mais frágil, sem
benefício proporcional para um monólito de portfólio.

### Implementar Outbox imediatamente

É a solução preferida para durabilidade, mas foi mantida como evolução consciente para
separar o aprendizado de eventos, topologia, publicação e consumo. O risco atual permanece
documentado e observável, não tratado como resolvido.

## Consequências

### Positivas

- rollback nunca publica evento de pedido inexistente;
- domínio e casos de uso continuam independentes do broker;
- contrato externo nasce versionado e inspecionável;
- nack, timeout e mensagem não roteada deixam evidência operacional.

### Negativas

- queda do processo após commit e antes da publicação ainda pode perder o evento;
- metadados no log ajudam a conciliar a falha, mas não substituem armazenamento durável nem
  permitem reconstruir o payload isoladamente;
- aguardar confirm acrescenta latência após o commit.

### Neutras

- consumidores devem ser idempotentes, pois confirmação ambígua pode gerar reenvio;
- a adoção futura de Outbox não exige alterar o agregado nem o service.
