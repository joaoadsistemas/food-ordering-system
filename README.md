# Food Ordering System — Aula: DDD + Arquitetura Hexagonal na prática

> Este README foi reescrito como um **guia didático**, não como documentação de status de projeto. A ideia é que você consiga ler de cima para baixo e entender **por que** cada peça existe, **o que** ela faz e **como** elas se encaixam — usando o código real deste repositório como exemplo, e usando como fio condutor o `POST /orders` que você testou no Postman/Insomnia.

---

## 0. O que esta aplicação realmente é hoje

Antes de qualquer conceito, um alinhamento de expectativas importante — porque isso explica sua sensação de "abstrato":

Este repositório implementa, até agora, **apenas duas fatias do hexágono**:

1. `order-domain-core` → o **domínio puro** (as regras de negócio do pedido, sem framework nenhum).
2. `order-application-service` → a **camada de aplicação** (os casos de uso que orquestram o domínio) — incluindo as **portas** (interfaces) que os adaptadores externos deveriam implementar.

Os módulos que fariam a aplicação **rodar de verdade** ainda são só esqueletos gerados por `maven-archetype` (só têm `App.java`/`AppTest.java` de exemplo, sem nenhuma linha de código real):

- `order-application` → seria o adaptador **REST** (os `@RestController`).
- `order-data-access` → seria o adaptador de **persistência** (JPA + banco de dados).
- `order-messaging` → seria o adaptador de **mensageria** (Kafka).
- `order-container` → seria o módulo Spring Boot que **liga tudo** (o `main()`).

**Isso explica o print que você me mandou.** O `POST http://localhost:8181/orders` com aquele JSON é o request que a aplicação **vai aceitar quando `order-application` e `order-container` existirem** — hoje, se você rodasse esse projeto, não haveria nenhum servidor HTTP escutando na porta 8181, porque o `order-container` (quem sobe o Spring Boot) está vazio. É por isso que a aplicação é "falsa"/simulada neste momento: **a regra de negócio já existe e já pode ser testada isoladamente (com testes unitários, sem HTTP e sem banco), mas o "fio elétrico" que liga o mundo externo a essa regra ainda não foi implementado.**

Isso, aliás, **não é um defeito** — é a essência da Arquitetura Hexagonal: você consegue construir e validar 100% da regra de negócio antes mesmo de decidir se vai expor ela via REST, gRPC, mensageria, ou CLI. O curso do Ali Gelenler propositalmente começa pelo centro (domínio) e vai construindo os adaptadores por fora, módulo a módulo.

Com isso em mente, vamos aos conceitos.

---

## 1. DDD (Domain-Driven Design) em poucas palavras

DDD é uma forma de desenhar software onde o **código reflete a linguagem e as regras do negócio** (o "domínio"), em vez de refletir tabelas de banco de dados ou frameworks. Os blocos de construção ("tactical patterns") que aparecem neste projeto são:

- **Entity (Entidade):** um objeto que tem **identidade própria** (um ID) que persiste ao longo do tempo, mesmo que seus atributos mudem. Ex.: `OrderItem`, `Product`.
- **Value Object (Objeto de Valor):** um objeto que **não tem identidade**, é definido inteiramente pelos seus atributos, e normalmente é imutável. Dois VOs com os mesmos valores são "iguais". Ex.: `Money`, `StreetAddress`, `TrackingId`, `CustomerId`.
- **Aggregate (Agregado) / Aggregate Root (Raiz do Agregado):** um conjunto de entidades e VOs tratados como uma **unidade de consistência**. Só a raiz do agregado é acessível de fora; ela garante que as regras internas nunca fiquem em estado inválido. Ex.: `Order` é a raiz; `OrderItem` só existe dentro de um `Order`.
- **Domain Service (Serviço de Domínio):** uma regra de negócio que **não pertence naturalmente a uma única entidade**, geralmente porque envolve mais de um agregado (ex.: `Order` + `Restaurant`). Ex.: `OrderDomainService`.
- **Domain Event (Evento de Domínio):** um fato imutável que aconteceu no domínio ("o pedido foi criado", "o pedido foi pago"). Ex.: `OrderCreatedEvent`, `OrderPaidEvent`, `OrderCancelledEvent`.
- **Repository (Repositório):** uma abstração para carregar/persistir agregados, **sem expor detalhes de banco de dados** para o domínio. Ex.: `OrderRepository`.
- **Application Service (Serviço de Aplicação):** orquestra um caso de uso (ex.: "criar pedido"), chamando repositórios, serviços de domínio e publicadores de eventos — mas **sem conter regra de negócio**. Ex.: `OrderApplicationService`.

