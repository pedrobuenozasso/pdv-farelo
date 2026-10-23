"use client";

import Link from "next/link";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { useSession } from "@/lib/use-session";

const ADMIN_ONLY_LINKS = new Set<string>([
  "/admin/users",
  "/admin/fiscal-profiles",
  "/admin/fiscal-review",
  "/admin/fiscal-company",
]);

const READY_AREAS = [
  {
    title: "Cadastros",
    description: "Produtos, grupos e cadastros disponíveis para seu perfil.",
    links: [
      { href: "/admin/products", label: "Produtos" },
      { href: "/admin/menu-preview", label: "Prévia do cardápio" },
      { href: "/admin/categories", label: "Grupos / categorias" },
      { href: "/admin/users", label: "Usuários e permissões" },
      { href: "/admin/fiscal-profiles", label: "Perfis fiscais" },
      { href: "/admin/fiscal-review", label: "Revisão fiscal" },
      { href: "/admin/fiscal-company", label: "Dados fiscais da empresa" },
    ],
  },
  {
    title: "Operação",
    description: "Venda, produção, estoque e documentos operacionais.",
    links: [
      { href: "/pdv", label: "Abrir PDV" },
      { href: "/pdv/caixa", label: "Caixa" },
      { href: "/kds", label: "Cozinha" },
      { href: "/admin/inventory", label: "Estoque" },
      { href: "/admin/commands/qrcodes", label: "Comandas e QR Codes" },
      { href: "/admin/print-jobs", label: "Impressão" },
      { href: "/admin/notifications", label: "Notificações" },
    ],
  },
] as const;

const PLANNED_AREAS = [
  {
    title: "Financeiro e caixa",
    detail:
      "A tela de caixa cobre a operação básica. Faltam configuração de turnos, contas, títulos, conciliação financeira e vínculo auditável de cada pagamento ao caixa.",
  },
  {
    title: "Fiscal e contábil",
    detail:
      "Cadastro fiscal por item, XML, NF-e/NFC-e, séries, eventos, DRE e DFC.",
  },
  {
    title: "Produtos com passos",
    detail:
      "Adicionais, escolhas obrigatórias, limites e preços por opção no cardápio e no PDV.",
  },
  {
    title: "Relatórios",
    detail:
      "Vendas por canal e pagamento, estoque, caixa e confronto com o legado.",
  },
] as const;

export default function AdminHomePage() {
  const session = useSession();

  return (
    <AuthGuard allow={["ADMIN", "MANAGER"]}>
      <AdminShell>
        <main className="mx-auto max-w-5xl space-y-8">
          <header className="space-y-2">
            <p className="text-ink-soft text-sm">Retaguarda</p>
            <h1 className="font-serif text-3xl font-semibold">Administração</h1>
            <p className="text-ink-soft max-w-2xl text-sm">
              Acesse as áreas já disponíveis do Farelo OS. As próximas áreas
              estão identificadas abaixo para acompanhar a migração do Sischef.
            </p>
          </header>

          <section aria-labelledby="ready-heading">
            <h2 id="ready-heading" className="mb-4 text-lg font-semibold">
              Áreas disponíveis
            </h2>
            <div className="grid gap-4 md:grid-cols-2">
              {READY_AREAS.map((area) => (
                <div
                  key={area.title}
                  className="border-line bg-surface rounded-2xl border p-5"
                >
                  <h3 className="font-serif text-xl font-semibold">
                    {area.title}
                  </h3>
                  <p className="text-ink-soft mt-1 text-sm">
                    {area.description}
                  </p>
                  <div className="mt-5 flex flex-wrap gap-2">
                    {area.links
                      .filter(
                        (link) =>
                          !ADMIN_ONLY_LINKS.has(link.href) ||
                          session?.role === "ADMIN",
                      )
                      .map((link) => (
                        <Link
                          key={link.href}
                          href={link.href}
                          className="border-line text-ink hover:bg-bg-alt rounded-lg border px-3 py-2 text-sm font-medium"
                        >
                          {link.label}
                        </Link>
                      ))}
                  </div>
                </div>
              ))}
            </div>
          </section>

          <section aria-labelledby="planned-heading">
            <div className="mb-4 flex flex-wrap items-baseline gap-3">
              <h2 id="planned-heading" className="text-lg font-semibold">
                Próximas áreas
              </h2>
              <span className="text-ink-soft text-sm">
                Mapeadas na migração; ainda sem tela operacional
              </span>
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              {PLANNED_AREAS.map((area) => (
                <div
                  key={area.title}
                  className="border-line rounded-xl border p-4"
                >
                  <h3 className="font-semibold">{area.title}</h3>
                  <p className="text-ink-soft mt-1 text-sm">{area.detail}</p>
                </div>
              ))}
            </div>
          </section>
        </main>
      </AdminShell>
    </AuthGuard>
  );
}
