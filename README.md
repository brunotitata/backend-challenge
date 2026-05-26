# Payment API — Trace Finance Back-end Challenge

API de pagamentos para carteiras virtuais com políticas de limite por período, idempotência, concorrência e arquitetura em camadas.

---

## Visão Geral da Arquitetura

O projeto segue **Clean Architecture** com módulos Gradle separados, garantindo que dependências sempre apontem para dentro:

```
application/       # Bootstrap (Ktor + wiring manual)
core/              # Regras de negócio e entidades puras
  entities/        # WalletEntity, PaymentEntity, PolicyEntity...
  use-cases/       # CreateWallet, ProcessPayment, ListPayments...
boundary-context/  # Contratos / Portas
  input-boundary/  # UseCaseSpec (portas de entrada)
  database-boundary/  # DAOSpec (portas de saída)
  exceptions/      # Exceções padronizadas do domínio
adapters/          # Implementações concretas
  web/             # Rotas Ktor, DTOs HTTP, tratamento de erros
  database-adapter/  # DAOs jOOQ, migrações Flyway, configuração de banco
```

Regra de dependência:
```
Controller (adapters/web)
    v
Input Boundary (boundary-context/input-boundary)
    v
UseCase (core/use-cases)
    v
Database Boundary (boundary-context/database-boundary)
    v
Database Adapter (adapters/database-adapter)
    v
PostgreSQL
```

- O domínio (`core`) **não conhece** HTTP, banco de dados ou frameworks.
- As portas (`boundary-context`) definem contratos que os adapters implementam.
- O wiring é feito manual em `Application.kt`, sem framework de DI.

---

## Tecnologias

| Componente | Tecnologia |
|------------|------------|
| Linguagem | Kotlin 1.9.22 |
| Framework HTTP | Ktor 2.3.12 |
| Serialização | kotlinx.serialization |
| Banco de dados | PostgreSQL 16 |
| Acesso a dados | jOOQ 3.19.15 + Flyway 10.20.1 |
| Pool de conexões | HikariCP 5.1.0 |
| Testes | JUnit 5, Ktor TestHost, Testcontainers 1.20.4 |
| Build | Gradle Kotlin DSL |
| Documentação | Swagger UI (ktor-swagger-ui) |
| Container | Docker Compose |

---

## Como Rodar Localmente

### Pré-requisitos

- JDK 21
- Docker e Docker Compose

### Passo a passo

1. Clone o repositório:
   ```bash
   git clone <url-do-repo>
   cd payment-api
   ```

2. Suba as dependências (PostgreSQL):
   ```bash
   docker compose up -d
   ```

3. Execute a aplicação:
   ```bash
   ./gradlew :application:run
   ```

   A aplicação estará disponível em `http://localhost:8080`.

### Variáveis de ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/payment_api` | JDBC URL do PostgreSQL |
| `DATABASE_USER` | `payment_api` | Usuário do banco |
| `DATABASE_PASSWORD` | `payment_api` | Senha do banco |
| `PORT` | `8080` | Porta da aplicação |

---

## Documentação da API (Swagger UI)

A API está documentada automaticamente via **Swagger UI** integrado ao Ktor. Após iniciar a aplicação, acesse:

```
http://localhost:8080/swagger-ui
```

A documentação interativa inclui:
- Descrição de todos os endpoints disponíveis
- Parâmetros de path, query e headers
- Schemas dos request/response bodies
- Códigos de status HTTP e possíveis erros
- Testes diretos pela interface do Swagger

---

## Como Rodar Testes

```bash
./gradlew test
```

Isso executa todos os testes unitários e de integração, incluindo testes com Testcontainers PostgreSQL.

---

## Como Executar Migrações

As migrações são executadas automaticamente pelo Flyway na inicialização da aplicação. Os scripts estão em:

```
adapters/database-adapter/src/main/resources/db/migration/
```

Para executar manualmente (requer banco rodando):

```bash
./gradlew :database-adapter:flywayMigrate
```

---

## Endpoints Disponíveis

