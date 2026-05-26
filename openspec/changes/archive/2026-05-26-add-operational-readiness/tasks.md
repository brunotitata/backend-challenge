## 1. Documentação Técnica (README)

- [x] 1.1 Revisar e atualizar o README.md com visão geral da arquitetura
- [x] 1.2 Adicionar instruções de como rodar a aplicação localmente
- [x] 1.3 Adicionar instruções de como rodar testes (`./gradlew test`)
- [x] 1.4 Adicionar instruções de como subir dependências (Docker Compose)
- [x] 1.5 Documentar endpoints disponíveis com exemplos de request e response
- [x] 1.6 Documentar decisões de modelagem de domínio (Wallet, Payment, LimitPolicy, etc.)
- [x] 1.7 Documentar decisão de idempotência (header `Idempotency-Key`, constraint no banco)
- [x] 1.8 Documentar decisão de concorrência (transações, bloqueios pessimistas/UPSERT)
- [x] 1.9 Documentar decisão de timezone (UTC normalizado, classificação de período)
- [x] 1.10 Documentar decisão de precisão monetária (BigDecimal, centavos, NUMERIC(19,2))
- [x] 1.11 Justificar escolha do PostgreSQL e trade-off com MongoDB do desafio
- [x] 1.12 Justificar uso do jOOQ para queries tipadas e transações
- [x] 1.13 Documentar trade-offs e pontos de atenção
- [x] 1.14 Adicionar seção de como validar manualmente os principais fluxos

## 2. Docker Compose

- [x] 2.1 Criar/atualizar `docker-compose.yml` com serviço PostgreSQL
- [x] 2.2 Configurar variáveis de ambiente (POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD)
- [x] 2.3 Mapear volume para persistência de dados local
- [x] 2.4 (Opcional) Adicionar serviço da aplicação no Docker Compose
- [ ] 2.5 Testar `docker compose up` do zero em ambiente limpo

## 3. CI/CD

- [x] 3.1 Criar `.github/workflows/ci.yml` com trigger em push e pull request
- [x] 3.2 Configurar job de build com `./gradlew build`
- [x] 3.3 Configurar job de testes com `./gradlew test`
- [x] 3.4 Configurar service container PostgreSQL para testes de integração
- [ ] 3.5 Testar workflow em branch de teste

## 4. Observabilidade

- [x] 4.1 Implementar geração de request ID para cada requisição HTTP
- [x] 4.2 Configurar logs estruturados (JSON) incluindo request ID
- [x] 4.3 Implementar métricas simples: contador de pagamentos aprovados e recusados
- [ ] 4.4 (Opcional) Expor métricas em endpoint `/metrics`
- [x] 4.5 Implementar auditoria de eventos de pagamento (tabela ou log estruturado)

## 5. OpenAPI / Postman

- [x] 5.1 Adicionar plugin Ktor Swagger UI ou gerar `openapi.yaml`
- [x] 5.2 Documentar todos os endpoints obrigatórios com schemas de request/response
- [x] 5.3 (Alternativa) Criar Postman Collection e Environment exportáveis

## 6. Revisão Final e Submissão

- [x] 6.1 Executar `./gradlew test` e garantir 100% de sucesso
- [x] 6.2 Validar manualmente todos os endpoints obrigatórios contra o README do desafio
- [x] 6.3 Verificar se todas as migrações executam do zero em banco limpo
- [x] 6.4 Verificar se o projeto sobe localmente seguindo apenas o README
- [x] 6.5 Adicionar checklist de submissão no README (repositório privado, permissão para @tracefinancedev, e-mail para backend@trace.finance)
- [x] 6.6 Revisar commits para garantir Conventional Commits
- [x] 6.7 Verificar se todos os requisitos eliminatórios do desafio estão cobertos por testes
- [x] 6.8 Arquivar change OpenSpec após conclusão
