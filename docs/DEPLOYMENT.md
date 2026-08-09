# Implantação

## Escopo deste guia

O repositório entrega uma imagem Docker da API e um Compose de produção com OrderFlow,
PostgreSQL, Redis e RabbitMQ. Provisionamento da VPS, DNS, TLS, firewall, backups externos e
gerenciador de segredos dependem da plataforma escolhida e não são automatizados nesta
baseline.

## Artefatos

- [`Dockerfile`](../Dockerfile): build multi-stage com Java 21, camadas do Spring Boot e
  runtime não-root;
- [`docker-compose.yml`](../docker-compose.yml): ambiente local com portas de banco expostas;
- [`docker-compose.prod.yml`](../docker-compose.prod.yml): ambiente de implantação, sem
  publicar PostgreSQL, Redis ou RabbitMQ no host;
- [`.env.example`](../.env.example): catálogo de variáveis, sem valores reais.

## Preparar o ambiente

1. Instale Docker Engine com Compose v2.
2. Libere somente SSH, HTTP e HTTPS no firewall; não publique `5432`, `6379`, `5672` nem
   `15672`.
3. Configure um reverse proxy com certificado válido para encaminhar HTTPS à porta da API.
4. Crie armazenamento e política de backup para o volume PostgreSQL.
5. Mantenha o arquivo `.env` fora do Git e restrito ao usuário do serviço.

Crie `.env` a partir do exemplo e substitua todos os placeholders. No mínimo:

```dotenv
DB_NAME=orderflow
DB_USER=orderflow_app
DB_PASSWORD=<senha-aleatoria-forte>
REDIS_PASSWORD=<outra-senha-aleatoria-forte>
RABBITMQ_USER=orderflow_app
RABBITMQ_PASSWORD=<senha-aleatoria-exclusiva-do-rabbitmq>
RABBITMQ_PUBLISHER_CONFIRM_TIMEOUT=5s
RABBITMQ_CONSUMER_MAX_RETRIES=2
RABBITMQ_CONSUMER_RETRY_INITIAL_INTERVAL=1s
RABBITMQ_CONSUMER_RETRY_MULTIPLIER=2
RABBITMQ_CONSUMER_RETRY_MAX_INTERVAL=2s
RABBITMQ_CONSUMER_CONCURRENCY=2
RABBITMQ_CONSUMER_MAX_CONCURRENCY=5
RABBITMQ_CONSUMER_PREFETCH=10
NOTIFICATION_PROCESSED_EVENT_RETENTION=30d
NOTIFICATION_CLEANUP_CRON="0 0 3 * * *"
JWT_SECRET=<segredo-aleatorio-com-ao-menos-32-bytes>
APP_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

Não reutilize senhas entre banco, Redis, RabbitMQ e JWT. Uma alteração de `JWT_SECRET`
invalida tokens emitidos anteriormente.

## RabbitMQ

No Compose local, AMQP fica em `localhost:5672` e a Management UI em
`http://localhost:15672`. As portas podem ser alteradas com
`RABBITMQ_AMQP_HOST_PORT` e `RABBITMQ_MANAGEMENT_HOST_PORT`. A aplicação usa a porta interna
`5672`, independentemente do remapeamento no host.

O Compose de produção não publica AMQP nem a UI. A topologia é criada pela aplicação:

- `order.events`: exchange `topic`;
- `notification.order-created`, `notification.order-paid` e `audit.queue`: filas de trabalho
  com dead-letter configurado;
- `order.events.dlx`: exchange `direct` de mensagens mortas;
- `order.events.dlq`: fila de inspeção, sem dead-letter próprio.

Quando a DLQ acumular, não republique tudo automaticamente. Primeiro inspecione headers
`x-death`, routing key, versão e payload; corrija a causa; registre os `eventId` afetados; e
somente então faça reprocessamento controlado. Mantenha backup do volume `rabbitmq_data` de
acordo com a criticidade das mensagens.

O publicador aguarda confirmação correlacionada por até
`RABBITMQ_PUBLISHER_CONFIRM_TIMEOUT`. Aumentar esse valor prolonga a resposta HTTP depois do
commit; reduzi-lo aumenta falsos timeouts sob latência. Monitore
`orderflow.messaging.order_event.publications` pelas tags `event.type` e `result`. Falha de
publicação exige conciliar o `eventId` registrado no log antes de qualquer reenvio manual.