A diferença mais importante para fixar: **Domain Service tem regra de negócio. Application Service não tem regra de negócio, só orquestra.**

---

## 2. Arquitetura Hexagonal (Ports & Adapters) em poucas palavras

A ideia central: o **domínio fica no centro** e não conhece nada do mundo externo (HTTP, banco, Kafka, frameworks). Tudo que o domínio precisa do mundo externo (persistir um pedido, publicar um evento) é declarado como uma **interface** — chamada de **porta** — definida *dentro* da camada de aplicação/domínio.

Quem implementa essa interface é um **adaptador**, que fica *fora*, em outro módulo Maven:

```text
                     MUNDO EXTERNO
       ┌────────────────────┬────────────────────┐
       │ Adaptador de       │  Adaptadores de     │
       │ entrada (driving)  │  saída (driven)     │
       │ order-application  │  order-data-access  │
       │  (REST controller) │  order-messaging     │
       └─────────┬──────────┴──────────┬──────────┘
                  │ chama                │ implementa
                  ▼                      │
          PORTA DE ENTRADA        PORTA DE SAÍDA
      OrderApplicationService   OrderRepository,
                  │             OrderCreatedPaymentRequestMessagePublisher...
                  ▼                      ▲
            order-application-service ───┘
             (casos de uso / orquestração)
                  │
                  ▼
             order-domain-core
          (regras de negócio puras)
```

Duas categorias de porta neste projeto:

- **Portas de entrada** (quem chama o domínio de fora): `OrderApplicationService`, `PaymentResponseMessageListener`, `RestaurantApprovalResponseListener` — ficam em `.../ports/input/...`.
- **Portas de saída** (o que o domínio precisa do mundo externo): `OrderRepository`, `CustomerRepository`, `RestaurantRepository`, `OrderCreatedPaymentRequestMessagePublisher`, `OrderCancelledPaymentRequestMessagePublisher`, `OrderPaidRestaurantRequestMessagePublisher` — ficam em `.../ports/output/...`.

**Regra de ouro:** a seta de dependência do código sempre aponta **para dentro**. `order-data-access` pode depender de `order-application-service` (para implementar `OrderRepository`), mas `order-application-service` **jamais** pode depender de `order-data-access`. Isso é o que te permite trocar Postgres por MongoDB, ou Kafka por RabbitMQ, sem tocar uma linha da regra de negócio.

---

## 3. Estrutura de módulos Maven e o que cada um representa no hexágono

```text
food-ordering-system/
├── pom.xml                                # POM raiz (agrupa "common" e "order-service")
├── common/
│   └── common-domain/                     # Blocos de DDD reutilizáveis por qualquer serviço
└── order-service/
    ├── order-domain/
    │   ├── order-domain-core/             # ★ DOMÍNIO do pedido (regras puras) — IMPLEMENTADO
    │   └── order-application-service/     # ★ CASOS DE USO + PORTAS               — IMPLEMENTADO
    ├── order-application/                 # Adaptador de entrada REST             — vazio (esqueleto)
    ├── order-data-access/                 # Adaptador de saída (JPA/DB)           — vazio (esqueleto)
    ├── order-messaging/                   # Adaptador de saída (Kafka)            — vazio (esqueleto)
    └── order-container/                   # Módulo Spring Boot que liga tudo      — vazio (esqueleto)
```

### `common-domain` (`common/common-domain`)

Não depende de Spring, JPA ou nada externo — é Java puro. Contém as abstrações genéricas de DDD que **qualquer** microsserviço do sistema (order, payment, restaurant, customer...) vai reaproveitar:

