# Cronograma de Desenvolvimento com OpenSpec

Este documento descreve uma forma recomendada de desenvolver o desafio `backend-challenge` usando OpenSpec como guia de especificacao, implementacao, verificacao e arquivamento de cada fatia de entrega.

O objetivo e evitar uma implementacao grande e arriscada, dividindo o desafio em capacidades pequenas, testaveis e com criterios claros de conclusao. Cada fatia deve gerar uma entrega funcional, com requisitos suficientemente detalhados para virar artefatos OpenSpec (`proposal.md`, `design.md`, `spec.md` e `tasks.md`).

## Fonte dos Requisitos

Os requisitos deste cronograma foram extraidos do README do desafio `backend-challenge`.

Repositorio de referencia:

```text
https://github.com/brunotitata/backend-challenge
```

Este documento deve ser tratado como um roteiro detalhado para criar as changes OpenSpec. Em caso de divergencia entre este documento e o README do desafio, o README do desafio prevalece.

## Contexto do Projeto

O repositorio contem um scaffold minimo em Kotlin + Ktor para uma API de pagamentos de carteiras virtuais.

O desafio pede:

- Criacao de carteiras.
- Gerenciamento de politicas de limite.
- Politica padrao `VALUE_LIMIT`.
- Processamento de pagamentos.
- Regras de limite por periodo: diurno, noturno e final de semana.
- Idempotencia para evitar pagamentos duplicados.
- Consistencia em acessos concorrentes.
- Listagem paginada de pagamentos com filtro por data.
- Testes automatizados abrangentes.
- Documentacao tecnica e instrucoes de execucao.
- Como bonus, politica `TX_COUNT_LIMIT` por quantidade diaria de transacoes.

## Requisitos Globais do Desafio

Estes requisitos devem ser considerados em todas as fatias aplicaveis.

### API

- A API deve ser HTTP/JSON.
- Todas as respostas de colecao devem usar o formato `data` e `meta`.
- Erros de validacao devem retornar `400 Bad Request`.
- Falta de limite deve retornar `422 Unprocessable Entity`.
- Tentativa idempotente conflitante deve retornar `409 Conflict`.
- Recursos inexistentes devem retornar `404 Not Found`.
- Respostas de erro devem ter estrutura padronizada e documentada no README tecnico.

### Datas, Horarios e Timezone

- Todos os campos de data/hora da API devem ser serializados em ISO-8601 com offset UTC, por exemplo `2024-08-25T22:31:44.4758Z`.
- O `occurredAt` informado no pagamento deve ser a data usada para classificar periodo e consumir limite.
- O `createdAt` representa a data de criacao do registro no sistema.
- O timezone operacional deve ser definido explicitamente no `design.md` da fatia de classificacao de periodos.
- Recomendacao: normalizar armazenamento em UTC e documentar a zona usada para classificacao de periodo.

### Dinheiro e Precisao

- Valores monetarios devem ser tratados sem erro de ponto flutuante.
- Recomendacao: usar `BigDecimal` no dominio e persistir como `NUMERIC(19, 2)` ou armazenar centavos como inteiro.
- A API deve rejeitar valores negativos, zero, nulos, ausentes ou com precisao invalida.
- A regra de comparacao de limite deve ser inclusiva: valores iguais ao limite permitido devem ser aceitos.

### Politica Padrao

- A politica padrao deve ser de categoria `VALUE_LIMIT`.
- A politica padrao deve se chamar `DEFAULT_VALUE_LIMIT`.
- A politica padrao deve conter `maxPerPayment` igual a `1000.00`.
- A politica padrao deve conter `daytimeDailyLimit` igual a `4000.00`.
- A politica padrao deve conter `nighttimeDailyLimit` igual a `1000.00`.
- A politica padrao deve conter `weekendDailyLimit` igual a `1000.00`.
- Toda carteira criada deve possuir uma politica ativa resolvivel em tempo de execucao.
- A implementacao deve associar automaticamente a politica `DEFAULT_VALUE_LIMIT` na criacao da carteira.

### Politicas Ativas

- Cada carteira pode estar vinculada a uma ou mais politicas.
- Para este desafio, apenas uma politica deve estar ativa por carteira em um dado momento.
- O pagamento deve resolver a politica ativa em tempo de execucao.
- A inclusao de nova categoria de politica nao deve exigir alteracao no fluxo principal de pagamento.
- O design deve prever um mecanismo como `PolicyResolver`, `PolicyEvaluator` e `PolicyEvaluatorRegistry`.

### Banco de Dados

- O candidato pode escolher o banco de dados.
- Este cronograma recomenda PostgreSQL para facilitar transacoes, constraints, indices unicos e consistencia concorrente.
- O README tecnico final deve justificar a escolha do PostgreSQL e explicar o trade-off em relacao ao diferencial citado no desafio de Docker Compose com MongoDB.
- Todas as tabelas, constraints e indices devem ser criados por script ou migracao.

### Testes Eliminatorios

Os testes automatizados devem cobrir obrigatoriamente:

- Validacao de regras de negocio.
- Limites por periodo.
- Valor maximo por pagamento.
- Calculo correto dos periodos diurno, noturno e final de semana.
- Bordas `05:59:59`, `06:00:00`, `17:59:59` e `18:00:00`.
- Logica de politicas diferentes: `VALUE_LIMIT` e `TX_COUNT_LIMIT`.
- Tratamento de excecoes e validacoes.
- Persistencia de carteiras, pagamentos e politicas.
- Filtros e paginacao por cursor.
- Reset diario e reset por periodo.
- Politicas dinamicas na mesma base de codigo.
- APIs end-to-end retornando estruturas corretas conforme a politica ativa.
- Concorrencia para impedir consumo acima do limite.
- Idempotencia para impedir pagamentos duplicados.

## Estrategia Recomendada

A melhor abordagem para este desafio e desenvolver por capacidade de negocio, e nao por endpoint isolado.

### Alternativas consideradas

| Abordagem | Vantagem | Risco |
| --- | --- | --- |
| Por endpoint | Mais simples de organizar inicialmente | Pode espalhar regra de negocio e deixar concorrencia/idempotencia para o fim |
| Por camada tecnica | Boa para arquitetura | Demora para gerar entregas funcionais testaveis |
| Por capacidade de negocio | Melhor para OpenSpec e testes incrementais | Exige disciplina para manter cada fatia pequena |

