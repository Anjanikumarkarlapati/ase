# BistroByte — Multi-Channel Restaurant Management & Kitchen Execution System

A Spring Boot microservices backend for BistroByte Hospitality: dynamic digital menus, real-time
ordering for **dine-in, takeaway and delivery**, kitchen execution and live order tracking.

Menu management and order processing are separate services so menu browsing stays available even
when order processing is under load, and every API call is authenticated with a role-based JWT,
routed through an API Gateway, and discovered through Netflix Eureka.

---

## 1. Architecture

```
                       ┌──────────────────────────┐
   Customer app        │   discovery-server       │
   Staff console  ───▶ │   Netflix Eureka : 8761  │ ◀── all services register here
   Kitchen display     └──────────────────────────┘
          │                        ▲
          ▼                        │ lb://  (service ids, load balanced)
   ┌──────────────────┐            │
   │   api-gateway    │────────────┘
   │  :8080  (JWT at  │
   │   the edge)      │
   └──────┬───────────┘
          │  routes
   ┌──────┴────────────┬────────────────────┬──────────────────────┐
   ▼                   ▼                    ▼                      │
┌──────────────┐  ┌──────────────┐  ┌────────────────┐             │
│ user-service │  │ menu-service │  │ order-service  │─── Feign ───┘
│    :8081     │  │    :8082     │  │     :8083      │   reserve / release stock
│ accounts,    │  │ categories,  │  │ placement,     │
│ roles, JWT   │  │ dishes,      │  │ kitchen flow,  │
│ issuance     │  │ availability │  │ tracking       │
└──────────────┘  └──────────────┘  └────────────────┘
      H2                 H2                 H2
```

| Module | Port | Responsibility |
|---|---|---|
| `discovery-server` | 8761 | Netflix Eureka registry |
| `api-gateway` | 8080 | Single entry point, JWT verification at the edge, routing, CORS, correlation ids |
| `user-service` | 8081 | Accounts, roles, credential verification, **the only issuer of JWTs** |
| `menu-service` | 8082 | Categories, dishes, pricing, live availability, stock reservation |
| `order-service` | 8083 | Order placement, kitchen workflow, cancellation, live tracking |
| `common-lib` | — | Shared JWT plumbing, error envelope, domain exceptions, pagination DTO |

### Key design decisions

* **Stock is reserved before an order exists.** Placement is a two-step saga: the order service asks
  the menu service to price the basket and hold stock; only then is the ticket written. If the write
  fails, the reservation is compensated. A customer can never be given an order for a sold-out dish.
* **All-or-nothing reservations.** If any line in a basket is unavailable, the whole reservation
  rolls back with HTTP 409 naming the dish — no partial stock consumption.
* **Row-level locking** (`PESSIMISTIC_WRITE`) on stock decrements, so two concurrent orders for the
  last portion cannot both succeed during the dinner rush.
* **Auto-86.** When the last portion is sold, the dish is marked unavailable immediately, so the next
  customer never even sees it as orderable.
* **Defence in depth.** The gateway verifies the token and applies coarse path/role rules; each
  service re-verifies the same token and applies fine-grained `@PreAuthorize` rules. A service is
  safe even if reached directly.
* **Identity headers are never trusted from clients.** The gateway strips any inbound `X-Auth-*`
  headers and re-adds them from the verified token.
* **Soft deletes.** Retired dishes and deactivated accounts stay resolvable so historical orders
  remain intact.

---

## 2. Running it

### Prerequisites

* **JDK 17** (the build targets Java 17; it also compiles and runs on 21)
* **Maven 3.8+** (or use the Maven support built into Eclipse)

### Build and test everything

```bash
mvn clean install
```

### Start the platform (five terminals, in this order)

```bash
java -jar discovery-server/target/discovery-server-1.0.0.jar   # wait until :8761 is UP
java -jar user-service/target/user-service-1.0.0.jar
java -jar menu-service/target/menu-service-1.0.0.jar
java -jar order-service/target/order-service-1.0.0.jar
java -jar api-gateway/target/api-gateway-1.0.0.jar
```

Everything is then reached through the gateway at **http://localhost:8080**.
The Eureka dashboard is at **http://localhost:8761**.

### Running from Eclipse (Spring Tools 4 / STS)

The project is a plain Maven multi-module build with no wrapper scripts or IDE-specific files, so it
imports cleanly:

1. **File → Import… → Maven → Existing Maven Projects**, select this folder. Eclipse imports the
   parent plus all six modules.
