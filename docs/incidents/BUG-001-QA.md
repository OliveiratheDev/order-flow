# Checklist de reteste — BUG-001

## Objetivo

Confirmar que um pedido recusado não modifica o estoque e que o fluxo válido continua funcionando.

## Dados necessários

| Produto | Estoque | Quantidade no pedido |
|---|---:|---:|
| A | 10 | 2 |
| B | 1 | 2 |
| C | 10 | 2 |

Cadastre os produtos na ordem A, B e C. Use os identificadores reais retornados pela API.

Para preparar A e B, siga a seção **Preparação** de [BUG-001.md](BUG-001.md). Não invente IDs: use os
IDs retornados pelos endpoints de criação.

## CT-001 — Pedido recusado

1. Execute exatamente o cenário descrito em [BUG-001.md](BUG-001.md), usando A e B.
2. Consulte os pedidos.
3. Consulte os estoques pela listagem de produtos.

Resultado esperado:

- pedido recusado;
- nenhum pedido novo;
- estoque de A igual a `10`;
- estoque de B igual a `1`.

## CT-002 — Pedido válido

1. Crie um pedido com duas unidades de A e duas unidades de C.
2. Consulte o pedido criado.
3. Consulte os estoques.

Resultado esperado:

- pedido criado com sucesso;
- exatamente dois itens no pedido;
- estoque de A igual a `8`;
- estoque de C igual a `8`.

## CT-003 — Contrato da recusa

No cenário CT-001, confirme que a resposta:

- utiliza o status previsto pelo contrato atual;
- apresenta uma mensagem de negócio compreensível;
- não expõe stack trace, SQL, credenciais ou nomes internos;
- não cria registros de pedido.

## Registro do reteste

| Caso | Resultado | Evidência | Observação |
|---|---|---|---|
| CT-001 | Pendente | — | — |
| CT-002 | Pendente | — | — |
| CT-003 | Pendente | — | — |

## Critério de aprovação

O BUG-001 pode ser aprovado pelo QA quando os três casos passarem e o teste automatizado do cenário
principal estiver aprovado junto com a suíte relevante.