O consumidor de `notification.order-paid` usa ack automático somente após retorno normal. As
duas repetições configuradas, além da tentativa inicial, produzem três tentativas com backoff
de 1 e 2 segundos. `default-requeue-rejected=false` envia a falha final à DLQ. Ajuste
concorrência e prefetch em conjunto: com os padrões, até 50 mensagens podem ficar reservadas
pelas cinco threads máximas de uma instância.

O PostgreSQL mantém `processed_event` e `notification_delivery`. O job diário expurga ambos
após `NOTIFICATION_PROCESSED_EVENT_RETENTION`; backup e retenção devem refletir o prazo máximo
em que a operação pretende reprocessar a DLQ com deduplicação garantida.

## Gateway de pagamento Asaas

O profile de ambiente isolado mantém o simulador determinístico do portfólio. Para usar o
Sandbox Asaas no Compose local, configure o `.env` sem versioná-lo:

```dotenv
SPRING_PROFILES_ACTIVE=docker,payment-asaas
ASAAS_BASE_URL=https://api-sandbox.asaas.com/v3
ASAAS_API_KEY=<chave-do-sandbox>
ASAAS_USER_AGENT=OrderFlow/1.0
ASAAS_PAYMENT_DUE_DAYS=3
ASAAS_CONNECT_TIMEOUT=3s
ASAAS_READ_TIMEOUT=3s
ASAAS_WEBHOOK_TOKEN=<token-exclusivo-do-webhook>
PAYMENT_RECONCILIATION_CRON=0 */15 * * * *
PAYMENT_RECONCILIATION_MIN_AGE=10m
PAYMENT_RECONCILIATION_MAX_AGE=7d
PAYMENT_RECONCILIATION_BATCH_SIZE=200
PAYMENT_RECONCILIATION_LOCK_AT_MOST=15m
PAYMENT_RECONCILIATION_LOCK_AT_LEAST=1m
```

O adapter envia a chave no header `access_token`, identifica a aplicação por `User-Agent`,
reutiliza o cliente pelo `externalReference`, persiste a URL da fatura e cancela a cobrança
remota quando o pagamento local é cancelado. CPF/CNPJ é obrigatório no cadastro de clientes.

Conexão, leitura e `TimeLimiter` usam limite de 3 segundos. A criação de cobrança não é
repetida automaticamente: o endpoint do Asaas documenta `externalReference`, mas não uma
chave de idempotência. Após timeout ou 5xx, a aplicação consulta a cobrança pela referência
`orderflow-order-<id>`; se o resultado continuar inconclusivo, salva o pagamento como
`PENDING`, mantém o pedido em `AWAITING_PAYMENT` e devolve `processingMessage` ao cliente.
O retry exponencial (até 3 tentativas, 500 ms e 1 s de espera) é aplicado somente às consultas
seguras. Respostas 4xx nunca são repetidas.

O job de conciliação consulta a cada 15 minutos pagamentos pendentes entre 10 minutos e 7
dias, em lotes de até 200. ShedLock grava o lock no PostgreSQL usando o horário do banco;
apenas uma instância executa o lote. O tempo máximo padrão de 15 minutos supera os cerca de
10 minutos do pior caso esperado do lote e o mínimo de 1 minuto evita execuções muito
próximas. Se aumentar lote, timeout ou retry, recalcule o lock máximo.

Para acompanhar o job com JWT administrativo:

```text
GET /actuator/metrics/orderflow.payment.reconciliation.executions
GET /actuator/metrics/orderflow.payment.reconciliation.duration
GET /actuator/metrics/orderflow.payment.reconciliation.divergences
GET /actuator/metrics/orderflow.payment.reconciliation.errors
```

Configure alertas externos para crescimento de `divergences`, `not_found` ou `errors`.
`DIVERGENT` nunca é corrigido automaticamente: compare pedido, pagamento e cobrança no Asaas
antes de uma ação operacional.

Há circuitos independentes para criação, consulta e cancelamento. Cada circuito usa janela
de 20 chamadas, mínimo de 10, taxa de falha de 50%, abertura por 30 segundos e 3 chamadas em
half-open. Consulte, com JWT administrativo:

