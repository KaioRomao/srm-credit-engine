# Collection Postman — SRM Credit Engine

**50 requisições · 7 pastas · 3 environments · 482 linhas de script**

Todas as requisições foram executadas contra a aplicação real antes de serem publicadas aqui.

---

## Arquivos

| Arquivo | Conteúdo |
|---|---|
| `SRM-Credit-Engine.postman_collection.json` | Collection completa |
| `environments/SRM-Local.postman_environment.json` | `localhost:8080` · SLA 3000 ms |
| `environments/SRM-Homologacao.postman_environment.json` | Homologação · SLA 5000 ms |
| `environments/SRM-Producao.postman_environment.json` | Produção · SLA 2000 ms |

## Como usar

```bash
docker-compose up -d
```

Importe a collection e o environment **SRM - Local** no Postman, selecione o environment e rode a collection inteira pelo Collection Runner. **Não é preciso copiar id manualmente** — as requisições se encadeiam por variáveis de coleção.

---

## Sequência recomendada

| Ordem | Pasta | Reqs | Por que nessa ordem |
|---|---|---|---|
| 1 | `00 - Diagnostico` | 2 | Confirma que a aplicação subiu antes de qualquer teste |
| 2 | `01 - Cambio` | 4 | Sincroniza cotação — **pré-requisito** do cross-currency |
| 3 | `02 - Lote` | 3 | Cria recebíveis e precificações; publica `precificacaoId` |
| 4 | `03 - Precificacao` | 3 | Simulações independentes, sem efeito colateral |
| 5 | `04 - Liquidacao` | 8 | Consome `precificacaoId`; faz polling do desfecho e lista com filtros |
| 6 | `05 - Extrato` | 5 | Consulta o que foi liquidado nos passos anteriores |
| 7 | `06 - Cenarios Negativos` | 25 | Contrato de erro; roda isolado, tem setup próprio |

Rodar fora de ordem quebra o encadeamento: `04` sem `02` não tem `precificacaoId`; `02` cross-currency sem `01` retorna 422.

---

## Encadeamento por variáveis

| Variável | Definida em | Consumida em |
|---|---|---|
| `precificacaoId` | `02` — Criar lote | `04` — Solicitar liquidação |
| `precificacaoIdNegativo` | `06` — Setup | `06` — 422 moeda inexistente |
| `liquidacaoId` | `04` — Solicitar | `04` — Consultar desfecho |
| `trackId` / `trackIdUsado` | `04` — pre-request | `04` — Replay |
| `cotacaoId` | `01` — Sincronizar | `01` — Prova de idempotência |
| `vlLiquidoDuplicata` | `03` — Simular duplicata | `03` — Simular cheque |
| `dtVencimentoFutura` | pre-request de coleção | `02`, `03`, `06` |
| `dataCotacao` | pre-request de coleção | `01` |
| `dataInicioMes` / `dataFimMes` | pre-request de coleção | `05` |

### Scripts de coleção

**Pre-request** — roda antes de toda requisição, calculando datas dinâmicas para que a collection **não expire com o tempo**:

- `dtVencimentoFutura`: sempre 90 dias à frente. Data fixa acabaria virando passado e gerando 422 de prazo inválido.
- `dataCotacao`: último dia útil. A Frankfurter não publica cotação em fim de semana.
- `dataInicioMes` / `dataFimMes`: janela do mês corrente para os filtros do extrato.

**Test** — roda após toda requisição, somando-se aos testes locais:

- Tempo de resposta abaixo do `slaMs` do environment.
- Nenhum 5xx inesperado.
- `Content-Type: application/json` quando há corpo JSON.

---

## Endpoints documentados

### `00 - Diagnostico`

| Requisição | Método | Rota | Sucesso |
|---|---|---|---|
| Sinal de vida — OpenAPI | `GET` | `/v3/api-docs` | `200` |
| Swagger UI | `GET` | `/swagger-ui/index.html` | `200` |

> **Não há Spring Boot Actuator**, portanto não existem `/health`, `/readiness` nem `/liveness`. Enquanto isso não muda, `/v3/api-docs` é o sinal de vida mais confiável: se responde `200`, o contexto Spring subiu por completo — controllers, beans e datasource incluídos.

### `01 - Cambio`

| Requisição | Método | Rota | Sucesso | Erros |
|---|---|---|---|---|
| Sincronizar BRL→USD | `POST` | `/api/v1/cambios/sincronizar` | `201` | `400`, `422` |
| Sincronizar novamente | `POST` | idem | `201` | — |
| Consultar última cotação | `GET` | `/api/v1/cambios` | `200` | `400`, `422` |
| Consultar taxa inversa | `GET` | idem | `200` | `422` |

