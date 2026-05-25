## 1. Dominio e Contratos

- [x] 1.1 Criar entidade `PolicyEntity` em `core/entities` com `id`, `name`, `category`, `maxPerPayment`, `daytimeDailyLimit`, `nighttimeDailyLimit`, `weekendDailyLimit`, `createdAt`, `updatedAt`
- [x] 1.2 Criar interface `PolicyDAOSpec` em `boundary-context/database-boundary` com metodos `save`, `findAll`, `findByWalletId`, `findById`, `findActiveByWalletId`
- [x] 1.3 Criar interface `PolicyResolverSpec` em `boundary-context/input-boundary` com metodo `resolve(walletId): PolicyEntity`
- [x] 1.4 Criar interface `PolicyEvaluatorSpec` em `boundary-context/input-boundary` com metodo `evaluate(policy, context): EvaluationResult`
- [x] 1.5 Criar interface `PolicyEvaluatorRegistrySpec` em `boundary-context/input-boundary` com metodos `register(category, evaluator)` e `get(category): PolicyEvaluatorSpec?`
- [x] 1.6 Criar DTOs `CreatePolicyRequestDTO` e `PolicyResponseDTO` em `adapters/web/dtos`
- [x] 1.7 Criar DTOs `WalletPolicyResponseDTO` e `AssignPolicyRequestDTO` / `AssignPolicyResponseDTO`

## 2. Banco de Dados

- [x] 2.1 Criar migracao V2 com `CREATE UNIQUE INDEX` partial para `(wallet_id) WHERE active = TRUE` em `wallet_policies`
- [x] 2.2 Implementar `PolicyDAOSpecImpl` em `adapters/database-adapter/dao` com implementacoes dos metodos do DAO

## 3. Casos de Uso

- [x] 3.1 Implementar `CreatePolicyUseCaseImpl` em `core/use-cases` com validacao de campos obrigatorios e categoria `VALUE_LIMIT`
- [x] 3.2 Implementar `ListPoliciesUseCaseImpl` em `core/use-cases` retornando `data`/`meta`
- [x] 3.3 Implementar `ListWalletPoliciesUseCaseImpl` em `core/use-cases` que verifica existencia da carteira e retorna politicas com `active`
- [x] 3.4 Implementar `AssignPolicyUseCaseImpl` em `core/use-cases` que valida carteira e politica, desativa anterior e ativa nova
- [x] 3.5 Implementar `PolicyResolverImpl` em `core/use-cases` usando `PolicyDAOSpec.findActiveByWalletId`

## 4. API Web

- [x] 4.1 Criar `PolicyRoutes` com rota `POST /policies` para criacao de politica
- [x] 4.2 Criar rota `GET /policies` para listagem de politicas
- [x] 4.3 Criar rota `GET /wallets/{walletId}/policies` para consulta de politicas da carteira
- [x] 4.4 Criar rota `PUT /wallets/{walletId}/policy` para associacao de politica ativa
- [x] 4.5 Registrar `PolicyRoutes` no modulo `Application.kt`

## 5. PolicyEvaluatorRegistry

- [x] 5.1 Implementar `PolicyEvaluatorRegistryImpl` em `core/use-cases` com registro de evaluators por categoria
- [x] 5.2 Criar `ValueLimitEvaluator` implementando `PolicyEvaluatorSpec` (esboco vazio, implementacao real na Fatia 4)

## 6. Testes

- [x] 6.1 Teste de criacao valida de politica `VALUE_LIMIT`
- [x] 6.2 Teste de rejeicao de politica sem `name`
- [x] 6.3 Teste de rejeicao de categoria desconhecida
- [x] 6.4 Teste de rejeicao de limites invalidos (zero, negativo)
- [x] 6.5 Teste de listagem de politicas no formato `data`/`meta`
- [x] 6.6 Teste de associacao de politica a carteira
- [x] 6.7 Teste de desativacao de politica anterior ao associar nova politica ativa
- [x] 6.8 Teste de consulta de politicas da carteira
- [x] 6.9 Teste de resolucao de politica ativa
- [x] 6.10 Teste de `404` para carteira inexistente
- [x] 6.11 Teste de `404` para politica inexistente
- [x] 6.12 Teste de constraint de unicidade de politica ativa
