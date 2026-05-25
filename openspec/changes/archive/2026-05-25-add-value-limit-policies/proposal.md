## Why

O gerenciamento de carteiras esta implementado, mas ainda nao existem endpoints para criar, listar e associar politicas de limite. As tabelas `policies` e `wallet_policies` ja foram criadas na migracao V1, e a politica `DEFAULT_VALUE_LIMIT` ja e inserida automaticamente na criacao de carteiras. Esta change expoe os endpoints de CRUD de politicas `VALUE_LIMIT`, permitindo que o usuario crie novas politicas, liste as existentes, consulte as politicas de uma carteira e altere a politica ativa de uma carteira.

## What Changes

- Criar endpoint `POST /policies` para criar politicas `VALUE_LIMIT` com nome, categoria e limites.
- Criar endpoint `GET /policies` para listar todas as politicas no formato `data`/`meta`.
- Criar endpoint `GET /wallets/{walletId}/policies` para consultar as politicas vinculadas a uma carteira com indicacao `active`.
- Criar endpoint `PUT /wallets/{walletId}/policy` para associar/ativar uma politica em uma carteira, desativando a anterior.
- Adicionar constraint de unicidade para garantir apenas uma politica ativa por carteira.
- Implementar o dominio de `PolicyResolver` para resolucao de politica ativa em tempo de execucao.
- Implementar `PolicyEvaluator` e `PolicyEvaluatorRegistry` como preparacao para futuras categorias de politica.

## Capabilities

### New Capabilities
- `policies`: Criacao, listagem e gerenciamento de politicas de limite `VALUE_LIMIT`, incluindo resolucao de politica ativa por carteira.

### Modified Capabilities
- _(nenhuma spec existente e modificada)_

## Impact

- `adapters/web`: Novas rotas `PolicyRoutes` para endpoints de politica.
- `adapters/database-adapter`: Nova migracao V2 para constraint de unicidade; novo DAO para politicas.
- `core/entities`: Nova entidade `PolicyEntity`.
- `core/use-cases`: Novos casos de uso `CreatePolicyUseCase`, `ListPoliciesUseCase`, `AssignPolicyUseCase`.
- `boundary-context`: Novas interfaces `PolicyDAOSpec`, `PolicyResolverSpec`, `PolicyEvaluatorSpec`, `PolicyEvaluatorRegistrySpec`.
- `application`: Registro de novas rotas e dependencias.