Recomendacao: desenvolver por capacidade de negocio.

## Fluxo OpenSpec por Fatia

Cada fatia deve seguir o mesmo ciclo:

```text
proposal.md -> design.md -> specs/.../spec.md -> tasks.md -> implementacao -> verificacao -> archive
```

Fluxo operacional sugerido:

```bash
openspec list --json
```

Criar proposta da fatia:

```bash
/opsx-propose nome-da-change
```

Implementar:

```bash
/opsx-apply nome-da-change
```

Verificar:

```bash
/opsx-verify nome-da-change
```

Arquivar apos conclusao:

```bash
/opsx-archive nome-da-change
```

Todos os artefatos OpenSpec devem ser mantidos em PT-BR, conforme `openspec/config.yaml`.

## Padrao Minimo para Cada Spec OpenSpec

Cada `spec.md` deve evitar linguagem vaga. Use requisitos verificaveis.

Formato recomendado:

```markdown
## ADDED Requirements

### Requirement: Nome do requisito
O sistema DEVE ...

#### Scenario: Cenario observavel
- GIVEN ...
- WHEN ...
- THEN ...
```

Cada fatia deve explicitar:

- Escopo.
- Fora de escopo.
- Contratos HTTP.
- Regras de negocio.
- Validacoes.
- Codigos de erro.
- Persistencia e indices.
- Testes unitarios.
- Testes de integracao.
- Criterios de aceite.

## Arquitetura-Alvo

Estrutura sugerida para manter separacao entre API, aplicacao, dominio e infraestrutura:

```text
Ktor API
  |
  |-- routes
  |   |-- WalletRoutes
  |   |-- PolicyRoutes
  |   `-- PaymentRoutes
  |
  |-- application/usecases
  |   |-- CreateWallet
  |   |-- CreatePolicy
  |   |-- AssignPolicyToWallet
  |   |-- ProcessPayment
  |   `-- ListPayments
  |
  |-- domain
  |   |-- Wallet
  |   |-- Payment
  |   |-- LimitPolicy
  |   |-- PolicyResolver
  |   |-- PolicyEvaluator
  |   |-- PolicyEvaluatorRegistry
  |   |-- ValueLimitEvaluator
  |   `-- TxCountLimitEvaluator
  |
  `-- infrastructure
      |-- repositories
      |-- database
      `-- migrations
```

Banco recomendado: PostgreSQL.

Justificativa:

- Facilita transacoes.
- Permite constraints e indices unicos para idempotencia.
- Ajuda a garantir consistencia em concorrencia.
- Torna mais simples justificar decisoes tecnicas no README.

## Ordem das Fatias

```text
setup-project-foundation
  -> add-wallet-management
  -> add-value-limit-policies
  -> add-payment-period-classification
  -> add-value-limit-payment-processing
  -> add-payment-idempotency
  -> harden-payment-concurrency
  -> add-payment-listing
  -> add-transaction-count-policy
  -> add-operational-readiness
```

## Fatia 0: Fundacao do Projeto

Change: `setup-project-foundation`

Objetivo: preparar a base tecnica para que as proximas fatias sejam implementadas com seguranca.

Artefatos sugeridos:

```text
openspec/changes/setup-project-foundation/
  proposal.md
  design.md
  tasks.md
  specs/project-foundation/spec.md
```

### Escopo

- Manter Kotlin + Ktor.
- Manter JSON como formato de entrada e saida.
- Definir PostgreSQL como banco recomendado.
- Adicionar migracoes para schema inicial.
- Configurar Docker Compose para banco local.
- Configurar estrutura inicial de camadas.
- Configurar tratamento global de erros com `StatusPages`.
- Configurar serializacao de datas em ISO-8601 UTC.
- Configurar testes com `testApplication`.
- Manter `/health` funcionando.

### Contratos HTTP

```http
GET /health
```

Resposta esperada:

```http
200 OK
```

```json
{
  "status": "UP"
}
```

### Persistencia

- Criar infraestrutura de migracoes.
- Criar extensoes necessarias para UUID, se aplicavel.
- Definir padrao de colunas `id`, `created_at` e `updated_at`.

### Criterios de aceite

- A aplicacao sobe localmente.
- O banco sobe via Docker Compose.
- As migracoes executam sem erro.
- Os testes basicos executam com sucesso.
- `/health` retorna `200 OK`.
- O README possui comandos iniciais de execucao.
- A estrutura de erro padrao esta definida, mesmo que ainda com poucos casos.

### Testes obrigatorios

- Teste de health check.
- Teste de inicializacao da aplicacao com `testApplication`.
- Teste de serializacao JSON basica.
- Teste de resposta padrao para rota inexistente, se o tratamento global ja estiver configurado.

### Fora de escopo

- Criacao de carteiras.
- Politicas.
- Pagamentos.
- Idempotencia.
- Concorrencia.

Commit sugerido:

```text
chore(project): configura base da aplicacao
```

## Fatia 1: Carteiras

Change: `add-wallet-management`

Objetivo: implementar a criacao de carteiras e garantir que toda carteira possua politica ativa default.

Artefatos sugeridos:

```text
openspec/changes/add-wallet-management/
  proposal.md
  design.md
  tasks.md
  specs/wallets/spec.md
```

### Endpoint

```http
POST /wallets
```

Request:

```json
{
  "ownerName": "string"
}
```

Response de sucesso:

```http
201 Created
```

```json
{
  "id": "uuid",
  "ownerName": "string",
  "createdAt": "2024-08-25T22:31:44.4758Z"
}
```

### Regras de negocio

- O campo `ownerName` e obrigatorio.
- `ownerName` nao pode ser nulo.
- `ownerName` nao pode ser vazio.
- `ownerName` nao pode conter apenas espacos.
- A carteira deve ser persistida com `id` UUID e `createdAt`.
- Toda carteira criada deve receber automaticamente a politica ativa `DEFAULT_VALUE_LIMIT`.
- A politica ativa deve ser resolvivel antes de qualquer pagamento.

### Validacoes e erros

- Request sem body deve retornar `400 Bad Request`.
- `ownerName` ausente, nulo, vazio ou em branco deve retornar `400 Bad Request`.
- Erros inesperados devem seguir a estrutura de erro padrao.

### Persistencia

Tabelas sugeridas:

```text
wallets
  id
  owner_name
  created_at
  updated_at

