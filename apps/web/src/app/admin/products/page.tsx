"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { ApiError, listCategories } from "@/lib/api/categories";
import { createProduct, listProducts, type Product } from "@/lib/api/products";

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

export default function ProductsAdminPage() {
  const queryClient = useQueryClient();

  const productsQuery = useQuery({
    queryKey: PRODUCTS_QUERY_KEY,
    queryFn: listProducts,
  });

  const categoriesQuery = useQuery({
    queryKey: CATEGORIES_QUERY_KEY,
    queryFn: listCategories,
  });

  const categoryNameById = new Map(
    (categoriesQuery.data ?? []).map((category) => [
      category.id,
      category.name,
    ]),
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

  const apiErrorMessage =
    createProductMutation.error instanceof ApiError
      ? createProductMutation.error.message
      : createProductMutation.error
        ? "Não foi possível criar o produto."
        : null;

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
            {categoriesQuery.data?.map((category) => (
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
          {categoriesQuery.data && categoriesQuery.data.length === 0 ? (
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
          {productsQuery.data?.map((product: Product) => (
            <li key={product.id} className="flex flex-col gap-0.5 py-2">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-black dark:text-zinc-50">
                  {product.name}
                </span>
                <span
                  className={
                    product.active
                      ? "text-xs text-green-600 dark:text-green-400"
                      : "text-xs text-zinc-400"
                  }
                >
                  {product.active ? "Ativo" : "Inativo"}
                </span>
              </div>
              <div className="flex items-center justify-between text-xs text-zinc-500 dark:text-zinc-400">
                <span>
                  {categoryNameById.get(product.categoryId) ??
                    product.categoryId}
                </span>
                <span>{currencyFormatter.format(product.price)}</span>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </main>
  );
}
