## ADDED Requirements

### Requirement: Idempotencia via Idempotency-Key
O sistema DEVE implementar idempotencia em `POST /wallets/{walletId}/payments` utilizando o header HTTP `Idempotency-Key`. A chave DEVE ser obrigatoria e unica por carteira. A garantia de unicidade DEVE ser feita por constraint unique no banco.

#### Scenario: Header ausente retorna 400
- **GIVEN** uma requisicao `POST /wallets/{walletId}/payments`
- **WHEN** o header `Idempotency-Key` nao for enviado
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Header vazio retorna 400
- **GIVEN** uma requisicao `POST /wallets/{walletId}/payments`
- **WHEN** o header `Idempotency-Key` for enviado vazio ou em branco
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Header acima do tamanho maximo retorna 400
- **GIVEN** uma requisicao `POST /wallets/{walletId}/payments`
- **WHEN** o header `Idempotency-Key` exceder 255 caracteres
- **THEN** o sistema DEVE retornar `400 Bad Request`

#### Scenario: Retry identico nao duplica pagamento
- **GIVEN** um pagamento aprovado com `Idempotency-Key: "key-123"`
- **WHEN** a mesma requisicao com `Idempotency-Key: "key-123"` for reenviada
- **THEN** o sistema DEVE retornar o mesmo status code e body da resposta original
- **AND** o `paymentId` DEVE ser o mesmo do pagamento original
- **AND** nenhum novo pagamento DEVE ser criado

#### Scenario: Retry com payload diferente retorna 409
- **GIVEN** um pagamento aprovado com `Idempotency-Key: "key-123"` e `amount: 500.00`
- **WHEN** uma requisicao com `Idempotency-Key: "key-123"` e `amount: 600.00` for enviada
- **THEN** o sistema DEVE retornar `409 Conflict`

#### Scenario: Retry com occurredAt diferente retorna 409
- **GIVEN** um pagamento aprovado com `Idempotency-Key: "key-123"` e `occurredAt: "2024-08-26T09:00:00Z"`
- **WHEN** uma requisicao com `Idempotency-Key: "key-123"` e `occurredAt: "2024-08-26T10:00:00Z"` for enviada
- **THEN** o sistema DEVE retornar `409 Conflict`

#### Scenario: Mesma chave em carteiras diferentes nao conflita
- **GIVEN** duas carteiras diferentes `wallet-A` e `wallet-B`
- **GIVEN** um pagamento aprovado em `wallet-A` com `Idempotency-Key: "key-123"`
- **WHEN** uma requisicao com `Idempotency-Key: "key-123"` for enviada para `wallet-B`
- **THEN** o sistema DEVE processar o pagamento normalmente
- **AND** retornar `201 Created` com um novo `paymentId`

#### Scenario: Idempotencia nao consome limite duas vezes
- **GIVEN** um pagamento aprovado de `1000.00` com `Idempotency-Key: "key-123"` no periodo diurno
- **GIVEN** o limite diurno restante e `3000.00`
- **WHEN** o mesmo pagamento for reenviado com `Idempotency-Key: "key-123"`
- **THEN** o consumo de limite DEVE permanecer em `1000.00`
- **AND** o segundo retry NAO DEVE consumir mais limite

#### Scenario: Validacao 400 nao registra chave idempotente
- **GIVEN** uma requisicao com `Idempotency-Key: "key-456"` e `amount: 0`
- **WHEN** a requisicao for rejeitada com `400 Bad Request`
- **THEN** a chave `"key-456"` NAO DEVE ser registrada como consumida
- **AND** o cliente pode reenviar a mesma chave com payload valido

#### Scenario: Constraint do banco garante unicidade
- **GIVEN** uma tentativa de inserir `(wallet_id: "w1", idempotency_key: "dup-key")` ja existente
- **WHEN** a insercao for tentada
- **THEN** o banco DEVE rejeitar com violacao de unique constraint

### Requirement: Registro de chave idempotente
O sistema DEVE registrar a chave idempotente apos aceitar a requisicao para processamento, armazenando o request hash, o paymentId, o response status e o response body para permitir retorno do resultado original em retries.

#### Scenario: Chave registrada apos pagamento aprovado
- **GIVEN** uma requisicao valida com `Idempotency-Key: "key-789"` e `amount: 500.00`
- **WHEN** o pagamento for aprovado
- **THEN** o sistema DEVE registrar em `payment_idempotency_keys` a chave, o request hash, o `paymentId`, o status `201` e o body da resposta

#### Scenario: Chave registrada apos pagamento rejeitado por limite
- **GIVEN** uma requisicao com `Idempotency-Key: "key-999"` e `amount` acima do limite restante
- **WHEN** o pagamento for rejeitado com `422 Unprocessable Entity`
- **THEN** o sistema DEVE registrar em `payment_idempotency_keys` a chave, o request hash e o status `422`
- **AND** um retry com mesma chave e payload DEVE retornar `422` sem processar novamente