- `com.food.ordering.system.domain.entity.BaseEntity<ID>` — classe-base de qualquer Entity: guarda um `id` e define `equals`/`hashCode` **baseados no ID**, não nos atributos (isso é a diferença fundamental entre Entity e Value Object).
- `com.food.ordering.system.domain.entity.AggregateRoot<ID>` — apenas marca semanticamente que uma classe é raiz de agregado (hoje é um `extends BaseEntity` vazio, mas comunica intenção).
- `com.food.ordering.system.domain.valueObject.BaseId<T>` — classe-base para IDs tipados e imutáveis (em vez de passar `UUID` cru por todo o sistema, você cria `OrderId`, `CustomerId`, `RestaurantId`, `ProductId`, cada um "carimbado" com seu próprio tipo — isso evita, por exemplo, passar um `RestaurantId` onde se esperava um `CustomerId`, erro que o compilador pegaria).
- `Money` — Value Object que encapsula `BigDecimal` e centraliza as regras de dinheiro (soma, subtração, multiplicação, arredondamento com `RoundingMode.HALF_EVEN`, comparação "maior que zero"). Isso evita que a lógica de arredondamento fique espalhada pelo código.
- `OrderStatus` (`PENDING, PAID, APPROVED, CANCELLING, CANCELLED`), `PaymentStatus` (`COMPLETED, CANCELLED, FAILED`), `OrderApprovalStatus` (`APPROVED, REJECTED`) — enums que representam os estados possíveis das máquinas de estado do sistema.
- `com.food.ordering.system.domain.exception.DomainException` — exceção-base para violações de regra de negócio (sem depender de HTTP; quem traduzir isso para um `400 Bad Request` será o adaptador REST, futuramente).
- `com.food.ordering.system.domain.event.DomainEvent<T>` — marker interface para eventos de domínio.
- `com.food.ordering.system.domain.event.publisher.DomainEventPublisher<T>` — porta de saída genérica: `void publish(T domainEvent)`. Cada publicador específico (pagamento, aprovação do restaurante) estende essa interface.

### `order-domain-core`

O coração do microsserviço de pedidos — regras de negócio, zero dependência de framework. Detalhado na seção 4.

### `order-application-service`

Os casos de uso ("criar pedido", "rastrear pedido") e as portas de entrada/saída específicas do domínio de pedidos. Detalhado na seção 5.

### `order-application`, `order-data-access`, `order-messaging`, `order-container`

Ainda são esqueletos de archetype Maven (sem código de produção). São os adaptadores que, quando implementados, vão:

- `order-application`: expor o `POST /orders` (e `GET /orders/{trackingId}`) via `@RestController`, receber o JSON do Postman, validar formato e converter em `CreateOrderCommand`.
- `order-data-access`: implementar `OrderRepository`, `CustomerRepository`, `RestaurantRepository` usando JPA/Postgres.
- `order-messaging`: implementar `OrderCreatedPaymentRequestMessagePublisher` (e os demais publishers) usando Kafka, e também os `@KafkaListener` que chamam `PaymentResponseMessageListener`/`RestaurantApprovalResponseListener`.
- `order-container`: ter a classe `@SpringBootApplication` e o `application.yml` (porta 8181, datasource, kafka brokers etc.), além dos `@Bean`/`@Configuration` que "casam" cada porta com seu adaptador concreto.

---

## 4. O domínio (`order-domain-core`) em detalhe

### 4.1. O agregado `Order`

`Order extends AggregateRoot<OrderId>` (`order-service/order-domain/order-domain-core/.../entity/Order.java`). Campos:

```java
private final CustomerId customerId;
private final RestaurantId restaurantId;
private final StreetAddress deliveryAddress;
private final Money price;
private final List<OrderItem> items;

private TrackingId trackingId;
private OrderStatus orderStatus;
private List<String> failureMessages;
```

Repare: `customerId` e `restaurantId` **não são as entidades inteiras**, são só os IDs. Isso é uma regra clássica de DDD: **um agregado não guarda referência direta a outro agregado**, só ao seu ID. `Order` não conhece o `Customer` nem o `Restaurant` completos — só sabe "a quem" e "de qual restaurante" pertence.

A máquina de estados do pedido, implementada como métodos do próprio agregado (isso é "comportamento rico", em oposição a um DTO anêmico que só tem getters/setters):

