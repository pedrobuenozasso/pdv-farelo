# Backlog de fechamento — Farelo OS

Em 24/10/2026, os tickets verificáveis foram publicados em
[GitHub Issues](https://github.com/pedrobuenozasso/pdv-farelo/issues). O estado
de cada ticket deve ser acompanhado **no GitHub**: fechar somente depois de
validar os critérios de aceite e registrar commit/ensaio. Esta lista é um
índice para outra conversa, não substitui o estado ao vivo dos issues. Em
26/10, #5, #14 e #16 foram fechados com teste e deploy; #20 tem checagem
automática a cada cinco minutos, mas ainda sem canal de alertas ao responsável.
Em 29/10, 19 issues continuavam abertos. O proprietário adquiriu
`farelodebolo.com.br` no Registro.br e decidiu publicar cardápio e Admin/PDV
com proteção extra. O [#21](https://github.com/pedrobuenozasso/pdv-farelo/issues/21)
é o próximo trabalho de infraestrutura: integrar o Caddy já usado por outro
projeto nas portas 80/443, proteger o acesso de equipe, configurar DNS e
validar HTTPS sem derrubar o outro serviço. Ainda não há A no domínio.

## Operação primeiro

1. [CORE-01: acesso do caixa principal](https://github.com/pedrobuenozasso/pdv-farelo/issues/1) — exige redefinir a senha fora do chat.
2. [CORE-02: comanda completa](https://github.com/pedrobuenozasso/pdv-farelo/issues/2) — ensaio com a equipe.
3. [CORE-03: KDS](https://github.com/pedrobuenozasso/pdv-farelo/issues/3) — papel KITCHEN e ensaio.
4. [CORE-04: pagamento e fechamento](https://github.com/pedrobuenozasso/pdv-farelo/issues/4) — maquininha separada.
5. [CORE-05: impressão opt-in em todas as origens](https://github.com/pedrobuenozasso/pdv-farelo/issues/5) — ajuste técnico prioritário.
6. [CAT-01: cardápio e opções](https://github.com/pedrobuenozasso/pdv-farelo/issues/6) — conferência com a equipe.
7. [UI-01: usabilidade do PDV](https://github.com/pedrobuenozasso/pdv-farelo/issues/7) — triagem de problemas concretos no Windows.

## Dispositivo físico

8. [PRINT-01: Epson USB](https://github.com/pedrobuenozasso/pdv-farelo/issues/8).
9. [PRINT-02: layout e entrega única](https://github.com/pedrobuenozasso/pdv-farelo/issues/9) — depende do transporte USB.

## Fiscal

10. [FISC-01: emitente](https://github.com/pedrobuenozasso/pdv-farelo/issues/10) — Farelo/contabilidade.
11. [FISC-02: classificação dos itens](https://github.com/pedrobuenozasso/pdv-farelo/issues/11) — Farelo/contabilidade.
12. [FISC-03: variantes/adicionais](https://github.com/pedrobuenozasso/pdv-farelo/issues/12) — decisão fiscal + implementação.
13. [FISC-04: série e número](https://github.com/pedrobuenozasso/pdv-farelo/issues/13) — Farelo/contabilidade.
14. [FISC-05: XSD no fluxo](https://github.com/pedrobuenozasso/pdv-farelo/issues/14) — engenharia.
15. [FISC-06: custódia do A1](https://github.com/pedrobuenozasso/pdv-farelo/issues/15) — engenharia + inserção segura pelo proprietário.
16. [FISC-07: comando de documento pendente](https://github.com/pedrobuenozasso/pdv-farelo/issues/16) — concluído na API `d058180`; não transmite NFC-e.
17. [FISC-08: homologação real](https://github.com/pedrobuenozasso/pdv-farelo/issues/17) — depende dos anteriores.
18. [FISC-09: contingência e produção](https://github.com/pedrobuenozasso/pdv-farelo/issues/18) — depende da homologação.

## Continuidade e corte

19. [OPS-01: backup externo e restauração](https://github.com/pedrobuenozasso/pdv-farelo/issues/19) — depende de destino/credenciais fora do Git.
20. [OPS-02: monitoramento](https://github.com/pedrobuenozasso/pdv-farelo/issues/20) — timer instalado; alertas externos pendentes.
21. [OPS-03: domínio/HTTPS](https://github.com/pedrobuenozasso/pdv-farelo/issues/21) — domínio comprado; DNS, proxy compartilhado, proteção de equipe e ensaio ainda pendentes.
22. [OPS-04: piloto e corte do Sischef](https://github.com/pedrobuenozasso/pdv-farelo/issues/22) — último marco.

O relatório de evidência e critérios gerais está em
[`fechamento-operacional-20261024.md`](fechamento-operacional-20261024.md).
Segredos, XMLs e dumps não pertencem aos tickets.
