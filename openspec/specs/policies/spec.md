## ADDED Requirements

### Requirement: Criar politica VALUE_LIMIT
O sistema DEVE permitir criar uma politica de categoria `VALUE_LIMIT` com nome, `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` e `weekendDailyLimit`.

#### Scenario: Criacao valida de politica VALUE_LIMIT
- **WHEN** o cliente envia `POST /policies` com body valido contendo `name`, `category` igual a `VALUE_LIMIT`, `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit` e `weekendDailyLimit`
- **THEN** o sistema DEVE retornar `201 Created` com `id`, `name`, `category`, `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit`, `weekendDailyLimit`, `createdAt` e `updatedAt`

#### Scenario: Criacao da politica DEFAULT_VALUE_LIMIT
- **WHEN** o cliente envia `POST /policies` com `name` igual a `DEFAULT_VALUE_LIMIT`, `category` igual a `VALUE_LIMIT`, `maxPerPayment` igual a `1000.00`, `daytimeDailyLimit` igual a `4000.00`, `nighttimeDailyLimit` igual a `1000.00` e `weekendDailyLimit` igual a `1000.00`
- **THEN** o sistema DEVE retornar `201 Created` com limites exatamente iguais aos valores enviados

### Requirement: Validacao de campos obrigatorios na criacao de politica
O sistema DEVE rejeitar criacao de politica quando campos obrigatorios estiverem ausentes, nulos, vazios ou em branco.

#### Scenario: Name ausente
- **WHEN** o cliente envia `POST /policies` sem o campo `name`
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Name vazio
- **WHEN** o cliente envia `POST /policies` com `name` igual a string vazia
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Name apenas com espacos
- **WHEN** o cliente envia `POST /policies` com `name` composto apenas por espacos
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Categoria ausente
- **WHEN** o cliente envia `POST /policies` sem o campo `category`
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Categoria desconhecida
- **WHEN** o cliente envia `POST /policies` com `category` igual a `INVALID_CATEGORY`
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Limite maxPerPayment ausente para VALUE_LIMIT
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` sem o campo `maxPerPayment`
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Limite maxPerPayment igual a zero
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `maxPerPayment` igual a `0`
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Limite maxPerPayment negativo
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `maxPerPayment` igual a `-100`
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Limite daytimeDailyLimit igual a zero
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `daytimeDailyLimit` igual a `0`
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Limite nighttimeDailyLimit igual a zero
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `nighttimeDailyLimit` igual a `0`
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Limite weekendDailyLimit igual a zero
- **WHEN** o cliente envia `POST /policies` com `category` igual a `VALUE_LIMIT` e `weekendDailyLimit` igual a `0`
- **THEN** o sistema DEVE retornar `400 Bad Request`

### Requirement: Listar politicas
O sistema DEVE permitir listar todas as politicas cadastradas no formato `data` e `meta`.

#### Scenario: Listagem de politicas
- **WHEN** o cliente envia `GET /policies`
- **THEN** o sistema DEVE retornar `200 OK` com body contendo `data` array de politicas e `meta` com `nextCursor`, `previousCursor`, `total` e `totalMatches`

### Requirement: Consultar politicas da carteira
O sistema DEVE permitir consultar as politicas vinculadas a uma carteira, incluindo indicacao `active`.

#### Scenario: Consulta de politicas de carteira existente
- **WHEN** o cliente envia `GET /wallets/{walletId}/policies` para uma carteira existente
- **THEN** o sistema DEVE retornar `200 OK` com body contendo `data` array de politicas e `meta` com `total`
- **AND** cada politica no array DEVE possuir campo `active` indicando se e a politica ativa da carteira

#### Scenario: Consulta de politicas de carteira inexistente
- **WHEN** o cliente envia `GET /wallets/{walletId}/policies` para um `walletId` inexistente
- **THEN** o sistema DEVE retornar `404 Not Found`

### Requirement: Associar politica ativa a carteira
O sistema DEVE permitir associar uma politica como ativa para uma carteira, desativando a politica anteriormente ativa.

#### Scenario: Associacao de politica valida
- **WHEN** o cliente envia `PUT /wallets/{walletId}/policy` com body `{ "policyId": "uuid" }` para uma carteira e politica existentes
- **THEN** o sistema DEVE retornar `200 OK` com `walletId`, `policyId`, `active` igual a `true` e `updatedAt`

#### Scenario: Associacao substitui politica anterior
- **WHEN** o cliente envia `PUT /wallets/{walletId}/policy` para uma carteira que ja possui uma politica ativa
- **THEN** o sistema DEVE desativar a politica anteriormente ativa e ativar a nova
- **AND** a consulta `GET /wallets/{walletId}/policies` DEVE mostrar apenas a nova politica como `active`

#### Scenario: Associacao com carteira inexistente
- **WHEN** o cliente envia `PUT /wallets/{walletId}/policy` para um `walletId` inexistente
- **THEN** o sistema DEVE retornar `404 Not Found`

#### Scenario: Associacao com politica inexistente
- **WHEN** o cliente envia `PUT /wallets/{walletId}/policy` com `policyId` inexistente
- **THEN** o sistema DEVE retornar `404 Not Found`

### Requirement: Resolucao de politica ativa
O sistema DEVE prover um mecanismo para resolver a politica ativa de uma carteira em tempo de execucao.

#### Scenario: Resolucao de politica ativa para carteira com politica associada
- **WHEN** o mecanismo `PolicyResolverSpec` recebe um `walletId` de uma carteira que possui politica ativa
- **THEN** o sistema DEVE retornar a entidade da politica ativa

#### Scenario: Resolucao de politica ativa para carteira sem politica
- **WHEN** o mecanismo `PolicyResolverSpec` recebe um `walletId` de uma carteira sem politica ativa
- **THEN** o sistema DEVE retornar `null` ou lancar excecao documentada

### Requirement: Estrutura de avaliadores de politica
O sistema DEVE prover `PolicyEvaluatorRegistry` para registrar e resolver avaliadores por categoria de politica.

#### Scenario: Registro de evaluator VALUE_LIMIT
- **WHEN** um `PolicyEvaluator` para categoria `VALUE_LIMIT` e registrado no `PolicyEvaluatorRegistrySpec`
- **THEN** o registry DEVE retornar o evaluator correto ao consultar pela categoria `VALUE_LIMIT`

#### Scenario: Consulta de evaluator para categoria nao registrada
- **WHEN** o `PolicyEvaluatorRegistrySpec` e consultado para uma categoria sem evaluator registrado
- **THEN** o sistema DEVE retornar `null` ou comportamento documentado