| Método | Transição válida | O que faz |
|---|---|---|
| `initializeOrder()` | (novo) | Gera `OrderId` e `TrackingId` aleatórios, status vira `PENDING`, zera `failureMessages`, atribui IDs sequenciais aos `OrderItem` |
| `validateOrder()` | antes de `initializeOrder()` | Valida que o pedido ainda não foi inicializado, que o preço total é > 0, e que a soma dos subtotais dos itens bate com o preço total |
| `pay()` | `PENDING` → `PAID` | Lança `OrderDomainException` se o status não for `PENDING` |
| `approve()` | `PAID` → `APPROVED` | Lança exceção se não estiver `PAID` |
| `initCancel(failureMessages)` | `PAID` → `CANCELLING` | Início do cancelamento (aguardando estorno do pagamento) |
| `cancel(failureMessages)` | `CANCELLING` ou `PENDING` → `CANCELLED` | Cancelamento definitivo |

Note que essas transições **só existem dentro do agregado** — nenhuma classe externa consegue, por exemplo, colocar um pedido direto em `APPROVED` sem passar por `PAID`. É o agregado protegendo suas próprias invariantes.

### 4.2. `OrderItem` (Entity, não Aggregate Root)

`OrderItem extends BaseEntity<OrderItemId>` — tem identidade própria (`OrderItemId`), mas só existe **dentro** do agregado `Order` (por isso não é `AggregateRoot`). Contém `product`, `quantity`, `price`, `subTotal`, e o método de validação:

```java
protected boolean isPriceValid() {
    return price.isGreaterThanZero() &&
            price.equals(product.getPrice()) &&
            price.multiply(quantity).equals(subTotal);
}
```

Ou seja: o preço do item precisa bater com o preço **atual do produto** (que vem do restaurante — veja 4.4), e `price × quantity` precisa bater com o `subTotal` enviado.

### 4.3. Value Objects específicos do pedido

- `StreetAddress` (`street`, `postalCode`, `city`) — Value Object clássico: `equals`/`hashCode` comparam os **valores**, não uma identidade.
- `TrackingId` — o ID público que o cliente final usa para rastrear o pedido (diferente do `OrderId` interno).
- `OrderItemId` — ID sequencial (1, 2, 3...) atribuído a cada item quando o pedido é inicializado.

### 4.4. `Restaurant`, `Product`, `Customer`

- `Restaurant extends AggregateRoot<RestaurantId>` — tem uma lista de `Product` e um flag `active`.
- `Product extends BaseEntity<ProductId>` — tem `name` e `price`, com o método `updateWithConfirmedNameAndPrice(...)`.
- `Customer extends AggregateRoot<CustomerId>` — hoje é só um "casco" (sem atributos), pois `order-domain-core` só precisa **confirmar que o cliente existe**, não precisa saber seu nome/e-mail.

### 4.5. `OrderDomainService` — a regra que cruza dois agregados

```java
public interface OrderDomainService {
    OrderCreatedEvent validateAndInitiateOrder(Order order, Restaurant restaurant);
    OrderPaidEvent payOrder(Order order);
    void approveOrder(Order order);
    OrderCancelledEvent cancelOrderPayment(Order order, List<String> failureMessages);
    void cancelOrder(Order order, List<String> failureMessages);
}
```

Por que isso não é um método do próprio `Order`? Porque `validateAndInitiateOrder` precisa **do `Restaurant`** também — e um agregado não deveria depender de outro diretamente. Essa é a razão de existir de um **Domain Service**: coordenar regra de negócio que atravessa mais de um agregado.

`OrderDomainServiceImpl.validateAndInitiateOrder(order, restaurant)` faz, em ordem:

1. `validateRestaurant(restaurant)` → lança `OrderDomainException` se `!restaurant.isActive()`.
2. `setOrderProductInformation(order, restaurant)` → para cada item do pedido, procura o produto correspondente na lista de produtos do restaurante (usando `equals`, que compara só o `ProductId`, herdado de `BaseEntity`) e **sobrescreve nome e preço do produto do pedido com os dados "oficiais" do restaurante**. Isso é uma proteção importante: o cliente manda um `price` no JSON, mas quem manda de verdade no preço é o restaurante — o pedido nunca deveria confiar cegamente no preço que veio de fora.
3. `order.validateOrder()` → valida preço total vs. soma dos itens (usando os preços já corrigidos no passo 2).
4. `order.initializeOrder()` → gera IDs e coloca `PENDING`.
5. Retorna um `OrderCreatedEvent(order, timestamp)`.

