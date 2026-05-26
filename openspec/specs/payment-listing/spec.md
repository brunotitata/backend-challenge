## Purpose
Especifica requisitos funcionais e de comportamento para esta capability.n

## Requirements

### Requirement: Listar pagamentos paginados por cursor
O sistema MUST expor `GET /wallets/{walletId}/payments` para listar pagamentos de uma carteira com paginação por cursor, filtro opcional por data e ordenação estável.

#### Scenario: Lista todos os pagamentos sem filtro
- **GIVEN** uma carteira com 3 pagamentos aprovados
- **WHEN** uma requisicao `GET /wallets/{walletId}/payments` sem parametros for enviada
- **THEN** o sistema MUST retornar `200 OK`
- **AND** o body MUST conter `data` com os 3 pagamentos
- **AND** o body MUST conter `meta` com `nextCursor`, `previousCursor`, `total` e `totalMatches`

#### Scenario: Filtra por startDate
- **GIVEN** pagamentos com `occurredAt` em 2024-08-25 e 2024-08-26
- **WHEN** `startDate=2024-08-26T00:00:00.0000Z` for enviado
- **THEN** o sistema MUST retornar apenas pagamentos a partir de 2024-08-26

#### Scenario: Filtra por endDate
- **GIVEN** pagamentos com `occurredAt` em 2024-08-25 e 2024-08-26
- **WHEN** `endDate=2024-08-25T23:59:59.9999Z` for enviado
- **THEN** o sistema MUST retornar apenas pagamentos ate 2024-08-25

#### Scenario: Filtra por intervalo startDate e endDate
- **GIVEN** pagamentos com `occurredAt` em 2024-08-24, 2024-08-25 e 2024-08-26
- **WHEN** `startDate=2024-08-25T00:00:00.0000Z` e `endDate=2024-08-25T23:59:59.9999Z` forem enviados
- **THEN** o sistema MUST retornar apenas pagamentos de 2024-08-25

#### Scenario: Retorna nextCursor quando ha proxima pagina
- **GIVEN** uma carteira com 25 pagamentos aprovados
- **WHEN** `limit=20` for enviado
- **THEN** o sistema MUST retornar 20 pagamentos na pagina atual
- **AND** `meta.nextCursor` MUST ser uma string nao nula

#### Scenario: Usa cursor para buscar proxima pagina
- **GIVEN** uma carteira com 25 pagamentos aprovados
- **WHEN** a primeira pagina com `limit=20` for obtida
- **AND** o `nextCursor` da primeira pagina for usado como parametro em nova requisicao
- **THEN** o sistema MUST retornar os 5 pagamentos restantes

#### Scenario: previousCursor permite navegacao reversa
- **GIVEN** uma carteira com 25 pagamentos aprovados
- **WHEN** a segunda pagina for obtida via cursor
- **THEN** `meta.previousCursor` MUST ser uma string nao nula
- **AND** ao usar `previousCursor`, o sistema MUST retornar a pagina anterior

#### Scenario: Ordenacao estavel para mesmo occurredAt
- **GIVEN** dois pagamentos com o mesmo `occurredAt` e `id` diferentes
- **WHEN** a listagem for solicitada
- **THEN** a ordem MUST ser deterministica por `occurredAt` ASC e `id` ASC

#### Scenario: Nao mistura pagamentos de carteiras diferentes
- **GIVEN** duas carteiras com pagamentos aprovados
- **WHEN** a listagem for solicitada para a primeira carteira
- **THEN** o sistema MUST retornar apenas pagamentos da primeira carteira

#### Scenario: Lista apenas pagamentos APPROVED
- **GIVEN** uma carteira com pagamentos `APPROVED` e `REJECTED`
- **WHEN** a listagem for solicitada
- **THEN** o sistema MUST retornar apenas pagamentos com `status: "APPROVED"`

### Requirement: Validar parametros de consulta
O sistema MUST validar os parametros da query e retornar erros apropriados.

#### Scenario: Rejeita walletId inexistente
- **WHEN** uma requisicao `GET /wallets/{inexistentId}/payments` for enviada
- **THEN** o sistema MUST retornar `404 Not Found`

#### Scenario: Rejeita startDate invalido
- **WHEN** `startDate=data-invalida` for enviado
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Rejeita endDate invalido
- **WHEN** `endDate=data-invalida` for enviado
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Rejeita startDate posterior a endDate
- **WHEN** `startDate=2024-08-26T00:00:00.0000Z` e `endDate=2024-08-25T00:00:00.0000Z` forem enviados
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Rejeita cursor malformado
- **WHEN** `cursor=invalido` for enviado
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Rejeita limit invalido
- **WHEN** `limit=0` for enviado
- **THEN** o sistema MUST retornar `400 Bad Request`

#### Scenario: Rejeita limit acima do maximo
- **WHEN** `limit=200` for enviado (maximo permitido: 100)
- **THEN** o sistema MUST retornar `400 Bad Request`

### Requirement: Estruturar resposta no formato padrao
A resposta MUST seguir o formato `data` e `meta` conforme o README do desafio.

#### Scenario: Resposta contem data com campos obrigatorios
- **GIVEN** uma lista com pagamentos aprovados
- **WHEN** a requisicao for bem-sucedida
- **THEN** cada item em `data` MUST conter `id`, `walletId`, `amount`, `occurredAt`, `status`, `createdAt`, `updatedAt`

#### Scenario: Resposta contem meta com todos os campos
- **GIVEN** uma lista com pagamentos aprovados
- **WHEN** a requisicao for bem-sucedida
- **THEN** `meta` MUST conter `nextCursor`, `previousCursor`, `total` e `totalMatches`

#### Scenario: totalMatches retorna null
- **GIVEN** uma lista com pagamentos aprovados
- **WHEN** a requisicao for bem-sucedida
- **THEN** `meta.totalMatches` MUST ser `null`

#### Scenario: total reflete contagem total da carteira
- **GIVEN** uma carteira com 10 pagamentos aprovados
- **WHEN** a requisicao for bem-sucedida
- **THEN** `meta.total` MUST ser `10`
