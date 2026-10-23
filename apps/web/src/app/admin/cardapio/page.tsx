"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { type FormEvent, useState } from "react";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import {
  createCategory,
  listCategories,
  type Category,
} from "@/lib/api/categories";
import {
  createProduct,
  listProducts,
  updateProduct,
  type Product,
  type UpdateProductInput,
} from "@/lib/api/products";

const currency = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});
const fieldClass =
  "border-line bg-surface focus:border-primary w-full rounded-lg border px-3 py-2 text-sm outline-none";

type ProductDraft = {
  name: string;
  description: string;
  price: string;
  imageUrl: string;
  categoryId: string;
  availableOnMenu: boolean;
  availableOnPos: boolean;
};

function draftFor(categoryId: string, product?: Product): ProductDraft {
  return {
    name: product?.name ?? "",
    description: product?.description ?? "",
    price: product ? String(product.price) : "",
    imageUrl: product?.imageUrl ?? "",
    categoryId: product?.categoryId ?? categoryId,
    availableOnMenu: product?.availableOnMenu ?? false,
    availableOnPos: product?.availableOnPos ?? true,
  };
}

function fullUpdate(product: Product, changes: Partial<UpdateProductInput>) {
  return updateProduct(product.id, {
    name: product.name,
    description: product.description ?? undefined,
    price: product.price,
    categoryId: product.categoryId,
    imageUrl: product.imageUrl ?? undefined,
    active: product.active,
    availableOnMenu: product.availableOnMenu,
    availableOnPos: product.availableOnPos,
    productionStation: product.productionStation ?? undefined,
    fiscalProfileId: product.fiscalProfileId,
    ...changes,
  });
}

function errorMessage(error: unknown) {
  return error instanceof Error
    ? error.message
    : "Não foi possível salvar. Tente novamente.";
}