**Ponto de atenção pedagógico:** para o passo 2 funcionar, o `Restaurant` reconstruído a partir do `CreateOrderCommand` precisa conter, na lista de produtos, um `Product` com o **mesmo `ProductId`** de cada item do pedido — é isso que a porta `RestaurantRepository.findRestaurantInformation(restaurant)` deveria devolver (ver seção 5.4).

---

## 5. A camada de aplicação (`order-application-service`) em detalhe

Esta é a camada que **orquestra** o domínio para realizar casos de uso. Ela não decide regra de negócio — ela decide **a sequência de passos**.

### 5.1. A porta de entrada: `OrderApplicationService`

```java
public interface OrderApplicationService {
    CreateOrderResponse createOrder(@Valid CreateOrderCommand createOrderCommand);
    TrackOrderResponse trackOrder(@Valid TrackOrderQuery trackOrderQuery);
}
```

É essa interface que o futuro `@RestController` do módulo `order-application` vai chamar. Implementada por `OrderApplicationServiceImpl`, que só delega para dois "handlers":

```java
@Service @Validated @Slf4j @RequiredArgsConstructor
class OrderApplicationServiceImpl implements OrderApplicationService {
    private final OrderCreateCommandHandler orderCreateCommandHandler;
    private final OrderTrackCommandHandler orderTrackCommandHandler;

    public CreateOrderResponse createOrder(CreateOrderCommand createOrderCommand) {
        return orderCreateCommandHandler.createOrder(createOrderCommand);
    }
    public TrackOrderResponse trackOrder(TrackOrderQuery trackOrderQuery) {
        return orderTrackCommandHandler.trackOrder(trackOrderQuery);
    }
}
```

O `@Validated` + `@Valid` nos parâmetros ativa a validação Bean Validation (`@NotNull` etc.) dos DTOs **antes** de qualquer lógica rodar.

### 5.2. Os DTOs — e a ligação direta com o seu print do Postman

O JSON que você mandou é exatamente a forma serializada de `CreateOrderCommand` (`.../dto/create/CreateOrderCommand.java`):

```json
{
  "customerId": "d215b5f8-0249-4dc5-89a3-51fd148cfb41",
  "restaurantId": "d215b5f8-0249-4dc5-89a3-51fd148cfb45",
  "address": { "street": "street_1", "postalCode": "1000AB", "city": "Amsterdam" },
  "price": 50.00,
  "items": [
    { "productId": "d215b5f8-0249-4dc5-89a3-51fd148cfb48", "quantity": 1, "price": 50.00, "subTotal": 50.00 }
  ]
}
```

```java
@Getter @Builder @AllArgsConstructor
public class CreateOrderCommand {
    @NotNull private final UUID customerId;
    @NotNull private final UUID restaurantId;
    @NotNull private final BigDecimal price;
    @NotNull private final List<OrderItem> items;   // dto.create.OrderItem, não entity.OrderItem!
    @NotNull private final OrderAddress address;
}
```

Note que existe um `dto.create.OrderItem` (campos "crus": `productId`, `quantity`, `price`, `subTotal`, todos em tipos primitivos/`UUID`/`BigDecimal`) **separado** do `entity.OrderItem` do domínio (que usa Value Objects como `Money` e `ProductId`). Isso é intencional: **o domínio nunca deveria receber tipos "de borda" (`UUID`, `BigDecimal` cru) diretamente** — quem faz essa tradução é o `OrderDataMapper` (seção 5.3).

Hoje, como `order-application` (o controller REST) ainda não existe, é exatamente esse `CreateOrderCommand` que um teste (ou, futuramente, o controller) monta manualmente para simular a requisição do Postman.

### 5.3. `OrderDataMapper` — a fronteira de tradução

