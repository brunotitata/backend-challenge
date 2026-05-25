## Why

O projeto precisa expor a primeira capacidade de negocio do desafio: criacao de carteiras virtuais por HTTP/JSON. Esta fatia tambem garante a pre-condicao das proximas entregas, em que toda carteira criada deve possuir uma politica ativa default resolvivel para processamento futuro de pagamentos.

## What Changes

- Adicionar o endpoint `POST /wallets` para criar carteiras com `ownerName` obrigatorio.
- Validar requests sem body, `ownerName` ausente, nulo, vazio ou composto apenas por espacos com `400 Bad Request`.
- Persistir carteiras com `id` UUID, `ownerName`, `createdAt` e metadados tecnicos necessarios.
- Garantir que toda carteira criada seja associada automaticamente a politica ativa `DEFAULT_VALUE_LIMIT`.
- Preparar a persistencia de vinculo entre carteiras e politicas com restricao de uma politica ativa por carteira.
- Retornar resposta `201 Created` com `id`, `ownerName` e `createdAt` em ISO-8601 UTC.

## Capabilities

### New Capabilities

- `wallets`: Criacao e persistencia de carteiras virtuais, incluindo validacoes de entrada, contrato HTTP de criacao e associacao automatica a politica ativa default.

### Modified Capabilities

- Nenhuma.

## Impact

- API HTTP/Ktor: nova rota `POST /wallets`.
- Aplicacao: novo caso de uso para criacao de carteira.
- Dominio: entidade de carteira e regra de associacao a politica default.
- Persistencia: tabelas `wallets` e `wallet_policies`, indices e constraints relacionadas.
- Testes: cobertura de contrato HTTP, validacoes, persistencia e associacao da politica ativa default.