export default function CardapioAdminPage() {
  const queryClient = useQueryClient();
  const [openId, setOpenId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [creatingIn, setCreatingIn] = useState<string | null>(null);
  const [draft, setDraft] = useState<ProductDraft | null>(null);
  const [search, setSearch] = useState("");
  const [newSectionOpen, setNewSectionOpen] = useState(false);
  const [newSectionName, setNewSectionName] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const categoriesQuery = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });
  const productsQuery = useQuery({
    queryKey: ["products"],
    queryFn: listProducts,
  });
  const categories = categoriesQuery.data ?? [];
  const products = productsQuery.data ?? [];

  const productMutation = useMutation({
    mutationFn: async ({
      product,
      values,
    }: {
      product?: Product;
      values: ProductDraft;
    }) => {
      const price = Number(values.price.trim().replace(",", "."));
      if (!values.name.trim()) throw new Error("Informe o nome do produto.");
      if (!values.price.trim() || !Number.isFinite(price) || price < 0) {
        throw new Error("Informe um preço válido.");
      }
      if (values.imageUrl.trim() && !/^https?:\/\//.test(values.imageUrl)) {
        throw new Error("A imagem precisa ter uma URL http ou https.");
      }
      const input = {
        name: values.name.trim(),
        description: values.description.trim() || undefined,
        price,
        categoryId: values.categoryId,
        imageUrl: values.imageUrl.trim() || undefined,
        availableOnMenu: values.availableOnMenu,
        availableOnPos: values.availableOnPos,
      };
      return product ? fullUpdate(product, input) : createProduct(input);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products"] });
      setEditingId(null);
      setCreatingIn(null);
      setDraft(null);
      setFormError(null);
    },
  });

  const visibilityMutation = useMutation({
    mutationFn: (product: Product) =>
      fullUpdate(product, { availableOnMenu: !product.availableOnMenu }),
    onSuccess: (saved) => {
      queryClient.setQueryData<Product[]>(["products"], (current) =>
        current?.map((item) => (item.id === saved.id ? saved : item)),
      );
      queryClient.invalidateQueries({ queryKey: ["products"] });
    },
  });

  const sectionMutation = useMutation({
    mutationFn: (name: string) =>
      createCategory({
        name: name.trim(),
        sortOrder: Math.max(0, ...categories.map((item) => item.sortOrder)) + 1,
      }),
    onSuccess: (category) => {
      queryClient.invalidateQueries({ queryKey: ["categories"] });
      setNewSectionOpen(false);
      setNewSectionName("");
      setOpenId(category.id);
    },
  });

  const normalizedSearch = search.trim().toLocaleLowerCase("pt-BR");
  const sections = categories
    .map((category) => ({
      category,
      items: products.filter((product) => product.categoryId === category.id),
    }))
    .filter(
      ({ category, items }) =>
        !normalizedSearch ||
        category.name.toLocaleLowerCase("pt-BR").includes(normalizedSearch) ||
        items.some((item) =>
          item.name.toLocaleLowerCase("pt-BR").includes(normalizedSearch),
        ),
    );
  const publishedCount = products.filter(
    (item) =>
      item.active &&
      item.availableOnMenu &&
      categories.some(
        (category) => category.id === item.categoryId && category.active,
      ),
  ).length;

  function openEditor(categoryId: string, product?: Product) {
    setOpenId(categoryId);
    setEditingId(product?.id ?? null);
    setCreatingIn(product ? null : categoryId);
    setDraft(draftFor(categoryId, product));
    setFormError(null);
    productMutation.reset();
  }

  function submitProduct(event: FormEvent<HTMLFormElement>, product?: Product) {
    event.preventDefault();
    if (!draft) return;
    setFormError(null);
    productMutation.mutate({ product, values: draft });
  }

  return (
    <AuthGuard allow={["ADMIN", "MANAGER"]}>
      <AdminShell>
        <main className="mx-auto max-w-5xl space-y-6">
          <header className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="text-primary text-xs font-bold tracking-[0.16em] uppercase">
                Gestão do catálogo
              </p>
              <h1 className="font-serif text-3xl font-semibold">Cardápio</h1>
              <p className="text-ink-soft mt-1 text-sm">
                {categories.length} seções · {products.length} produtos ·{" "}
                {publishedCount} visíveis aos clientes
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Link
                href="/admin/menu-preview"
                className="border-line bg-surface hover:bg-bg-alt rounded-lg border px-4 py-2 text-sm font-semibold"
              >
                Ver como cliente
              </Link>
              <button
                type="button"
                onClick={() => setNewSectionOpen((open) => !open)}
                className="bg-primary text-primary-ink hover:bg-primary-dark rounded-lg px-4 py-2 text-sm font-semibold"
              >
                Nova seção
              </button>
            </div>
          </header>

          {newSectionOpen ? (
            <form
              onSubmit={(event) => {
                event.preventDefault();
                if (newSectionName.trim())
                  sectionMutation.mutate(newSectionName);
              }}
              className="border-line bg-surface flex flex-wrap items-end gap-3 rounded-xl border p-4"
            >
              <label className="min-w-52 flex-1 text-sm font-semibold">
                Nome da seção
                <input
                  value={newSectionName}
                  onChange={(event) => setNewSectionName(event.target.value)}
                  className={`${fieldClass} mt-1`}
                  required
                  autoFocus
                />
              </label>
              <button
                type="submit"
                disabled={sectionMutation.isPending}
                className="bg-primary text-primary-ink rounded-lg px-4 py-2 text-sm font-semibold disabled:opacity-50"
              >
                {sectionMutation.isPending ? "Salvando..." : "Criar seção"}
              </button>
              {sectionMutation.isError ? (
                <p role="alert" className="text-red w-full text-sm">
                  {errorMessage(sectionMutation.error)}
                </p>
              ) : null}
            </form>
          ) : null}

          <div className="border-line bg-surface rounded-xl border p-4">
            <label
              htmlFor="menu-search"
              className="text-ink-soft text-xs font-semibold uppercase"
            >
              Buscar seção ou produto
            </label>
            <input
              id="menu-search"
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Ex.: café, bebidas, bolo..."
              className={`${fieldClass} mt-2`}
            />
          </div>

          {categoriesQuery.isLoading || productsQuery.isLoading ? (
            <p className="text-ink-soft text-sm">Carregando cardápio...</p>
          ) : categoriesQuery.isError || productsQuery.isError ? (
            <p role="alert" className="text-red text-sm">
              Não foi possível carregar as seções e os produtos.
            </p>
          ) : sections.length === 0 ? (
            <p className="text-ink-soft text-sm">Nenhuma seção encontrada.</p>
          ) : (
            <div className="space-y-3">
              {sections.map(({ category, items }) => {
                const expanded =
                  openId === category.id || Boolean(normalizedSearch);
                const visibleItems =
                  normalizedSearch &&
                  !category.name
                    .toLocaleLowerCase("pt-BR")
                    .includes(normalizedSearch)
                    ? items.filter((item) =>
                        item.name
                          .toLocaleLowerCase("pt-BR")
                          .includes(normalizedSearch),
                      )
                    : items;
                const sectionPublished = items.filter(
                  (item) => item.active && item.availableOnMenu,
                ).length;
                return (
                  <section
                    key={category.id}
                    className="border-line bg-surface overflow-hidden rounded-2xl border"
                  >
                    <button
                      type="button"
                      onClick={() => setOpenId(expanded ? null : category.id)}
                      aria-expanded={expanded}
                      className="hover:bg-bg-alt flex w-full flex-wrap items-center justify-between gap-2 px-5 py-4 text-left"
                    >
                      <span className="min-w-0 font-serif text-lg font-semibold">
                        {category.name}
                      </span>
                      <span className="text-ink-soft flex items-center gap-3 text-xs">
                        {items.length} {items.length === 1 ? "item" : "itens"} ·{" "}
                        {sectionPublished}{" "}
                        {sectionPublished === 1 ? "publicado" : "publicados"}
                        <span aria-hidden="true">{expanded ? "−" : "+"}</span>
                      </span>
                    </button>
                    {expanded ? (
                      <div className="border-line border-t">
                        {category.description ? (
                          <p className="text-ink-soft px-5 pt-4 text-sm">
                            {category.description}
                          </p>
                        ) : null}
                        {!category.active ? (
                          <p className="text-amber-ink bg-amber-soft mx-5 mt-4 rounded-lg px-3 py-2 text-xs">
                            Esta seção está inativa; seus itens não aparecem aos
                            clientes.
                          </p>
                        ) : null}
                        <ul className="divide-line divide-y">
                          {visibleItems.map((product) => (
                            <li key={product.id} className="px-5 py-4">
                              <div className="flex flex-wrap items-center gap-3">
                                <div className="min-w-44 flex-1">
                                  <p className="text-sm font-semibold">
                                    {product.name}
                                  </p>
                                  {product.description ? (
                                    <p className="text-ink-soft mt-0.5 line-clamp-2 text-xs">
                                      {product.description}
                                    </p>
                                  ) : null}
                                </div>
                                <span className="w-20 text-right text-sm font-semibold">
                                  {currency.format(product.price)}
                                </span>
                                <button
                                  type="button"
                                  role="switch"
                                  aria-checked={product.availableOnMenu}
                                  aria-label={`${product.availableOnMenu ? "Pausar" : "Publicar"} ${product.name} no cardápio`}
                                  disabled={visibilityMutation.isPending}
                                  onClick={() =>
                                    visibilityMutation.mutate(product)
                                  }
                                  className={`focus-visible:outline-primary min-w-20 rounded-full px-3 py-1.5 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50 ${product.availableOnMenu ? "bg-green-soft text-green-ink" : "bg-bg-alt text-ink-soft"}`}
                                >
                                  {product.availableOnMenu
                                    ? "Publicado"
                                    : "Pausado"}
                                </button>
                                <button
                                  type="button"
                                  onClick={() =>
                                    openEditor(category.id, product)
                                  }
                                  className="text-primary rounded-lg px-2 py-1.5 text-xs font-semibold hover:underline"
                                >
                                  Editar
                                </button>
                                <Link
                                  href={`/admin/products/${product.id}/steps`}
                                  className="text-primary rounded-lg px-2 py-1.5 text-xs font-semibold hover:underline"
                                >
                                  Opções
                                </Link>
                              </div>
                              {editingId === product.id && draft ? (
                                <ProductForm
                                  draft={draft}
                                  setDraft={setDraft}
                                  categories={categories}
                                  pending={productMutation.isPending}
                                  error={
                                    formError ??
                                    (productMutation.isError
                                      ? errorMessage(productMutation.error)
                                      : null)
                                  }
                                  onSubmit={(event) =>
                                    submitProduct(event, product)
                                  }
                                  onCancel={() => {
                                    setEditingId(null);
                                    setDraft(null);
                                  }}
                                />
                              ) : null}
                            </li>
                          ))}
                        </ul>
                        {visibilityMutation.isError ? (
                          <p
                            role="alert"
                            className="text-red px-5 py-2 text-xs"
                          >
                            {errorMessage(visibilityMutation.error)}
                          </p>
                        ) : null}
                        {creatingIn === category.id && draft ? (
                          <div className="border-line border-t px-5 py-4">
                            <ProductForm
                              draft={draft}
                              setDraft={setDraft}
                              categories={categories}
                              pending={productMutation.isPending}
                              error={
                                formError ??
                                (productMutation.isError
                                  ? errorMessage(productMutation.error)
                                  : null)
                              }
                              onSubmit={submitProduct}
                              onCancel={() => {
                                setCreatingIn(null);
                                setDraft(null);
                              }}
                            />
                          </div>
                        ) : (
                          <button
                            type="button"
                            onClick={() => openEditor(category.id)}
                            className="text-primary border-line hover:bg-primary-soft w-full border-t px-5 py-3 text-left text-sm font-semibold"
                          >
                            + Adicionar produto nesta seção
                          </button>
                        )}
                      </div>
                    ) : null}
                  </section>
                );
              })}
            </div>
          )}
          <p className="text-ink-faint text-xs">
            Dados fiscais e configurações avançadas continuam em{" "}
            <Link
              href="/admin/products"
              className="text-primary font-semibold underline"
            >
              Produtos
            </Link>
            .
          </p>
        </main>
      </AdminShell>
    </AuthGuard>
  );
}

