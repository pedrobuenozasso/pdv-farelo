"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

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
    <main className="mx-auto flex max-w-2xl flex-col gap-8 p-8">
      <div>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">Admin</p>
        <h1 className="text-2xl font-semibold text-black dark:text-zinc-50">
          Produtos
        </h1>
      </div>

      <form
        onSubmit={onSubmit}
        noValidate
        className="flex flex-col gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800"
      >
        <div className="flex flex-col gap-1">
          <label
            htmlFor="name"
            className="text-sm font-medium text-black dark:text-zinc-50"
          >
            Nome
          </label>
          <input
            id="name"
            type="text"
            placeholder="Ex: Café Espresso"
            className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-black"
            {...register("name")}
          />
          {errors.name ? (
            <p className="text-sm text-red-600 dark:text-red-400">
              {errors.name.message}
            </p>
          ) : null}
        </div>

        <div className="flex flex-col gap-1">
          <label
            htmlFor="description"
            className="text-sm font-medium text-black dark:text-zinc-50"
          >
            Descrição (opcional)
          </label>
          <input
            id="description"
            type="text"
            placeholder="Ex: Espresso curto, torra média"
            className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-black"
            {...register("description")}
          />
        </div>

        <div className="flex flex-col gap-1">
          <label
            htmlFor="price"
            className="text-sm font-medium text-black dark:text-zinc-50"
          >
            Preço (R$)
          </label>
          <input
            id="price"
            type="number"
            step="0.01"
            min="0"
            placeholder="Ex: 7.50"
            className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-black"
            {...register("price")}
          />
          {errors.price ? (
            <p className="text-sm text-red-600 dark:text-red-400">
              {errors.price.message}
            </p>
          ) : null}
        </div>

        <div className="flex flex-col gap-1">
          <label
            htmlFor="categoryId"
            className="text-sm font-medium text-black dark:text-zinc-50"
          >
            Categoria
          </label>
          <select
            id="categoryId"
            className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-black"
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
            <p className="text-sm text-red-600 dark:text-red-400">
              {errors.categoryId.message}
            </p>
          ) : null}
          {categoriesQuery.data && categories.length === 0 ? (
            <p className="text-sm text-zinc-500 dark:text-zinc-400">
              Nenhuma categoria cadastrada ainda — crie uma em /admin/categories
              antes de cadastrar produtos.
            </p>
          ) : null}
        </div>

        <div className="flex flex-col gap-1">
          <label
            htmlFor="imageUrl"
            className="text-sm font-medium text-black dark:text-zinc-50"
          >
            URL da imagem (opcional)
          </label>
          <input
            id="imageUrl"
            type="text"
            placeholder="https://..."
            className="rounded border border-zinc-300 px-3 py-2 text-sm dark:border-zinc-700 dark:bg-black"
            {...register("imageUrl")}
          />
          {errors.imageUrl ? (
            <p className="text-sm text-red-600 dark:text-red-400">
              {errors.imageUrl.message}
            </p>
          ) : null}
        </div>

        {apiErrorMessage ? (
          <p className="text-sm text-red-600 dark:text-red-400">
            {apiErrorMessage}
          </p>
        ) : null}

        <button
          type="submit"
          disabled={isSubmitting || createProductMutation.isPending}
          className="self-start rounded bg-black px-4 py-2 text-sm font-medium text-white disabled:opacity-50 dark:bg-white dark:text-black"
        >
          {createProductMutation.isPending
            ? "Salvando..."
            : "Adicionar produto"}
        </button>
      </form>

      <div className="flex flex-col gap-2">
        {productsQuery.isLoading ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Carregando...
          </p>
        ) : null}
        {productsQuery.isError ? (
          <p className="text-sm text-red-600 dark:text-red-400">
            Não foi possível carregar os produtos.
          </p>
        ) : null}
        {productsQuery.data && productsQuery.data.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Nenhum produto cadastrado.
          </p>
        ) : null}
        <ul className="flex flex-col divide-y divide-zinc-200 dark:divide-zinc-800">
          {productsQuery.data?.map((product: Product) =>
            editingProductId === product.id ? (
              <li key={product.id} className="py-2">
                <ProductEditForm
                  product={product}
                  categories={categories}
                  onCancel={() => setEditingProductId(null)}
                  onSaved={() => setEditingProductId(null)}
                />
              </li>
            ) : (
              <li key={product.id} className="flex flex-col gap-0.5 py-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-black dark:text-zinc-50">
                    {product.name}
                  </span>
                  <div className="flex items-center gap-2">
                    <span
                      className={
                        product.active
                          ? "text-xs text-green-600 dark:text-green-400"
                          : "text-xs text-zinc-400"
                      }
                    >
                      {product.active ? "Ativo" : "Inativo"}
                    </span>
                    <button
                      type="button"
                      onClick={() => setEditingProductId(product.id)}
                      className="text-xs font-medium text-blue-600 hover:underline dark:text-blue-400"
                    >
                      Editar
                    </button>
                  </div>
                </div>
                <div className="flex items-center justify-between text-xs text-zinc-500 dark:text-zinc-400">
                  <span>
                    {categoryNameById.get(product.categoryId) ??
                      product.categoryId}
                  </span>
                  <span>{currencyFormatter.format(product.price)}</span>
                </div>
                <div className="flex gap-3 text-xs text-zinc-400">
                  <span>
                    Cardápio: {product.availableOnMenu ? "sim" : "não"}
                  </span>
                  <span>PDV: {product.availableOnPos ? "sim" : "não"}</span>
                </div>
              </li>
            ),
          )}
        </ul>
      </div>
    </main>
  );
}

