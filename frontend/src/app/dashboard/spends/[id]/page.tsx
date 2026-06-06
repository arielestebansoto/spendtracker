"use client";

import { useParams } from "next/navigation";

import { SpendFormPage } from "../new/page";

export default function ViewSpendPage() {
    const params = useParams<{ id: string }>();

    return (
        <SpendFormPage
            mode="view"
            spendId={params.id}
        />
    );
}