```java
public Order createOrderCommandToOrder(CreateOrderCommand createOrderCommand) {
    return Order.Builder.builder()
            .customerId(new CustomerId(createOrderCommand.getCustomerId()))
            .restaurantId(new RestaurantId(createOrderCommand.getRestaurantId()))
            .deliveryAddress(orderAddressToStreetAddress(createOrderCommand.getAddress()))
            .price(new Money(createOrderCommand.getPrice()))
            .items(orderItemsToOrderItemEntities(createOrderCommand.getItems()))
            .build();
}
```

Esse método converte o "mundo externo" (`UUID`, `BigDecimal`) para o "mundo do domínio" (`CustomerId`, `Money`...). Há também `createOrderCommandToRestaurant(...)`, que monta um `Restaurant` "esqueleto" só com o `RestaurantId` e a lista de `Product` (só com os IDs pedidos) — esse objeto serve de **filtro de consulta** para `RestaurantRepository.findRestaurantInformation(restaurant)`.

No sentido inverso, `orderToCreateOrderResponse(order, message)` e `orderToTrackOrderResponse(order)` convertem o agregado de volta para DTOs de resposta.

> Nota de leitura de código: repare no typo `orderTackingId` (faltou o "r" de "Tracking") em `CreateOrderResponse`, `TrackOrderQuery` e `TrackOrderResponse` — não é um bug funcional, é só um erro de digitação que se propagou; útil saber para não se confundir ao procurar por "trackingId" no código.

### 5.4. `OrderCreateHelper` — o orquestrador do caso de uso "criar pedido"

Este é o coração da camada de aplicação:

```java
@Transactional
public OrderCreatedEvent persistOrder(CreateOrderCommand createOrderCommand) {
    checkCustomer(createOrderCommand.getCustomerId());
    Restaurant restaurant = checkRestaurant(createOrderCommand);
    Order order = orderDataMapper.createOrderCommandToOrder(createOrderCommand);
    OrderCreatedEvent orderCreatedEvent = orderDomainService.validateAndInitiateOrder(order, restaurant);
    saveOrder(order);
    return orderCreatedEvent;
}
```

Passo a passo, aplicado ao seu print:

1. **`checkCustomer(customerId)`** → chama a porta `CustomerRepository.findCustomer(UUID)`. Se vazio, lança `OrderDomainException("Could not find customer with customer id: ...")`. *(Hoje sem implementação real — quem vai implementar essa porta é `order-data-access`, provavelmente consultando uma tabela local de clientes replicada de outro serviço, ou chamando o customer-service.)*
2. **`checkRestaurant(command)`** → converte o comando num `Restaurant` "de consulta" e chama `RestaurantRepository.findRestaurantInformation(restaurant)`. Se vazio, lança exceção. O `Restaurant` retornado deveria vir com `active=true/false` e os `Product` reais (nome/preço confirmados).
3. **`orderDataMapper.createOrderCommandToOrder(command)`** → monta o agregado `Order` (ainda sem ID, sem status — puro "rascunho").
4. **`orderDomainService.validateAndInitiateOrder(order, restaurant)`** → aqui entra toda a regra de negócio da seção 4.5: valida se o restaurante está ativo, corrige preços dos itens com base no restaurante, valida o total, inicializa o pedido (gera IDs, `PENDING`) e devolve o `OrderCreatedEvent`.
5. **`saveOrder(order)`** → chama a porta `OrderRepository.save(order)`. Se retornar `null`, lança exceção (`order-data-access` ainda não implementa isso).
6. Retorna o evento para quem chamou.

Tudo isso dentro de `@Transactional` — a intenção é que a checagem de cliente/restaurante e a gravação do pedido aconteçam **atomicamente** (quando `order-data-access` existir com um `DataSource` real).

### 5.5. `OrderCreateCommandHandler` — o "caso de uso" exposto

```java
public CreateOrderResponse createOrder(CreateOrderCommand createOrderCommand) {
    OrderCreatedEvent orderCreatedEvent = orderCreateHelper.persistOrder(createOrderCommand);
    orderCreatedPaymentRequestMessagePublisher.publish(orderCreatedEvent);
    return orderDataMapper.orderToCreateOrderResponse(orderCreatedEvent.getOrder(), "Order created successfully");
}
```

