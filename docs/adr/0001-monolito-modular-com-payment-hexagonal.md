# ADR-001 — Monólito modular com `payment` em arquitetura hexagonal

## Status

Aceito em 2026-08-09.

Decisão relacionada: OF-005.

## Contexto

O OrderFlow precisa exercitar catálogo, identidade, pedidos, pagamentos e notificações sem
perder a capacidade de executar, testar e implantar a aplicação como uma unidade. O projeto
é mantido por uma pessoa e ainda está estabilizando o domínio. Separá-lo em processos agora
adicionaria contratos de rede, descoberta e configuração de serviços, observabilidade
distribuída, tolerância a falhas e estratégias de consistência antes que esses custos resolvessem
um problema observado.

Um monólito sem fronteiras também seria inadequado: os domínios têm ritmos e regras
diferentes. Dependências livres entre packages transformariam mudanças locais em alterações
transversais e dificultariam uma separação futura. Precisamos, portanto, de isolamento lógico
sem assumir o custo operacional de isolamento físico.

O módulo `payment` possui uma força adicional. Ele integra com um gateway externo cujo
contrato, latência, disponibilidade e fornecedor não são controlados pelo OrderFlow. Essa
dependência não deve determinar o modelo de cobrança nem impedir testes quando o gateway
estiver indisponível. Catálogo, identidade e pedidos, por outro lado, concentram nesta fase
operações sobre o banco da própria aplicação e não apresentam o mesmo benefício para portas
e adaptadores em todos os seus casos de uso.

## Decisão

Nós vamos construir o OrderFlow como um monólito modular, com uma única aplicação e um
único processo de implantação. Cada domínio terá package raiz próprio e só compartilhará
contratos deliberados por meio de `shared` ou de interfaces explícitas.

Catálogo, identidade, pedidos e notificações usarão arquitetura em camadas: controllers
traduzem HTTP, services coordenam casos de uso e transações, repositories persistem e as
entidades protegem invariantes.

O módulo `payment` usará Ports & Adapters. Seu domínio não dependerá de Spring, HTTP, JPA,
mensageria nem do SDK de um gateway. Portas de entrada exporão os casos de uso; portas de
saída representarão persistência, consulta de pedidos e cobrança; adaptadores traduzirão REST,
banco e fornecedores externos. A estrutura de packages, este ADR e o ADR-002 sinalizam
explicitamente que `payment` segue uma convenção diferente do restante do monólito.

Esta decisão será revista quando ao menos um sinal observável ocorrer:

- dois ou mais times precisarem implantar módulos em cadências independentes e o pipeline
  compartilhado bloquear entregas de forma recorrente;
- um módulo exigir escala ou disponibilidade incompatível com o restante da aplicação,
  comprovada por métricas e testes de carga;
- falhas ou consumo de recursos de um módulo afetarem repetidamente os demais, mesmo após
  isolamento dentro do processo;
- limites transacionais e contratos entre módulos estiverem estáveis o suficiente para assumir
  consistência distribuída conscientemente.

Esses gatilhos autorizam uma nova análise; eles não tornam microsserviços a solução automática.

## Alternativas consideradas

### Microsserviços desde o início

Descartada porque um time de uma pessoa pagaria imediatamente pelo versionamento de
contratos, comunicação de rede, múltiplos pipelines, rastreamento distribuído, tolerância a
falhas e consistência eventual. Ainda não há pressão independente de escala ou implantação
que compense esse custo, e dividir cedo cristalizaria fronteiras de um domínio em formação.

### Monólito em camadas puro, inclusive em `payment`

Descartada porque o gateway externo é uma dependência instável e substituível. Um service
acoplado ao cliente HTTP ou aos DTOs do fornecedor contaminaria a regra de cobrança,
dificultaria testes isolados e aumentaria o alcance de uma troca de integração justamente no
módulo em que a inversão de dependência traz retorno concreto.

### Arquitetura hexagonal em todos os módulos

Descartada porque criar portas e adaptadores para cada CRUD que conversa somente com o
banco da aplicação acrescentaria interfaces, conversões e caminhos de navegação sem uma
segunda implementação ou fronteira externa real. A cerimônia reduziria a legibilidade do
catálogo e de identidade sem comprar isolamento adicional relevante nesta fase.

## Consequências

### Positivas

- há um único artefato para construir, testar, observar e implantar;
- transações locais preservam consistência nos fluxos que cruzam dados relacionados;
- packages de domínio mantêm fronteiras explícitas e permitem extração futura orientada por
  evidências;
- regras de pagamento podem ser testadas sem rede, banco ou gateway real;
- trocar o fornecedor de pagamento exige um novo adaptador, sem alterar o domínio.

### Negativas

- uma falha grave ou pressão de recursos no processo pode afetar todos os módulos;
- o banco e o pipeline compartilhados exigem disciplina para evitar acoplamento entre
  domínios;
- duas convenções arquiteturais no mesmo repositório aumentam a carga de aprendizado e
  podem levar uma implementação para o package errado;
- a separação futura em serviços não será gratuita: contratos, dados, observabilidade e
  consistência precisarão ser redesenhados para a rede;
- Ports & Adapters cria mais tipos e conversões dentro de `payment` do que uma solução em
  camadas direta.

### Neutras

- modularidade é garantida principalmente por convenção, revisão e testes; não há isolamento
  de processo entre os domínios;
- decisões futuras que alterem esta arquitetura serão registradas em um novo ADR, preservando
  este documento como histórico.
