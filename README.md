# SRM Credit Engine

Plataforma de cessão de crédito multimoedas (BRL/USD) para precificação e liquidação de recebíveis.

**Stack:** Java 17 · Spring Boot 4.1 · Spring Data JPA · MySQL 8 · Flyway · Maven

---

## Estratégia de Branching

Este projeto adota um fluxo baseado em **duas branches :**

| Branch        | Papel                                                                 |
|---------------|-----------------------------------------------------------------------|
| `main`        | Branch estável e protegida. Representa o estado entregável do projeto. |
| `development` | Branch de trabalho. Todo desenvolvimento, correção e documentação acontece aqui. |

Ao concluir o projeto, é aberto um **Pull Request de `development` → `main`**.

```
development ──►(Pull Request)──► main
```

### Por que essa escolha

- **Isola o estável do trabalho em andamento.** A `main` nunca recebe código pela metade; ela só avança via Pull Request.

### Fluxo de desenvolvimento

1. Trabalhar sempre na `development` (ou branch derivada dela, se necessário).
2. Commits pequenos e atômicos seguindo **Conventional Commits**.
3. `push` para `origin/development`.
4. Ao finalizar, abrir **PR `development` → `main`**.
5. O merge é feito via Pull Request (a `main` não aceita push direto).

---

## Convenção de Commits (Conventional Commits)

Formato: `<tipo>: <descrição no imperativo>`

| Tipo       | Uso                                        | Exemplo                                  |
|------------|--------------------------------------------|------------------------------------------|
| `feat`     | Nova funcionalidade                        | `feat: add pricing engine`               |
| `fix`      | Correção de bug                            | `fix: correct exchange calculation`      |
| `docs`     | Documentação                               | `docs: update README`                    |
| `style`    | Formatação, sem mudança de lógica          | `style: format entity classes`           |
| `refactor` | Refatoração sem mudança de comportamento   | `refactor: extract spread strategy`      |
| `perf`     | Melhoria de performance                    | `perf: optimize settlement query`        |
| `test`     | Testes                                     | `test: add pricing tests`                |
| `build`    | Build, dependências                        | `build: add flyway dependency`           |
| `chore`    | Tarefas gerais / config                    | `chore: update gitignore`                |
| `revert`   | Reverter commit anterior                   | `revert: revert pricing engine change`   |

---

## Proteção da branch `main`

A `main` é protegida no GitHub com as seguintes regras:

- **Push direto bloqueado** — alterações só entram via Pull Request.
- **Pull Request obrigatório** antes de qualquer merge.
- **Histórico linear** exigido (sem merges poluídos).
- **Force-push e deleção desabilitados.**
- **Regras aplicadas também ao administrador** (sem bypass).
