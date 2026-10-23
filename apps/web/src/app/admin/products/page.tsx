"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { AdminShell } from "@/components/admin-shell";
import { AuthGuard } from "@/components/auth-guard";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { ApiError, listCategories, type Category } from "@/lib/api/categories";
import {
  listFiscalProfiles,
  type FiscalProfile,
} from "@/lib/api/fiscal-profiles";
import {
  createProduct,
  listProducts,
  updateProduct,
  type Product,
  type ProductionStation,
} from "@/lib/api/products";
import { useSession } from "@/lib/use-session";

const PRODUCTS_QUERY_KEY = ["products"];
const CATEGORIES_QUERY_KEY = ["categories"];
const FISCAL_PROFILES_QUERY_KEY = ["fiscal-profiles"];

const currencyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

const inputClass =
  "rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-primary";

const PRODUCTION_STATION_LABEL: Record<ProductionStation, string> = {
  BAR: "Bar",
  KITCHEN: "Cozinha",
};

// price/imageUrl are kept as raw strings from the inputs and
// parsed/validated here — avoids fighting native <input> string values vs.
// number coercion, and keeps the schema readable.
const productFormSchema = z.object({
  name: z.string().trim().min(1, "Nome é obrigatório"),
  description: z.string().trim().optional(),
  price: z
    .string()
    .trim()
    .min(1, "Preço é obrigatório")
    .refine((value) => !Number.isNaN(Number(value)), "Preço inválido")
    .refine((value) => Number(value) >= 0, "Preço não pode ser negativo"),
  categoryId: z.string().min(1, "Categoria é obrigatória"),
  imageUrl: z
    .string()
    .trim()
    .optional()
    .refine(
      (value) => !value || /^https?:\/\/.+/.test(value),
      "URL inválida (deve começar com http:// ou https://)",
    ),
  // FARELO-271: the backend already accepted these on create — only the
  // form was missing them.
  availableOnMenu: z.boolean(),
  availableOnPos: z.boolean(),
  productionStation: z.enum(["", "BAR", "KITCHEN"]),
  fiscalProfileId: z.string(),
  gtin: z
    .string()
    .trim()
    .refine(
      (value) =>
        !value ||
        value === "SEM GTIN" ||
        /^(?:[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14})$/.test(value),
      "Use GTIN-8/12/13/14 ou SEM GTIN",
    ),
  fiscalUnit: z
    .string()
    .trim()
    .regex(/^$|^[A-Z0-9]{1,6}$/, "Use 1 a 6 letras maiúsculas ou números"),
});

type ProductFormValues = z.infer<typeof productFormSchema>;

// PUT is a full replace — the backend requires active/availableOnMenu/
// availableOnPos explicitly (see ProductUpdateRequest / docs/api.md), so the
// edit form extends the create schema with active (productionStation is
// already on productFormSchema, FARELO-272).
const productEditFormSchema = productFormSchema.extend({
  active: z.boolean(),
});

type ProductEditFormValues = z.infer<typeof productEditFormSchema>;

function apiErrorMessageFor(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message;
  if (error) return fallback;
  return null;
}

