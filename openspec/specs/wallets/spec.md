## Purpose

Definir o comportamento de criacao, validacao e persistencia de carteiras virtuais, incluindo a associacao automatica da politica ativa default necessaria para pagamentos futuros.

## Requirements

### Requirement: Criacao de carteira via HTTP
O sistema MUST permitir a criacao de uma carteira por meio do endpoint `POST /wallets`, recebendo JSON com `ownerName` e retornando `201 Created` com `id`, `ownerName` e `createdAt`.

#### Scenario: Criacao de carteira valida
- **WHEN** o cliente envia `POST /wallets` com body `{ "ownerName": "Ana Silva" }`
- **THEN** o sistema DEVE retornar `201 Created` com `id` em formato UUID, `ownerName` igual a `Ana Silva` e `createdAt` em ISO-8601 UTC

### Requirement: Validacao de ownerName
O sistema MUST rejeitar criacao de carteira quando `ownerName` estiver ausente, nulo, vazio ou composto apenas por espacos.

#### Scenario: OwnerName ausente
- **WHEN** o cliente envia `POST /wallets` com body sem o campo `ownerName`
- **THEN** o sistema DEVE retornar `400 Bad Request` usando a estrutura padrao de erro

#### Scenario: OwnerName nulo
- **WHEN** o cliente envia `POST /wallets` com `ownerName` nulo
- **THEN** o sistema DEVE retornar `400 Bad Request` usando a estrutura padrao de erro

#### Scenario: OwnerName vazio
- **WHEN** o cliente envia `POST /wallets` com `ownerName` igual a string vazia
- **THEN** o sistema DEVE retornar `400 Bad Request` usando a estrutura padrao de erro

#### Scenario: OwnerName apenas com espacos
- **WHEN** o cliente envia `POST /wallets` com `ownerName` composto apenas por espacos
- **THEN** o sistema DEVE retornar `400 Bad Request` usando a estrutura padrao de erro

### Requirement: Validacao de body obrigatorio
O sistema MUST rejeitar criacao de carteira quando a requisicao nao possuir body JSON valido.

#### Scenario: Request sem body
- **WHEN** o cliente envia `POST /wallets` sem body
- **THEN** o sistema DEVE retornar `400 Bad Request` usando a estrutura padrao de erro

### Requirement: Persistencia de carteira
O sistema MUST persistir cada carteira criada com identificador UUID, nome do titular e data de criacao.

#### Scenario: Carteira persistida corretamente
- **WHEN** uma carteira valida e criada com sucesso
- **THEN** o sistema DEVE gravar a carteira na tabela `wallets` com `id`, `owner_name` e `created_at`

### Requirement: Politica ativa default na criacao
O sistema MUST associar automaticamente a politica ativa `DEFAULT_VALUE_LIMIT` a toda carteira criada.

#### Scenario: Carteira criada possui politica default ativa
- **WHEN** uma carteira valida e criada com sucesso
- **THEN** o sistema DEVE registrar um vinculo ativo entre a carteira e a politica `DEFAULT_VALUE_LIMIT`

#### Scenario: Politica ativa resolvivel
- **WHEN** uma carteira recem-criada e consultada pelo mecanismo interno de resolucao de politica ativa
- **THEN** o sistema DEVE resolver a politica `DEFAULT_VALUE_LIMIT` para essa carteira

### Requirement: Controle de politica ativa por carteira na aplicacao
O sistema MUST manter o fluxo de criacao de carteira com exatamente uma politica ativa associada, sem codificar esta regra de negocio no DDL Flyway.

#### Scenario: Carteira criada possui apenas um vinculo ativo
- **WHEN** uma carteira valida e criada com sucesso
- **THEN** o sistema DEVE registrar apenas um vinculo ativo para a carteira criada
