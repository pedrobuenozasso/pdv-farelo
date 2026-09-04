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
import { apiErrorMessage } from "@/lib/api/client";
import {
  createIngredient,
  getIngredientBalance,
  listIngredientMovements,
  listIngredients,
  recordLoss,
  recordPurchase,
  updateIngredient,
  type Ingredient,
  type IngredientUnit,
  type InventoryMovementType,
} from "@/lib/api/ingredients";
import { listProducts } from "@/lib/api/products";
import {
  addRecipeItem,
  createRecipe,
  deactivateRecipe,
  listRecipeItems,
  listRecipes,
  removeRecipeItem,
  type Recipe,
} from "@/lib/api/recipes";

const INGREDIENTS_QUERY_KEY = ["ingredients"];
const RECIPES_QUERY_KEY = ["recipes"];

const UNIT_LABEL: Record<IngredientUnit, string> = {
  GRAM: "g",
  MILLILITER: "ml",
  UNIT: "un",
};

const MOVEMENT_TYPE_LABEL: Record<InventoryMovementType, string> = {
  PURCHASE: "Compra",
  ORDER_CONSUMPTION: "Consumo (pedido)",
  LOSS: "Perda",
  ADJUSTMENT: "Ajuste",
  RETURN: "Devolução",
  CANCELLATION: "Cancelamento",
  INTERNAL_CONSUMPTION: "Consumo interno",
};

const inputClass =
  "border-line bg-surface focus:border-primary rounded-lg border px-3 py-2 text-sm outline-none";

const quantityFormatter = new Intl.NumberFormat("pt-BR", {
  maximumFractionDigits: 2,
});

const dateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
});

const ingredientFormSchema = z.object({
  name: z.string().trim().min(1, "Nome é obrigatório"),
  unit: z.enum(["GRAM", "MILLILITER", "UNIT"]),
  minimumStock: z
    .string()
    .trim()
    .optional()
    .refine((value) => !value || !Number.isNaN(Number(value)), "Valor inválido")
    .refine((value) => !value || Number(value) >= 0, "Não pode ser negativo"),
});

type IngredientFormValues = z.infer<typeof ingredientFormSchema>;

export default function InventoryAdminPage() {
  return (
    <AuthGuard>
      <AdminShell>
        <div className="mx-auto flex max-w-4xl flex-col gap-10">
          <IngredientsSection />
          <RecipesSection />
        </div>
      </AdminShell>
    </AuthGuard>
  );
}

