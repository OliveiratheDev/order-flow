# Architecture Decision Records

Este diretório preserva o contexto e os trade-offs das decisões arquiteturais do OrderFlow.
O código mostra o resultado; o ADR registra por que uma alternativa foi escolhida e quais
custos foram aceitos.

## Convenção

- use o arquivo [`template.md`](template.md) como ponto de partida;
- numere novos documentos sequencialmente no formato `000X-titulo-em-kebab-case.md`;
- escreva Contexto, Decisão, Alternativas e Consequências;
- depois de aceito, não reescreva um ADR para mudar a história;
- se a decisão mudar, crie outro ADR e relacione os status de substituição nos dois documentos.

O ADR-002 foi criado antes da formalização desta convenção e mantém o nome original para
preservar seu histórico. Os próximos documentos devem usar o próximo número disponível,
sem renumerar decisões aceitas.

## Índice

| ADR | Status | Decisão | Ticket |
|---|---|---|---|
| [ADR-001](0001-monolito-modular-com-payment-hexagonal.md) | Aceito | Monólito modular com `payment` em arquitetura hexagonal | OF-005 |
| [ADR-002](ADR-002-camadas-vs-hexagonal-em-pagamentos.md) | Aceito | Camadas versus Ports & Adapters em pagamentos | OF-040 |