```text
GET /actuator/circuitbreakers
GET /actuator/circuitbreakerevents
GET /actuator/metrics/resilience4j.circuitbreaker.state
```

No painel/API do Asaas, configure um webhook com:

- URL: `https://<dominio-publico>/api/v1/webhooks/asaas`;
- token de autenticação igual a `ASAAS_WEBHOOK_TOKEN` e diferente da API key;
- eventos: `PAYMENT_CONFIRMED`, `PAYMENT_RECEIVED`,
  `PAYMENT_CREDIT_CARD_CAPTURE_REFUSED`, `PAYMENT_REPROVED_BY_RISK_ANALYSIS` e
  `PAYMENT_REFUNDED`;
- envio sequencial inicialmente, até a capacidade do ambiente ser medida.

O token deve possuir entre 32 e 255 caracteres. O endpoint responde 200 para eventos
processados, duplicados ou destinados à conciliação manual; falha transitória responde 500
para solicitar retry. Restrinja acesso aos IPs oficiais do Asaas no firewall/reverse proxy
quando a infraestrutura de destino permitir.

Para produção, altere os profiles e a URL:

```dotenv
SPRING_PROFILES_ACTIVE=prod,payment-asaas
ASAAS_BASE_URL=https://api.asaas.com/v3
```

Não ative cobranças reais antes de definir a política de retenção do payload, integrar as
métricas ao canal de alertas e aprovar os procedimentos operacionais de divergência e
estorno. Nunca exponha `ASAAS_API_KEY` ou `ASAAS_WEBHOOK_TOKEN` em logs, commits ou respostas
HTTP.

## Validar a configuração

O Compose deve resolver todas as variáveis obrigatórias sem iniciar containers:

```bash
docker compose -f docker-compose.prod.yml config --quiet
```

Se o comando falhar, corrija o `.env` antes de prosseguir.

## Construir e iniciar

```bash
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

O container da aplicação só inicia depois que PostgreSQL, Redis e RabbitMQ estiverem
saudáveis. O Flyway aplica migrations pendentes antes de o Hibernate validar o schema.

## Verificação pós-deploy

Execute na própria VPS ou por uma rota protegida do balanceador:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
```

Confirme também:

- containers sem loop de reinício;
- migrations V1 a V8 aplicadas uma única vez;
- tabela `shedlock` acessível e métrica de execução da conciliação presente;
- tabela `order_event_audit` recebendo somente eventos de transações confirmadas;
- exchanges e filas duráveis presentes e `order.events.dlq` sem ciclo de dead-letter;
- registro e login respondendo sem detalhes internos;
- logs contendo `correlationId`;
- PostgreSQL, Redis, AMQP e RabbitMQ Management inacessíveis pela internet;
- Swagger retornando 404/403 em produção, conforme esperado;
- certificado, redirecionamento HTTPS e renovação automática funcionando.

Use uma conta administrativa criada por procedimento operacional seguro. O profile `prod`
desabilita o bootstrap automático.

## Logs e diagnóstico

```bash
docker compose -f docker-compose.prod.yml logs --tail 200 app
docker compose -f docker-compose.prod.yml logs -f app
```

Os logs de produção são JSON. Nunca registre token JWT, senha, chave do gateway ou corpo com
dados sensíveis.

## Atualização

Antes da troca de versão:

1. execute `./mvnw verify` no commit que será implantado;
2. leia novas migrations e confirme compatibilidade com a versão anterior;
3. faça backup testado do PostgreSQL;
4. construa a nova imagem;
5. recrie a aplicação e acompanhe health check e logs.

```bash
git pull --ff-only
docker compose -f docker-compose.prod.yml build app
docker compose -f docker-compose.prod.yml up -d app
```

As migrations são forward-only. Se uma migration incompatível for aplicada, voltar somente a
imagem pode não ser suficiente; o plano de rollback deve considerar o schema e os dados.

## Backup mínimo do PostgreSQL

Exemplo conceitual, com destino fora do volume do container:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > orderflow.dump
```

Um backup só é válido depois de um teste de restauração. Automatize retenção, criptografia e
cópia para outro host ou armazenamento de objetos antes de tratar o ambiente como produção.