**Parâmetros de `sincronizar`:**

| Nome | Obrigatório | Formato | Observação |
|---|---|---|---|
| `data` | Sim | ISO `yyyy-MM-dd` | Fim de semana ou feriado faz a API devolver a última cotação disponível; o sistema grava com a **data real retornada**, não a solicitada |
| `sgMoedaCambioOrigem` | Sim | Sigla cadastrada | `BRL` ou `USD` |
| `sgMoedaCambioDestino` | Sim | Sigla cadastrada | — |

O endpoint é **idempotente por par + data** (upsert). A segunda requisição da pasta prova isso: compara o `id` retornado com o da primeira e exige que sejam iguais.

> Antes da correção registrada na ADR-009, sincronizar duas vezes criava linhas duplicadas, e a consulta subsequente lançava `NonUniqueResultException` — derrubando todo o fluxo cross-currency. Hoje há proteção em três camadas: `LIMIT 1` na query, upsert no service e `UNIQUE (origem, destino, dt_fechamento)` na migration `V3`.

**Taxa inversa:** o par `USD→BRL` nunca é sincronizado explicitamente. O sistema calcula `1/taxa` a partir de `BRL→USD`, com 10 casas antes do arredondamento final.

### `02 - Lote (intake de recebíveis)`

| Requisição | Método | Rota | Sucesso | Erros |
|---|---|---|---|---|
| Criar lote — 1 recebível BRL | `POST` | `/api/v1/lotes` | `201` | `400`, `422` |
| Duplicata e cheque no mesmo lote | `POST` | idem | `201` | — |
| Cross-currency BRL→USD | `POST` | idem | `201` | `422` sem cotação |

> **Não existe endpoint de recebível isolado.** Recebíveis nascem dentro de `POST /lotes`, numa única transação — decisão de domínio: recebível sem lote e sem precificação não tem significado.

**Campos:**

| Campo | Obrigatório | Observação |
|---|---|---|
| `dsReferencia` | Sim | Identificação comercial do lote |
| `cedenteDocumento` | Sim | CNPJ com **dígito verificador válido** — validado por `@CNPJ` |
| `cedenteNome` | Não | Usado apenas na **criação**. Cedente existente não tem o nome atualizado |
| `vlTaxaBase` | Sim | Custo de capital do momento, congelado em cada precificação |
| `recebiveis[]` | Sim, não vazio | Ver abaixo |
| `recebiveis[].vlFace` | Sim | Positivo |
| `recebiveis[].dtVencimento` | Sim | **Futura** |
| `recebiveis[].tipoRecebivel` | Sim | `DUPLICATA_MERCANTIL` ou `CHEQUE_PRE_DATADO` |
| `recebiveis[].sgMoeda` | Sim | `BRL` ou `USD` |
| `recebiveis[].sgMoedaPagamento` | Não | Nulo ou vazio = mesma moeda do título |

A segunda requisição da pasta envia **duplicata e cheque com valor de face e prazo idênticos**, e o teste exige que o cheque renda menos — prova da regra de risco (spread 2,5% contra 1,5%).

### `03 - Precificacao`

| Requisição | Método | Rota | Sucesso | Erros |
|---|---|---|---|---|
| Simular duplicata | `POST` | `/api/v1/precificacoes/simular` | `200` | `400`, `422` |
| Simular cheque | `POST` | idem | `200` | — |
| Simular cross-currency | `POST` | idem | `200` | `422` |

Fórmula: `VP = VF / (1 + TaxaBase + Spread) ^ (dias / 30)`.

**Simulação não persiste nada.** Não existe endpoint separado de "executar precificação" — a execução com persistência acontece dentro de `POST /lotes`.

### `04 - Liquidacao`

| Requisição | Método | Rota | Sucesso | Erros |
|---|---|---|---|---|
| Solicitar liquidação | `POST` | `/api/v1/liquidacoes` | `202` | `400`, `404`, `409`, `422` |
| Replay — mesmo TrackId | `POST` | idem | `202` | — |
| Consultar desfecho | `GET` | `/api/v1/liquidacoes/{id}` | `200` | `400`, `404` |
| Guard 1:1 | `POST` | `/api/v1/liquidacoes` | `409` | — |
| Listar — sem filtros | `GET` | `/api/v1/liquidacoes` | `200` | `400` |
| Listar — filtro por id | `GET` | idem | `200` | — |
| Listar — filtro por trackId | `GET` | idem | `200` | — |
| Listar — filtro por status | `GET` | idem | `200` | — |

