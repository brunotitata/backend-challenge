## Purpose
Especifica requisitos funcionais e de comportamento para esta capability.n

## Requirements

### Requirement: Idempotencia via Idempotency-Key
O sistema MUST implementar idempotencia em `POST /wallets/{walletId}/payments` utilizando o header HTTP `Idempotency-Key`. A chave MUST ser obrigatoria e unica por carteira. A garantia de unicidade MUST ser feita por constraint unique no banco. A checagem e o registro da chave idempotente MUSTM ocorrer dentro da mesma transacao do pagamento, utilizando `INSERT ... ON CONFLICT DO NOTHING` para atomicidade.

#### Scenario: Header ausente retorna 400
- **GIVEN** uma requisicao `POST /wallets/{walletId}/payments`
- **WHEN** o header `Idempotency-Key` nao for enviado
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Header vazio retorna 400
- **GIVEN** uma requisicao `POST /wallets/{walletId}/payments`
- **WHEN** o header `Idempotency-Key` for enviado vazio ou em branco
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Header acima do tamanho maximo retorna 400
- **GIVEN** uma requisicao `POST /wallets/{walletId}/payments`
- **WHEN** o header `Idempotency-Key` exceder 255 caracteres
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Retry identico nao duplica pagamento
- **GIVEN** um pagamento aprovado com `Idempotency-Key: "key-123"`
- **WHEN** a mesma requisicao com `Idempotency-Key: "key-123"` for reenviada
- **THEN** o sistema MUST retornar o mesmo status code e body da resposta original
- **AND** o `paymentId` MUST ser o mesmo do pagamento original
- **AND** nenhum novo pagamento MUST ser criado

#### Scenario: Retry com payload diferente retorna 409
- **GIVEN** um pagamento aprovado com `Idempotency-Key: "key-123"` e `amount: 500.00`
- **WHEN** uma requisicao com `Idempotency-Key: "key-123"` e `amount: 600.00` for enviada
- **THEN** o sistema MUST retornar `409 Conflict`

#### Scenario: Retry com occurredAt diferente retorna 409
- **GIVEN** um pagamento aprovado com `Idempotency-Key: "key-123"` e `occurredAt: "2024-08-26T09:00:00Z"`
- **WHEN** uma requisicao com `Idempotency-Key: "key-123"` e `occurredAt: "2024-08-26T10:00:00Z"` for enviada
- **THEN** o sistema MUST retornar `409 Conflict`

#### Scenario: Mesma chave em carteiras diferentes nao conflita
- **GIVEN** duas carteiras diferentes `wallet-A` e `wallet-B`
- **GIVEN** um pagamento aprovado em `wallet-A` com `Idempotency-Key: "key-123"`
- **WHEN** uma requisicao com `Idempotency-Key: "key-123"` for enviada para `wallet-B`
- **THEN** o sistema MUST processar o pagamento normalmente
- **AND** retornar `201 Created` com um novo `paymentId`

#### Scenario: Idempotencia nao consome limite duas vezes
- **GIVEN** um pagamento aprovado de `1000.00` com `Idempotency-Key: "key-123"` no periodo diurno
- **GIVEN** o limite diurno restante e `3000.00`
- **WHEN** o mesmo pagamento for reenviado com `Idempotency-Key: "key-123"`
- **THEN** o consumo de limite MUST permanecer em `1000.00`
- **AND** o segundo retry NAO MUST consumir mais limite

#### Scenario: Validacao 400 nao registra chave idempotente
- **GIVEN** uma requisicao com `Idempotency-Key: "key-456"` e `amount: 0`
- **WHEN** a requisicao for rejeitada com `400 Bad Request`
- **THEN** a chave `"key-456"` NAO MUST ser registrada como consumida
- **AND** o cliente pode reenviar a mesma chave com payload valido

#### Scenario: Constraint do banco garante unicidade
- **GIVEN** uma tentativa de inserir `(wallet_id: "w1", idempotency_key: "dup-key")` ja existente
- **WHEN** a insercao for tentada
- **THEN** o banco MUST rejeitar com violacao de unique constraint

#### Scenario: Duas requisicoes simultaneas com mesma chave resultam em um pagamento
- **GIVEN** duas requisicoes simultaneas com `Idempotency-Key: "key-concurrent"` e mesmo payload valido
- **WHEN** ambas forem processadas concorrentemente
- **THEN** apenas um pagamento MUST ser criado
- **AND** ambas as requisicoes MUSTM retornar `201 Created` com o mesmo `paymentId`

#### Scenario: Duas requisicoes simultaneas com mesma chave e payload diferente
- **GIVEN** duas requisicoes simultaneas com `Idempotency-Key: "key-conflict"` e payloads diferentes
- **WHEN** ambas forem processadas concorrentemente
- **THEN** apenas uma MUST ser processada com sucesso ou rejeitada por limite
- **AND** a outra MUST retornar `409 Conflict`

### Requirement: Registro de chave idempotente
O sistema MUST registrar a chave idempotente dentro da transacao do pagamento, utilizando `INSERT ... ON CONFLICT DO NOTHING` para atomicidade. O registro MUST armazenar o request hash, o paymentId, o response status e o response body para permitir retorno do resultado original em retries.

#### Scenario: Chave registrada apos pagamento aprovado dentro da transacao
- **GIVEN** uma requisicao valida com `Idempotency-Key: "key-789"` e `amount: 500.00`
- **WHEN** o pagamento for aprovado
- **THEN** o sistema MUST registrar em `payment_idempotency_keys` dentro da mesma transacao do pagamento
- **AND** o registro MUST conter a chave, o request hash, o `paymentId`, o status `201` e o body da resposta

#### Scenario: Chave registrada apos pagamento rejeitado por limite dentro da transacao
- **GIVEN** uma requisicao com `Idempotency-Key: "key-999"` e `amount` acima do limite restante
- **WHEN** o pagamento for rejeitado com `422 Unprocessable Entity`
- **THEN** o sistema MUST registrar em `payment_idempotency_keys` dentro da mesma transacao
- **AND** o registro MUST conter a chave, o request hash e o status `422`
- **AND** um retry com mesma chave e payload MUST retornar `422` sem processar novamente