2. Make sure the project uses a **JDK 17** JRE (Project → Properties → Java Build Path → Libraries).
3. Start each service with **Run As → Spring Boot App** (or *Java Application* on the
   `…Application` class), keeping the order above. With Spring Tools 4 you can add all five to the
   **Boot Dashboard** and start them together.
4. If Eclipse shows "Plugin execution not covered by lifecycle configuration", use
   **Maven → Update Project…** — the build itself is unaffected.

### Seeded data

Each service seeds itself on first start (turn it off with `bistrobyte.seed.enabled=false`):

| Username | Password | Role |
|---|---|---|
| `admin` | `Password@123` | ADMIN |
| `staff` | `Password@123` | STAFF |
| `kitchen` | `Password@123` | KITCHEN |
| `customer` | `Password@123` | CUSTOMER |

The menu service seeds four categories and eight dishes, some with limited stock.

### Configuration

Ports, the JWT secret, tax rate and delivery fee live in each module's `application.yml`.
The **signing secret must be identical** across the gateway, user, menu and order services; override
it everywhere at once with the `BISTROBYTE_JWT_SECRET` environment variable, and point services at a
remote registry with `EUREKA_URI`.

In-memory H2 is used so the system runs with no external dependencies. Swapping in PostgreSQL or
MySQL is a datasource change plus a driver dependency — no code changes.

---

## 3. Security model

Roles: `CUSTOMER`, `STAFF`, `KITCHEN`, `ADMIN`.

* `POST /api/v1/auth/register` and `POST /api/v1/auth/login` are the only public endpoints.
* Login returns an HS256 token carrying `uid`, `role`, `email`, `sub` and `iss`; send it as
  `Authorization: Bearer <token>`.
* Anonymous registration always creates a `CUSTOMER`. Only an authenticated `ADMIN` may register an
  account with an elevated role.
* Customers can only read and cancel **their own** orders; staff, kitchen and admin see everything.
* Errors always come back as the same JSON envelope:

```json
{
  "timestamp": "2026-09-21T16:25:53.669Z",
  "status": 409,
  "error": "Conflict",
  "message": "Order cannot be placed: 'Chef Special Risotto' is sold out for today",
  "path": "/api/v1/orders"
}
```

Validation failures add a `violations` array of `{field, message}`.

---

## 4. API reference

All paths below are through the gateway (`http://localhost:8080`).

### Authentication — `user-service`

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Create an account (CUSTOMER unless an ADMIN supplies a role) |
| POST | `/api/v1/auth/login` | public | Exchange credentials for a JWT |
| GET | `/api/v1/auth/me` | any | Profile behind the presented token |
| GET | `/api/v1/users` | ADMIN, STAFF | Paged account list, optional `?role=` filter |
| GET | `/api/v1/users/{id}` | ADMIN, STAFF, self | Fetch an account |
| GET | `/api/v1/users/by-username/{username}` | ADMIN, STAFF, KITCHEN | Lookup used for order tickets |
| PUT | `/api/v1/users/{id}` | self or ADMIN | Update a profile |
| PUT | `/api/v1/users/{id}/password` | self or ADMIN | Change a password |
| PATCH | `/api/v1/users/{id}/role` | ADMIN | Promote or demote |
| DELETE | `/api/v1/users/{id}` | ADMIN | Deactivate (soft delete) |
| PATCH | `/api/v1/users/{id}/activate` | ADMIN | Re-activate |

### Menu — `menu-service`

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/v1/categories` | any | List sections (`?includeInactive=true`) |
| POST/PUT | `/api/v1/categories`, `/{id}` | ADMIN, STAFF | Create / update a section |
| DELETE | `/api/v1/categories/{id}` | ADMIN | Delete an empty section |
| GET | `/api/v1/menu/items` | any | Browse: `categoryId`, `search`, `orderableOnly`, `vegetarianOnly`, `includeInactive`, `page`, `size`, `sortBy` |
| GET | `/api/v1/menu/items/{id}` | any | Fetch a dish |
| GET | `/api/v1/menu/items/batch?ids=1,2` | any | Bulk fetch |
| POST | `/api/v1/menu/items` | ADMIN, STAFF | Add a dish |
| PUT | `/api/v1/menu/items/{id}` | ADMIN, STAFF | Update a dish |
| PATCH | `/api/v1/menu/items/{id}/availability` | ADMIN, STAFF, KITCHEN | 86 a dish, or put it back |
| PATCH | `/api/v1/menu/items/{id}/stock` | ADMIN, STAFF, KITCHEN | Set remaining portions (`unlimited: true` clears the cap) |
| DELETE | `/api/v1/menu/items/{id}` | ADMIN | Retire a dish (soft delete) |
| POST | `/api/v1/menu/inventory/reserve` | any authenticated | **Inter-service:** price a basket and hold stock |
| POST | `/api/v1/menu/inventory/release` | any authenticated | **Inter-service:** return portions after a cancellation |

`stockQuantity` is optional: omit it for dishes the kitchen makes to order with no hard cap.

### Orders — `order-service`

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/api/v1/orders` | CUSTOMER, STAFF, ADMIN | Place an order |
| GET | `/api/v1/orders` | any | List (customers are scoped to their own); `status`, `channel`, `from`, `to`, `customerId` |
| GET | `/api/v1/orders/my` | any | The caller's own history |
| GET | `/api/v1/orders/{id}` | owner or staff | Full ticket |
| GET | `/api/v1/orders/reference/{ref}` | owner or staff | Fetch by tracking code |
| GET | `/api/v1/orders/reference/{ref}/track` | owner or staff | Live stage, minutes remaining, timeline |
| GET | `/api/v1/orders/kitchen/queue` | KITCHEN, STAFF, ADMIN | Live board: confirmed / preparing / ready, oldest first |
| PATCH | `/api/v1/orders/{id}/status` | KITCHEN, STAFF, ADMIN | Advance the ticket |
| POST | `/api/v1/orders/{id}/cancel` | owner or staff | Cancel and return the stock |

