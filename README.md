# OrderFlow

É uma Plataforma de Pedidos e Cobrança para E-commerce — projeto de estudo e portfólio focado no ecossistema Spring completo, arquitetura e padrões de projeto.

## Pré-requisitos
- Ter a JDK 17+
- Docker Desktop
- Maven

## Como rodar

### 1. Subir a infraestrutura

`docker compose up -d`

### 2. Rodar a aplicação

`nmvn spring:boot:run "-Dspring-boot.run.profiles=dev`

## Estrutura do projeto
- `catalog` -> produtos e categorias (arquitetura em camada)
- `identity` -> usuários, autenticação, autorizaçao
- `order` ->  pedidos, máquina de estados, descontos
- `payment` -> cobrança (ARQUITETURA HEXAGONAL: domain / ports / adapters)
- `notification` — consumidores de eventos, notificações
- `shared` -> erros, config, eventos, utilitários
