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

1. PostgreSQL e Redis estão `healthy`;
2. `JWT_SECRET` possui ao menos 32 bytes;
3. credenciais e nomes do banco coincidem entre app e PostgreSQL;
4. a migration mais recente foi aplicada sem erro;
5. a porta do host está livre.

O health check interno usa `/actuator/health` na porta `8080` do container, mesmo quando a
porta do host é diferente.

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
A confirmação assíncrona depende do webhook da demanda OF-041.

## Caracteres acentuados aparecem incorretamente no PowerShell

Os arquivos são UTF-8. No Windows PowerShell antigo, leia explicitamente com:

```powershell
Get-Content -Encoding UTF8 README.md
```

Isso é uma limitação de exibição do terminal e não altera o conteúdo versionado.