> A documentação completa e interativa de todos os endpoints está disponível em [`http://localhost:8080/swagger-ui`](http://localhost:8080/swagger-ui) após iniciar a aplicação.

### Health Check
```
GET /health
```

### Carteiras
```
POST /wallets
GET  /wallets/{walletId}/policies
PUT  /wallets/{walletId}/policy
```

### Políticas
```
POST /policies
GET  /policies
```

### Pagamentos
```
POST /wallets/{walletId}/payments
GET  /wallets/{walletId}/payments
```

---

## Exemplos de Request e Response

### Criar carteira

**Request:**
```bash
curl -X POST http://localhost:8080/wallets \
  -H "Content-Type: application/json" \
  -d '{"ownerName": "João Silva"}'
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "ownerName": "João Silva",
  "createdAt": "2024-08-25T22:31:44.4758Z"
}
```

---

### Criar política VALUE_LIMIT

**Request:**
```bash
curl -X POST http://localhost:8080/policies \
  -H "Content-Type: application/json" \
  -d '{
    "name": "DEFAULT_VALUE_LIMIT",
    "category": "VALUE_LIMIT",
    "maxPerPayment": 1000,
    "daytimeDailyLimit": 4000,
    "nighttimeDailyLimit": 1000,
    "weekendDailyLimit": 1000
  }'
```

**Response:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "name": "DEFAULT_VALUE_LIMIT",
  "category": "VALUE_LIMIT",
  "maxPerPayment": 1000,
  "daytimeDailyLimit": 4000,
  "nighttimeDailyLimit": 1000,
  "weekendDailyLimit": 1000,
  "createdAt": "2024-08-20T10:00:00.0000Z",
  "updatedAt": "2024-08-26T12:34:56.7890Z"
}
```

---

### Associar política a carteira

**Request:**
```bash
curl -X PUT http://localhost:8080/wallets/550e8400-e29b-41d4-a716-446655440000/policy \
  -H "Content-Type: application/json" \
  -d '{"policyId": "550e8400-e29b-41d4-a716-446655440001"}'
```

**Response:**
```json
{
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "policyId": "550e8400-e29b-41d4-a716-446655440001",
  "active": true,
  "updatedAt": "2024-08-26T12:34:56.7890Z"
}
```

---

### Realizar pagamento (com idempotência)

**Request:**
```bash
curl -X POST http://localhost:8080/wallets/550e8400-e29b-41d4-a716-446655440000/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: abc123" \
  -d '{
    "amount": 999.99,
    "occurredAt": "2024-08-26T09:42:17.2500Z"
  }'
```

**Response (201 Created):**
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440002",
  "status": "APPROVED",
  "amount": "999.99",
  "occurredAt": "2024-08-26T09:42:17.2500Z"
}
```

**Response (422 — limite excedido):**
```json
{
  "error": {
    "code": "UNPROCESSABLE_ENTITY",
    "message": "Payment rejected: limit exceeded"
  }
}
```

**Response (409 — idempotência conflitante):**
```json
{
  "error": {
    "code": "CONFLICT",
    "message": "Idempotency key already used with different payload"
  }
}
```

---

### Listar pagamentos

**Request:**
```bash
curl "http://localhost:8080/wallets/550e8400-e29b-41d4-a716-446655440000/payments?startDate=2024-08-25T00:00:00.0000Z&endDate=2024-08-26T23:59:59.9999Z"
```

**Response:**
```json
{
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "walletId": "550e8400-e29b-41d4-a716-446655440000",
      "amount": "999.99",
      "occurredAt": "2024-08-26T09:42:17.2500Z",
      "status": "APPROVED",
      "createdAt": "2024-08-26T09:42:17.3500Z",
      "updatedAt": "2024-08-26T09:42:17.3500Z"
    }
  ],
  "meta": {
    "nextCursor": null,
    "previousCursor": null,
    "total": 1,
    "totalMatches": null
  }
}
```

---

## Decisões de Modelagem de Domínio

### Entidades

- **WalletEntity**: representa uma carteira virtual com `id` (UUID), `ownerName` e timestamps.
- **PaymentEntity**: representa um pagamento com `id`, `walletId`, `policyId`, `amount`, `occurredAt`, `periodType`, `periodStart` e `status`.
- **PolicyEntity**: representa uma política de limite com `id`, `name`, `category` e campos específicos por categoria (`maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit`, `weekendDailyLimit`, `dailyTransactionLimit`).

### Casos de Uso

Cada operação de negócio é encapsulada em um caso de uso (`UseCaseSpec` + `UseCaseSpecImpl`), seguindo o princípio de responsabilidade única:

