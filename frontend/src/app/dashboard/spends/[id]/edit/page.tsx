"use client";

import { useParams } from "next/navigation";

import { SpendFormPage } from "../../new/page";

export default function EditSpendPage() {
    const params = useParams<{ id: string }>();

    return (
        <SpendFormPage
            mode="edit"
            spendId={params.id}
        />
    );
}
