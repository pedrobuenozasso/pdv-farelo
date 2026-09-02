/**
 * Farelo Edge Agent — entrypoint mínimo.
 *
 * Este é apenas o esqueleto do serviço (FARELO-075). Nenhuma lógica real de
 * conexão com a API, polling de PrintJobs ou impressão existe ainda — isso é
 * escopo de tickets futuros (FARELO-076+). Este arquivo só prova que o
 * projeto Node.js/TypeScript inicializa corretamente.
 *
 * Lembrete de propósito (docs/PROMPT_MESTRE.md, seção 11): o Edge Agent é
 * apenas infraestrutura de dispositivos — nunca deve conter regra de
 * negócio de pedidos.
 */

function main(): void {
  console.log("Farelo Edge Agent iniciado");
}

main();
