"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { LogoBadge } from "@/components/logo-badge";
import { listCategories } from "@/lib/api/categories";
import { listProducts } from "@/lib/api/products";

const currencyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

export default function MenuPreviewPage() {
  const [publishedOnly, setPublishedOnly] = useState(false);
  const categories = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });
  const products = useQuery({
    queryKey: ["products"],
    queryFn: listProducts,
  });

  const sections = (categories.data ?? [])
    .filter((category) => category.active)
    .map((category) => ({
      category,
      products: (products.data ?? []).filter(
        (product) =>
          product.active &&
          product.categoryId === category.id &&
          (!publishedOnly || product.availableOnMenu),
      ),
    }))
    .filter((section) => section.products.length > 0);
  const shownCount = sections.reduce(
    (count, section) => count + section.products.length,
    0,
  );
  const pausedCount = (products.data ?? []).filter(
    (product) => product.active && !product.availableOnMenu,
  ).length;

  return (
    <AuthGuard allow={["ADMIN", "MANAGER"]}>
      <AdminShell>
        <main className="mx-auto max-w-5xl space-y-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-ink-soft text-sm">Prévia interna</p>
              <h1 className="font-serif text-3xl font-semibold">Cardápio</h1>
              <p className="text-ink-soft mt-2 max-w-2xl text-sm">
                Veja a apresentação dos produtos antes de liberá-los. Esta
                página não recebe pedidos nem altera a publicação.
              </p>
            </div>
            <Link
              href="/admin/cardapio"
              className="border-line bg-surface hover:bg-bg-alt rounded-lg border px-4 py-2 text-sm font-semibold"
            >
              Editar cardápio
            </Link>
          </div>

          <div className="border-line bg-surface flex flex-wrap items-center gap-3 rounded-xl border p-3">
            <button
              type="button"
              onClick={() => setPublishedOnly(false)}
              aria-pressed={!publishedOnly}
              className={`rounded-lg px-3 py-2 text-sm font-semibold ${!publishedOnly ? "bg-primary text-primary-ink" : "hover:bg-bg-alt"}`}
            >
              Prévia completa
            </button>
            <button
              type="button"
              onClick={() => setPublishedOnly(true)}
              aria-pressed={publishedOnly}
              className={`rounded-lg px-3 py-2 text-sm font-semibold ${publishedOnly ? "bg-primary text-primary-ink" : "hover:bg-bg-alt"}`}
            >
              Visível aos clientes
            </button>
            <span className="text-ink-soft ml-auto text-sm">
              {shownCount} exibidos · {pausedCount} pausados
            </span>
          </div>

          <div className="border-line bg-bg mx-auto w-full max-w-md overflow-hidden rounded-[30px] border shadow-xl">
            <div className="bg-primary text-primary-ink rounded-b-[28px] px-5 pt-6 pb-8">
              <div className="flex items-center gap-3">
                <LogoBadge size={52} />
                <div>
                  <div className="font-serif text-xl font-semibold italic">
                    Farelo de Bolo
                  </div>
                  <div className="text-primary-ink/85 mt-0.5 text-[11px] tracking-wide uppercase">
                    Quintal e Café
                  </div>
                </div>
              </div>
              <p className="text-primary-ink/90 mt-3 text-sm">
                Preparo em 30–60 minutos
              </p>
            </div>
            <div className="border-line bg-surface relative mx-5 -mt-5 rounded-2xl border px-4 py-3.5 shadow-sm">
              <p className="text-ink-faint text-[11px] tracking-wide uppercase">
                Sua comanda
              </p>
              <p className="font-serif text-[17px] font-semibold">
                Prévia do cardápio
              </p>
            </div>
            <div className="space-y-7 px-5 py-6">
              {categories.isLoading || products.isLoading ? (
                <p className="text-ink-soft text-center text-sm">
                  Carregando cardápio...
                </p>
              ) : categories.isError || products.isError ? (
                <p className="text-red text-center text-sm">
                  Não foi possível carregar os produtos.
                </p>
              ) : sections.length === 0 ? (
                <p className="text-ink-soft text-center text-sm">
                  O cardápio ainda não está disponível. Peça ajuda no balcão.
                </p>
              ) : (
                sections.map(({ category, products: items }) => (
                  <section key={category.id} className="space-y-3.5">
                    <h2 className="text-ink-soft font-serif text-[15px] font-semibold italic">
                      {category.name}
                    </h2>
                    <ul className="space-y-3">
                      {items.map((product) => (
                        <li
                          key={product.id}
                          className="border-line bg-surface flex gap-3 rounded-2xl border p-3.5"
                        >
                          {product.imageUrl ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img
                              src={product.imageUrl}
                              alt={product.name}
                              className="h-16 w-16 shrink-0 rounded-xl object-cover"
                            />
                          ) : null}
                          <div className="min-w-0 flex-1 space-y-1">
                            <p className="font-serif text-[15px] font-semibold">
                              {product.name}
                            </p>
                            {product.description ? (
                              <p className="text-ink-soft text-[13px]">
                                {product.description}
                              </p>
                            ) : null}
                            <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
                              <span className="text-sm font-bold">
                                {currencyFormatter.format(product.price)}
                              </span>
                              {!product.availableOnMenu ? (
                                <span className="bg-amber-soft text-amber-ink rounded-full px-2 py-1 text-[11px] font-semibold">
                                  Pausado
                                </span>
                              ) : null}
                            </div>
                          </div>
                        </li>
                      ))}
                    </ul>
                  </section>
                ))
              )}
            </div>
          </div>
          <p className="text-ink-soft text-center text-xs">
            Prévia visual sem compra. O cliente só vê produtos ativos com
            “Cardápio” ligado na comanda QR.
          </p>
        </main>
      </AdminShell>
    </AuthGuard>
  );
}
