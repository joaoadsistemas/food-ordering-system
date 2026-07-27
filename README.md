# Food Ordering System

## Visão geral

Este repositório contém a base do `order-service`, organizado em módulos Maven e preparado para seguir **Arquitetura Hexagonal (Ports and Adapters)**.

A regra central é manter o domínio de pedidos isolado de HTTP, banco de dados, mensageria e frameworks. O domínio define o que precisa ser feito; os adaptadores definem como a aplicação conversa com o mundo externo.

> **Estado atual:** os módulos e seus `pom.xml` estão estruturados, mas o código de produção ainda não contém as entidades, casos de uso, portas e adaptadores do pedido. Os arquivos Java presentes são templates de archetype. O fluxo abaixo documenta a organização esperada para a implementação do serviço.

## Tecnologias e requisitos

- Java 21
- Maven
- Spring Boot 3.2.2, herdado pelo `pom.xml` raiz
- Arquitetura Hexagonal

## Estrutura do projeto

```text
food-ordering-system/
├── pom.xml
└── order-service/
    ├── pom.xml
    ├── order-domain/
    │   ├── pom.xml
    │   ├── order-domain-core/
    │   └── order-application-service/
    ├── order-application/
    ├── order-data-access/
    ├── order-messaging/
    └── order-container/
```

## Responsabilidade de cada módulo

### `order-domain-core`

É o núcleo do domínio. Deve conter entidades e agregados (`Order` e seus itens), value objects, eventos de domínio, exceções de negócio e invariantes do pedido.

Este módulo **não deve depender** de Spring, banco de dados, HTTP, Kafka ou qualquer outro detalhe de infraestrutura.

### `order-application-service`

Contém os casos de uso e coordena o domínio. Deve conter serviços de aplicação, comandos, resultados, portas de entrada e portas de saída para dependências como persistência e publicação de eventos.

Depende de `order-domain-core`, mas o domínio não conhece este módulo.

### `order-application`

É o adaptador de entrada. Deve receber requisições externas, converter DTOs em comandos e chamar as portas de entrada de `order-application-service`.

Exemplos: controllers REST, DTOs, mapeadores e tratamento de erros HTTP. Não deve implementar regras de negócio.

### `order-data-access`

É um adaptador de saída. Deve implementar as portas de persistência definidas pela aplicação, contendo repositórios concretos, mapeamentos, integração com banco e conversão entre modelos de persistência e domínio.

A aplicação conhece somente a interface da porta; a implementação fica neste módulo.

### `order-messaging`

É o adaptador de mensageria. Deve implementar as portas de publicação ou consumo de eventos, incluindo serialização, desserialização e integração com o broker.

O caso de uso não deve conhecer a API ou o tipo de broker utilizado.

### `order-container`

É o módulo de composição e inicialização. Reúne os módulos, configura beans e conecta as implementações concretas às portas da aplicação.

É nele que a aplicação deixa de depender apenas de abstrações e passa a usar os adaptadores reais.

## Grafo de dependências Maven

```text
food-ordering-system
└── order-service
    ├── order-domain
    │   ├── order-domain-core
    │   └── order-application-service ──> order-domain-core
    ├── order-application ──────────────> order-application-service
    ├── order-data-access ───────────────> order-application-service
    ├── order-messaging ─────────────────> order-application-service
    └── order-container ─────────────────> order-domain-core
                                          order-application-service
                                          order-application
                                          order-data-access
                                          order-messaging
```

A seta significa “depende de”. Assim:

- `order-domain-core` é a camada mais interna;
- `order-application-service` usa o domínio para executar casos de uso;
- `order-application`, `order-data-access` e `order-messaging` são adaptadores externos;
- `order-container` conhece todos os componentes para realizar a composição;
- o domínio não depende de infraestrutura.

Os módulos são agregados em `order-service/pom.xml`. As versões dos módulos internos são centralizadas no `dependencyManagement` do `pom.xml` raiz.

## Como a Arquitetura Hexagonal se aplica

```text
                         MUNDO EXTERNO
                               │
             ┌─────────────────┴─────────────────┐
             │                                   │
      Adaptador de entrada                 Adaptadores de saída
       order-application             data-access / messaging
             │                                   ▲
             │ chama                             │ implementam
             ▼                                   │
    Porta de entrada                     Portas de saída
             │                                   ▲
             └──────────────┐    ┌───────────────┘
                            ▼    │
                  order-application-service
                            │
                            ▼
                   order-domain-core
```

As dependências de código apontam para dentro: adaptadores dependem da aplicação, e a aplicação depende do domínio. Uma porta é uma abstração definida pelo lado interno; um adaptador é uma entrada externa ou implementação dessa abstração.

## Fluxo esperado: criação de um pedido

1. O cliente envia uma requisição para `order-application`.
2. O controller valida o formato e converte o DTO em um comando.
3. O controller chama a porta de entrada em `order-application-service`.
4. O serviço de aplicação busca dados necessários pelas portas de saída.
5. O serviço cria ou altera o agregado `Order` usando `order-domain-core`.
6. O domínio valida as invariantes. Uma violação gera uma exceção de negócio sem depender de HTTP ou infraestrutura.
7. O serviço solicita a persistência pela porta de saída.
8. `order-data-access` executa a operação no banco e converte o resultado.
9. Se existir um evento, o serviço solicita sua publicação por outra porta de saída.
10. `order-messaging` publica o evento no broker.
11. O resultado volta ao controller, que o converte em resposta HTTP.

```text
HTTP
 │
 ▼
order-application  (DTO -> comando)
 │
 ▼
porta de entrada
 │
 ▼
order-application-service
 │
 ├──> order-domain-core       (regras do pedido)
 ├──> porta de persistência ──> order-data-access
 └──> porta de mensageria ────> order-messaging

resultado -> order-application (resultado -> DTO) -> HTTP response
```

## Como rastrear uma dependência

1. Coloque a regra de negócio em `order-domain-core`.
2. Defina o caso de uso e suas interfaces em `order-application-service`.
3. Crie o adaptador de entrada em `order-application`.
4. Implemente persistência em `order-data-access` e mensageria em `order-messaging`.
5. Registre as implementações no `order-container`.
6. Garanta que o domínio continue testável sem adaptadores.

Se uma classe de domínio precisar importar banco, controller ou broker, a dependência está invertida. Extraia uma interface para uma porta no lado interno e faça o adaptador implementá-la.

## Executar o build

Na raiz do projeto:

```bash
mvn clean verify
```

Para compilar o serviço de pedidos e suas dependências:

```bash
mvn -pl order-service -am clean verify
```

## Próximos passos

- Implementar entidades e regras em `order-domain-core`;
- Definir portas e casos de uso em `order-application-service`;
- Criar controllers e DTOs em `order-application`;
- Implementar persistência em `order-data-access`;
- Implementar eventos em `order-messaging`;
- Configurar a composição em `order-container`;
- Criar testes unitários do domínio e testes dos casos de uso com adaptadores falsos.
