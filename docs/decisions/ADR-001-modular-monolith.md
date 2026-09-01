# ADR-001: Modular Monolith em vez de Microserviços

## Status

Aceito

## Contexto

O Farelo OS é inicialmente para uma única cafeteria, com escopo funcional amplo
(PDV, cardápio QR, comandas, impressão, estoque, cozinha, notificações e, futuramente,
fiscal). O sistema precisa ser suficientemente organizado para eventualmente suportar
múltiplas unidades, mas não é um SaaS multi-tenant hoje.

Uma arquitetura de microserviços introduziria complexidade operacional (deploy,
observabilidade, comunicação distribuída, consistência eventual) desproporcional ao
tamanho da operação e ao estágio do produto.

## Decisão

Construir o backend como um **Modular Monolith**: uma única aplicação Spring Boot
organizada em domínios internos com fronteiras claras (`auth`, `catalog`, `customer`,
`command`, `ordering`, `kitchen`, `printing`, `inventory`, `recipe`, `notification`,
`payment`, `fiscal`, `reporting`, `audit`), evitando dependências cruzadas
desnecessárias entre eles.

Comunicação entre domínios dentro do mesmo processo; eventos internos via
Transactional Outbox + Worker antes de introduzir qualquer broker externo (ver
ADR-003, quando registrado).

## Consequências

- Deploy único, mais simples de operar em uma VPS de porte modesto (2 vCPU / 8 GB RAM).
- Transações ACID reais entre domínios quando necessário (ex: criação de pedido +
  registro de evento outbox na mesma transação).
- Fronteiras de domínio precisam ser mantidas por disciplina de código (módulos/pacotes
  bem definidos), não por isolamento de processo.
- Uma futura extração para microserviços, se necessária, fica mais barata partindo de
  módulos já bem delimitados do que de um monolito não-modular.
