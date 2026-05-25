## 1. Persistencia e Dados Default

- [x] 1.1 Revisar migracoes existentes de carteiras e ajustar `wallets` para incluir colunas padrao necessarias, incluindo `updated_at` se ainda ausente.
- [x] 1.2 Criar ou ajustar migracao da tabela `policies` sem inserir dados de negocio no DDL.
- [x] 1.3 Criar migracao da tabela `wallet_policies` com `wallet_id`, `policy_id`, `active`, timestamps e chaves estrangeiras.
- [x] 1.4 Adicionar indices estruturais sem codificar regra de negocio de politica ativa unica no DDL.

## 2. Dominio e Casos de Uso

- [x] 2.1 Ajustar entidade/modelo de carteira para refletir os campos persistidos e o contrato da API.
- [x] 2.2 Criar porta de persistencia para salvar carteira e associar a politica default ativa em transacao.
- [x] 2.3 Implementar regra de validacao de `ownerName` no caso de uso de criacao de carteira.
- [x] 2.4 Implementar mecanismo interno minimo para resolver a politica ativa default de uma carteira recem-criada.

## 3. API HTTP

- [x] 3.1 Garantir que `POST /wallets` receba body JSON com `ownerName` e retorne `201 Created` no contrato esperado.
- [x] 3.2 Garantir que request sem body ou com body invalido retorne `400 Bad Request` na estrutura padrao de erro.
- [x] 3.3 Garantir que `ownerName` ausente, nulo, vazio ou em branco retorne `400 Bad Request` na estrutura padrao de erro.
- [x] 3.4 Garantir que `createdAt` seja serializado em ISO-8601 UTC na resposta.

## 4. Testes

- [x] 4.1 Adicionar ou ajustar testes de API para criacao de carteira valida e estrutura da resposta.
- [x] 4.2 Adicionar testes de validacao para `ownerName` ausente, nulo, vazio e apenas com espacos.
- [x] 4.3 Adicionar teste para request sem body ou JSON invalido.
- [x] 4.4 Adicionar teste de persistencia comprovando gravacao da carteira.
- [x] 4.5 Adicionar teste comprovando associacao da politica ativa `DEFAULT_VALUE_LIMIT` na criacao da carteira.
- [x] 4.6 Adicionar teste ou verificacao de migracao para tabelas e indices estruturais de `wallet_policies`.

## 5. Verificacao

- [x] 5.1 Executar testes automatizados relevantes com Gradle.
- [x] 5.2 Executar validacao OpenSpec da change `add-wallet-management`.
- [x] 5.3 Revisar contratos implementados contra a spec `wallets`.