- `CreateWalletUseCaseSpec` — cria carteira e associa política default.
- `ProcessPaymentUseCaseSpec` — orquestra validação, resolução de política, avaliação de limite e persistência.
- `ListPaymentsUseCaseSpec` — consulta pagamentos com filtro de data e paginação por cursor.

### Registry Pattern para Políticas

O `PolicyEvaluatorRegistryImpl` registra avaliadores por categoria (`VALUE_LIMIT`, `TX_COUNT_LIMIT`). Quando uma nova categoria é adicionada, basta registrar um novo avaliador — o fluxo principal de pagamento (`ProcessPaymentUseCaseImpl`) não precisa ser alterado.

---

## Decisão de Idempotência

A idempotência é implementada via **header HTTP `Idempotency-Key`**.

- A chave é obrigatória para `POST /wallets/{walletId}/payments`.
- A unicidade é garantida por **constraint no banco de dados** (`UNIQUE(wallet_id, idempotency_key)`).
- O payload da requisição é hasheado (`SHA-256`) e comparado com o hash armazenado.
- Retry com mesmo payload → retorna o resultado original.
- Retry com payload diferente → retorna `409 Conflict`.
- Tentativas que falham por validação (`400`) **não** consomem a chave.

Tabela: `payment_idempotency_keys`

---

## Decisão de Concorrência

O processamento de pagamento ocorre dentro de uma **transação SQL atômica**:

1. Verifica se a chave idempotente já existe (com hash).
2. Bloqueia/verifica o consumo atual de limite na tabela `limit_consumptions`.
3. Avalia se o pagamento pode ser aprovado.
4. Se aprovado: insere o pagamento, atualiza o consumo e registra a chave idempotente.
5. Se rejeitado: registra a chave idempotente com status 422 (sem consumir limite).

A tabela `limit_consumptions` possui índice único por `(wallet_id, policy_id, period_type, period_start)`, garantindo que não haja linhas duplicadas para o mesmo período. Em PostgreSQL, operações de `INSERT ... ON CONFLICT` (UPSERT) garantem atomicidade mesmo sob concorrência.

Pagamentos e troca de política ativa serializam por carteira usando lock na linha de `wallets`. O pagamento persiste o `policy_id` usado na decisão, mantendo uma semântica de snapshot auditável mesmo que a política ativa seja trocada por uma requisição concorrente.

---

## Decisão de Timezone

- Todas as datas são armazenadas em **UTC** no banco de dados (`TIMESTAMPTZ`).
- A classificação de período (diurno, noturno, final de semana) usa o timezone **`America/Sao_Paulo`** (BRT/BRST).
- O campo `occurredAt` informado pelo cliente é usado para classificar o período e consumir limite.
- O campo `createdAt` representa o momento de criação do registro no sistema.
- A chave de consumo (`period_start`) é calculada no timezone local para respeitar as bordas corretas:
  - Noturno antes das 06:00 → `period_start` do dia anterior às 18:00.
  - Noturno a partir das 18:00 → `period_start` do mesmo dia às 18:00.

---

## Decisão de Precisão Monetária

- Valores monetários usam `BigDecimal` no domínio e são serializados como string decimal nas respostas para preservar precisão.
- O banco armazena como `NUMERIC(19, 2)`.
- A API aceita números JSON ou strings decimais nas requisições e rejeita valores negativos, zero, nulos, ausentes, com mais de 2 casas decimais ou fora de `NUMERIC(19,2)`.
- A regra de comparação de limite é **inclusiva**: valores iguais ao limite permitido são aceitos.

---

## Justificativa para PostgreSQL

O desafio menciona MongoDB com Docker Compose como diferencial, mas optamos por **PostgreSQL** pelos seguintes motivos:

1. **Transações ACID**: pagamentos exigem atomicidade entre verificação de limite, inserção do pagamento e atualização de consumo.
2. **Constraints e índices únicos**: a idempotência é garantida por `UNIQUE INDEX` no banco, não apenas em memória.
3. **Consistência em concorrência**: `SELECT FOR UPDATE` e `INSERT ... ON CONFLICT` permitem bloqueio otimista/pessimista diretamente no banco.
4. **jOOQ**: geração de código tipada a partir do schema SQL, eliminando strings magicas em queries.
5. **Migrações versionadas**: Flyway gerencia evolução do schema de forma controlada e reversível.