function ProductEditForm({
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
    <form
      onSubmit={onSubmit}
      noValidate
      className="flex flex-col gap-2 rounded border border-zinc-300 bg-zinc-50 p-3 dark:border-zinc-700 dark:bg-zinc-900"
    >
      <div className="flex flex-col gap-1">
        <input
          type="text"
          placeholder="Nome"
          aria-label="Nome"
          className="rounded border border-zinc-300 px-2 py-1 text-sm dark:border-zinc-700 dark:bg-black"
          {...register("name")}
        />
        {errors.name ? (
          <p className="text-xs text-red-600 dark:text-red-400">
            {errors.name.message}
          </p>
        ) : null}
      </div>

      <input
        type="text"
        placeholder="Descrição (opcional)"
        aria-label="Descrição"
        className="rounded border border-zinc-300 px-2 py-1 text-sm dark:border-zinc-700 dark:bg-black"
        {...register("description")}
      />

      <div className="flex flex-col gap-1">
        <input
          type="number"
          step="0.01"
          min="0"
          placeholder="Preço (R$)"
          aria-label="Preço"
          className="rounded border border-zinc-300 px-2 py-1 text-sm dark:border-zinc-700 dark:bg-black"
          {...register("price")}
        />
        {errors.price ? (
          <p className="text-xs text-red-600 dark:text-red-400">
            {errors.price.message}
          </p>
        ) : null}
      </div>

      <div className="flex flex-col gap-1">
        <select
          aria-label="Categoria"
          className="rounded border border-zinc-300 px-2 py-1 text-sm dark:border-zinc-700 dark:bg-black"
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
          <p className="text-xs text-red-600 dark:text-red-400">
            {errors.categoryId.message}
          </p>
        ) : null}
      </div>

      <div className="flex flex-col gap-1">
        <input
          type="text"
          placeholder="URL da imagem (opcional)"
          aria-label="URL da imagem"
          className="rounded border border-zinc-300 px-2 py-1 text-sm dark:border-zinc-700 dark:bg-black"
          {...register("imageUrl")}
        />
        {errors.imageUrl ? (
          <p className="text-xs text-red-600 dark:text-red-400">
            {errors.imageUrl.message}
          </p>
        ) : null}
      </div>

      <div className="flex flex-wrap gap-4 text-sm text-black dark:text-zinc-50">
        <label className="flex items-center gap-1.5">
          <input type="checkbox" {...register("active")} />
          Ativo
        </label>
        <label className="flex items-center gap-1.5">
          <input type="checkbox" {...register("availableOnMenu")} />
          Disponível no cardápio
        </label>
        <label className="flex items-center gap-1.5">
          <input type="checkbox" {...register("availableOnPos")} />
          Disponível no PDV
        </label>
      </div>

      {apiErrorMessage ? (
        <p className="text-sm text-red-600 dark:text-red-400">
          {apiErrorMessage}
        </p>
      ) : null}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={isSubmitting || updateProductMutation.isPending}
          className="rounded bg-black px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50 dark:bg-white dark:text-black"
        >
          {updateProductMutation.isPending ? "Salvando..." : "Salvar"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="rounded border border-zinc-300 px-3 py-1.5 text-sm font-medium text-black dark:border-zinc-700 dark:text-zinc-50"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}
