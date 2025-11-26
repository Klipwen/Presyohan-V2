📌 GENERAL RULES (Applies to All Formats)
Category Detection

A line is a category header if:

It is in [Category] form OR

It contains a category-like text on a line by itself (no dash, no price, no bullet).

Examples of valid category lines:

[ALCOHOL/LIQUOR]
ALCOHOL/LIQUOR
CIGARETTES
[COOKERY]

Item Line Requirements

An item line may contain:

Bullet: -, •, *

Optional description in parentheses ( )

Optional price separator (—, -, =, >, :, or no symbol)

Price with or without peso sign

Optional unit after a pipe |

📌 SUPPORTED FORMATS
1. Default Format (with brackets + dash + peso sign)
[Category]
- Item name (Description) — ₱999.99 | unit


Example

[ALCOHOL/LIQUOR]
- Alfonso Light (alfonso) — ₱375.00 | 1pc

2. Presyohan Export Format (with • bullet + headers)

Headers (store name, date, “Shared via Presyohan”) must be ignored.

PRICELIST:
Store Name — Branch
11/26/2025

[Category]
• Item (Description) — ₱999.99 | unit

Shared via Presyohan

3. No Brackets on Category

Category without [] is still valid.

Category
- Item name (Description) — ₱999.99 | unit


Example

ALCOHOL/LIQUOR
- Red Horse (beer) — ₱145.00 | bottle

4. No Peso Sign

Price without ₱ must be accepted.

[Category]
- Item — 999.99 | unit


Example

ALCOHOL/LIQUOR
- Alfonso Light — 375.00 | 1pc

5. Multiple Accepted Price Separators

The separator between name/description and price can be any of:

— - = > : (space → no symbol)

Valid formats:

- Item (desc) — 999.99 | unit
- Item (desc) - 999.99 | unit
- Item (desc) = 999.99 | unit
- Item (desc) > 999.99 | unit
- Item (desc) : 999.99 | unit
- Item (desc) 999.99 | unit

6. No Description

Description is optional.

[Category]
- Item name — ₱999.99 | unit

7. No Unit

Unit is optional.
If missing → the system defaults to 1pc.

- Item (desc) — ₱999.99

8. Using Different Bullet Types

Accept:

-

•

*

Examples:

* Item (desc) — 99.00 | 1pc
• Item — 55 | box
- Item — 120

9. Multiple Spaces / No Spaces

Parser must trim and normalize spacing.

Valid:

- Item   (desc) — ₱ 999.99    |   unit
- Item(desc)—999.99|unit

10. Category + Items Without Blank Lines

This is also valid:

[ALCOHOL/LIQUOR]
- Item 1 — ₱99
- Item 2 — ₱199


Or:

ALCOHOL/LIQUOR
- Item 1 — 99
- Item 2 — 199

11. Multi-line Descriptions Should Still Parse

If a user formats description weirdly, parser must still catch the item line.

Valid:

- Item name
  (desc) — ₱99 | 1pc


Or:

- Item  
  — ₱50


As long as line 1 has the item name and the next line contains the price, it should be merged.


The parser must:

Detect categories with or without brackets

Extract item name, description, price, and unit

Accept multiple bullet types

Accept multiple separators

Accept prices with or without peso sign

Accept or default missing units

Ignore metadata (store name, date, PRICELIST:, Shared via Presyohan)

Normalize line spacings

Merge multiline items if necessary

Flag duplicates but still parse them

The parser must NOT accept:

Lines without category context

Items without a numeric price

Prices with letters (ex: 90pesos, 90php)

Incorrectly structured categories (ex: “— Category”)