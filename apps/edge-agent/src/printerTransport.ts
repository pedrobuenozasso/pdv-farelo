/**
 * Transporte de rede para o ticket ESC/POS (FARELO-078): impressoras
 * térmicas de rede tipicamente escutam em TCP porta 9100 (protocolo
 * raw/JetDirect — os bytes ESC/POS são enviados crus, sem handshake de
 * aplicação nenhum). `printOverTcp` abre um socket, escreve o ticket
 * completo e resolve/rejeita conforme sucesso, erro ou timeout.
 *
 * Sem lib de impressão nova (mesma decisão de `escpos.ts`) — `node:net` já
 * cobre tudo que este ticket precisa (conectar, escrever, timeout).
 *
 * Nenhuma regra de negócio de pedido aqui: só transporte de bytes já
 * prontos — mesmo princípio do resto do Edge Agent (seção 11 do prompt
 * mestre).
 */

import net from "node:net";

/** Porta padrão de impressoras térmicas de rede (raw/JetDirect). */
export const DEFAULT_PRINTER_PORT = 9100;

const DEFAULT_TIMEOUT_MS = 5000;

export type PrintOverTcpOptions = {
  /**
   * Tempo máximo, em ms, tolerado tanto para conectar quanto para terminar
   * de escrever o ticket antes de rejeitar como timeout. Default 5000ms.
   */
  timeoutMs?: number;
};

/**
 * Abre uma conexão TCP para `host:port`, escreve `ticket` (bytes ESC/POS já
 * prontos — ver `buildEscPosTicket`) e resolve assim que os bytes forem
 * entregues ao socket local (não espera confirmação da impressora — o
 * protocolo raw/JetDirect não tem isso). Rejeita em caso de erro de
 * conexão/escrita (ex: impressora desligada → `ECONNREFUSED`, endereço
 * inválido → `ENOTFOUND`/`EHOSTUNREACH`) ou de timeout (impressora não
 * responde/drena os dados dentro de `options.timeoutMs`).
 *
 * O timeout usa `socket.setTimeout`, que dispara quando o socket fica
 * ocioso pelo período configurado — cobre tanto "nunca terminou de
 * conectar" quanto "conectou mas nunca terminou de receber os dados"
 * (impressora travada/sem papel segurando o buffer, por exemplo).
 *
 * Quem chama (`poller.ts`) nunca deixa uma rejeição daqui derrubar o
 * processo — vira `reportPrintJobFailed`, não uma exceção não tratada.
 */
export function printOverTcp(
  host: string,
  port: number,
  ticket: Buffer,
  options: PrintOverTcpOptions = {},
): Promise<void> {
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;

  return new Promise((resolve, reject) => {
    const socket = new net.Socket();
    let settled = false;

    // `settle` garante que a Promise só resolve/rejeita uma vez, mesmo que
    // mais de um evento dispare depois do primeiro (ex: "error" seguido de
    // "close") — e sempre limpa o socket.
    function settle(action: () => void): void {
      if (settled) {
        return;
      }
      settled = true;
      socket.destroy();
      action();
    }

    socket.setTimeout(timeoutMs);

    socket.once("timeout", () => {
      settle(() =>
        reject(
          new Error(
            `Timeout ao imprimir em ${host}:${port} (sem resposta em ${timeoutMs}ms)`,
          ),
        ),
      );
    });

    socket.once("error", (error) => {
      settle(() => reject(error));
    });

    socket.connect(port, host, () => {
      socket.write(ticket, (error) => {
        if (error) {
          settle(() => reject(error));
          return;
        }
        settle(() => resolve());
      });
    });
  });
}