Depois que o pedido foi validado e persistido, o handler **publica o evento** `OrderCreatedEvent` na porta de saída `OrderCreatedPaymentRequestMessagePublisher` — isto é, "avisa" (futuramente, via Kafka) o **serviço de pagamento** que existe um novo pedido pendente de cobrança. Isso é o início de uma **SAGA coreografada**: order-service não chama payment-service diretamente (não é uma chamada síncrona/REST), ele publica um evento e segue seu fluxo; quem reage é o outro serviço, de forma assíncrona.

Depois, o handler devolve um `CreateOrderResponse` — que seria serializado como o **corpo da resposta HTTP** do seu `POST /orders`, contendo `orderTackingId`, `orderStatus` (`PENDING`) e uma mensagem.

### 5.6. Rastreamento: `OrderTrackCommandHandler`

Caso de uso mais simples — só leitura (`@Transactional(readOnly = true)`):

```java
Optional<Order> orderResult = orderRepository.findByTrackingId(new TrackingId(trackOrderQuery.getOrderTackingId()));
if (orderResult.isEmpty()) throw new OrderNotFoundException(...);
return orderDataMapper.orderToTrackOrderResponse(orderResult.get());
```

Seria o backend de um futuro `GET /orders/{trackingId}`.

### 5.7. As portas de saída de mensageria (SAGA de pedidos)

```java
public interface OrderCreatedPaymentRequestMessagePublisher extends DomainEventPublisher<OrderCreatedEvent> {}
public interface OrderCancelledPaymentRequestMessagePublisher extends DomainEventPublisher<OrderCancelledEvent> {}
public interface OrderPaidRestaurantRequestMessagePublisher extends DomainEventPublisher<OrderPaidEvent> {}
```

Cada uma representa um ponto de saída da SAGA:

- Pedido criado → pede pagamento (`OrderCreatedPaymentRequestMessagePublisher`).
- Pedido pago → pede aprovação do restaurante (`OrderPaidRestaurantRequestMessagePublisher`).
- Pedido cancelado → pede estorno do pagamento (`OrderCancelledPaymentRequestMessagePublisher`).

### 5.8. As portas de entrada de mensageria (respostas da SAGA)

```java
public interface PaymentResponseMessageListener {
    void paymentCompleted(PaymentResponse paymentResponse);
    void paymentCancelled(PaymentResponse paymentResponse);
}
public interface RestaurantApprovalResponseListener {
    void orderApproved(RestaurantApprovalResponse restaurantApprovalResponse);
    void orderRejected(RestaurantApprovalResponse restaurantApprovalResponse);
}
```

Já existem as implementações `PaymentResponseMessageListenerImpl` e `RestaurantApprovalResponseListenerImpl` (anotadas `@Service`), mas os métodos ainda estão **vazios** — são o próximo passo natural do curso: quando o serviço de pagamento (via Kafka) responder "paguei" ou "falhou", esse listener deve chamar `orderDomainService.payOrder(order)` ou `cancelOrderPayment(order, mensagens)`, e persistir o novo estado via `OrderRepository`.

---

## 6. O quadro geral: como isso se encaixaria numa SAGA completa

Mesmo sem os outros microsserviços existirem neste repositório, dá para entender a intenção pelo desenho das portas:

```text
 Cliente                order-service                 payment-service        restaurant-service
   │  POST /orders            │                              │                      │
   ├─────────────────────────►│                              │                      │
   │                          │ valida cliente/restaurante    │                      │
   │                          │ cria Order (PENDING)          │                      │
   │                          │ salva no banco                │                      │
   │  201 (PENDING)           │                              │                      │
   │◄─────────────────────────┤                              │                      │
   │                          │  evento OrderCreated (Kafka) ─►│                      │
   │                          │                              │ processa pagamento    │
   │                          │◄─── PaymentResponse (Kafka) ──┤                      │
   │                          │ Order.pay() -> PAID           │                      │
   │                          │  evento OrderPaid (Kafka) ─────┼─────────────────────►│
   │                          │                              │           restaurante avalia
   │                          │◄────────── RestaurantApprovalResponse (Kafka) ────────┤
   │                          │ Order.approve() -> APPROVED   │                      │
```

