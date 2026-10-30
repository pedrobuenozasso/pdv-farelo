import { LogoBadge } from "@/components/logo-badge";
import { listCategories } from "@/lib/api/categories";
import { listProducts } from "@/lib/api/products";

const currencyFormatter = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

export default async function PublicCatalogPage() {
  let sections: {
    id: string;
    name: string;
    products: Awaited<ReturnType<typeof listProducts>>;
  }[] = [];
  let unavailable = false;

  try {
    const [categories, products] = await Promise.all([
      listCategories(),
      listProducts(),
    ]);
    const published = products.filter(
      (product) => product.active && product.availableOnMenu,
    );
    sections = categories
      .filter((category) => category.active)
      .map((category) => ({
        id: category.id,
        name: category.name,
        products: published.filter(
          (product) => product.categoryId === category.id,
        ),
      }))
      .filter((section) => section.products.length > 0);
  } catch {
    unavailable = true;
  }

  return (
    <main className="bg-bg min-h-screen pb-14">
      <header className="bg-primary text-primary-ink rounded-b-[28px] px-5 pt-6 pb-8">
        <div className="mx-auto flex max-w-3xl items-center gap-3">
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
        <div className="mx-auto mt-6 max-w-3xl">
          <h1 className="font-serif text-3xl font-semibold">Cardápio</h1>
          <p className="text-primary-ink/90 mt-2 text-sm">
            Confira os produtos e preços. Para pedir na cafeteria, leia o QR
            atualizado da sua comanda.
          </p>
        </div>
      </header>

      <div className="mx-auto max-w-3xl space-y-8 px-5 pt-8">
        {unavailable ? (
          <p className="text-ink-soft text-sm">
            Não foi possível carregar o cardápio agora. Tente novamente em
            instantes.
          </p>
        ) : sections.length === 0 ? (
          <p className="text-ink-soft text-sm">
            O cardápio ainda não está disponível.
          </p>
        ) : (
          sections.map((section) => (
            <section key={section.id} className="space-y-3">
              <h2 className="font-serif text-xl font-semibold">
                {section.name}
              </h2>
              <ul className="space-y-3">
                {section.products.map((product) => (
                  <li
                    key={product.id}
                    className="border-line bg-surface flex gap-4 rounded-2xl border p-4"
                  >
                    {product.imageUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={product.imageUrl}
                        alt=""
                        className="h-20 w-20 flex-none rounded-xl object-cover"
                      />
                    ) : null}
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
                        <h3 className="font-serif text-base font-semibold">
                          {product.name}
                        </h3>
                        <span className="text-sm font-bold whitespace-nowrap">
                          {currencyFormatter.format(product.price)}
                        </span>
                      </div>
                      {product.description ? (
                        <p className="text-ink-soft mt-1 text-sm">
                          {product.description}
                        </p>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          ))
        )}
      </div>
    </main>
  );
}
