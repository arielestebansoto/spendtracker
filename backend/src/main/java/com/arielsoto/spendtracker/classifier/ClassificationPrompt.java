package com.arielsoto.spendtracker.classifier;

public class ClassificationPrompt {

    public static String buildPrompt(String ocrText) {
        return """
            Analyze this receipt text and extract the following information:

            1. Total amount (number)
            2. Category (one of: Comida, Transporte, Servicios, Salud, Streaming, Trabajo, Hogar, Otros)
            3. Description (brief summary of what was purchased)
            4. Date (if visible, in YYYY-MM-DD format)
            5. Items (list of individual items with name and amount)

            Receipt text:
            """ + ocrText + """

            Return JSON in this exact format:
            {
              "amount": 42.50,
              "category": "Comida",
              "description": "Lunch at Restaurant XYZ",
              "date": "2026-08-29",
              "items": [
                {"description": "Burger", "amount": 15.00},
                {"description": "Fries", "amount": 8.50},
                {"description": "Drink", "amount": 5.00}
              ]
            }

            If you cannot determine a field, use null.
            """;
    }
}
