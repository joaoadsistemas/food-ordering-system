# Food Ordering System

## Visão geral

Este repositório contém o microsserviço `order-service` de um sistema de pedidos de comida. Ele está organizado como um projeto **Maven multi-módulo** e segue a **Arquitetura Hexagonal (Ports and Adapters)**.

O objetivo arquitetural é manter o domínio de pedidos isolado de qualquer tecnologia externa, como HTTP, banco de dados, mensageria ou frameworks. O domínio define *o que* o sistema faz; os adaptadores definem *como* ele conversa com o mundo externo.

> **Estado atual (resumo):**
> - A base da arquitetura está montada e os módulos Maven estão estruturados.
> - O módulo compartilhado `common-domain` já possui as abstrações base (entidades, identificadores, value objects, exceções e eventos).
> - O núcleo do domínio de pedidos (`order-domain-core`) já possui as entidades principais (`Order`, `OrderItem`, `Product`) e as primeiras regras de negócio de validação de preço.
> - Os outros módulos (`order-application-service`, `order-application`, `order-data-access`, `order-messaging` e `order-container`) ainda estão vazios, contendo apenas os arquivos gerados por archetype.
> - O projeto ainda **não compila** por um problema no `common/pom.xml`, descrito na seção de problemas conhecidos.

---

## Tecnologias e requisitos

- **Java 21**
- **Maven**
- **Spring Boot 3.2.2** (herdado do `pom.xml` raiz)
- **Arquitetura Hexagonal**

---

## Estrutura do projeto

```text
food-ordering-system/
├── pom.xml                              # POM raiz: agrupa order-service e common
├── common/
│   ├── pom.xml                          # Agrupador de módulos comuns
│   └── common-domain/
│       └── src/main/java/.../domain/   # Base reutilizável do domínio
├── order-service/
│   ├── pom.xml                          # Agrupador do serviço de pedidos
│   ├── order-domain/
│   │   ├── pom.xml
│   │   ├── order-domain-core/           # Regras do pedido (AGREGADO Order)
│   │   └── order-application-service/   # Casos de uso e portas (vazio)
│   ├── order-application/               # Adaptadores de entrada REST (vazio)
│   ├── order-data-access/               # Adaptadores de saída: persistência (vazio)
│   ├── order-messaging/                 # Adaptadores de saída: mensageria (vazio)
│   └── order-container/                 # Composição e inicialização (vazio)
```

---

## Responsabilidade de cada módulo

### `common-domain`

Módulo compartilhado entre todos os serviços do sistema. Contém abstrações genéricas do Domain-Driven Design (DDD):

- **`BaseEntity<ID>`** (`com.food.ordering.system.domain.entity`): classe base para qualquer entidade com identidade.
- **`AggregateRoot<ID>`** (`com.food.ordering.system.domain.entity`): marca uma entidade como raiz de agregado. No DDD, a raiz controla a consistência de todo o agregado.
- **`BaseId<T>`** (`com.food.ordering.system.domain.valueObject`): classe base imutável para identificadores tipados.
- **Value objects comuns** (`com.food.ordering.system.domain.valueObject`):
  - `Money`: representa valores monetários com `BigDecimal`, garantindo precisão e operações seguras.
  - `CustomerId`, `OrderId`, `ProductId`, `RestaurantId`: identificadores tipados baseados em `UUID`.
  - `OrderStatus`: enumeração dos estados do pedido (`PENDING`, `PAID`, `APPROVED`, `CANCELLING`, `CANCELLED`).
- **`DomainException`** (`com.food.ordering.system.domain.exception`): classe base para exceções de domínio.
- **`DomainEvent<T>`** (`com.food.ordering.system.domain.event`): interface base para eventos de domínio (ainda vazia, servindo de contrato futuro).

> Este módulo não depende de Spring, banco de dados, HTTP ou Kafka. É puro Java.

### `order-domain-core`

É o núcleo do domínio do serviço de pedidos. Já implementa o agregado `Order` e suas entidades filhas.

- **Entidades** (`com.food.ordering.system.order.service.domain.entity`):
  - `Order`: raiz do agregado. Contém `customerId`, `restaurantId`, `deliveryAddress`, `price`, `items`, `trackingId`, `orderStatus` e `failureMessages`.
  - `OrderItem`: item de um pedido. Contém `product`, `quantity`, `price` e `subTotal`.
  - `Product`: representa um produto vinculado a um item.
