package com.arielsoto.spendtracker.classifier;

public class ClassificationPrompt {

    public static String buildPrompt(String ocrText) {
        return """
            You are a receipt classifier. Analyze the receipt text below and extract structured data.

            Receipt text:
            """ + ocrText + """

            Return ONLY a JSON object with these fields:
            - amount (number): the TOTAL amount from the receipt. Use the actual number printed on the receipt.
            - category (string): choose the ONE best category based on what was purchased:
              - Comida: food, groceries, restaurants, cafes, drinks
              - Transporte: gas, taxi, ride-share, parking, public transit
              - Servicios: utilities, phone, internet, subscriptions
              - Salud: medicine, pharmacy, doctor, hospital
              - Streaming: Netflix, Spotify, Disney+, etc.
              - Trabajo: office supplies, tools, work-related
              - Hogar: home improvement, furniture, cleaning
              - Otros: anything that does not fit the above
            - description (string): brief summary of what was purchased
            - date (string|null): date in YYYY-MM-DD format, or null if not visible
            - items (array): list of objects with "description" (string) and "amount" (number) for each line item

            IMPORTANT:
            - Use the ACTUAL amounts from the receipt, not example values.
            - Choose the category that best matches the receipt content.
            - Return ONLY the raw JSON object. No explanation, no markdown, no code blocks.
            """;
    }
}