wallet_policies
  id
  wallet_id
  policy_id
  active
  created_at
  updated_at
```

Constraint recomendada:

```text
uma politica ativa por wallet_id
```

### Testes obrigatorios

- Cria carteira valida.
- Rejeita `ownerName` ausente.
- Rejeita `ownerName` vazio.
- Rejeita `ownerName` com apenas espacos.
- Persiste carteira corretamente.
- Retorna estrutura esperada com `id`, `ownerName` e `createdAt`.
- Associa ou garante politica ativa default para a carteira criada.
- Retorna `404 Not Found` ao consultar ou manipular carteira inexistente em endpoints aplicaveis das fatias seguintes.

### Criterios de aceite

- `POST /wallets` esta funcional.
- Carteiras validas sao persistidas.
- Carteiras invalidas sao rejeitadas.
- Uma carteira recem-criada possui politica ativa default resolvivel.
- A API retorna exatamente o contrato esperado pelo README do desafio.

### Fora de escopo

- Endpoint de listagem de carteiras.
- Atualizacao de carteira.
- Remocao de carteira.
- Processamento de pagamentos.

Commit sugerido:

```text
feat(wallet): adiciona criacao de carteira
```

## Fatia 2: Politicas VALUE_LIMIT

Change: `add-value-limit-policies`

Objetivo: implementar politicas de limite baseadas em valor, incluindo criacao, listagem, consulta por carteira e associacao de politica ativa.

Artefatos sugeridos:

```text
openspec/changes/add-value-limit-policies/
  proposal.md
  design.md
  tasks.md
  specs/policies/spec.md
