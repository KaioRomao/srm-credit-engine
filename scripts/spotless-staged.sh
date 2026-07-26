#!/usr/bin/env bash
#
# Roda spotless:apply APENAS nos arquivos recebidos como argumento.
#
# Por que nao chamar `mvn spotless:apply` direto no hook: o Spotless formataria o
# projeto inteiro, incluindo arquivos que o pre-commit guardou no stash por estarem
# fora do staging. Na hora de restaurar o stash daria conflito, o pre-commit
# reverteria as correcoes e o commit falharia — em qualquer commit feito com
# trabalho nao-staged na arvore, o que e o caso comum do dia a dia.
#
# O -DspotlessFiles recebe uma lista de regex casada contra o caminho absoluto,
# por isso cada caminho e prefixado com `.*` e tem os pontos escapados.

set -euo pipefail

if [ "$#" -eq 0 ]; then
  exit 0
fi

regex=""
for arquivo in "$@"; do
  escapado="${arquivo//./[.]}"
  if [ -z "$regex" ]; then
    regex=".*${escapado}"
  else
    regex="${regex},.*${escapado}"
  fi
done

exec ./mvnw -B -q spotless:apply "-DspotlessFiles=${regex}"