**Header `TrackId`** (obrigatório): UUID de idempotência. O pre-request gera um novo a cada execução; para testar replay, o valor é preservado em `trackIdUsado`.

**O `202` não traz o resultado.** `vlLiquidado`, `vlCambioAplicado` e `dtLiquidacao` vêm **nulos** — são preenchidos pelo consumidor depois. A requisição "Consultar desfecho" implementa **polling automático**: se o status ainda é `PENDENTE` ou `PROCESSANDO`, reagenda a si mesma até 10 vezes usando `postman.setNextRequest`.

**Estados:** `PENDENTE` → `PROCESSANDO` → `LIQUIDADA` | `FALHA`. Em `FALHA`, o motivo vem em `dsObservacao`.

**Listagem.** Diferente do extrato, `GET /liquidacoes` traz liquidações de **qualquer status**. Filtros opcionais e combináveis: `id` (Long), `trackId` (UUID) e `status` (enum da máquina de estados) — valor fora do tipo → `400` apontando o campo. Paginação com `size` limitado a 100 e `sort` com whitelist própria (`dtCriacao`, `dtLiquidacao`, `id`, `status`, `trackId`, `vlLiquidado`; padrão `dtCriacao,desc`). Filtro por `id` inexistente devolve página **vazia** (`200`), não `404` — quem quer `404` usa `GET /liquidacoes/{id}`.

> **Não existem** os endpoints de cancelamento nem de histórico. O estado `CANCELADA` existe na máquina de estados e é respeitado pelo consumidor, mas **nenhum endpoint o produz**.

### `05 - Extrato`

| Requisição | Método | Rota | Sucesso | Erros |
|---|---|---|---|---|
| Sem filtros | `GET` | `/api/v1/liquidacoes/extrato` | `200` | — |
| Por período | `GET` | idem | `200` | `400` |
| Por cedente e moeda | `GET` | idem | `200` | — |
| Ordenado por valor | `GET` | idem | `200` | `400` |
| Size acima do teto | `GET` | idem | `200` | — |

**Retorna somente `LIQUIDADA`.** Pendente, falha e cancelada não entram — extrato é relatório de dinheiro efetivamente liquidado.

| Parâmetro | Formato | Observação |
|---|---|---|
| `dataInicio` / `dataFim` | ISO `yyyy-MM-dd` | Inclusivo nas duas pontas. Outro formato → `400` |
| `cedenteId` | Long | — |
| `sgMoeda` | Sigla | Normalizada para maiúscula |
| `page` | Int base 0 | — |
| `size` | Int | Limitado a **100**, reduzido silenciosamente |
| `sort` | `campo,direcao` | Whitelist de 6 campos; fora dela → `400` |

Campos permitidos em `sort`: `dtLiquidacao`, `vlLiquidado`, `vlFace`, `dtVencimento`, `cedenteNome`, `sgMoedaLiquidacao`.

> **Por que existe whitelist:** o `sort` é o único trecho **concatenado no SQL** do extrato. Aceitar valor livre seria SQL injection — a pasta `06` tem um cenário que tenta exatamente isso.

**Detalhe do período:** o limite superior é exclusivo no dia seguinte, internamente. `dt_liquidacao` é `DATETIME`, então um `<= dataFim` descartaria tudo que liquidou depois da meia-noite do último dia.

> **Não existe exportação** CSV/XLSX.

### `06 - Cenarios Negativos`

25 requisições que **devem falhar**, cada uma com status específico. Todas verificam o corpo padronizado `ErroRS` e a **ausência de stacktrace**.

| Status | Cenários |
|---|---|
| `400` | lista vazia · CNPJ com DV inválido · campo aninhado com path indexado · JSON malformado · TrackId não-UUID · TrackId ausente · data fora do ISO · período invertido · sort fora da whitelist · SQL injection no sort · trackId de filtro não-UUID · status de filtro inexistente · sort fora da whitelist na listagem · id não numérico |
| `404` | precificação inexistente · liquidação inexistente |
| `405` | método não suportado |
| `415` | content-type não suportado |
| `422` | tipo inexistente · rollback do lote · moeda inexistente · prazo inválido · cotação inexistente · moeda de liquidação inexistente |

**A pasta tem setup próprio.** A primeira requisição cria uma precificação **não liquidada**, usada pelo cenário "422 — moeda de liquidação inexistente". Sem ela, o teste reutilizaria a precificação da pasta `04` — já liquidada — e o guard 1:1 responderia `409` antes de chegar na validação de moeda.

---

## Corpo de erro padronizado

Todo erro devolve o mesmo formato:

