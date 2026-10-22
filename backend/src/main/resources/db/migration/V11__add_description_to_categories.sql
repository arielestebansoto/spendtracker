-- DRAFT descriptions: to be reviewed by the maintainer.
ALTER TABLE categories
    ADD COLUMN description TEXT;

UPDATE categories SET description = 'Food and drink: supermarkets, groceries, restaurants, cafes, delivery, bakeries and snacks.' WHERE name = 'Comida';
UPDATE categories SET description = 'Getting around: public transport, taxis and ride-hailing, fuel, tolls, parking, car maintenance and fares.' WHERE name = 'Transporte';
UPDATE categories SET description = 'Recurring utilities and bills: electricity, gas, water, internet, phone, taxes and municipal fees.' WHERE name = 'Servicios';
UPDATE categories SET description = 'Health and wellbeing: pharmacies, medicines, doctors, dentists, health insurance, therapy and gyms.' WHERE name = 'Salud';
UPDATE categories SET description = 'Digital entertainment subscriptions: video, music, games and similar streaming services.' WHERE name = 'Streaming';
UPDATE categories SET description = 'Work-related expenses: office supplies, equipment, coworking, software and tools, and professional services.' WHERE name = 'Trabajo';
UPDATE categories SET description = 'Home and household: rent, furniture, appliances, cleaning products, repairs, hardware and decoration.' WHERE name = 'Hogar';
UPDATE categories SET description = 'Anything that is clearly categorizable but does not fit any other category, such as gifts, clothing or leisure.' WHERE name = 'Otros';
UPDATE categories SET description = 'Default when the expense has not been classified yet or its purpose cannot be determined.' WHERE name = 'Uncategorized';
