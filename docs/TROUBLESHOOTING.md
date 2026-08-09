# Solução de problemas

## A porta 8080 já está em uso

Sintoma: o Compose informa falha ao publicar `0.0.0.0:8080`.

Use outra porta sem alterar a porta interna do container:

```powershell
$env:APP_PORT = "8081"
docker compose up -d app
```

Acesse `http://localhost:8081`. O OpenAPI usa URL relativa e acompanha a porta atual.

## `password authentication failed` no PostgreSQL

As variáveis `POSTGRES_*` só criam usuário e senha quando o volume é inicializado. Alterar
`DB_PASSWORD` depois não modifica a senha armazenada no banco.

Em ambiente com dados importantes, altere a senha dentro do PostgreSQL e atualize a variável.
Em desenvolvimento descartável, pare o ambiente e recrie apenas depois de confirmar que o
volume pode ser apagado. Nunca remova volumes de produção como tentativa de correção.

## A aplicação reinicia ou fica `unhealthy`

```bash
docker compose ps
docker compose logs --tail 200 app
docker inspect overflow-app-1
```

Verifique, nesta ordem:

1. PostgreSQL, Redis e RabbitMQ estão `healthy`;
2. `JWT_SECRET` possui ao menos 32 bytes;
3. credenciais e nomes do banco coincidem entre app e PostgreSQL;
4. a migration mais recente foi aplicada sem erro;
5. a porta do host está livre.

O health check interno usa `/actuator/health` na porta `8080` do container, mesmo quando a
porta do host é diferente.

## RabbitMQ não inicia ou a UI não abre

Use `docker compose ps rabbitmq` e `docker compose logs --tail 200 rabbitmq`. Localmente,
confirme se `5672` e `15672` estão livres; remapeie apenas as portas do host quando necessário:

```dotenv
RABBITMQ_AMQP_HOST_PORT=5673
RABBITMQ_MANAGEMENT_HOST_PORT=15673
```

A aplicação dentro do Compose continua usando `rabbitmq:5672`. A UI usa
`RABBITMQ_USER`/`RABBITMQ_PASSWORD`, não credenciais do PostgreSQL ou JWT. Alterar as
credenciais depois que o volume foi criado não modifica automaticamente o usuário já salvo
no broker; faça rotação pelo procedimento administrativo do RabbitMQ.

## Fila `order.events.dlq` está acumulando

Não purgue nem reenvie a fila inteira como primeira ação. Inspecione `x-death`, exchange,
routing key, `eventId`, `eventType` e `eventVersion`; identifique se a causa é payload
incompatível, falha transitória ou bug do consumidor. Corrija a causa e reprocesse apenas os
eventos registrados. A DLQ deliberadamente não possui outra DLQ, evitando loop infinito.

## Log informa que a mensagem não encontrou binding

`mandatory=true` e publisher returns transformam uma mensagem não roteada em aviso. Compare a
routing key com os bindings declarados em `RabbitTopology`. Não crie binding manual pelo
console: ajuste a configuração versionada, faça deploy e confirme a topologia no broker.

## `JWT_SECRET é obrigatório` ou possui menos de 32 bytes

O segredo não tem fallback na aplicação. Defina um valor local antes de executar pelo Maven:

```powershell
$env:JWT_SECRET = "segredo-local-com-mais-de-trinta-e-dois-bytes"
```

Em produção, gere um valor aleatório e armazene-o no gerenciador de segredos da plataforma.

## Swagger não abre

- local: confira `/swagger-ui.html` e `/v3/api-docs`;
- porta remapeada: use a porta de `APP_PORT`;
- produção: Swagger e OpenAPI são desativados deliberadamente pelo profile `prod`;
- confirme que o profile ativo não inclui `prod` durante a demonstração local.

## Pedido retorna erro de `Idempotency-Key`

Toda criação de pedido exige uma chave no header:

```text
Idempotency-Key: tentativa-unica-123
```

A mesma chave e o mesmo usuário podem repetir somente o mesmo payload. Outro payload retorna
`422`; uma requisição simultânea ainda em processamento retorna `409`. Depois de sucesso, a
resposta é mantida por 24 horas.

## Pedido retorna 503 quando Redis está indisponível

Esse comportamento é intencional. Sem Redis, a API não consegue garantir idempotência e
falha fechado antes de criar um pedido possivelmente duplicado. Restaure o Redis e repita com
a mesma chave.

## Testes de integração são ignorados ou falham ao iniciar containers

Mantenha o Docker ativo e confirme:

```bash
docker version
docker info
```

Execute pelo Maven Wrapper:

```powershell
.\mvnw.cmd verify
```

Testcontainers precisa acessar o daemon e baixar `postgres:16-alpine` e `redis:7-alpine` na
primeira execução.

## Build falha na regra do JaCoCo