```json
{
  "timestamp": "2026-07-26T14:20:35.123",
  "status": 400,
  "error": "Bad Request",
  "message": "Falha de validação na requisição",
  "path": "/api/v1/lotes",
  "erros": [
    { "campo": "recebiveis[0].vlFace", "mensagem": "vlFace deve ser positivo" }
  ]
}
```

O array `erros[]` só aparece em falhas de validação. **Stacktrace nunca aparece** — verificado por teste em cada cenário negativo.

### Semântica dos códigos

| Código | Significa | Exemplo |
|---|---|---|
| `400` | Falha de **forma** | Campo ausente, tipo errado, JSON malformado |
| `404` | Recurso **não existe** | Precificação ou liquidação inexistente |
| `405` | Verbo errado na rota | `GET` em rota de `POST` |
| `409` | Conflito com o **estado atual** | Precificação já liquidada |
| `415` | Content-type não suportado | `text/plain` |
| `422` | Payload válido violando **regra de negócio** | Moeda inexistente, prazo inválido |
| `500` | Falha inesperada | Mensagem **fixa**; detalhe apenas no log |

---

## Autenticação

**Não há.** A API está aberta — lacuna de escopo reconhecida e bloqueante para produção. Os environments já têm a variável `token` (tipo `secret`) reservada; quando a autenticação existir, bastará configurar Bearer Token no nível raiz da collection.

---

## Endpoints que não existem

A especificação original previa domínios que o sistema não implementa. Registrado para não gerar expectativa falsa:

| Pedido | Situação real |
|---|---|
| Health · Readiness · Liveness | Sem Actuator. `/v3/api-docs` é o substituto |
| Moedas — CRUD completo | Sem controller. Dados de referência via migration `V2` |
| Recebíveis — CRUD, paginação, filtros | Sem controller. Criados dentro de `POST /lotes` |
| Câmbio — atualizar taxa, histórico | `sincronizar` é upsert; não há listagem histórica |
| Precificação — executar, consultar | Execução acontece em `POST /lotes` |
| Liquidação — cancelar, histórico | `CANCELADA` existe na máquina de estados, sem endpoint |
| Extrato — exportar | Sem exportação CSV/XLSX |
| `PUT` · `DELETE` · `PATCH` | A API tem apenas `GET` e `POST` |

**Por que não há `PUT`/`DELETE`:** nada no domínio é atualizado ou removido. Liquidação é imutável por design (auditoria financeira), precificação carrega snapshot histórico, e cotação é upsert idempotente — não `PUT`.

---

## Cenários que a collection não cobre

Honestidade sobre os limites deste material:

| Cenário pedido | Por que não está aqui |
|---|---|
| RabbitMQ indisponível | Exige derrubar o broker no meio da execução. Coberto por teste automatizado (`LiquidacaoConsumerTest`), não por requisição HTTP |
| Banco indisponível | Idem — não é reproduzível por requisição |
| Liquidação concorrente | Precisa de threads simultâneas. Coberto por `ConcorrenciaLiquidacaoIT`, com 6 threads reais |
| Teste de carga | Ferramenta errada. Postman mede uma requisição por vez; carga se mede com k6, Gatling ou JMeter |

Ver [`../TESTES.md`](../TESTES.md) para a suíte automatizada — 208 testes, 97,9% de cobertura.

---

## Validação executada

Todas as 50 requisições foram executadas contra a aplicação real antes da publicação — última rodada completa via `newman`, com 293 assertions e zero falhas:

| Grupo | Resultado |
|---|---|
| Caminho feliz (`00`–`05`) | 25/25 com o status esperado |
| Cenários negativos (`06`) | 25/25 com o status esperado |
| Idempotência do câmbio | Mesmo `id` em duas sincronizações |
| Replay de liquidação | Mesmo `id`, sem duplicar |
| Filtros da listagem | `id`, `trackId` e `status` devolvendo só o item esperado |
| Teto de paginação | `size=5000` → `size=100` |
| Rollback do lote | Zero vestígio após falha no segundo item |

> Um defeito foi encontrado e corrigido durante a primeira validação: o cenário "422 — moeda de liquidação inexistente" retornava `409`, porque reutilizava a precificação já liquidada pela pasta `04`. A ordem dos guards em `iniciaLiquidacao` é: replay → precificação existe → já liquidada → valor líquido → moeda. Corrigido com o setup dedicado.

> Na rodada que validou a listagem, outro defeito foi encontrado e corrigido na própria collection: o corpo de "Criar lote — duplicata e cheque no mesmo lote" tinha uma chave `}` sobrando, virando JSON malformado e derrubando o `201` esperado para `400`.