Trade-off: perde-se a flexibilidade de schema de documentos (NoSQL), mas ganha-se consistência e simplicidade para um domínio altamente transacional.

---

## Justificativa para jOOQ

O projeto usa **jOOQ** como camada de acesso a dados em vez de JDBC manual ou ORM:

1. **Tipagem forte**: queries são verificadas em tempo de compilação graças à geração de código a partir das migrações Flyway.
2. **Controle total de SQL**: não há abstração que esconda queries N+1 ou joins ineficientes.
3. **Imutabilidade**: `Record` gerados pelo jOOQ são imutáveis por padrão, alinhados com práticas funcionais.
4. **Transparência**: o SQL gerado é previsível e otimizável.

Trade-off: há curva de aprendizado inicial e configuração extra no build (plugin Gradle + geração de código), mas o benefício de segurança em queries complexas supera o custo.

---

## Trade-offs e Pontos de Atenção

1. **Wiring manual em `Application.kt`**: não usamos framework de DI (Koin, Spring). Isso reduz dependências e magicas, mas aumenta o código boilerplate de wiring. Em escala maior, um DI container seria recomendado.
2. **Timezone fixo (`America/Sao_Paulo`)**: a classificação de período está hardcoded para o timezone brasileiro. Para multi-região, o timezone deveria vir da carteira ou do request.
3. **Paginação por cursor simples**: o cursor é base64 URL-safe de `direction|occurredAt|id`. Não inclui criptografia ou assinatura. Para produção, considerar cursor opaco com HMAC.
4. **Métricas Prometheus básicas**: `/metrics` expõe contador de pagamentos aprovados/rejeitados. Para produção, ampliar com latência, saturação de pool, erros por endpoint e métricas de banco.
5. **Auditoria em tabela separada**: eventos de auditoria são persistidos em `payment_audit_events`. Em alta escala, considerar arquivamento periódico.

---

## Como Validar Manualmente os Principais Fluxos

### 1. Criar carteira e verificar política default
```bash
curl -X POST http://localhost:8080/wallets -H "Content-Type: application/json" -d '{"ownerName":"Test"}'
# Anote o id retornado (WALLET_ID)

curl http://localhost:8080/wallets/$WALLET_ID/policies
# Deve retornar DEFAULT_VALUE_LIMIT com active=true
```

### 2. Pagamento aprovado
```bash
curl -X POST http://localhost:8080/wallets/$WALLET_ID/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: key1" \
  -d '{"amount":500.00,"occurredAt":"2024-08-26T10:00:00.0000Z"}'
# Status: 201, status: APPROVED
```

### 3. Idempotência
```bash
# Repita a mesma requisição com Idempotency-Key: key1
# Deve retornar 201 com o MESMO paymentId

# Altere o amount e repita com key1
# Deve retornar 409 Conflict
```

### 4. Limite excedido
```bash
# Faça pagamentos até somar mais de 4000.00 no período diurno
# O próximo pagamento deve retornar 422
```

### 5. Listagem paginada
```bash
curl "http://localhost:8080/wallets/$WALLET_ID/payments"
# Deve retornar data + meta com nextCursor/previousCursor
```

### 6. Política TX_COUNT_LIMIT (bônus)
```bash
# Crie uma política TX_COUNT_LIMIT
curl -X POST http://localhost:8080/policies \
  -H "Content-Type: application/json" \
  -d '{"name":"TX_LIMIT","category":"TX_COUNT_LIMIT","dailyTransactionLimit":2}'

# Associe à carteira e faça 3 pagamentos
# O terceiro deve retornar 422
```

---

## Checklist de Submissão

- [ ] Repositório está privado no GitHub.
- [ ] Permissão de leitura concedida para `@tracefinancedev`.
- [ ] E-mail enviado para `backend@trace.finance` com assunto **Vaga Back-end** e URL do repositório.

---

## Commits

Todos os commits seguem **Conventional Commits**:

```
feat(wallet): adiciona endpoint de criação de carteira
fix(payment): corrige validação de limite noturno
refactor(policy): simplifica regra de categorização
test(payment): adiciona testes de concorrência
docs(readme): atualiza instruções do docker
chore(ci): adiciona workflow do GitHub Actions
```

---

> Projeto desenvolvido como parte do desafio back-end da Trace Finance.
