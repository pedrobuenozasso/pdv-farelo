import { authHeaders, parseResponse } from "./client";

export type DiningTable = {
  id: string;
  number: number;
  capacity: number;
};

export type TableOccupancy = {
  occupiedTables: number;
  totalTables: number;
  occupancyPercentage: number;
};

export async function listTables(): Promise<DiningTable[]> {
  const response = await fetch("/api/v1/tables", {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<DiningTable[]>(response);
}

export async function getTableOccupancy(): Promise<TableOccupancy> {
  const response = await fetch("/api/v1/tables/occupancy", {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<TableOccupancy>(response);
}