function IngredientsSection() {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [movementId, setMovementId] = useState<string | null>(null);

  const ingredientsQuery = useQuery({
    queryKey: INGREDIENTS_QUERY_KEY,
    queryFn: listIngredients,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<IngredientFormValues>({
    resolver: zodResolver(ingredientFormSchema),
    defaultValues: { name: "", unit: "GRAM", minimumStock: "" },
  });

  const createIngredientMutation = useMutation({
    mutationFn: (values: IngredientFormValues) =>
      createIngredient({
        name: values.name,
        unit: values.unit,
        minimumStock: values.minimumStock
          ? Number(values.minimumStock)
          : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: INGREDIENTS_QUERY_KEY });
      reset();
      setCreateOpen(false);
    },
  });

  const apiErrorMsg = apiErrorMessage(
    createIngredientMutation.error,
    "Não foi possível criar o insumo.",
  );

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-serif text-2xl font-semibold">Estoque</h1>
          <p className="text-ink-soft mt-0.5 text-sm">
            {ingredientsQuery.data?.length ?? 0} insumos cadastrados
          </p>
        </div>
        <Button onClick={() => setCreateOpen((open) => !open)}>
          {createOpen ? "Fechar" : "Novo insumo"}
        </Button>
      </div>

      {createOpen ? (
        <Card>
          <form
            onSubmit={handleSubmit((values) =>
              createIngredientMutation.mutate(values),
            )}
            noValidate
            className="flex flex-wrap items-end gap-3"
          >
            <div className="flex min-w-[200px] flex-1 flex-col gap-1">
              <label htmlFor="name" className="text-sm font-medium">
                Nome
              </label>
              <input
                id="name"
                type="text"
                placeholder="Ex: Leite integral"
                className={inputClass}
                {...register("name")}
              />
              {errors.name ? (
                <p className="text-red text-sm">{errors.name.message}</p>
              ) : null}
            </div>
            <div className="flex w-32 flex-col gap-1">
              <label htmlFor="unit" className="text-sm font-medium">
                Unidade
              </label>
              <select id="unit" className={inputClass} {...register("unit")}>
                <option value="GRAM">Grama (g)</option>
                <option value="MILLILITER">Mililitro (ml)</option>
                <option value="UNIT">Unidade (un)</option>
              </select>
            </div>
            <div className="flex w-40 flex-col gap-1">
              <label htmlFor="minimumStock" className="text-sm font-medium">
                Estoque mínimo
              </label>
              <input
                id="minimumStock"
                type="number"
                step="0.01"
                min="0"
                placeholder="Opcional"
                className={inputClass}
                {...register("minimumStock")}
              />
              {errors.minimumStock ? (
                <p className="text-red text-sm">
                  {errors.minimumStock.message}
                </p>
              ) : null}
            </div>
            {apiErrorMsg ? (
              <p className="text-red w-full text-sm">{apiErrorMsg}</p>
            ) : null}
            <Button
              type="submit"
              disabled={isSubmitting || createIngredientMutation.isPending}
            >
              {createIngredientMutation.isPending ? "Salvando..." : "Adicionar"}
            </Button>
          </form>
        </Card>
      ) : null}

      <div className="border-line bg-surface overflow-hidden rounded-2xl border">
        {ingredientsQuery.isLoading ? (
          <p className="text-ink-faint p-5 text-sm">Carregando...</p>
        ) : null}
        {ingredientsQuery.isError ? (
          <p className="text-red p-5 text-sm">
            Não foi possível carregar os insumos.
          </p>
        ) : null}
        {ingredientsQuery.data && ingredientsQuery.data.length === 0 ? (
          <p className="text-ink-faint p-5 text-sm">
            Nenhum insumo cadastrado.
          </p>
        ) : null}
        {ingredientsQuery.data?.map((ingredient) => (
          <IngredientRow
            key={ingredient.id}
            ingredient={ingredient}
            editing={editingId === ingredient.id}
            movementOpen={movementId === ingredient.id}
            onToggleEdit={() =>
              setEditingId((current) =>
                current === ingredient.id ? null : ingredient.id,
              )
            }
            onToggleMovement={() =>
              setMovementId((current) =>
                current === ingredient.id ? null : ingredient.id,
              )
            }
            onEditSaved={() => setEditingId(null)}
          />
        ))}
      </div>
    </div>
  );
}

function IngredientRow({
  ingredient,
  editing,
  movementOpen,
  onToggleEdit,
  onToggleMovement,
  onEditSaved,
}: {
  ingredient: Ingredient;
  editing: boolean;
  movementOpen: boolean;
  onToggleEdit: () => void;
  onToggleMovement: () => void;
  onEditSaved: () => void;
}) {
  const balanceQuery = useQuery({
    queryKey: ["ingredients", ingredient.id, "balance"],
    queryFn: () => getIngredientBalance(ingredient.id),
  });

  return (
    <div className="border-line border-t first:border-t-0">
      <div className="flex items-center gap-4 px-5 py-3.5">
        <div className="flex-1">
          <div className="text-sm font-semibold">{ingredient.name}</div>
          <div className="text-ink-faint text-xs">
            {UNIT_LABEL[ingredient.unit]}
          </div>
        </div>
        <div className="w-28 text-sm">
          {balanceQuery.data ? (
            <span
              className={
                balanceQuery.data.belowMinimum
                  ? "text-red font-bold"
                  : "font-semibold"
              }
            >
              {quantityFormatter.format(balanceQuery.data.balance)}{" "}
              {UNIT_LABEL[ingredient.unit]}
            </span>
          ) : (
            <span className="text-ink-faint">…</span>
          )}
        </div>
        <div className="w-24 text-sm">
          {ingredient.minimumStock !== null ? (
            <span className="text-ink-soft">
              min. {quantityFormatter.format(ingredient.minimumStock)}
            </span>
          ) : (
            <span className="text-ink-faint">—</span>
          )}
        </div>
        <div className="w-24">
          <Badge tone={ingredient.active ? "green" : "red"}>
            {ingredient.active ? "Ativo" : "Inativo"}
          </Badge>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onToggleMovement}
            className="text-ink-faint hover:text-primary"
            aria-label="Movimentar estoque"
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
              <path d="M12 20V4M5 11l7-7 7 7" />
            </svg>
          </button>
          <button
            type="button"
            onClick={onToggleEdit}
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
        </div>
      </div>

      {editing ? (
        <IngredientEditForm ingredient={ingredient} onSaved={onEditSaved} />
      ) : null}
      {movementOpen ? (
        <IngredientMovementPanel ingredient={ingredient} />
      ) : null}
    </div>
  );
}

