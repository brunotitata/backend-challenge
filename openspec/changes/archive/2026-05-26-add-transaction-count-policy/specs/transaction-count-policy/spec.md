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

### Requirement: Avaliacao de pagamento com TX_COUNT_LIMIT
O sistema MUST aprovar pagamentos enquanto a quantidade de transacoes aprovadas no dia for menor que `dailyTransactionLimit`, e rejeitar com `422` quando o limite for atingido.

#### Scenario: Aprova pagamentos ate a quantidade diaria definida
- **GIVEN** uma carteira com politica `TX_COUNT_LIMIT` ativa com `dailyTransactionLimit: 5`
- **GIVEN** a carteira nao possui pagamentos aprovados no dia atual
- **WHEN** cinco requisicoes de pagamento validas forem enviadas com `occurredAt` no mesmo dia
- **THEN** todas as cinco requisicoes MUSTm retornar `201 Created` com `status: "APPROVED"`

#### Scenario: Rejeita pagamento acima da quantidade diaria
- **GIVEN** uma carteira com politica `TX_COUNT_LIMIT` ativa com `dailyTransactionLimit: 5`
- **GIVEN** a carteira ja possui 5 pagamentos aprovados no dia atual
- **WHEN** uma sexta requisicao de pagamento valida for enviada com `occurredAt` no mesmo dia
- **THEN** o sistema MUST retornar `422 Unprocessable Entity`

#### Scenario: Dia seguinte reseta contador
- **GIVEN** uma carteira com politica `TX_COUNT_LIMIT` ativa com `dailyTransactionLimit: 5`
- **GIVEN** a carteira possui 5 pagamentos aprovados no dia anterior
- **WHEN** uma requisicao de pagamento valida for enviada com `occurredAt` no dia seguinte
- **THEN** o sistema MUST retornar `201 Created` com `status: "APPROVED"`

### Requirement: Concorrencia para TX_COUNT_LIMIT
O sistema MUST garantir que acessos simultaneos nao aprovem mais transacoes que `dailyTransactionLimit`.

#### Scenario: Pagamentos simultaneos nao ultrapassam quantidade diaria
- **GIVEN** uma carteira com politica `TX_COUNT_LIMIT` ativa com `dailyTransactionLimit: 2`
- **GIVEN** a carteira nao possui pagamentos aprovados no dia atual
- **WHEN** tres requisicoes simultaneas de pagamento validas forem enviadas
- **THEN** exatamente duas requisicoes MUSTm retornar `201 Created`
- **AND** exatamente uma requisicao MUST retornar `422 Unprocessable Entity`

### Requirement: Estrutura de resposta especifica por categoria
O sistema MUST retornar na resposta de politicas apenas os campos relevantes a categoria.

#### Scenario: Resposta de politica TX_COUNT_LIMIT
- **WHEN** o cliente consulta `GET /policies` ou `GET /wallets/{walletId}/policies` para uma politica `TX_COUNT_LIMIT`
- **THEN** a resposta MUST conter `id`, `name`, `category`, `dailyTransactionLimit`, `createdAt` e `updatedAt`
- **AND** a resposta NAO MUST conter `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` ou `weekendDailyLimit`