export default function ProductsAdminPage() {
  const queryClient = useQueryClient();
  const session = useSession();
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  // FARELO-270 ("busca; categoria; ativo/inativo; disponível no menu;
  // disponível no PDV"): filtered client-side over the already-fetched
  // product list — same reasoning already used elsewhere in this codebase
  // for filtering over a small, fully-loaded catalog rather than adding
  // query params to GET /api/v1/products.
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState<
    "all" | "active" | "inactive"
  >("all");
  const [menuFilter, setMenuFilter] = useState<"all" | "yes" | "no">("all");
  const [posFilter, setPosFilter] = useState<"all" | "yes" | "no">("all");

  const productsQuery = useQuery({
    queryKey: PRODUCTS_QUERY_KEY,
    queryFn: listProducts,
  });

  const categoriesQuery = useQuery({
    queryKey: CATEGORIES_QUERY_KEY,
    queryFn: listCategories,
  });
  const fiscalProfilesQuery = useQuery({
    queryKey: FISCAL_PROFILES_QUERY_KEY,
    queryFn: listFiscalProfiles,
  });
  const fiscalProfiles = fiscalProfilesQuery.data ?? [];

  const visibilityMutation = useMutation({
    mutationFn: ({
      product,
      field,
    }: {
      product: Product;
      field: "availableOnMenu" | "availableOnPos";
    }) =>
      updateProduct(product.id, {
        name: product.name,
        description: product.description ?? undefined,
        price: product.price,
        categoryId: product.categoryId,
        imageUrl: product.imageUrl ?? undefined,
        active: product.active,
        availableOnMenu:
          field === "availableOnMenu"
            ? !product.availableOnMenu
            : product.availableOnMenu,
        availableOnPos:
          field === "availableOnPos"
            ? !product.availableOnPos
            : product.availableOnPos,
        productionStation: product.productionStation ?? undefined,
        fiscalProfileId: product.fiscalProfileId,
      }),
    onSuccess: (savedProduct) => {
      queryClient.setQueryData<Product[]>(PRODUCTS_QUERY_KEY, (products) =>
        products?.map((product) =>
          product.id === savedProduct.id ? savedProduct : product,
        ),
      );
      queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY });
    },
  });

  const toggleVisibility = (
    product: Product,
    field: "availableOnMenu" | "availableOnPos",
  ) => {
    if (visibilityMutation.isPending) return;
    visibilityMutation.reset();
    visibilityMutation.mutate({ product, field });
  };

  const categories = categoriesQuery.data ?? [];
  const categoryNameById = new Map(
    categories.map((category) => [category.id, category.name]),
  );

  const filteredProducts = (productsQuery.data ?? []).filter((product) => {
    if (
      search.trim() &&
      !product.name.toLowerCase().includes(search.trim().toLowerCase())
    ) {
      return false;
    }
    if (categoryFilter && product.categoryId !== categoryFilter) return false;
    if (statusFilter === "active" && !product.active) return false;
    if (statusFilter === "inactive" && product.active) return false;
    if (menuFilter === "yes" && !product.availableOnMenu) return false;
    if (menuFilter === "no" && product.availableOnMenu) return false;
    if (posFilter === "yes" && !product.availableOnPos) return false;
    if (posFilter === "no" && product.availableOnPos) return false;
    return true;
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: {
      name: "",
      description: "",
      price: "",
      categoryId: "",
      imageUrl: "",
      availableOnMenu: true,
      availableOnPos: true,
      productionStation: "",
      fiscalProfileId: "",
      gtin: "",
      fiscalUnit: "",
    },
  });

  const createProductMutation = useMutation({
    mutationFn: createProduct,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY });
      reset();
      setCreateOpen(false);
    },
  });

  const onSubmit = handleSubmit((values) => {
    createProductMutation.mutate({
      name: values.name,
      description: values.description?.trim() || undefined,
      price: Number(values.price),
      categoryId: values.categoryId,
      imageUrl: values.imageUrl?.trim() || undefined,
      availableOnMenu: values.availableOnMenu,
      availableOnPos: values.availableOnPos,
      productionStation: values.productionStation || undefined,
      fiscalProfileId: values.fiscalProfileId || null,
      gtin: values.gtin || null,
      fiscalUnit: values.fiscalUnit || null,
    });
  });

  const apiErrorMessage = apiErrorMessageFor(
    createProductMutation.error,
    "Não foi possível criar o produto.",
  );

  return (
    <AuthGuard allow={["ADMIN", "MANAGER"]}>
      <AdminShell>
        <div className="mx-auto flex max-w-4xl flex-col gap-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="font-serif text-2xl font-semibold">Produtos</h1>
              <p className="text-ink-soft mt-0.5 text-sm">
                {filteredProducts.length} de {productsQuery.data?.length ?? 0}{" "}
                produtos
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Link
                href="/admin/menu-preview"
                className="text-primary text-sm font-semibold"
              >
                Ver cardápio
              </Link>
              {session?.role === "ADMIN" && (
                <Link
                  href="/admin/fiscal-profiles"
                  className="text-primary text-sm font-semibold"
                >
                  Perfis fiscais
                </Link>
              )}
              <Button onClick={() => setCreateOpen((open) => !open)}>
                {createOpen ? "Fechar" : "Novo produto"}
              </Button>
            </div>
          </div>

          {createOpen ? (
            <Card>
              <form
                onSubmit={onSubmit}
                noValidate
                className="flex flex-col gap-3"
              >
                <div className="flex flex-col gap-1">
                  <label htmlFor="name" className="text-sm font-medium">
                    Nome
                  </label>
                  <input
                    id="name"
                    type="text"
                    placeholder="Ex: Café Espresso"
                    className={inputClass}
                    {...register("name")}
                  />
                  {errors.name ? (
                    <p className="text-red text-sm">{errors.name.message}</p>
                  ) : null}
                </div>

                <div className="flex flex-col gap-1">
                  <label htmlFor="description" className="text-sm font-medium">
                    Descrição (opcional)
                  </label>
                  <input
                    id="description"
                    type="text"
                    placeholder="Ex: Espresso curto, torra média"
                    className={inputClass}
                    {...register("description")}
                  />
                </div>

                <div className="flex gap-3">
                  <div className="flex flex-1 flex-col gap-1">
                    <label htmlFor="price" className="text-sm font-medium">
                      Preço (R$)
                    </label>
                    <input
                      id="price"
                      type="number"
                      step="0.01"
                      min="0"
                      placeholder="Ex: 7.50"
                      className={inputClass}
                      {...register("price")}
                    />
                    {errors.price ? (
                      <p className="text-red text-sm">{errors.price.message}</p>
                    ) : null}
                  </div>

                  <div className="flex flex-1 flex-col gap-1">
                    <label htmlFor="categoryId" className="text-sm font-medium">
                      Categoria
                    </label>
                    <select
                      id="categoryId"
                      className={inputClass}
                      {...register("categoryId")}
                    >
                      <option value="">Selecione uma categoria</option>
                      {categories.map((category) => (
                        <option key={category.id} value={category.id}>
                          {category.name}
                        </option>
                      ))}
                    </select>
                    {errors.categoryId ? (
                      <p className="text-red text-sm">
                        {errors.categoryId.message}
                      </p>
                    ) : null}
                  </div>
                </div>
                {categoriesQuery.data && categories.length === 0 ? (
                  <p className="text-ink-faint text-sm">
                    Nenhuma categoria cadastrada ainda — crie uma em
                    /admin/categories antes de cadastrar produtos.
                  </p>
                ) : null}

                <div className="flex flex-col gap-1">
                  <label htmlFor="imageUrl" className="text-sm font-medium">
                    URL da imagem (opcional)
                  </label>
                  <input
                    id="imageUrl"
                    type="text"
                    placeholder="https://..."
                    className={inputClass}
                    {...register("imageUrl")}
                  />
                  {errors.imageUrl ? (
                    <p className="text-red text-sm">
                      {errors.imageUrl.message}
                    </p>
                  ) : null}
                </div>

                <div className="flex flex-col gap-1">
                  <label
                    htmlFor="productionStation"
                    className="text-sm font-medium"
                  >
                    Estação de produção (opcional)
                  </label>
                  <select
                    id="productionStation"
                    className={inputClass}
                    {...register("productionStation")}
                  >
                    <option value="">Sem estação</option>
                    {Object.entries(PRODUCTION_STATION_LABEL).map(
                      ([value, label]) => (
                        <option key={value} value={value}>
                          {label}
                        </option>
                      ),
                    )}
                  </select>
                </div>

                <div className="flex flex-col gap-1">
                  <label
                    htmlFor="fiscalProfileId"
                    className="text-sm font-medium"
                  >
                    Perfil fiscal (opcional)
                  </label>
                  <select
                    id="fiscalProfileId"
                    className={inputClass}
                    {...register("fiscalProfileId")}
                  >
                    <option value="">Sem perfil fiscal</option>
                    {fiscalProfiles
                      .filter((profile) => profile.active)
                      .map((profile) => (
                        <option key={profile.id} value={profile.id}>
                          {profile.name}
                          {profile.ncm ? ` · NCM ${profile.ncm}` : ""}
                        </option>
                      ))}
                  </select>
                  {fiscalProfilesQuery.isError && (
                    <p className="text-red text-sm">
                      Não foi possível carregar os perfis fiscais.
                    </p>
                  )}
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  <label
                    htmlFor="gtin"
                    className="flex flex-col gap-1 text-sm font-medium"
                  >
                    GTIN fiscal
                    <input
                      id="gtin"
                      className={inputClass}
                      placeholder="Código ou SEM GTIN"
                      {...register("gtin")}
                    />
                    {errors.gtin && (
                      <span className="text-red text-xs">
                        {errors.gtin.message}
                      </span>
                    )}
                  </label>
                  <label
                    htmlFor="fiscalUnit"
                    className="flex flex-col gap-1 text-sm font-medium"
                  >
                    Unidade comercial fiscal
                    <input
                      id="fiscalUnit"
                      className={inputClass}
                      maxLength={6}
                      placeholder="Ex.: UN, KG, CX"
                      {...register("fiscalUnit")}
                    />
                    {errors.fiscalUnit && (
                      <span className="text-red text-xs">
                        {errors.fiscalUnit.message}
                      </span>
                    )}
                  </label>
                </div>
                <p className="text-ink-soft text-xs">
                  Informe os valores conforme o cadastro fiscal do item. Campo
                  vazio permanece pendente.
                </p>

                <div className="flex flex-wrap items-center gap-6 text-sm font-medium">
                  <label className="flex items-center gap-2">
                    <Switch {...register("availableOnMenu")} />
                    Cardápio
                  </label>
                  <label className="flex items-center gap-2">
                    <Switch {...register("availableOnPos")} />
                    PDV
                  </label>
                </div>

                {apiErrorMessage ? (
                  <p className="text-red text-sm">{apiErrorMessage}</p>
                ) : null}

                <Button
                  type="submit"
                  disabled={isSubmitting || createProductMutation.isPending}
                  className="self-start"
                >
                  {createProductMutation.isPending
                    ? "Salvando..."
                    : "Adicionar produto"}
                </Button>
              </form>
            </Card>
          ) : null}

          {/* FARELO-270 */}
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="text"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar por nome..."
              className={`${inputClass} min-w-[200px] flex-1`}
            />
            <select
              value={categoryFilter}
              onChange={(event) => setCategoryFilter(event.target.value)}
              className={inputClass}
            >
              <option value="">Todas as categorias</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
            <select
              value={statusFilter}
              onChange={(event) =>
                setStatusFilter(event.target.value as typeof statusFilter)
              }
              className={inputClass}
            >
              <option value="all">Ativos e inativos</option>
              <option value="active">Só ativos</option>
              <option value="inactive">Só inativos</option>
            </select>
            <select
              value={menuFilter}
              onChange={(event) =>
                setMenuFilter(event.target.value as typeof menuFilter)
              }
              className={inputClass}
            >
              <option value="all">Cardápio: todos</option>
              <option value="yes">No cardápio</option>
              <option value="no">Fora do cardápio</option>
            </select>
            <select
              value={posFilter}
              onChange={(event) =>
                setPosFilter(event.target.value as typeof posFilter)
              }
              className={inputClass}
            >
              <option value="all">PDV: todos</option>
              <option value="yes">No PDV</option>
              <option value="no">Fora do PDV</option>
            </select>
          </div>

          <div className="border-line bg-surface overflow-hidden rounded-2xl border">
            {visibilityMutation.isError ? (
              <p role="alert" className="text-red p-5 text-sm">
                {apiErrorMessageFor(
                  visibilityMutation.error,
                  "Não foi possível atualizar a visibilidade do produto.",
                )}
              </p>
            ) : null}
            {productsQuery.isLoading ? (
              <p className="text-ink-faint p-5 text-sm">Carregando...</p>
            ) : null}
            {productsQuery.isError ? (
              <p className="text-red p-5 text-sm">
                Não foi possível carregar os produtos.
              </p>
            ) : null}
            {productsQuery.data && filteredProducts.length === 0 ? (
              <p className="text-ink-faint p-5 text-sm">
                {productsQuery.data.length === 0
                  ? "Nenhum produto cadastrado."
                  : "Nenhum produto encontrado com esses filtros."}
              </p>
            ) : null}
            {filteredProducts.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left">
                  <thead>
                    <tr>
                      <th className="text-ink-faint px-5 pt-4 pb-2.5 text-[11px] font-bold tracking-wide uppercase">
                        Nome
                      </th>
                      <th className="text-ink-faint px-3 pt-4 pb-2.5 text-[11px] font-bold tracking-wide uppercase">
                        Categoria
                      </th>
                      <th className="text-ink-faint px-3 pt-4 pb-2.5 text-[11px] font-bold tracking-wide uppercase">
                        Preço
                      </th>
                      <th className="text-ink-faint px-3 pt-4 pb-2.5 text-[11px] font-bold tracking-wide uppercase">
                        Cardápio
                      </th>
                      <th className="text-ink-faint px-3 pt-4 pb-2.5 text-[11px] font-bold tracking-wide uppercase">
                        PDV
                      </th>
                      <th className="text-ink-faint px-3 pt-4 pb-2.5 text-[11px] font-bold tracking-wide uppercase">
                        Status
                      </th>
                      <th className="px-5 pt-4 pb-2.5" />
                    </tr>
                  </thead>
                  <tbody>
                    {filteredProducts.map((product: Product) =>
                      editingProductId === product.id ? (
                        <ProductEditRow
                          key={product.id}
                          product={product}
                          categories={categories}
                          fiscalProfiles={fiscalProfiles}
                          onCancel={() => setEditingProductId(null)}
                          onSaved={() => setEditingProductId(null)}
                        />
                      ) : (
                        <tr key={product.id} className="border-line border-t">
                          <td className="px-5 py-3.5 text-sm font-semibold">
                            {product.name}
                            <div className="text-ink-soft text-xs font-normal">
                              Fiscal:{" "}
                              {product.fiscalProfileId
                                ? (fiscalProfiles.find(
                                    (profile) =>
                                      profile.id === product.fiscalProfileId,
                                  )?.name ?? "perfil não carregado")
                                : "não classificado"}
                            </div>
                          </td>
                          <td className="text-ink-soft px-3 py-3.5 text-sm">
                            {categoryNameById.get(product.categoryId) ??
                              product.categoryId}
                          </td>
                          <td className="px-3 py-3.5 text-sm font-semibold">
                            {currencyFormatter.format(product.price)}
                          </td>
                          <td className="px-3 py-3.5">
                            <VisibilitySwitch
                              on={product.availableOnMenu}
                              productName={product.name}
                              channel="cardápio"
                              disabled={visibilityMutation.isPending}
                              onClick={() =>
                                toggleVisibility(product, "availableOnMenu")
                              }
                            />
                          </td>
                          <td className="px-3 py-3.5">
                            <VisibilitySwitch
                              on={product.availableOnPos}
                              productName={product.name}
                              channel="PDV"
                              disabled={visibilityMutation.isPending}
                              onClick={() =>
                                toggleVisibility(product, "availableOnPos")
                              }
                            />
                          </td>
                          <td className="px-3 py-3.5">
                            <Badge tone={product.active ? "green" : "red"}>
                              {product.active ? "Ativo" : "Inativo"}
                            </Badge>
                          </td>
                          <td className="px-5 py-3.5 text-right">
                            <Link
                              href={`/admin/products/${product.id}/steps`}
                              className="text-primary mr-4 text-xs font-semibold"
                            >
                              Passos
                            </Link>
                            <button
                              type="button"
                              onClick={() => setEditingProductId(product.id)}
                              className="text-ink-faint hover:text-primary"
                              aria-label="Editar"
                            >
                              <svg
                                width="17"
                                height="17"
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                              >
                                <path d="M12 20h9" />
                                <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
                              </svg>
                            </button>
                          </td>
                        </tr>
                      ),
                    )}
                  </tbody>
                </table>
              </div>
            ) : null}
          </div>
        </div>
      </AdminShell>
    </AuthGuard>
  );
}