function IngredientEditForm({
  ingredient,
  onSaved,
}: {
  ingredient: Ingredient;
  onSaved: () => void;
}) {
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<IngredientFormValues & { active: boolean }>({
    resolver: zodResolver(ingredientFormSchema.extend({ active: z.boolean() })),
    defaultValues: {
      name: ingredient.name,
      unit: ingredient.unit,
      minimumStock:
        ingredient.minimumStock !== null ? String(ingredient.minimumStock) : "",
      active: ingredient.active,
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: IngredientFormValues & { active: boolean }) =>
      updateIngredient(ingredient.id, {
        name: values.name,
        unit: values.unit,
        active: values.active,
        minimumStock: values.minimumStock
          ? Number(values.minimumStock)
          : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: INGREDIENTS_QUERY_KEY });
      onSaved();
    },
  });

  const apiErrorMsg = apiErrorMessage(
    updateMutation.error,
    "Não foi possível salvar o insumo.",
  );

  return (
    <form
      onSubmit={handleSubmit((values) => updateMutation.mutate(values))}
      noValidate
      className="bg-primary-soft/40 flex flex-wrap items-end gap-3 px-5 py-4"
    >
      <div className="flex min-w-[180px] flex-1 flex-col gap-1">
        <input className={inputClass} {...register("name")} />
        {errors.name ? (
          <p className="text-red text-xs">{errors.name.message}</p>
        ) : null}
      </div>
      <div className="flex w-28 flex-col gap-1">
        <select className={inputClass} {...register("unit")}>
          <option value="GRAM">g</option>
          <option value="MILLILITER">ml</option>
          <option value="UNIT">un</option>
        </select>
      </div>
      <div className="flex w-32 flex-col gap-1">
        <input
          type="number"
          step="0.01"
          min="0"
          placeholder="Mínimo"
          className={inputClass}
          {...register("minimumStock")}
        />
      </div>
      <label className="flex items-center gap-2 text-sm font-medium">
        <Switch {...register("active")} />
        Ativo
      </label>
      {apiErrorMsg ? (
        <p className="text-red w-full text-sm">{apiErrorMsg}</p>
      ) : null}
      <Button
        type="submit"
        disabled={isSubmitting || updateMutation.isPending}
        className="px-4 py-2 text-[13px]"
      >
        {updateMutation.isPending ? "Salvando..." : "Salvar"}
      </Button>
    </form>
  );
}