Esse é o padrão **SAGA coreografada**: nenhum serviço chama o outro diretamente via HTTP; todos reagem a eventos via Kafka. Se o pagamento falhar, o fluxo é revertido chamando `cancelOrderPayment`/`cancelOrder`, voltando o pedido para `CANCELLING`/`CANCELLED` — por isso o agregado `Order` já tem esses métodos prontos, mesmo sem o Kafka existir ainda.

---

## 7. Glossário DDD aplicado a este código

| Conceito DDD | Classe(s) neste projeto |
|---|---|
| Aggregate Root | `Order`, `Restaurant`, `Customer` |
| Entity (não-raiz) | `OrderItem`, `Product` |
| Value Object | `Money`, `StreetAddress`, `TrackingId`, `OrderItemId`, `CustomerId`, `OrderId`, `RestaurantId`, `ProductId` |
| Domain Event | `OrderCreatedEvent`, `OrderPaidEvent`, `OrderCancelledEvent` |
| Domain Service | `OrderDomainService` / `OrderDomainServiceImpl` |
| Domain Exception | `OrderDomainException`, `OrderNotFoundException` |
| Application Service (porta de entrada) | `OrderApplicationService` |
| Use case handler (orquestração) | `OrderCreateCommandHandler`, `OrderCreateHelper`, `OrderTrackCommandHandler` |
| Repository (porta de saída) | `OrderRepository`, `CustomerRepository`, `RestaurantRepository` |
| Publisher (porta de saída de mensageria) | `OrderCreatedPaymentRequestMessagePublisher`, `OrderCancelledPaymentRequestMessagePublisher`, `OrderPaidRestaurantRequestMessagePublisher` |
| Listener (porta de entrada de mensageria) | `PaymentResponseMessageListener`, `RestaurantApprovalResponseListener` |
| Anticorruption / DTO Mapper | `OrderDataMapper` |

---

## 8. Como compilar hoje

```bash
mvn clean verify
```

O `common/pom.xml` já declara `<packaging>pom</packaging>` corretamente. `order-domain-core` e `order-application-service` compilam e podem ser cobertos por testes unitários (o domínio não depende de Spring/JPA/Kafka, então pode ser testado com JUnit + Mockito puro, sem subir contexto Spring nem banco).

Compilar só o `order-service` e suas dependências:

```bash
mvn -pl order-service -am clean verify
```

---

## 9. Próximos passos naturais (seguindo a lógica do curso)

1. **Testes unitários** do agregado `Order` e de `OrderDomainServiceImpl` (criação, transições de estado, falhas de validação de preço) — isso pode ser feito **agora**, sem esperar nenhum adaptador.
2. **`order-data-access`**: entidades JPA + implementação de `OrderRepository`, `CustomerRepository`, `RestaurantRepository`.
3. **`order-application`**: `@RestController` que recebe exatamente o JSON do seu print, monta um `CreateOrderCommand` e chama `OrderApplicationService.createOrder(...)`, mais um `@ControllerAdvice` para traduzir `OrderDomainException`/`OrderNotFoundException` em respostas HTTP (400/404).
4. **`order-messaging`**: implementação Kafka das portas de publisher, e os `@KafkaListener` que alimentam `PaymentResponseMessageListenerImpl`/`RestaurantApprovalResponseListenerImpl`.
5. **`order-container`**: classe `@SpringBootApplication`, `application.yml` com `server.port: 8181`, datasource e configuração do Kafka — só aí o `POST http://localhost:8181/orders` do seu Postman vai efetivamente funcionar de ponta a ponta.
6. Completar os corpos vazios de `PaymentResponseMessageListenerImpl`/`RestaurantApprovalResponseListenerImpl`, chamando `OrderDomainService.payOrder`/`approveOrder`/`cancelOrderPayment`/`cancelOrder` e persistindo o resultado.
7. Mais adiante no curso: padrão **Outbox** (para publicar eventos de forma transacional e confiável junto com o `save()` do pedido) e **CQRS** (separar o modelo de escrita do de leitura para consultas/rastreamento).

Bons estudos — e qualquer trecho de código que você quiser destrinchar linha a linha (por exemplo, `Order.validateOrder()` ou o `OrderDataMapper`), é só pedir.