- **Value objects específicos** (`com.food.ordering.system.order.service.domain.valueObject`):
  - `StreetAddress`: endereço de entrega (`street`, `postalCode`, `city`).
  - `TrackingId`: identificador de rastreamento do pedido.
  - `OrderItemId`: identificador sequencial de um item dentro do pedido.
- **Exceção de domínio** (`com.food.ordering.system.order.service.domain.exception`):
  - `OrderDomainException`: sinaliza violações nas regras de negócio do pedido.

### `order-application-service`

Ainda vazio. Deverá conter:

- Os casos de uso do sistema (por exemplo, *criar pedido*, *pagar pedido*, *cancelar pedido*).
- Os **ports** (portas) de entrada, que os adaptadores de entrada chamam.
- Os **ports** de saída, que o domínio e a aplicação usam para persistência e publicação de eventos.
- DTOs de comando e resposta.
- A coordenação entre domínio e adaptadores.

### `order-application`

Ainda vazio. Será o adaptador de entrada, provavelmente REST:

- Controllers Spring.
- DTOs de requisição e resposta.
- Mapeadores entre DTOs e comandos da aplicação.
- Tratamento global de erros HTTP.

### `order-data-access`

Ainda vazio. Será o adaptador de saída para persistência:

- Implementações das portas de persistência definidas em `order-application-service`.
- Entidades JPA, repositórios e mapeadores entre modelos de persistência e domínio.
- Integração com banco de dados relacional.

### `order-messaging`

Ainda vazio. Será o adaptador de saída para mensageria:

- Implementações das portas de publicação de eventos.
- Serialização e desserialização de mensagens.
- Integração com o broker de eventos (por exemplo, Kafka).

### `order-container`

Ainda vazio. Será o módulo de composição:

- Aplicação Spring Boot principal.
- Configuração de beans que ligam as portas às implementações concretas.
- Une todos os adaptadores ao núcleo da aplicação.

---

## Grafo de dependências Maven

```text
food-ordering-system
├── common
│   └── common-domain
└── order-service
    ├── order-domain
    │   ├── order-domain-core ──> common-domain
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

A seta significa “depende de”. As dependências apontam para dentro:

- `order-domain-core` é a camada mais interna.
- `order-application-service` usa o domínio para executar casos de uso.
- `order-application`, `order-data-access` e `order-messaging` são adaptadores externos.
- `order-container` conhece todos os componentes para realizar a composição.
- O domínio não depende de infraestrutura.

---

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

As dependências de código apontam para dentro:

- **Porta**: abstração definida pelo lado interno (aplicação ou domínio).
- **Adaptador**: implementação externa de uma porta ou ponto de entrada no sistema.

Se uma classe de domínio precisar importar algo de banco, controller ou broker, a dependência está invertida. A solução é extrair uma porta no lado interno e fazer o adaptador implementá-la.

---

## O que já funciona no domínio

### Agregado `Order`

A classe `Order` (`@/Users/joaoadsistemas/Documents/GitHub/food-ordering-system/order-service/order-domain/order-domain-core/src/main/java/com/food/ordering/system/order/service/domain/entity/Order.java`) é a raiz do agregado. Ela já possui:

- **Inicialização do pedido** (`initializeOrder()`):
  - Gera um `OrderId` aleatório com `UUID`.
  - Gera um `TrackingId` para rastreamento.
  - Define o status como `PENDING`.
  - Inicializa a lista de mensagens de falha.
  - Atribui IDs sequenciais aos itens do pedido.

- **Validação do pedido** (`validateOrder()`):
  - Garante que o pedido ainda não foi inicializado (`orderStatus` e `id` devem ser nulos).
  - Garante que o preço total é maior que zero.
  - Garante que a soma dos subtotais dos itens é igual ao preço total do pedido.

- **Validação de preço do item** (`validateItemPrice`):
  - Cada item é validado por `OrderItem.isPriceValid()`, que verifica:
    - O preço é maior que zero.
    - O preço do item é igual ao preço do produto.
    - `price × quantity` é igual ao `subTotal`.

### Exemplo de uso do `Builder`

```java
Order order = Order.builder()
        .customerId(new CustomerId(UUID.randomUUID()))
        .restaurantId(new RestaurantId(UUID.randomUUID()))
        .deliveryAddress(new StreetAddress(UUID.randomUUID(), "Rua A", "12345", "Cidade"))
        .price(new Money(new BigDecimal("100.00")))
        .items(List.of(
                OrderItem.builder()
                        .product(new Product(new ProductId(UUID.randomUUID()), "Pizza", new Money(new BigDecimal("50.00"))))
                        .quantity(2)
                        .price(new Money(new BigDecimal("50.00")))
                        .subTotal(new Money(new BigDecimal("100.00")))
                        .build()
        ))
        .build();

