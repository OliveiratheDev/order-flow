# ADR-002 — Camadas versus arquitetura hexagonal em pagamentos

- Status: aceita
- Data: 2026-08-08
- Decisão relacionada: OF-040

## Contexto

O OrderFlow é um monólito modular. Catálogo, identidade e pedidos operam principalmente
sobre recursos internos e usam uma arquitetura em camadas. O módulo de pagamentos é
diferente: ele precisa chamar um gateway externo, sujeito a indisponibilidade, mudança de
contrato e substituição de fornecedor.

Permitir que DTOs, exceções ou clientes HTTP do fornecedor atravessem o módulo faria a
regra de cobrança depender diretamente dessa infraestrutura. Isso aumentaria o custo de
testar e de trocar o gateway.

## Decisão

O módulo `payment` adota Ports & Adapters:

- `domain` contém somente Java e protege as regras de valor, estado e resultado da cobrança;
- `ports.in` expõe os casos de uso consumidos pelo adaptador REST;
- `ports.out` descreve persistência, integração com pedidos e gateway na linguagem interna;
- `application` coordena o caso de uso e a transação;
- `adapters` traduz HTTP, JPA e os contratos externos.

O gateway padrão é um simulador determinístico. Um segundo simulador de recusa pode ser
ativado com o perfil `payment-declined`. O adaptador HTTP é ativado com `payment-http` e
recebe URL e chave exclusivamente por `PAYMENT_GATEWAY_BASE_URL` e
`PAYMENT_GATEWAY_API_KEY`, sem valores padrão. Nenhuma dessas substituições altera o
domínio ou as portas.

O valor da cobrança é obtido do total persistido do pedido. A requisição do cliente informa
somente o pedido e o método de pagamento.

## Comparação

| Critério | Arquitetura em camadas | Arquitetura hexagonal |
|---|---|---|
| Custo inicial | Baixo | Maior, com portas e traduções explícitas |
| Teste sem infraestrutura | Pode exigir mocks de detalhes técnicos | Usa implementações simples das portas |
| Troca de fornecedor | Pode causar refatoração transversal | Exige um novo adaptador |
| Curva de leitura | Familiar para CRUD | Exige conhecer a direção das dependências |
| Quando compensa | Operações sobre o banco próprio | Integrações externas substituíveis ou instáveis |

## Consequências positivas

- o domínio de pagamentos compila apenas com o JDK;
- DTOs e erros do gateway ficam confinados ao adaptador;
- testes de domínio e aplicação não dependem de rede ou Spring;
- o CI usa um gateway determinístico;
- a restrição de um pagamento por pedido também existe no banco.

## Consequências negativas

- o repositório passa a ter duas convenções arquiteturais, aumentando a carga de leitura;
- uma funcionalidade equivalente exige mais interfaces, classes e conversões;
- `PaymentAmount` e os DTOs do gateway representam a mesma informação em formatos
  diferentes e precisam permanecer sincronizados;
- a chamada ao gateway ocorre dentro do caso de uso transacional nesta baseline; com um
  fornecedor real e latência relevante, será necessário separar a chamada externa da
  transação de banco e adotar uma estratégia explícita de consistência.

## Alternativas rejeitadas

- Aplicar arquitetura hexagonal a todos os módulos: o custo não se justifica para CRUDs
  internos e apagaria a comparação pedagógica do projeto.
- Usar diretamente o DTO do fornecedor na aplicação: mantém o acoplamento que a porta
  deveria eliminar.
- Depender do sandbox externo nos testes: tornaria o build dependente de rede e da
  disponibilidade de terceiros.