Abra `target/site/jacoco/index.html`, identifique regras sem cenário e escreva testes de
comportamento. O limite é 70% de linhas, mas o objetivo é cobrir riscos, não apenas aumentar
um número.

## Pagamento sempre é aprovado

Sem um profile de gateway, o adaptador padrão é determinístico e aprovado para permitir a
demonstração local. Use `payment-declined` para o fluxo de recusa ou `payment-asaas` com
`ASAAS_API_KEY` para o Sandbox. Não trate o simulador como processamento financeiro real.

## O profile `payment-asaas` não inicia

Confirme que `SPRING_PROFILES_ACTIVE` inclui o profile de ambiente e `payment-asaas`, que
`ASAAS_API_KEY` não está vazia e que `ASAAS_BASE_URL` usa HTTPS. No ambiente local esperado:

```dotenv
SPRING_PROFILES_ACTIVE=docker,payment-asaas
ASAAS_BASE_URL=https://api-sandbox.asaas.com/v3
```

Uma resposta pendente é normal para PIX e boleto: use o campo `paymentUrl` devolvido pela API.
A confirmação assíncrona ocorre pelo webhook Asaas configurado para a URL pública da API.

## Resposta informa pagamento pendente sem `externalId`

Esse é o fallback esperado quando a criação ficou inconclusiva após timeout/5xx e a consulta
por `externalReference` também não confirmou a cobrança. O pedido permanece
`AWAITING_PAYMENT`; não repita manualmente o `POST /payments`, pois isso pode duplicar a
cobrança. O job tentará localizá-la pela referência estável do pedido após 10 minutos; não
altere o `externalId` manualmente.

## Circuito do Asaas está aberto

Consulte `/actuator/circuitbreakers` e
`/actuator/metrics/resilience4j.circuitbreaker.state` com JWT administrativo. O circuito abre
após pelo menos 10 chamadas e taxa de falha igual ou superior a 50%; por 30 segundos, novas
chamadas falham imediatamente e não acessam a rede. Depois, até 3 chamadas half-open verificam
se o serviço se recuperou. Investigue credencial, DNS, TLS, timeout e disponibilidade do Asaas
antes de forçar novas tentativas.

## Webhook Asaas retorna 401

Confirme que o token configurado no Asaas é exatamente o mesmo de `ASAAS_WEBHOOK_TOKEN`.
Não use `ASAAS_API_KEY`, `Authorization: Bearer` ou o JWT do cliente nesse endpoint. O header
esperado é `asaas-access-token`.

## Webhook fica `FAILED`

Consulte `webhook_event_log` sem copiar o payload para logs ou chamados não protegidos.
Falha com resposta HTTP 500 é transitória e permite retry do mesmo `event_id`. Resposta 200
com status `RECONCILIATION_REQUIRED` indica divergência de valor/referência ou transição
impossível. O job volta a consultar o gateway e, se confirmar valor ou identificador
conflitante, muda o pagamento para `DIVERGENT`, sem alterar pedido nem estoque.

## Pagamento ficou `DIVERGENT`

Esse status é deliberadamente terminal para automação. Consulte os logs pelo `paymentId`,
`orderId` e `correlationId`; compare o valor e o identificador no Asaas com os dados locais.
Não altere o banco diretamente. Registre a decisão e execute o procedimento financeiro
aprovado para captura, cancelamento ou estorno.

## Job de conciliação não executa

Consulte `orderflow.payment.reconciliation.executions` no Actuator e a tabela `shedlock`.
Uma instância que não obtém o lock pula a execução, o que é esperado. Não apague a linha de
lock manualmente: ajuste `lock_until` apenas por procedimento operacional controlado. Confira
também o cron, o fuso esperado e se o PostgreSQL está disponível.

## Caracteres acentuados aparecem incorretamente no PowerShell

Os arquivos são UTF-8. No Windows PowerShell antigo, leia explicitamente com:

```powershell
Get-Content -Encoding UTF8 README.md
```

Isso é uma limitação de exibição do terminal e não altera o conteúdo versionado.

## Evento de pedido não aparece nas métricas

Consulte `orderflow.order.events` com a tag `event.type` (`OrderCreated`, `OrderPaid` ou
`OrderCancelled`) e procure o `eventId`, `orderId` e `correlationId` nos logs. Um rollback não
publica o evento, por definição. Se `orderflow.order.event_listener.failures` aumentar, a
transação principal já foi confirmada, mas um observador pós-commit falhou; investigue a causa
registrada sem repetir automaticamente o comando de negócio.

A tabela `order_event_audit` é gravada em `BEFORE_COMMIT`. A ausência simultânea do pedido e
da auditoria indica rollback esperado; pedido confirmado sem auditoria deve ser tratado como
inconsistência operacional e investigado antes de alterar dados manualmente.