function IngredientMovementPanel({ ingredient }: { ingredient: Ingredient }) {
  const queryClient = useQueryClient();
  const [purchaseQty, setPurchaseQty] = useState("");
  const [lossQty, setLossQty] = useState("");

  const movementsQuery = useQuery({
    queryKey: ["ingredients", ingredient.id, "movements"],
    queryFn: () => listIngredientMovements(ingredient.id),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({
      queryKey: ["ingredients", ingredient.id],
    });
    queryClient.invalidateQueries({ queryKey: INGREDIENTS_QUERY_KEY });
  };

  const purchaseMutation = useMutation({
    mutationFn: (quantity: number) => recordPurchase(ingredient.id, quantity),
    onSuccess: () => {
      setPurchaseQty("");
      invalidate();
    },
  });

  const lossMutation = useMutation({
    mutationFn: (quantity: number) => recordLoss(ingredient.id, quantity),
    onSuccess: () => {
      setLossQty("");
      invalidate();
    },
  });

  const purchaseError = apiErrorMessage(
    purchaseMutation.error,
    "Não foi possível registrar a compra.",
  );
  const lossError = apiErrorMessage(
    lossMutation.error,
    "Não foi possível registrar a perda.",
  );

  return (
    <div className="border-line bg-bg flex flex-col gap-4 border-t px-5 py-4">
      <div className="flex flex-wrap gap-6">
        <div className="flex flex-col gap-1.5">
          <span className="text-ink-soft text-xs font-semibold">
            Registrar compra ({UNIT_LABEL[ingredient.unit]})
          </span>
          <div className="flex gap-2">
            <input
              type="number"
              step="0.01"
              min="0"
              value={purchaseQty}
              onChange={(event) => setPurchaseQty(event.target.value)}
              className={`${inputClass} w-28`}
            />
            <Button
              disabled={!purchaseQty || purchaseMutation.isPending}
              onClick={() => purchaseMutation.mutate(Number(purchaseQty))}
              className="px-4 py-2 text-[13px]"
            >
              {purchaseMutation.isPending ? "..." : "Registrar"}
            </Button>
          </div>
          {purchaseError ? (
            <p className="text-red text-xs">{purchaseError}</p>
          ) : null}
        </div>
        <div className="flex flex-col gap-1.5">
          <span className="text-ink-soft text-xs font-semibold">
            Registrar perda ({UNIT_LABEL[ingredient.unit]})
          </span>
          <div className="flex gap-2">
            <input
              type="number"
              step="0.01"
              min="0"
              value={lossQty}
              onChange={(event) => setLossQty(event.target.value)}
              className={`${inputClass} w-28`}
            />
            <Button
              variant="outline"
              disabled={!lossQty || lossMutation.isPending}
              onClick={() => lossMutation.mutate(Number(lossQty))}
              className="px-4 py-2 text-[13px]"
            >
              {lossMutation.isPending ? "..." : "Registrar"}
            </Button>
          </div>
          {lossError ? <p className="text-red text-xs">{lossError}</p> : null}
        </div>
      </div>

      <div>
        <div className="text-ink-faint mb-1.5 text-xs font-bold tracking-wide uppercase">
          Últimas movimentações
        </div>
        {movementsQuery.data && movementsQuery.data.length === 0 ? (
          <p className="text-ink-faint text-sm">Nenhuma movimentação ainda.</p>
        ) : null}
        <ul className="flex flex-col gap-1">
          {movementsQuery.data
            ?.slice(-6)
            .reverse()
            .map((movement) => (
              <li
                key={movement.id}
                className="flex items-center justify-between text-sm"
              >
                <span className="text-ink-soft">
                  {MOVEMENT_TYPE_LABEL[movement.type]} ·{" "}
                  {dateTimeFormatter.format(new Date(movement.createdAt))}
                </span>
                <span
                  className={
                    movement.quantity < 0
                      ? "text-red font-semibold"
                      : "text-green-ink font-semibold"
                  }
                >
                  {movement.quantity > 0 ? "+" : ""}
                  {quantityFormatter.format(movement.quantity)}{" "}
                  {UNIT_LABEL[ingredient.unit]}
                </span>
              </li>
            ))}
        </ul>
      </div>
    </div>
  );
}

function RecipesSection() {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedProductId, setSelectedProductId] = useState("");
  const [expandedRecipeId, setExpandedRecipeId] = useState<string | null>(null);

  const recipesQuery = useQuery({
    queryKey: RECIPES_QUERY_KEY,
    queryFn: listRecipes,
  });

  const productsQuery = useQuery({
    queryKey: ["products"],
    queryFn: listProducts,
  });

  const createRecipeMutation = useMutation({
    mutationFn: (productId: string) => createRecipe(productId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: RECIPES_QUERY_KEY });
      setSelectedProductId("");
      setCreateOpen(false);
    },
  });

  const createErrorMsg = apiErrorMessage(
    createRecipeMutation.error,
    "Não foi possível criar a receita.",
  );

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-serif text-xl font-semibold">Receitas</h2>
          <p className="text-ink-soft mt-0.5 text-sm">
            Insumos consumidos por produto ao fechar um pedido
          </p>
        </div>
        <Button onClick={() => setCreateOpen((open) => !open)}>
          {createOpen ? "Fechar" : "Nova receita"}
        </Button>
      </div>

      {createOpen ? (
        <Card className="flex flex-wrap items-end gap-3">
          <div className="flex min-w-[220px] flex-1 flex-col gap-1">
            <label className="text-sm font-medium">Produto</label>
            <select
              className={inputClass}
              value={selectedProductId}
              onChange={(event) => setSelectedProductId(event.target.value)}
            >
              <option value="">Selecione um produto</option>
              {productsQuery.data?.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name}
                </option>
              ))}
            </select>
          </div>
          {createErrorMsg ? (
            <p className="text-red w-full text-sm">{createErrorMsg}</p>
          ) : null}
          <Button
            disabled={!selectedProductId || createRecipeMutation.isPending}
            onClick={() => createRecipeMutation.mutate(selectedProductId)}
          >
            {createRecipeMutation.isPending ? "Salvando..." : "Criar"}
          </Button>
        </Card>
      ) : null}

      <div className="border-line bg-surface overflow-hidden rounded-2xl border">
        {recipesQuery.isLoading ? (
          <p className="text-ink-faint p-5 text-sm">Carregando...</p>
        ) : null}
        {recipesQuery.data && recipesQuery.data.length === 0 ? (
          <p className="text-ink-faint p-5 text-sm">
            Nenhuma receita cadastrada.
          </p>
        ) : null}
        {recipesQuery.data?.map((recipe) => (
          <RecipeRow
            key={recipe.id}
            recipe={recipe}
            expanded={expandedRecipeId === recipe.id}
            onToggle={() =>
              setExpandedRecipeId((current) =>
                current === recipe.id ? null : recipe.id,
              )
            }
          />
        ))}
      </div>
    </div>
  );
}

