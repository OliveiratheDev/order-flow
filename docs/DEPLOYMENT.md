# Implantação

## Escopo deste guia

O repositório entrega uma imagem Docker da API e um Compose de produção com OrderFlow,
PostgreSQL e Redis. Provisionamento da VPS, DNS, TLS, firewall, backups externos e gerenciador
de segredos dependem da plataforma escolhida e não são automatizados nesta baseline.

## Artefatos

- [`Dockerfile`](../Dockerfile): build multi-stage com Java 21, camadas do Spring Boot e
  runtime não-root;
- [`docker-compose.yml`](../docker-compose.yml): ambiente local com portas de banco expostas;
- [`docker-compose.prod.yml`](../docker-compose.prod.yml): ambiente de implantação, sem
  publicar PostgreSQL ou Redis no host;
- [`.env.example`](../.env.example): catálogo de variáveis, sem valores reais.

## Preparar o ambiente

1. Instale Docker Engine com Compose v2.
2. Libere somente SSH, HTTP e HTTPS no firewall; não publique `5432` nem `6379`.
3. Configure um reverse proxy com certificado válido para encaminhar HTTPS à porta da API.
4. Crie armazenamento e política de backup para o volume PostgreSQL.
5. Mantenha o arquivo `.env` fora do Git e restrito ao usuário do serviço.

Crie `.env` a partir do exemplo e substitua todos os placeholders. No mínimo:

```dotenv
DB_NAME=orderflow
DB_USER=orderflow_app
DB_PASSWORD=<senha-aleatoria-forte>
REDIS_PASSWORD=<outra-senha-aleatoria-forte>
JWT_SECRET=<segredo-aleatorio-com-ao-menos-32-bytes>
APP_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

Não reutilize senha do banco como segredo JWT. Uma alteração de `JWT_SECRET` invalida tokens
emitidos anteriormente.

## Gateway de pagamento

O profile `prod` isolado mantém o simulador determinístico do portfólio. Ele não representa
uma integração financeira real.

Para um fornecedor real, configure:

```dotenv
SPRING_PROFILES_ACTIVE=prod,payment-http
PAYMENT_GATEWAY_BASE_URL=https://api.fornecedor.example
PAYMENT_GATEWAY_API_KEY=<chave-do-fornecedor>
```

Antes de aceitar cobranças reais, valide contrato, assinatura/autenticação, timeouts, retries,
idempotência do fornecedor, webhooks, conciliação, auditoria e requisitos regulatórios. O
adaptador HTTP desta baseline é uma demonstração arquitetural, não uma integração certificada.

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

O container da aplicação só inicia depois que PostgreSQL e Redis estiverem saudáveis. O
Flyway aplica migrations pendentes antes de o Hibernate validar o schema.

## Verificação pós-deploy

Execute na própria VPS ou por uma rota protegida do balanceador:

```bash
curl --fail http://127.0.0.1:8080/actuator/health
```

Confirme também:

- containers sem loop de reinício;
- migrations V1 a V4 aplicadas uma única vez;
- registro e login respondendo sem detalhes internos;
- logs contendo `correlationId`;
- PostgreSQL e Redis inacessíveis pela internet;
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
