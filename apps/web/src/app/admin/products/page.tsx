"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
  createProduct,
  listProducts,
  updateProduct,
  type Product,
} from "@/lib/api/products";

const PRODUCTS_QUERY_KEY = ["products"];
const CATEGORIES_QUERY_KEY = ["categories"];

const currencyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

const inputClass =
  "rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-primary";

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
});

type ProductFormValues = z.infer<typeof productFormSchema>;

// PUT is a full replace — the backend requires active/availableOnMenu/
// availableOnPos explicitly (see ProductUpdateRequest / docs/api.md), so the
// edit form extends the create schema with those three booleans.
const productEditFormSchema = productFormSchema.extend({
  active: z.boolean(),
  availableOnMenu: z.boolean(),
  availableOnPos: z.boolean(),
});

type ProductEditFormValues = z.infer<typeof productEditFormSchema>;

function apiErrorMessageFor(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message;
  if (error) return fallback;
  return null;
}

export default function ProductsAdminPage() {
  const queryClient = useQueryClient();
  const [editingProductId, setEditingProductId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const productsQuery = useQuery({
    queryKey: PRODUCTS_QUERY_KEY,
    queryFn: listProducts,
  });

  const categoriesQuery = useQuery({
    queryKey: CATEGORIES_QUERY_KEY,
    queryFn: listCategories,
  });

  const categories = categoriesQuery.data ?? [];
  const categoryNameById = new Map(
    categories.map((category) => [category.id, category.name]),
  );

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
    });
  });

  const apiErrorMessage = apiErrorMessageFor(
    createProductMutation.error,
    "Não foi possível criar o produto.",
  );

  return (
    <AuthGuard>
      <AdminShell>
        <div className="mx-auto flex max-w-4xl flex-col gap-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="font-serif text-2xl font-semibold">Produtos</h1>
              <p className="text-ink-soft mt-0.5 text-sm">
                {productsQuery.data?.length ?? 0} produtos cadastrados
              </p>
            </div>
            <Button onClick={() => setCreateOpen((open) => !open)}>
              {createOpen ? "Fechar" : "Novo produto"}
            </Button>
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

          <div className="border-line bg-surface overflow-hidden rounded-2xl border">
            {productsQuery.isLoading ? (
              <p className="text-ink-faint p-5 text-sm">Carregando...</p>
            ) : null}
            {productsQuery.isError ? (
              <p className="text-red p-5 text-sm">
                Não foi possível carregar os produtos.
              </p>
            ) : null}
            {productsQuery.data && productsQuery.data.length === 0 ? (
              <p className="text-ink-faint p-5 text-sm">
                Nenhum produto cadastrado.
              </p>
            ) : null}
            {productsQuery.data && productsQuery.data.length > 0 ? (
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
                    {productsQuery.data.map((product: Product) =>
                      editingProductId === product.id ? (
                        <ProductEditRow
                          key={product.id}
                          product={product}
                          categories={categories}
                          onCancel={() => setEditingProductId(null)}
                          onSaved={() => setEditingProductId(null)}
                        />
                      ) : (
                        <tr key={product.id} className="border-line border-t">
                          <td className="px-5 py-3.5 text-sm font-semibold">
                            {product.name}
                          </td>
                          <td className="text-ink-soft px-3 py-3.5 text-sm">
                            {categoryNameById.get(product.categoryId) ??
                              product.categoryId}
                          </td>
                          <td className="px-3 py-3.5 text-sm font-semibold">
                            {currencyFormatter.format(product.price)}
                          </td>
                          <td className="px-3 py-3.5">
                            <ReadonlyDot on={product.availableOnMenu} />
                          </td>
                          <td className="px-3 py-3.5">
                            <ReadonlyDot on={product.availableOnPos} />
                          </td>
                          <td className="px-3 py-3.5">
                            <Badge tone={product.active ? "green" : "red"}>
                              {product.active ? "Ativo" : "Inativo"}
                            </Badge>
                          </td>
                          <td className="px-5 py-3.5 text-right">
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

function ReadonlyDot({ on }: { on: boolean }) {
  return (
    <span
      className={
        on
          ? "bg-primary inline-block h-[19px] w-[34px] rounded-full"
          : "bg-line inline-block h-[19px] w-[34px] rounded-full"
      }
    >
      <span
        className={
          on
            ? "bg-primary-ink block h-[15px] w-[15px] translate-x-[17px] translate-y-[2px] rounded-full"
            : "bg-surface block h-[15px] w-[15px] translate-x-[2px] translate-y-[2px] rounded-full"
        }
      />
    </span>
  );
}

function ProductEditRow({
  product,
  categories,
  onCancel,
  onSaved,
}: {
  product: Product;
  categories: Category[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
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
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PRODUCTS_QUERY_KEY });
      onSaved();
    },
  });

  const onSubmit = handleSubmit((values) => {
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
          </div>

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