function RecipeRow({
  recipe,
  expanded,
  onToggle,
}: {
  recipe: Recipe;
  expanded: boolean;
  onToggle: () => void;
}) {
  const queryClient = useQueryClient();

  const deactivateMutation = useMutation({
    mutationFn: () => deactivateRecipe(recipe.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: RECIPES_QUERY_KEY });
    },
  });

  return (
    <div className="border-line border-t first:border-t-0">
      <div className="flex items-center gap-4 px-5 py-3.5">
        <button
          type="button"
          onClick={onToggle}
          className="flex flex-1 items-center justify-between text-left"
        >
          <span className="text-sm font-semibold">{recipe.productName}</span>
          <Badge tone={recipe.active ? "green" : "neutral"}>
            {recipe.active ? "Ativa" : "Inativa"}
          </Badge>
        </button>
        {recipe.active ? (
          <button
            type="button"
            disabled={deactivateMutation.isPending}
            onClick={() => deactivateMutation.mutate()}
            className="text-ink-faint hover:text-red text-xs font-semibold"
          >
            Desativar
          </button>
        ) : null}
      </div>
      {expanded ? <RecipeItemsPanel recipe={recipe} /> : null}
    </div>
  );
}

function RecipeItemsPanel({ recipe }: { recipe: Recipe }) {
  const queryClient = useQueryClient();
  const [ingredientId, setIngredientId] = useState("");
  const [quantity, setQuantity] = useState("");

  const itemsQuery = useQuery({
    queryKey: ["recipes", recipe.id, "items"],
    queryFn: () => listRecipeItems(recipe.id),
  });

  const ingredientsQuery = useQuery({
    queryKey: INGREDIENTS_QUERY_KEY,
    queryFn: listIngredients,
  });

  const invalidateItems = () =>
    queryClient.invalidateQueries({
      queryKey: ["recipes", recipe.id, "items"],
    });

  const addMutation = useMutation({
    mutationFn: () => addRecipeItem(recipe.id, ingredientId, Number(quantity)),
    onSuccess: () => {
      setIngredientId("");
      setQuantity("");
      invalidateItems();
    },
  });

  const removeMutation = useMutation({
    mutationFn: (itemId: string) => removeRecipeItem(recipe.id, itemId),
    onSuccess: invalidateItems,
  });

  const addErrorMsg = apiErrorMessage(
    addMutation.error,
    "Não foi possível adicionar o insumo.",
  );

  return (
    <div className="border-line bg-bg flex flex-col gap-3 border-t px-5 py-4">
      <ul className="flex flex-col gap-1.5">
        {itemsQuery.data && itemsQuery.data.length === 0 ? (
          <p className="text-ink-faint text-sm">Nenhum insumo na receita.</p>
        ) : null}
        {itemsQuery.data?.map((item) => (
          <li
            key={item.id}
            className="flex items-center justify-between text-sm"
          >
            <span>
              {quantityFormatter.format(item.quantity)}{" "}
              {UNIT_LABEL[item.ingredientUnit]} de {item.ingredientName}
            </span>
            <button
              type="button"
              disabled={removeMutation.isPending}
              onClick={() => removeMutation.mutate(item.id)}
              className="text-ink-faint hover:text-red text-xs font-semibold"
            >
              Remover
            </button>
          </li>
        ))}
      </ul>

      {recipe.active ? (
        <div className="flex flex-wrap items-end gap-2">
          <select
            className={`${inputClass} min-w-[180px] flex-1`}
            value={ingredientId}
            onChange={(event) => setIngredientId(event.target.value)}
          >
            <option value="">Selecione um insumo</option>
            {ingredientsQuery.data?.map((ingredient) => (
              <option key={ingredient.id} value={ingredient.id}>
                {ingredient.name}
              </option>
            ))}
          </select>
          <input
            type="number"
            step="0.01"
            min="0"
            placeholder="Qtd."
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            className={`${inputClass} w-24`}
          />
          <Button
            disabled={!ingredientId || !quantity || addMutation.isPending}
            onClick={() => addMutation.mutate()}
            className="px-4 py-2 text-[13px]"
          >
            {addMutation.isPending ? "..." : "Adicionar"}
          </Button>
        </div>
      ) : null}
      {addErrorMsg ? <p className="text-red text-sm">{addErrorMsg}</p> : null}
    </div>
  );
}
