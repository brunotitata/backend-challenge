## Purpose
Especifica requisitos funcionais e de comportamento para esta capability.n

Especifica a criacao, listagem, consulta e associacao de politicas de limite a carteiras.
## Requirements
### Requirement: Criar politica VALUE_LIMIT
O sistema MUST permitir criar uma politica de categoria `VALUE_LIMIT` com nome, `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` e `weekendDailyLimit`.

#### Scenario: Criacao valida de politica VALUE_LIMIT
- **WHEN** o cliente envia `POST /policies` com body valido contendo `name`, `category` igual a `VALUE_LIMIT`, `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` e `weekendDailyLimit`
- **THEN** o sistema MUST retornar `201 Created` com `id`, `name`, `category`, `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit`, `weekendDailyLimit`, `createdAt` e `updatedAt`

#### Scenario: Criacao da politica DEFAULT_VALUE_LIMIT
- **WHEN** o cliente envia `POST /policies` com `name` igual a `DEFAULT_VALUE_LIMIT`, `category` igual a `VALUE_LIMIT`, `maxPerPayment` igual a `1000.00`, `daytimeDailyLimit` igual a `4000.00`, `nighttimeDailyLimit` igual a `1000.00` e `weekendDailyLimit` igual a `1000.00`
- **THEN** o sistema MUST retornar `201 Created` com limites exatamente iguais aos valores enviados

### Requirement: Validacao de campos obrigatorios na criacao de politica
O sistema MUST rejeitar criacao de politica quando campos obrigatorios estiverem ausentes, nulos, vazios ou em branco.

#### Scenario: Name ausente
- **WHEN** o cliente envia `POST /policies` sem o campo `name`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Name vazio
- **WHEN** o cliente envia `POST /policies` com `name` igual a string vazia
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Name apenas com espacos
- **WHEN** o cliente envia `POST /policies` com `name` composto apenas por espacos
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Categoria ausente
- **WHEN** o cliente envia `POST /policies` sem o campo `category`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Categoria desconhecida
- **WHEN** o cliente envia `POST /policies` com `category` igual a `INVALID_CATEGORY`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Limite maxPerPayment ausente para VALUE_LIMIT
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` sem o campo `maxPerPayment`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Limite maxPerPayment igual a zero
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `maxPerPayment` igual a `0`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Limite maxPerPayment negativo
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `maxPerPayment` igual a `-100`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Limite daytimeDailyLimit igual a zero
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `daytimeDailyLimit` igual a `0`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Limite nighttimeDailyLimit igual a zero
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `nighttimeDailyLimit` igual a `0`
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Limite weekendDailyLimit igual a zero
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `weekendDailyLimit` igual a `0`
- **THEN** o sistema MUST retornar `400 Bad Request`

### Requirement: Listar politicas
O sistema MUST permitir listar todas as politicas cadastradas no formato `data` e `meta`.

#### Scenario: Listagem de politicas
- **WHEN** o cliente envia `GET /policies`
- **THEN** o sistema MUST retornar `200 OK` com body contendo `data` array de politicas e `meta` com `nextCursor`, `previousCursor`, `total` e `totalMatches`

### Requirement: Consultar politicas da carteira
O sistema MUST permitir consultar as politicas vinculadas a uma carteira, incluindo indicacao `active`.

#### Scenario: Consulta de politicas de carteira existente
- **WHEN** o cliente envia `GET /wallets/{walletId}/policies` para uma carteira existente
- **THEN** o sistema MUST retornar `200 OK` com body contendo `data` array de politicas e `meta` com `total`
- **AND** cada politica no array MUST possuir campo `active` indicando se e a politica ativa da carteira

#### Scenario: Consulta de politicas de carteira inexistente
- **WHEN** o cliente envia `GET /wallets/{walletId}/policies` para um `walletId` inexistente
- **THEN** o sistema MUST retornar `404 Not Found`

### Requirement: Associar politica ativa a carteira
O sistema MUST permitir associar uma politica como ativa para uma carteira, desativando a politica anteriormente ativa.

#### Scenario: Associacao de politica valida
- **WHEN** o cliente envia `PUT /wallets/{walletId}/policy` com body `{ "policyId": "uuid" }` para uma carteira e politica existentes
- **THEN** o sistema MUST retornar `200 OK` com `walletId`, `policyId`, `active` igual a `true` e `updatedAt`

#### Scenario: Associacao substitui politica anterior
- **WHEN** o cliente envia `PUT /wallets/{walletId}/policy` para uma carteira que ja possui uma politica ativa
- **THEN** o sistema MUST desativar a politica anteriormente ativa e ativar a nova
- **AND** a consulta `GET /wallets/{walletId}/policies` MUST mostrar apenas a nova politica como `active`

#### Scenario: Associacao com carteira inexistente
- **WHEN** o cliente envia `PUT /wallets/{walletId}/policy` para um `walletId` inexistente
- **THEN** o sistema MUST retornar `404 Not Found`

#### Scenario: Associacao com politica inexistente
- **WHEN** o cliente envia `PUT /wallets/{walletId}/policy` com `policyId` inexistente
- **THEN** o sistema MUST retornar `404 Not Found`

### Requirement: Resolucao de politica ativa
O sistema MUST prover um mecanismo para resolver a politica ativa de uma carteira em tempo de execucao.

#### Scenario: Resolucao de politica ativa para carteira com politica associada
- **WHEN** o mecanismo `PolicyResolverSpec` recebe um `walletId` de uma carteira que possui politica ativa
- **THEN** o sistema MUST retornar a entidade da politica ativa

#### Scenario: Resolucao de politica ativa para carteira sem politica
- **WHEN** o mecanismo `PolicyResolverSpec` recebe um `walletId` de uma carteira sem politica ativa
- **THEN** o sistema MUST retornar `null` ou lancar excecao documentada

### Requirement: Estrutura de avaliadores de politica
O sistema MUST prover `PolicyEvaluatorRegistry` para registrar e resolver avaliadores por categoria de politica.

#### Scenario: Registro de evaluator VALUE_LIMIT
- **WHEN** um `PolicyEvaluator` para categoria `VALUE_LIMIT` e registrado no `PolicyEvaluatorRegistrySpec`
- **THEN** o registry MUST retornar o evaluator correto ao consultar pela categoria `VALUE_LIMIT`

#### Scenario: Consulta de evaluator para categoria nao registrada
- **WHEN** o `PolicyEvaluatorRegistrySpec` e consultado para uma categoria sem evaluator registrado
- **THEN** o sistema MUST retornar `null` ou comportamento documentado

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

