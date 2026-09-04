import Link from "next/link";

import { LogoBadge } from "@/components/logo-badge";

export default function Home() {
  return (
    <div className="bg-bg flex flex-1 flex-col items-center justify-center gap-4 text-center">
      <LogoBadge size={56} />
      <div>
        <h1 className="font-serif text-2xl font-semibold italic">Farelo OS</h1>
        <p className="text-ink-soft mt-1 text-sm">
          Sistema de operação da Farelo de Bolo — Quintal e Café
        </p>
      </div>
      <Link
        href="/login"
        className="bg-primary text-primary-ink rounded-full px-5 py-2.5 text-sm font-semibold"
      >
        Entrar
      </Link>
    </div>
  );
}