**Channel requirements:** `DINE_IN` needs `tableNumber`; `TAKEAWAY` needs `contactPhone`;
`DELIVERY` needs `deliveryAddress` and `contactPhone` and adds the delivery fee.

**Order lifecycle** (enforced server-side — stages cannot be skipped):

```
PENDING → CONFIRMED → PREPARING → READY → COMPLETED
                                     └─→ OUT_FOR_DELIVERY → COMPLETED   (DELIVERY only)
any non-terminal state → CANCELLED   (via the cancel endpoint, which returns the stock)
```

Customers may cancel their own order while it is `PENDING` or `CONFIRMED`; staff may cancel any time
before completion.

### Interactive docs

Swagger UI per service: `http://localhost:8081|8082|8083/swagger-ui.html`.

---

## 5. Worked example

```bash
G=http://localhost:8080

# 1. Log in
TOKEN=$(curl -s -X POST $G/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"usernameOrEmail":"customer","password":"Password@123"}' | jq -r .accessToken)

# 2. Browse the menu
curl -s $G/api/v1/menu/items -H "Authorization: Bearer $TOKEN"

# 3. Place a delivery order
curl -s -X POST $G/api/v1/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
        "channel": "DELIVERY",
        "deliveryAddress": "14 Harbour Road",
        "contactPhone": "+44 7700 900123",
        "items": [{"menuItemId": 4, "quantity": 2, "specialInstructions": "Extra spicy"}]
      }'
# → 201 with orderReference BB-20260921-AJ9S, priced subtotal + tax + delivery fee,
#   and an estimated ready time derived from the slowest dish.

# 4. Track it
curl -s $G/api/v1/orders/reference/BB-20260921-AJ9S/track -H "Authorization: Bearer $TOKEN"

# 5. Kitchen advances it
KTOK=$(curl -s -X POST $G/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"usernameOrEmail":"kitchen","password":"Password@123"}' | jq -r .accessToken)
curl -s -X PATCH $G/api/v1/orders/1/status -H "Authorization: Bearer $KTOK" \
  -H 'Content-Type: application/json' -d '{"status":"CONFIRMED"}'
```

---

## 6. Tests

```bash
mvn test
```

46 tests across the platform:

| Suite | Covers |
|---|---|
| `JwtAuthenticationGatewayFilterTests` | Public paths, missing / forged / expired tokens, identity-header spoofing, edge role rules |
| `GatewayApplicationTests` | The three platform routes are published |
| `AuthApiTests` | Registration, role-escalation refusal, duplicates, validation, login by username and email, role enforcement, deactivated accounts |
| `MenuItemServiceTests` | Reservation pricing, duplicate-line merging, atomic rollback on a sold-out basket, auto-86, stock release, retired and unknown dishes |
| `MenuApiTests` | Authentication, customer vs. staff visibility, write-role enforcement, validation, 86 and restock, 404 and 409 shapes |
| `OrderStatusTests` | The full state machine, per channel |
| `OrderApiTests` | Placement and pricing for each channel, channel requirements, sold-out propagation, kitchen workflow, single-release cancellation, cross-customer isolation, tracking, kitchen queue |
| `DiscoveryServerApplicationTests` | The registry starts |

The system has also been validated at runtime with all five services running: Eureka registration,
gateway routing, the full order lifecycle, sold-out rejection across services, and stock returned on
cancellation.
