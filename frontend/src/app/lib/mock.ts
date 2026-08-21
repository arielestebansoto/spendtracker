export type DashboardSummary = {
  totalSpent: number;
  spendCount: number;
  averageSpend: number;
  recentSpends: {
    id: string;
    category: string;
    description: string;
    amount: number;
    spendDate: string;
  }[];
};

export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  await new Promise((resolve) => setTimeout(resolve, 500));

  return {
    totalSpent: 47500,
    spendCount: 42,
    averageSpend: 1131,
    recentSpends: [
      {
        id: "1",
        category: "Comida",
        description: "Almuerzo restaurante",
        amount: 8500,
        spendDate: new Date().toISOString().slice(0, 10),
      },
      {
        id: "2",
        category: "Transporte",
        description: "Uber al trabajo",
        amount: 4200,
        spendDate: new Date().toISOString().slice(0, 10),
      },
      {
        id: "3",
        category: "Shopping",
        description: "Zapatos nuevos",
        amount: 12000,
        spendDate: new Date(Date.now() - 86400000).toISOString().slice(0, 10),
      },
      {
        id: "4",
        category: "Servicios",
        description: "Internet mensual",
        amount: 3500,
        spendDate: new Date(Date.now() - 86400000 * 2).toISOString().slice(0, 10),
      },
      {
        id: "5",
        category: "Comida",
        description: "Cena con amigos",
        amount: 15000,
        spendDate: new Date(Date.now() - 86400000 * 3).toISOString().slice(0, 10),
      },
    ],
  };
}