function VisibilitySwitch({
  on,
  productName,
  channel,
  disabled,
  onClick,
}: {
  on: boolean;
  productName: string;
  channel: "cardápio" | "PDV";
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={`${on ? "Ocultar" : "Exibir"} ${productName} no ${channel}`}
      disabled={disabled}
      onClick={onClick}
      className={
        on
          ? "bg-primary focus-visible:outline-primary inline-block h-[19px] w-[34px] cursor-pointer rounded-full align-middle focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-wait disabled:opacity-50"
          : "bg-line focus-visible:outline-primary inline-block h-[19px] w-[34px] cursor-pointer rounded-full align-middle focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-wait disabled:opacity-50"
      }
    >
      <span
        className={
          on
            ? "bg-primary-ink block h-[15px] w-[15px] translate-x-[17px] translate-y-[2px] rounded-full"
            : "bg-surface block h-[15px] w-[15px] translate-x-[2px] translate-y-[2px] rounded-full"
        }
      />
    </button>
  );
}

function ProductEditRow({
  product,
  categories,
  fiscalProfiles,
  onCancel,
  onSaved,
}: {
  product: Product;
  categories: Category[];
  fiscalProfiles: FiscalProfile[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProductEditFormValues>({
    resolver: zodResolver(productEditFormSchema),
    defaultValues: {
      name: product.name,
      description: product.description ?? "",
      price: String(product.price),
      categoryId: product.categoryId,
      imageUrl: product.imageUrl ?? "",
      active: product.active,
      availableOnMenu: product.availableOnMenu,
      availableOnPos: product.availableOnPos,
      productionStation: product.productionStation ?? "",
      fiscalProfileId: product.fiscalProfileId ?? "",
      gtin: product.gtin ?? "",
      fiscalUnit: product.fiscalUnit ?? "",
    },
  });

  const updateProductMutation = useMutation({
    mutationFn: (values: ProductEditFormValues) =>
      updateProduct(product.id, {
        name: values.name,
        description: values.description?.trim() || undefined,
        price: Number(values.price),
        categoryId: values.categoryId,
        imageUrl: values.imageUrl?.trim() || undefined,
        active: values.active,
        availableOnMenu: values.availableOnMenu,
        availableOnPos: values.availableOnPos,
        productionStation: values.productionStation || undefined,
        fiscalProfileId: values.fiscalProfileId || null,
        gtin: values.gtin || undefined,
        fiscalUnit: values.fiscalUnit,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY });
      onSaved();
    },
  });

  const onSubmit = handleSubmit((values) => {
    if (product.gtin && !values.gtin) {
      setError("gtin", {
        message:
          "A API ainda não permite remover GTIN; substitua pelo código correto ou SEM GTIN.",
      });
      return;
    }
    updateProductMutation.mutate(values);
  });

  const apiErrorMessage = apiErrorMessageFor(
    updateProductMutation.error,
    "Não foi possível salvar o produto.",
  );

  return (
    <tr className="border-line bg-primary-soft/40 border-t">
      <td colSpan={7} className="p-4">
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3">
          <div className="flex flex-wrap gap-3">
            <div className="flex min-w-[180px] flex-1 flex-col gap-1">
              <input
                type="text"
                placeholder="Nome"
                aria-label="Nome"
                className={inputClass}
                {...register("name")}
              />
              {errors.name ? (
                <p className="text-red text-xs">{errors.name.message}</p>
              ) : null}
            </div>
            <div className="flex min-w-[180px] flex-1 flex-col gap-1">
              <input
                type="text"
                placeholder="Descrição (opcional)"
                aria-label="Descrição"
                className={inputClass}
                {...register("description")}
              />
            </div>
            <div className="flex w-28 flex-col gap-1">
              <input
                type="number"
                step="0.01"
                min="0"
                placeholder="Preço"
                aria-label="Preço"
                className={inputClass}
                {...register("price")}
              />
              {errors.price ? (
                <p className="text-red text-xs">{errors.price.message}</p>
              ) : null}
            </div>
            <div className="flex min-w-[160px] flex-1 flex-col gap-1">
              <select
                aria-label="Categoria"
                className={inputClass}
                {...register("categoryId")}
              >
                <option value="">Selecione uma categoria</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
              {errors.categoryId ? (
                <p className="text-red text-xs">{errors.categoryId.message}</p>
              ) : null}
            </div>
            <div className="flex min-w-[180px] flex-1 flex-col gap-1">
              <input
                type="text"
                placeholder="URL da imagem (opcional)"
                aria-label="URL da imagem"
                className={inputClass}
                {...register("imageUrl")}
              />
              {errors.imageUrl ? (
                <p className="text-red text-xs">{errors.imageUrl.message}</p>
              ) : null}
            </div>
            <div className="flex min-w-[160px] flex-1 flex-col gap-1">
              <select
                aria-label="Estação de produção"
                className={inputClass}
                {...register("productionStation")}
              >
                <option value="">Sem estação</option>
                {Object.entries(PRODUCTION_STATION_LABEL).map(
                  ([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ),
                )}
              </select>
            </div>
            <div className="flex min-w-[180px] flex-1 flex-col gap-1">
              <select
                aria-label="Perfil fiscal"
                className={inputClass}
                {...register("fiscalProfileId")}
              >
                <option value="">Sem perfil fiscal</option>
                {product.fiscalProfileId &&
                  !fiscalProfiles.some(
                    (profile) => profile.id === product.fiscalProfileId,
                  ) && (
                    <option value={product.fiscalProfileId}>
                      Perfil atual (não carregado)
                    </option>
                  )}
                {fiscalProfiles
                  .filter(
                    (profile) =>
                      profile.active || profile.id === product.fiscalProfileId,
                  )
                  .map((profile) => (
                    <option key={profile.id} value={profile.id}>
                      {profile.name}
                      {profile.active ? "" : " (inativo)"}
                    </option>
                  ))}
              </select>
            </div>
            <div className="flex min-w-[180px] flex-1 flex-col gap-1">
              <input
                aria-label="GTIN fiscal"
                className={inputClass}
                placeholder="GTIN ou SEM GTIN"
                {...register("gtin")}
              />
              {errors.gtin && (
                <p className="text-red text-xs">{errors.gtin.message}</p>
              )}
            </div>
            <div className="flex min-w-[160px] flex-1 flex-col gap-1">
              <input
                aria-label="Unidade comercial fiscal"
                className={inputClass}
                maxLength={6}
                placeholder="Unidade fiscal (UN, KG...)"
                {...register("fiscalUnit")}
              />
              {errors.fiscalUnit && (
                <p className="text-red text-xs">{errors.fiscalUnit.message}</p>
              )}
            </div>
          </div>
          <p className="text-ink-soft text-xs">
            GTIN deve ser informado ou marcado como SEM GTIN após conferência;
            unidade fiscal vazia remove o valor anterior.
          </p>

          <div className="flex flex-wrap items-center gap-6 text-sm font-medium">
            <label className="flex items-center gap-2">
              <Switch {...register("active")} />
              Ativo
            </label>
            <label className="flex items-center gap-2">
              <Switch {...register("availableOnMenu")} />
              Cardápio
            </label>
            <label className="flex items-center gap-2">
              <Switch {...register("availableOnPos")} />
              PDV
            </label>
          </div>

          {apiErrorMessage ? (
            <p className="text-red text-sm">{apiErrorMessage}</p>
          ) : null}

          <div className="flex gap-2">
            <Button
              type="submit"
              disabled={isSubmitting || updateProductMutation.isPending}
              className="px-4 py-2 text-[13px]"
            >
              {updateProductMutation.isPending ? "Salvando..." : "Salvar"}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={onCancel}
              className="px-4 py-2 text-[13px]"
            >
              Cancelar
            </Button>
          </div>
        </form>
      </td>
    </tr>
  );
}
