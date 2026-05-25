# Instrucoes para agentes

## Commits

Sempre realizar commits padronizados seguindo Conventional Commits.

Exemplos:

- `feat(wallet): adiciona endpoint de criacao de carteira`
- `fix(payment): corrige validacao de limite noturno`
- `refactor(policy): simplifica regra de categorizacao`
- `test(payment): adiciona testes de concorrencia`
- `docs(readme): atualiza instrucoes do docker`
- `chore(ci): adiciona workflow do github actions`

## Convencao de nomes para pacotes e pastas

Todos os nomes de pastas devem utilizar kebab-case.

Regra geral ao criar ou renomear diretorios:

- Separar palavras compostas com `-`
- Utilizar apenas letras minusculas
- Evitar abreviacoes desnecessarias
- Manter consistencia em toda a estrutura do projeto

Exemplos corretos:

- `boundary-context`
- `database-adapter`
- `external-service-adapter`
- `input-adapter`

Exemplos incorretos:

- `boundarycontext`
- `databaseadapter`
- `externalserviceadapter`
- `inputadapter`

Transformacoes esperadas:

| Antes | Depois |
| --- | --- |
| `boundarycontext` | `boundary-context` |
| `databaseadapter` | `database-adapter` |
| `externalserviceadapter` | `external-service-adapter` |
| `inputadapter` | `input-adapter` |

Excecao tecnica: packages Kotlin nao podem conter `-`. Nesses casos, manter o `package` com identificadores Kotlin validos e aplicar kebab-case no caminho fisico das pastas e na documentacao arquitetural.