function ProductForm({
  draft,
  setDraft,
  categories,
  pending,
  error,
  onSubmit,
  onCancel,
}: {
  draft: ProductDraft;
  setDraft: (draft: ProductDraft) => void;
  categories: Category[];
  pending: boolean;
  error: string | null;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
}) {
  return (
    <form
      onSubmit={onSubmit}
      className="bg-bg-alt mt-4 grid gap-3 rounded-xl p-4 sm:grid-cols-2"
    >
      <label className="text-xs font-semibold">
        Nome
        <input
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          className={`${fieldClass} mt-1`}
          required
        />
      </label>
      <label className="text-xs font-semibold">
        Preço
        <input
          value={draft.price}
          onChange={(event) =>
            setDraft({ ...draft, price: event.target.value })
          }
          inputMode="decimal"
          className={`${fieldClass} mt-1`}
          required
        />
      </label>
      <label className="text-xs font-semibold">
        Seção
        <select
          value={draft.categoryId}
          onChange={(event) =>
            setDraft({ ...draft, categoryId: event.target.value })
          }
          className={`${fieldClass} mt-1`}
        >
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
      </label>
      <label className="text-xs font-semibold">
        URL da imagem · opcional
        <input
          value={draft.imageUrl}
          onChange={(event) =>
            setDraft({ ...draft, imageUrl: event.target.value })
          }
          className={`${fieldClass} mt-1`}
          placeholder="https://..."
        />
      </label>
      <label className="text-xs font-semibold sm:col-span-2">
        Descrição
        <textarea
          value={draft.description}
          onChange={(event) =>
            setDraft({ ...draft, description: event.target.value })
          }
          className={`${fieldClass} mt-1 min-h-20 resize-y`}
        />
      </label>
      <label className="flex items-center gap-2 text-xs font-semibold">
        <input
          type="checkbox"
          checked={draft.availableOnMenu}
          onChange={(event) =>
            setDraft({ ...draft, availableOnMenu: event.target.checked })
          }
        />
        Visível no cardápio
      </label>
      <label className="flex items-center gap-2 text-xs font-semibold">
        <input
          type="checkbox"
          checked={draft.availableOnPos}
          onChange={(event) =>
            setDraft({ ...draft, availableOnPos: event.target.checked })
          }
        />
        Disponível no PDV
      </label>
      {error ? (
        <p role="alert" className="text-red text-xs sm:col-span-2">
          {error}
        </p>
      ) : null}
      <div className="flex gap-2 sm:col-span-2">
        <button
          type="submit"
          disabled={pending}
          className="bg-primary text-primary-ink rounded-lg px-4 py-2 text-sm font-semibold disabled:opacity-50"
        >
          {pending ? "Salvando..." : "Salvar produto"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="text-ink-soft hover:bg-surface rounded-lg px-4 py-2 text-sm font-semibold"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}