order.initializeOrder();
order.validateOrder();
```

Esse design torna o domínio testável sem banco de dados, sem Spring e sem HTTP.

---

## Fluxo esperado: criação de um pedido

Quando os adaptadores forem implementados, o fluxo será:

1. O cliente envia uma requisição HTTP para `order-application`.
2. O controller valida o formato e converte o DTO em um comando.
3. O controller chama a porta de entrada em `order-application-service`.
4. O serviço de aplicação busca dados necessários pelas portas de saída.
5. O serviço cria ou altera o agregado `Order` usando `order-domain-core`.
6. O domínio valida as invariantes. Uma violação gera `OrderDomainException`, sem depender de HTTP ou infraestrutura.
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

---

## Problemas conhecidos

### 1. `common/pom.xml` não declara `<packaging>pom</packaging>`

O arquivo `@/Users/joaoadsistemas/Documents/GitHub/food-ordering-system/common/pom.xml` é um projeto agregador (possui `<modules>`), mas está faltando a declaração `<packaging>pom</packaging>`. Isso faz com que o Maven rejeite o build com o erro:

```text
'packaging' with value 'jar' is invalid. Aggregator projects require 'pom' as packaging.
```

**Correção indicada:** adicionar `<packaging>pom</packaging>` logo após `<modelVersion>4.0.0</modelVersion>` em `common/pom.xml`.

### 2. Módulos externos ainda estão vazios

`order-application-service`, `order-application`, `order-data-access`, `order-messaging` e `order-container` não possuem código de produção. Apenas os templates gerados por archetype estão presentes.

### 3. Não há testes automatizados

Não existem testes unitários para o domínio nem testes de integração. Apenas arquivos `AppTest.java` de archetype estão presentes.

### 4. Transições de status do pedido ainda não existem

O enum `OrderStatus` já define os estados (`PENDING`, `PAID`, `APPROVED`, `CANCELLING`, `CANCELLED`), mas a classe `Order` ainda não possui métodos como `pay()`, `approve()` ou `cancel()`. Apenas a criação e a validação inicial estão implementadas.

### 5. `DomainEvent` é uma interface vazia

A interface `@/Users/joaoadsistemas/Documents/GitHub/food-ordering-system/common/common-domain/src/main/java/com/food/ordering/system/domain/event/DomainEvent.java` ainda não possui métodos. Eventos concretos como `OrderCreatedEvent` ou `OrderPaidEvent` ainda não foram criados.

---

## Executar o build

Na raiz do projeto:

```bash
mvn clean verify
```

Para compilar apenas o serviço de pedidos e suas dependências:

```bash
mvn -pl order-service -am clean verify
```

> **Atenção:** o build não passa no momento por causa do problema de `packaging` no `common/pom.xml`. Após corrigir esse ponto, o módulo `order-domain-core` deve compilar, pois seu código não depende de frameworks externos.

---

## Próximos passos sugeridos

1. **Corrigir o build** ajustando `common/pom.xml` para usar `<packaging>pom</packaging>`.
2. **Adicionar testes unitários** para o domínio em `order-domain-core`, cobrindo:
   - Criação e inicialização válida de um pedido.
   - Falha quando o preço total não bate com a soma dos itens.
   - Falha quando o preço de um item não corresponde ao preço do produto.
   - Falha quando o pedido já foi inicializado.
3. **Implementar transições de estado** em `Order`: `pay()`, `approve()`, `cancel()` etc.
4. **Criar eventos de domínio** em `order-domain-core` (por exemplo, `OrderCreatedEvent`, `OrderPaidEvent`).
5. **Implementar `order-application-service`**:
   - Definir ports de entrada e saída.
   - Implementar o caso de uso *CreateOrder*.
6. **Implementar `order-application`** com controllers REST e DTOs.
7. **Implementar `order-data-access`** com repositórios JPA.
8. **Implementar `order-messaging`** para publicar eventos.
9. **Implementar `order-container`** para ligar tudo via Spring Boot.
10. **Adicionar testes de integração** com adaptadores falsos (dublês) antes de conectar infraestrutura real.
