import { spawn } from "node:child_process";

const DEFAULT_TIMEOUT_MS = 30_000;

// Winspool recebe os bytes ESC/POS como RAW. O nome da fila vem do ambiente,
// nunca é interpolado no código PowerShell (ou numa linha de shell).
const RAW_SPOOL_SCRIPT = String.raw`
$ErrorActionPreference = 'Stop'
try {
  Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
public static class FareloRawPrinter {
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct DocInfo {
    [MarshalAs(UnmanagedType.LPWStr)] public string Name;
    [MarshalAs(UnmanagedType.LPWStr)] public string Output;
    [MarshalAs(UnmanagedType.LPWStr)] public string DataType;
  }
  [DllImport("winspool.drv", EntryPoint="OpenPrinterW", CharSet=CharSet.Unicode, SetLastError=true)]
  static extern bool OpenPrinter(string name, out IntPtr handle, IntPtr defaults);
  [DllImport("winspool.drv", EntryPoint="ClosePrinter", SetLastError=true)]
  static extern bool ClosePrinter(IntPtr handle);
  [DllImport("winspool.drv", EntryPoint="StartDocPrinterW", CharSet=CharSet.Unicode, SetLastError=true)]
  static extern int StartDocPrinter(IntPtr handle, int level, ref DocInfo info);
  [DllImport("winspool.drv", EntryPoint="EndDocPrinter", SetLastError=true)]
  static extern bool EndDocPrinter(IntPtr handle);
  [DllImport("winspool.drv", EntryPoint="WritePrinter", SetLastError=true)]
  static extern bool WritePrinter(IntPtr handle, byte[] bytes, int count, out int written);
  public static void Print(string name, byte[] bytes) {
    IntPtr handle;
    if (!OpenPrinter(name, out handle, IntPtr.Zero)) throw new Win32Exception(Marshal.GetLastWin32Error());
    try {
      var info = new DocInfo { Name = "Farelo OS", Output = null, DataType = "RAW" };
      if (StartDocPrinter(handle, 1, ref info) == 0) throw new Win32Exception(Marshal.GetLastWin32Error());
      try {
        int written;
        if (!WritePrinter(handle, bytes, bytes.Length, out written)) throw new Win32Exception(Marshal.GetLastWin32Error());
        if (written != bytes.Length) throw new Exception("Spooler aceitou apenas parte do ticket.");
      } finally {
        if (!EndDocPrinter(handle)) throw new Win32Exception(Marshal.GetLastWin32Error());
      }
    } finally { ClosePrinter(handle); }
  }
}
'@
  $stream = [Console]::OpenStandardInput()
  $buffer = New-Object System.IO.MemoryStream
  $stream.CopyTo($buffer)
  if ($buffer.Length -eq 0) { throw 'Ticket vazio.' }
  [FareloRawPrinter]::Print($env:FARELO_WINDOWS_PRINTER_NAME, $buffer.ToArray())
} catch {
  [Console]::Error.WriteLine($_.Exception.Message)
  exit 1
}
`;

/** Envia bytes sem conversão de texto e só confirma após o processo terminar. */
export function sendBinaryToProcess(
  command: string,
  args: string[],
  env: NodeJS.ProcessEnv,
  ticket: Buffer,
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      env,
      stdio: ["pipe", "ignore", "pipe"],
      windowsHide: true,
    });
    let settled = false;
    let stderr = "";
    const finish = (error?: Error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      if (error) reject(error);
      else resolve();
    };
    const timer = setTimeout(() => {
      child.kill();
      finish(
        new Error(
          `Timeout do spooler após ${timeoutMs}ms; estado da impressão incerto.`,
        ),
      );
    }, timeoutMs);
    child.stderr.on("data", (chunk: Buffer) => {
      stderr = (stderr + chunk.toString()).slice(-4096);
    });
    child.once("error", (error) => finish(error));
    child.once("close", (code) => {
      finish(
        code === 0
          ? undefined
          : new Error(`Spooler falhou (${code}): ${stderr.trim()}`),
      );
    });
    child.stdin.on("error", (error) => finish(error));
    child.stdin.end(ticket);
  });
}

/** Spoola um ticket ESC/POS RAW na fila instalada localmente no Windows. */
export function printOverWindowsSpooler(
  printerName: string,
  ticket: Buffer,
): Promise<void> {
  if (process.platform !== "win32") {
    return Promise.reject(new Error("Impressão via spooler exige Windows."));
  }
  if (!printerName.trim() || ticket.length === 0) {
    return Promise.reject(
      new Error("Nome da impressora e ticket são obrigatórios."),
    );
  }
  const encoded = Buffer.from(RAW_SPOOL_SCRIPT, "utf16le").toString("base64");
  return sendBinaryToProcess(
    "powershell.exe",
    ["-NoProfile", "-NonInteractive", "-EncodedCommand", encoded],
    { ...process.env, FARELO_WINDOWS_PRINTER_NAME: printerName },
    ticket,
  );
}