```

### Endpoints

```http
POST /policies
GET /policies
GET /wallets/{walletId}/policies
PUT /wallets/{walletId}/policy
```

### Criar politica VALUE_LIMIT

Request:

```json
{
  "name": "DEFAULT_VALUE_LIMIT",
  "category": "VALUE_LIMIT",
  "maxPerPayment": 1000,
  "daytimeDailyLimit": 4000,
  "nighttimeDailyLimit": 1000,
  "weekendDailyLimit": 1000
}
```

Response de sucesso:

```http
201 Created
```

```json
{
  "id": "uuid",
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

### Listar politicas

Response:

```json
{
  "data": [
    {
      "id": "uuid",
      "name": "DEFAULT_VALUE_LIMIT",
      "category": "VALUE_LIMIT",
      "maxPerPayment": 1000,
      "daytimeDailyLimit": 4000,
      "nighttimeDailyLimit": 1000,
      "weekendDailyLimit": 1000,
      "createdAt": "2024-08-18T08:30:00.0000Z",
      "updatedAt": "2024-08-25T22:31:44.4758Z"
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

### Consultar politicas da carteira

```http
GET /wallets/{walletId}/policies
```

Response:

```json
{
  "data": [
    {
      "id": "uuid",
      "name": "DEFAULT_VALUE_LIMIT",
      "category": "VALUE_LIMIT",
      "maxPerPayment": 1000,
      "daytimeDailyLimit": 4000,
      "nighttimeDailyLimit": 1000,
      "weekendDailyLimit": 1000,
      "active": true,
      "createdAt": "2024-08-20T10:00:00.0000Z",
      "updatedAt": "2024-08-26T12:34:56.7890Z"
    }
  ],
  "meta": {
    "total": 1
  }
}
```

O campo `active` nao aparece no exemplo do desafio, mas e recomendado para remover ambiguidade quando a carteira possuir mais de uma politica vinculada. Se optar por nao expor `active`, o design deve explicar como o cliente identifica a politica ativa.

### Associar politica a carteira

```http
PUT /wallets/{walletId}/policy
```

Request:

```json
{
  "policyId": "uuid"
}
```

Response recomendada:

```http
200 OK
```

```json
{
  "walletId": "uuid",
  "policyId": "uuid",
  "active": true,
  "updatedAt": "2024-08-26T12:34:56.7890Z"
}
```

### Regras de negocio

- `category` e obrigatorio.
- `VALUE_LIMIT` deve possuir `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` e `weekendDailyLimit`.
- Todos os limites devem ser maiores que zero.
- `maxPerPayment` da politica default deve ser `1000.00`.
- Limite diurno default deve ser `4000.00`.
- Limite noturno default deve ser `1000.00`.
- Limite de final de semana default deve ser `1000.00`.
- Uma carteira pode estar vinculada a mais de uma politica ao longo do tempo.
- Apenas uma politica pode estar ativa por carteira.
- `PUT /wallets/{walletId}/policy` deve tornar a politica informada ativa e desativar a politica anteriormente ativa.
- A politica ativa deve ser resolvida em tempo de execucao no processamento de pagamento.

### Validacoes e erros

- `name` ausente, nulo, vazio ou em branco deve retornar `400 Bad Request`.
- `category` ausente ou desconhecida deve retornar `400 Bad Request`.
- Campos obrigatorios de `VALUE_LIMIT` ausentes devem retornar `400 Bad Request`.
- Limites menores ou iguais a zero devem retornar `400 Bad Request`.
- `walletId` inexistente deve retornar `404 Not Found`.
- `policyId` inexistente deve retornar `404 Not Found`.

### Persistencia

Tabela sugerida:

```text
policies
  id
  name
  category
  max_per_payment
  daytime_daily_limit
  nighttime_daily_limit
  weekend_daily_limit
  daily_transaction_limit
  created_at
  updated_at
```

Indices recomendados:

```text
policies(category)
wallet_policies(wallet_id)
wallet_policies(policy_id)
unique active policy por wallet_id
```

### Testes obrigatorios

- Cria politica `VALUE_LIMIT` valida.
- Rejeita politica sem `name`.
- Rejeita categoria desconhecida.
- Rejeita limites invalidos.
- Lista politicas no formato `data` e `meta`.
- Associa politica a carteira.
- Desativa politica anterior ao associar nova politica ativa.
- Consulta politicas da carteira.
- Resolve politica ativa corretamente.
- Retorna `404` para carteira inexistente.
- Retorna `404` para politica inexistente.

### Criterios de aceite

- Endpoints de politicas estao funcionais.
- A politica default `DEFAULT_VALUE_LIMIT` existe ou e criada por migracao/seeding documentado.
- Cada carteira possui exatamente uma politica ativa.
- O contrato de resposta segue os exemplos do README do desafio.
- O dominio esta preparado para multiplas categorias de politica.

### Fora de escopo

- Processamento de pagamentos.
- `TX_COUNT_LIMIT` completo.
- Concorrencia de pagamentos.

Commit sugerido:

```text
feat(policy): adiciona politicas de limite por valor
```

## Fatia 3: Classificacao de Periodos

Change: `add-payment-period-classification`

Objetivo: isolar e testar a regra de classificacao de periodo usada no consumo de limite.

Artefatos sugeridos:

```text
openspec/changes/add-payment-period-classification/
  proposal.md
  design.md
  tasks.md
  specs/payment-periods/spec.md
```

### Periodos

- `DAYTIME`.
- `NIGHTTIME`.
- `WEEKEND`.

### Regras de classificacao

- Segunda a sexta, `>= 06:00:00` e `< 18:00:00`, deve ser `DAYTIME`.
- Segunda a sexta, `>= 18:00:00`, deve ser `NIGHTTIME`.
- Segunda a sexta, `< 06:00:00`, deve ser `NIGHTTIME`.
- Sabado e domingo, de `00:00:00` ate `23:59:59.999999999`, devem ser `WEEKEND`.
- Final de semana tem prioridade sobre horario. Sabado `10:00:00` e `22:00:00` devem ser `WEEKEND`.
- O timezone usado na classificacao deve estar definido explicitamente no `design.md`.

### Chave de consumo por periodo

- `DAYTIME` deve consumir limite do dia local do `occurredAt`.
- `WEEKEND` deve consumir limite do dia local do `occurredAt`.
- `NIGHTTIME` deve consumir limite da noite iniciada as `18:00:00`.
- Para horario noturno antes de `06:00:00`, o `period_start` deve ser o dia anterior as `18:00:00`.
- Para horario noturno a partir de `18:00:00`, o `period_start` deve ser o mesmo dia as `18:00:00`.
- Exemplo: segunda `23:00` e terca `01:00` pertencem a mesma noite iniciada segunda `18:00`.

### Testes obrigatorios

- Dia util `05:59:59` deve ser `NIGHTTIME`.
- Dia util `06:00:00` deve ser `DAYTIME`.
- Dia util `17:59:59` deve ser `DAYTIME`.
- Dia util `18:00:00` deve ser `NIGHTTIME`.
- Sabado `00:00:00` deve ser `WEEKEND`.
- Sabado `12:00:00` deve ser `WEEKEND`.
- Domingo `23:59:59` deve ser `WEEKEND`.
- Virada de dia no periodo noturno deve manter a mesma chave de consumo.
- Segunda `23:00` e terca `01:00` devem apontar para o mesmo `period_start` noturno.

### Criterios de aceite

- A classificacao de periodo esta isolada do framework HTTP.
- A regra pode ser testada unitariamente sem banco de dados.
- O timezone esta documentado.
- A chave de consumo de limite esta definida para todos os periodos.

### Fora de escopo

- Criacao de pagamentos.
- Persistencia de consumo.
- Concorrencia.

Commit sugerido:

```text
feat(payment): adiciona classificacao de periodos
```

## Fatia 4: Pagamento com VALUE_LIMIT

Change: `add-value-limit-payment-processing`

Objetivo: processar pagamentos usando a politica ativa `VALUE_LIMIT`.

Artefatos sugeridos:

```text
openspec/changes/add-value-limit-payment-processing/
  proposal.md
  design.md
  tasks.md
  specs/payments/spec.md
  specs/value-limit-policy/spec.md
```

### Endpoint

```http
POST /wallets/{walletId}/payments
```

Request:

```json
{
  "amount": 999.99,
  "occurredAt": "2024-08-26T09:42:17.2500Z"
}
```

Response de sucesso:

```http
201 Created
```

```json
{
  "paymentId": "uuid",
  "status": "APPROVED",
  "amount": 999.99,
  "occurredAt": "2024-08-26T09:42:17.2500Z"
}
```

### Regras de negocio

- `walletId` deve existir.
- A carteira deve possuir politica ativa.
- A politica ativa de categoria `VALUE_LIMIT` deve ser avaliada por um avaliador proprio.
- `amount` deve ser maior que zero.
- `amount` deve ser menor ou igual a `maxPerPayment` da politica ativa.
- `occurredAt` e obrigatorio.
- `occurredAt` deve ser usado para classificar o periodo.
- Pagamento aprovado deve ser persistido com status `APPROVED`.
- Pagamento rejeitado por limite insuficiente nao deve consumir limite.
- Pagamento rejeitado por validacao nao deve ser persistido como aprovado.
- O consumo de limite deve considerar pagamentos aprovados da mesma carteira, politica, periodo e chave de periodo.
- Uma carteira pode consumir ate `4000.00` no periodo diurno e mais `1000.00` no periodo noturno do mesmo dia util com a politica default.
- Valor exatamente igual ao limite restante deve ser aprovado.
- Valor acima do limite restante deve ser rejeitado com `422 Unprocessable Entity`.

### Validacoes e erros

- `walletId` inexistente deve retornar `404 Not Found`.
- Body ausente deve retornar `400 Bad Request`.
- `amount` ausente, nulo, zero ou negativo deve retornar `400 Bad Request`.
- `amount` acima de `maxPerPayment` deve retornar `400 Bad Request`.
- `occurredAt` ausente ou invalido deve retornar `400 Bad Request`.
- Limite diario ou por periodo insuficiente deve retornar `422 Unprocessable Entity`.
- Carteira sem politica ativa deve retornar erro documentado. Recomendacao: `422 Unprocessable Entity` por estado de negocio invalido.

### Persistencia

Tabela sugerida:

```text
payments
  id
  wallet_id
  policy_id
  amount
  occurred_at
  period_type
  period_start
  status
  created_at
  updated_at
```

Indices recomendados:

```text
payments(wallet_id, occurred_at, id)
payments(wallet_id, policy_id, period_type, period_start)
```

### Testes obrigatorios

- Aprova pagamento dentro do limite diurno.
- Aprova pagamento dentro do limite noturno.
- Aprova pagamento dentro do limite de final de semana.
- Rejeita `amount` zero.
- Rejeita `amount` negativo.
- Rejeita `amount` acima de `maxPerPayment`.
- Rejeita soma acima do limite diurno.
- Rejeita soma acima do limite noturno.
- Rejeita soma acima do limite de final de semana.
- Aceita soma exatamente igual ao limite.
- Reseta limite em novo dia diurno.
- Reseta limite em nova noite.
- Reseta limite em novo dia de final de semana.
- Usa `occurredAt` e nao `createdAt` para classificar periodo.
- Nao mistura consumo de carteiras diferentes.
- Retorna response com `paymentId`, `status`, `amount` e `occurredAt`.

### Criterios de aceite

- `POST /wallets/{walletId}/payments` processa pagamentos aprovados.
- Regras de periodo e limite sao aplicadas corretamente.
- Pagamentos aprovados sao persistidos.
- Pagamentos recusados por limite retornam `422`.
- A implementacao ainda nao precisa ser perfeita sob concorrencia; isso sera endurecido na Fatia 6.

### Fora de escopo

- Idempotencia completa.
- Concorrencia robusta.
- Listagem de pagamentos.
- `TX_COUNT_LIMIT`.

Commit sugerido:

```text
feat(payment): processa pagamentos com limite por valor
```

## Fatia 5: Idempotencia

Change: `add-payment-idempotency`

Objetivo: impedir que retries criem pagamentos duplicados.

Artefatos sugeridos:

```text
openspec/changes/add-payment-idempotency/
  proposal.md
  design.md
  tasks.md
  specs/payment-idempotency/spec.md
```

### Decisao recomendada

Usar header HTTP:

```http
Idempotency-Key: string
```

### Regras de negocio

- `Idempotency-Key` deve ser obrigatorio para `POST /wallets/{walletId}/payments` a partir desta fatia.
- A chave deve ser unica por carteira.
- Mesma carteira, mesma chave e mesmo payload deve retornar o resultado original.
- Mesma carteira, mesma chave e payload diferente deve retornar `409 Conflict`.
- A unicidade deve ser garantida no banco.
- A comparacao de payload deve considerar pelo menos `amount` e `occurredAt` normalizados.
- A resposta idempotente deve preservar o status code e o body original quando o payload for identico.
- A idempotencia deve ser isolada por carteira. A mesma chave pode ser usada em carteiras diferentes.
- Tentativas recusadas por validacao `400 Bad Request` nao devem ser registradas como chaves idempotentes consumidas.
- Tentativas aceitas para processamento devem registrar a chave idempotente para permitir retorno do resultado original em retries.

### Validacoes e erros

- Header ausente deve retornar `400 Bad Request`.
- Header vazio ou em branco deve retornar `400 Bad Request`.
- Header acima do tamanho maximo definido deve retornar `400 Bad Request`.
- Payload conflitante com chave ja usada deve retornar `409 Conflict`.

### Persistencia

Tabela sugerida:

```text
payment_idempotency_keys
  id
  wallet_id
  idempotency_key
  request_hash
  payment_id
  response_status
  response_body
  created_at
  updated_at
```

Indice obrigatorio:

```text
unique(wallet_id, idempotency_key)
```

### Testes obrigatorios

- Retry identico nao duplica pagamento.
- Retry identico retorna o mesmo `paymentId`.
- Retry identico retorna o mesmo status code e body.
- Retry com `amount` diferente retorna `409`.
- Retry com `occurredAt` diferente retorna `409`.
- Mesma chave em carteiras diferentes nao conflita.
- Header ausente retorna `400`.
- Header vazio retorna `400`.
- Constraint do banco garante unicidade.
- Idempotencia nao consome limite duas vezes.

### Criterios de aceite

- Pagamentos repetidos com mesma chave e payload nao geram duplicidade.
- Conflitos idempotentes retornam `409`.
- A regra e garantida por constraint no banco, nao apenas por checagem em memoria.

### Fora de escopo

- Testes de corrida concorrente pesados. Eles serao tratados na Fatia 6.

Commit sugerido:

```text
feat(payment): adiciona idempotencia no processamento
```

## Fatia 6: Concorrencia

Change: `harden-payment-concurrency`

Objetivo: garantir que acessos simultaneos nao ultrapassem limites e mantenham idempotencia correta.

Artefatos sugeridos:

```text
openspec/changes/harden-payment-concurrency/
  proposal.md
  design.md
  tasks.md
  specs/payment-concurrency/spec.md
```

### Estrategia recomendada

- Processar pagamento dentro de transacao.
- Usar bloqueio pessimista, UPSERT atomico ou atualizacao condicional atomica de consumo.
- Garantir que verificacao de limite, atualizacao de consumo, persistencia do pagamento e registro idempotente acontecam como uma unidade.
- Manter bloqueio no menor escopo possivel: carteira, politica e periodo.
- Evitar bloqueio global que serialize pagamentos de todas as carteiras.

### Tabela de consumo sugerida

```text
limit_consumptions
  id
  wallet_id
  policy_id
  period_type
  period_start
  consumed_amount
  transaction_count
  created_at
  updated_at
```

Indice recomendado:

```text
unique(wallet_id, policy_id, period_type, period_start)
```

### Regras de negocio

- Duas ou mais requisicoes simultaneas para a mesma carteira nao podem aprovar valor total acima do limite.
- Se duas requisicoes de `700.00` competirem por limite restante de `1000.00`, apenas uma deve ser aprovada e a outra deve retornar `422`.
- A checagem de limite e a gravacao do consumo devem ser atomicas.
- Idempotencia deve continuar correta sob concorrencia.
- Duas requisicoes simultaneas com mesma carteira, mesma chave e mesmo payload devem resultar em um unico pagamento.
- Duas requisicoes simultaneas com mesma carteira, mesma chave e payload diferente devem resultar em um sucesso ou erro original e um `409`, sem duplicidade.
- Pagamentos de carteiras diferentes nao devem bloquear indevidamente entre si.

### Testes obrigatorios

- Pagamentos simultaneos para a mesma carteira nao ultrapassam limite diurno.
- Pagamentos simultaneos para a mesma carteira nao ultrapassam limite noturno.
- Pagamentos simultaneos para a mesma carteira nao ultrapassam limite de final de semana.
- Cenario `700 + 700` com limite restante `1000` aprova apenas um pagamento.
- Idempotencia com mesma chave e mesmo payload continua criando apenas um pagamento sob concorrencia.
- Idempotencia com mesma chave e payload diferente retorna `409` para a tentativa conflitante.
- Pagamentos de carteiras diferentes podem ser aprovados em paralelo.
- Constraint de `limit_consumptions` impede linhas duplicadas para mesma chave de consumo.

### Criterios de aceite

- Nao existe overbooking de limite em testes concorrentes.
- A solucao usa suporte do banco/transacao para consistencia.
- A escolha tecnica esta documentada no `design.md` e no README tecnico final.

### Fora de escopo

- Otimizacoes avancadas de performance.
- Metricas de concorrencia.

Commit sugerido:

```text
fix(payment): garante consistencia em pagamentos concorrentes
```

## Fatia 7: Listagem de Pagamentos

Change: `add-payment-listing`

Objetivo: implementar listagem paginada de pagamentos com filtro por data.

Artefatos sugeridos:

```text
openspec/changes/add-payment-listing/
  proposal.md
  design.md
  tasks.md
  specs/payment-listing/spec.md
```

### Endpoint

```http
GET /wallets/{walletId}/payments?startDate=2024-08-25T00:00:00.0000Z&endDate=2024-08-26T23:59:59.9999Z&cursor=abc123
```

### Query parameters

- `startDate`: opcional, ISO-8601.
- `endDate`: opcional, ISO-8601.
- `cursor`: opcional.
- `limit`: opcional, se implementado. Deve ter valor padrao e maximo documentados.

### Response

```json
{
  "data": [
    {
      "id": "uuid",
      "walletId": "uuid",
      "amount": 250.00,
      "occurredAt": "2024-08-25T14:22:08.1234Z",
      "status": "APPROVED",
      "createdAt": "2024-08-25T14:22:08.2234Z",
      "updatedAt": "2024-08-25T14:22:08.2234Z"
    }
  ],
  "meta": {
    "nextCursor": "def456",
    "previousCursor": null,
    "total": 1,
    "totalMatches": null
  }
}
```

### Regras de negocio

- `startDate` e `endDate` sao opcionais.
- Se ausentes, deve retornar todos os pagamentos da carteira, paginados.
- A listagem nao pode misturar pagamentos de carteiras diferentes.
- A ordenacao deve ser estavel por `occurredAt` e `id`.
- O cursor deve codificar posicao suficiente para retomar a listagem de forma estavel.
- A resposta deve incluir `nextCursor`.
- A resposta deve incluir `previousCursor`.
- A resposta deve incluir `total`.
- A resposta deve incluir `totalMatches`, podendo ser `null` se a contagem filtrada for intencionalmente evitada por performance.
- Somente pagamentos aprovados devem aparecer na listagem.

### Validacoes e erros

- `walletId` inexistente deve retornar `404 Not Found`.
- `startDate` invalido deve retornar `400 Bad Request`.
- `endDate` invalido deve retornar `400 Bad Request`.
- `startDate` posterior a `endDate` deve retornar `400 Bad Request`.
- `cursor` invalido ou malformado deve retornar `400 Bad Request`.
- `limit` invalido deve retornar `400 Bad Request`.

### Persistencia

Indice recomendado:

```text
payments(wallet_id, occurred_at, id)
```

### Testes obrigatorios

- Lista todos os pagamentos sem filtro.
- Filtra apenas por `startDate`.
- Filtra apenas por `endDate`.
- Filtra por intervalo `startDate` e `endDate`.
- Retorna `nextCursor` quando ha proxima pagina.
- Retorna `previousCursor` quando aplicavel.
- Usa cursor para buscar proxima pagina.
- Mantem ordenacao estavel quando dois pagamentos possuem mesmo `occurredAt`.
- Nao mistura pagamentos de carteiras diferentes.
- Rejeita cursor invalido.
- Rejeita intervalo de datas invalido.

### Criterios de aceite

- `GET /wallets/{walletId}/payments` retorna `data` e `meta` conforme README.
- Paginacao por cursor funciona em ida e volta quando `previousCursor` estiver disponivel.
- Filtros de data funcionam corretamente.
- Indice adequado existe para a consulta.

### Fora de escopo

- Exportacao de pagamentos.
- Busca textual.
- Relatorios agregados.

Commit sugerido:

```text
feat(payment): adiciona listagem paginada por cursor
```

## Fatia 8: Bonus TX_COUNT_LIMIT

Change: `add-transaction-count-policy`

Objetivo: demonstrar extensibilidade com uma nova categoria de politica sem alterar o fluxo principal de pagamento.

Artefatos sugeridos:

```text
openspec/changes/add-transaction-count-policy/
  proposal.md
  design.md
  tasks.md
  specs/transaction-count-policy/spec.md
```

### Categoria

```text
TX_COUNT_LIMIT
```

### Criar politica TX_COUNT_LIMIT

Request recomendado:

```json
{
  "name": "DAILY_TX_LIMIT",
  "category": "TX_COUNT_LIMIT",
  "dailyTransactionLimit": 5
}
```

Response recomendada:

```json
{
  "id": "uuid",
  "name": "DAILY_TX_LIMIT",
  "category": "TX_COUNT_LIMIT",
  "dailyTransactionLimit": 5,
  "createdAt": "2024-08-20T10:00:00.0000Z",
  "updatedAt": "2024-08-26T12:34:56.7890Z"
}
```

### Regras de negocio

- `TX_COUNT_LIMIT` deve limitar a quantidade maxima de pagamentos aprovados por carteira em um dia.
- O limite diario deve ser definido por `dailyTransactionLimit`.
- Exemplo: `dailyTransactionLimit = 5` permite 5 pagamentos aprovados no dia e rejeita o sexto.
- O limite deve ser independente do valor do pagamento, salvo validacoes gerais de pagamento como `amount > 0`.
- O contador deve resetar no dia seguinte.
- A politica deve ser aplicada pela mesma rota `POST /wallets/{walletId}/payments`.
- O fluxo principal de pagamento nao deve conter `if/else` espalhado por categoria.
- A nova politica deve ser registrada em um `PolicyEvaluatorRegistry` ou mecanismo equivalente.
- A resposta de politicas deve ter estrutura especifica por categoria.

### Modelo conceitual

```text
ProcessPayment
  |
  v
PolicyResolver
  |
  v
PolicyEvaluatorRegistry
  |-- VALUE_LIMIT -> ValueLimitEvaluator
  `-- TX_COUNT_LIMIT -> TxCountLimitEvaluator
```

### Validacoes e erros

- `dailyTransactionLimit` ausente deve retornar `400 Bad Request`.
- `dailyTransactionLimit` menor ou igual a zero deve retornar `400 Bad Request`.
- Campos exclusivos de `VALUE_LIMIT` nao devem ser obrigatorios para `TX_COUNT_LIMIT`.
- Pagamento acima da quantidade diaria permitida deve retornar `422 Unprocessable Entity`.

### Persistencia

- Reutilizar `policies` com campo `daily_transaction_limit`, ou modelar configuracoes por categoria em tabelas separadas.
- A escolha deve estar justificada no `design.md`.
- Reutilizar `limit_consumptions.transaction_count` para controle concorrente.

### Testes obrigatorios

- Cria politica `TX_COUNT_LIMIT` valida.
- Rejeita `TX_COUNT_LIMIT` sem `dailyTransactionLimit`.
- Rejeita `dailyTransactionLimit` invalido.
- Associa politica `TX_COUNT_LIMIT` a uma carteira.
- Aprova pagamentos ate a quantidade diaria definida.
- Rejeita pagamento acima da quantidade diaria com `422`.
- Dia seguinte reseta contador.
- `VALUE_LIMIT` continua funcionando.
- O fluxo principal continua generico.
- A resposta de politica `TX_COUNT_LIMIT` possui campos especificos da categoria.
- Concorrencia nao permite aprovar mais transacoes que `dailyTransactionLimit`.

### Criterios de aceite

- `TX_COUNT_LIMIT` funciona como politica alternativa.
- A inclusao da categoria nao altera o contrato principal de pagamento.
- O design demonstra extensibilidade para novas politicas.
- Testes unitarios e de integracao provam que `VALUE_LIMIT` e `TX_COUNT_LIMIT` coexistem.

### Fora de escopo

- Combinacao de multiplas politicas ativas simultaneamente.
- Politicas compostas.
- Limite por quantidade em periodos diferentes do dia.

Commit sugerido:

```text
feat(policy): adiciona politica por quantidade de transacoes
```

## Fatia 9: Prontidao Operacional e Documentacao

Change: `add-operational-readiness`

Objetivo: finalizar entrega com diferenciais, documentacao clara e verificacoes completas.

Artefatos sugeridos:

```text
openspec/changes/add-operational-readiness/
  proposal.md
  design.md
  tasks.md
  specs/operational-readiness/spec.md
```

### Escopo obrigatorio

- README tecnico com decisoes de arquitetura.
- README com instrucoes para rodar aplicacao.
- README com instrucoes para rodar testes.
- README com instrucoes para subir dependencias.
- README explicando trade-offs.
- README explicando escolha do banco de dados.
- Migracoes ou scripts para criar tabelas, constraints e indices.
- Docker Compose completo para dependencias locais.
- Workflow de CI com build e testes.
- Revisao final dos contratos de API conforme README do desafio.

### Diferenciais recomendados

- Logs estruturados com identificador de requisicao.
- Metricas simples de pagamentos aprovados e recusados.
- Auditoria dos eventos de pagamento.
- OpenAPI/Swagger em `/docs` ou Postman Collection e Environment.
- Docker Compose incluindo aplicacao e banco.

### README tecnico deve conter

- Visao geral da arquitetura.
- Como rodar localmente.
- Como rodar testes.
- Como executar migracoes.
- Endpoints disponiveis.
- Exemplos de request e response.
- Decisoes de modelagem de dominio.
- Decisoes de idempotencia.
- Decisoes de concorrencia.
- Decisao de timezone.
- Decisao de precisao monetaria.
- Justificativa para PostgreSQL.
- Trade-offs e pontos de atencao.
- Como validar manualmente os principais fluxos.

### Submissao

- O repositorio final deve ser privado no GitHub, conforme instrucao do desafio.
- Deve ser concedida permissao de leitura para `@tracefinancedev`.
- Deve ser enviado e-mail para `backend@trace.finance` com assunto `Vaga Back-end` e a URL do repositorio.
- Estes passos devem constar no checklist final do projeto, mesmo que nao sejam automatizaveis.

### Testes e verificacoes

- `./gradlew test` executa com sucesso.
- Aplicacao sobe localmente.
- Dependencias sobem via Docker Compose.
- Migracoes executam do zero.
- README permite rodar o projeto do zero.
- CI executa build e testes.
- Todos os endpoints obrigatorios possuem teste end-to-end.
- Todos os requisitos eliminatorios do desafio estao cobertos por testes.

### Criterios de aceite

- O projeto pode ser clonado e executado seguindo apenas o README.
- A documentacao explica as principais decisoes tecnicas.
- CI esta configurado.
- Os diferenciais opcionais foram implementados ou documentados como nao implementados por restricao de tempo.
- A entrega final esta pronta para submissao.

Commits sugeridos:

```text
docs(readme): documenta decisoes tecnicas
chore(ci): adiciona workflow de build e testes
chore(docker): adiciona compose da aplicacao
```

## Cronograma de 7 Dias Uteis

| Dia | Foco | Resultado esperado |
| --- | --- | --- |
| Dia 1 | Fatia 0 | Base tecnica, banco, Docker inicial, testes basicos, migracoes e arquitetura definida |
| Dia 2 | Fatias 1 e 2 | Carteiras, politica default e politicas `VALUE_LIMIT` funcionando |
| Dia 3 | Fatias 3 e 4 | Classificacao de periodos e pagamento com limite por valor |
| Dia 4 | Fatias 5 e 6 | Idempotencia persistida e consistencia em concorrencia |
| Dia 5 | Fatia 7 | Listagem de pagamentos com filtro de data e cursor completo |
| Dia 6 | Fatia 8 | Bonus `TX_COUNT_LIMIT` implementado e testado |
| Dia 7 | Fatia 9 | README, CI, Docker, revisao final e testes completos |

## Priorizacao se o Tempo For Menor

Ordem de prioridade:

1. Carteiras, politica default, politicas e pagamento com `VALUE_LIMIT`.
2. Testes de periodo, limite e validacao.
3. Idempotencia.
4. Concorrencia.
5. Listagem de pagamentos.
6. Bonus `TX_COUNT_LIMIT`.
7. Observabilidade, OpenAPI, auditoria e metricas.

Se for necessario cortar escopo, nao cortar os requisitos eliminatorios. Os diferenciais podem ser reduzidos antes do bonus, e o bonus pode ser reduzido antes de idempotencia, concorrencia ou testes obrigatorios.

## Matriz de Cobertura dos Requisitos do README

| Requisito | Fatia principal | Observacao |
| --- | --- | --- |
| Criar carteira | Fatia 1 | `POST /wallets` |
| `ownerName` obrigatorio | Fatia 1 | Validacao `400` |
| Politica default `VALUE_LIMIT` | Fatias 1 e 2 | `DEFAULT_VALUE_LIMIT` |
| Gerenciar politicas | Fatia 2 | `POST /policies`, `GET /policies`, associacao |
| Consultar politicas da carteira | Fatia 2 | `GET /wallets/{walletId}/policies` |
| Uma politica ativa por carteira | Fatia 2 | Constraint e resolver |
| Resolver politica em runtime | Fatias 2, 4 e 8 | `PolicyResolver` |
| Valor maximo por pagamento | Fatia 4 | `maxPerPayment` |
| Limite diurno | Fatias 3 e 4 | `06:00-18:00` |
| Limite noturno | Fatias 3 e 4 | `18:00-06:00` com chave de noite |
| Limite final de semana | Fatias 3 e 4 | Sabado e domingo |
| Idempotencia | Fatia 5 | `Idempotency-Key` |
| Concorrencia | Fatia 6 | Transacao e consumo atomico |
| Listar pagamentos | Fatia 7 | Filtro por data e cursor |
| Testes unitarios | Todas | Detalhados por fatia |
| Testes de integracao | Todas | Persistencia e API |
| `TX_COUNT_LIMIT` | Fatia 8 | Bonus |
| README tecnico | Fatia 9 | Decisoes e trade-offs |
| Docker Compose | Fatias 0 e 9 | Dependencias e opcionalmente app |
| CI | Fatia 9 | Build e testes |
| OpenAPI/Postman | Fatia 9 | Diferencial |
| Logs e metricas | Fatia 9 | Diferencial |
| Auditoria | Fatia 9 | Diferencial |

## Criterios de Sucesso da Entrega

A entrega final deve demonstrar:

- Dominio bem modelado.
- Regras de periodo cobertas por testes.
- Politicas extensiveis.
- Politica default aplicada corretamente.
- Idempotencia real e persistida.
- Concorrencia tratada com suporte do banco/transacao.
- API compativel com o README do desafio.
- Testes unitarios e de integracao abrangentes.
- README tecnico explicando decisoes e trade-offs.
- Migracoes ou scripts criando schema, constraints e indices.
- Commits seguindo Conventional Commits.

## Checklist Final

- [ ] Todas as changes OpenSpec relevantes foram arquivadas.
- [ ] Todos os endpoints obrigatorios foram implementados.
- [ ] `POST /wallets` retorna contrato correto.
- [ ] `GET /wallets/{walletId}/policies` retorna contrato correto.
- [ ] `POST /wallets/{walletId}/payments` retorna contrato correto.
- [ ] `GET /wallets/{walletId}/payments` retorna contrato correto.
- [ ] `POST /policies` retorna contrato correto.
- [ ] `GET /policies` retorna contrato correto.
- [ ] `PUT /wallets/{walletId}/policy` funciona corretamente.
- [ ] Politica default `DEFAULT_VALUE_LIMIT` existe.
- [ ] Toda carteira possui politica ativa.
- [ ] Testes unitarios cobrem regras de negocio.
- [ ] Testes unitarios cobrem calculo de periodo.
- [ ] Testes unitarios cobrem bordas de horario.
- [ ] Testes unitarios cobrem `VALUE_LIMIT`.
- [ ] Testes unitarios cobrem `TX_COUNT_LIMIT`, se o bonus for implementado.
- [ ] Testes de integracao cobrem persistencia de carteiras.
- [ ] Testes de integracao cobrem persistencia de politicas.
- [ ] Testes de integracao cobrem persistencia de pagamentos.
- [ ] Testes de integracao cobrem paginacao e filtros.
- [ ] Testes de integracao cobrem reset diario e por periodo.
- [ ] Testes de concorrencia validam limite sob acessos simultaneos.
- [ ] Idempotencia retorna resultado original ou `409` corretamente.
- [ ] Idempotencia e garantida por constraint no banco.
- [ ] Migracoes criam tabelas, constraints e indices.
- [ ] README explica como rodar aplicacao, banco e testes.
- [ ] README explica decisoes de arquitetura.
- [ ] README explica idempotencia, concorrencia, timezone e precisao monetaria.
- [ ] Docker Compose sobe dependencias.
- [ ] CI executa build e testes.
- [ ] OpenAPI ou Postman foi adicionado, se houver tempo.
- [ ] Logs estruturados foram adicionados, se houver tempo.
- [ ] Metricas simples foram adicionadas, se houver tempo.
- [ ] Auditoria de eventos foi adicionada, se houver tempo.
- [ ] Commits seguem Conventional Commits.
- [ ] Repositorio privado recebeu permissao de leitura para `@tracefinancedev`.
- [ ] E-mail de submissao foi preparado com assunto `Vaga Back-end` e URL do repositorio.
