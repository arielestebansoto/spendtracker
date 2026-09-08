package com.arielsoto.spendtracker.classifier;

public class ClassificationPrompt {

    public static String buildPrompt(String ocrText) {
        return """
            You are a receipt classifier. Extract structured data from the receipt text below.

            Receipt text:
            """ + ocrText + """

            Return ONLY a JSON object with these fields:
            - amount (number): total amount spent
            - category (string): one of Comida, Transporte, Servicios, Salud, Streaming, Trabajo, Hogar, Otros
            - description (string): brief summary of what was purchased
            - date (string|null): date in YYYY-MM-DD format, or null if not visible
            - items (array): list of objects with "description" (string) and "amount" (number)

            Example:
            {"amount":42.50,"category":"Comida","description":"Lunch at Restaurant XYZ","date":"2026-08-29","items":[{"description":"Burger","amount":15.00}]}

            IMPORTANT: Return ONLY the raw JSON object. No explanation, no markdown, no code blocks, no preamble. Start your response with { and end with }.
            """;
    }
}
