## ADDED Requirements

### Requirement: Criar politica TX_COUNT_LIMIT
O sistema MUST permitir criar uma politica de categoria `TX_COUNT_LIMIT` com `name` e `dailyTransactionLimit`.

#### Scenario: Criacao valida de politica TX_COUNT_LIMIT
- **WHEN** o cliente envia `POST /policies` com body contendo `name`, `category` igual a `TX_COUNT_LIMIT` e `dailyTransactionLimit`
- **THEN** o sistema MUST retornar `201 Created` com `id`, `name`, `category`, `dailyTransactionLimit`, `createdAt` e `updatedAt`

#### Scenario: Criacao de politica TX_COUNT_LIMIT com dailyTransactionLimit igual a 5
- **WHEN** o cliente envia `POST /policies` com `name` igual a `DAILY_TX_LIMIT`, `category` igual a `TX_COUNT_LIMIT` e `dailyTransactionLimit` igual a `5`
- **THEN** o sistema MUST retornar `201 Created` com `dailyTransactionLimit` igual a `5`

### Requirement: Validacao de campos obrigatorios para TX_COUNT_LIMIT
O sistema MUST rejeitar criacao de politica `TX_COUNT_LIMIT` quando `dailyTransactionLimit` estiver ausente, nulo, menor ou igual a zero.

#### Scenario: dailyTransactionLimit ausente
- **WHEN** o cliente envia `POST /policies` com `category` igual a `TX_COUNT_LIMIT` sem o campo `dailyTransactionLimit`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: dailyTransactionLimit igual a zero
- **WHEN** o cliente envia `POST /policies` com `category` igual a `TX_COUNT_LIMIT` e `dailyTransactionLimit` igual a `0`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: dailyTransactionLimit negativo
- **WHEN** o cliente envia `POST /policies` com `category` igual a `TX_COUNT_LIMIT` e `dailyTransactionLimit` igual a `-1`
- **THEN** o sistema MUST retornar `400 Bad Request`

### Requirement: Campos exclusivos de VALUE_LIMIT nao sao obrigatorios para TX_COUNT_LIMIT
O sistema MUST aceitar criacao de politica `TX_COUNT_LIMIT` sem `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` e `weekendDailyLimit`.

#### Scenario: TX_COUNT_LIMIT sem campos de VALUE_LIMIT
- **WHEN** o cliente envia `POST /policies` com `category` igual a `TX_COUNT_LIMIT`, `name` e `dailyTransactionLimit`, sem `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` e `weekendDailyLimit`
- **THEN** o sistema MUST retornar `201 Created`

### Requirement: Estrutura de resposta especifica por categoria
O sistema MUST retornar na resposta de politicas apenas os campos relevantes a categoria.

#### Scenario: Resposta de politica TX_COUNT_LIMIT
- **WHEN** o cliente consulta `GET /policies` ou `GET /wallets/{walletId}/policies` para uma politica `TX_COUNT_LIMIT`
- **THEN** a resposta MUST conter `id`, `name`, `category`, `dailyTransactionLimit`, `createdAt` e `updatedAt`
- **AND** a resposta NAO MUST conter `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` ou `weekendDailyLimit`
