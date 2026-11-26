# Phase IV: PROMO

## Configuration and calculation

The app reads promo rules from `backend/config/app.properties` when it starts. Dates use the `America/Toronto` time zone. A code is valid through its `expiresOn` date. The minimum is checked against the item subtotal before fees. One code can be entered per order. The discount is a percentage of the computed fare, rounded to cents using half-up rounding.

| Code | Minimum item subtotal | Expires on | Discount |
| --- | ---: | --- | ---: |
| `SAVE10` | $8.00 | 2027-12-31 | 10% |
| `BIG20` | $25.00 | 2027-12-31 | 20% |
| `OLD10` | $8.00 | 2026-11-01 | 10% |

With one $8.50 cake, the configured $3.00 delivery fee, $1.00 service fee, and $2.00 small-order surcharge make the fare $14.50. `SAVE10` subtracts $1.45, so the quote and final charge are $13.05. `OLD10` is expired; `BIG20` requires an item subtotal of at least $25.00. Each refusal appears beside the promo field during quoting and in the console if the customer tries to place the order.

## Use case

**ID:** UC-PROMO-01

**Title:** Apply a promo code at checkout

**Description:** A customer enters one code and places an order with a discounted fare.

**Primary actor:** Customer

**Preconditions:** The customer is signed in, has items from an open restaurant in the cart, and has selected a delivery address.

**Postconditions:** A valid order stores the code, discount, and charged total. A refused code creates no order.

**Inputs:** Cart, delivery address, optional promo code.
**Outputs:** Fare breakdown, order total, or a clear refusal message.

**Main success scenario:**

1. The customer enters a promo code at checkout.
2. The system previews the fare, discount, and total.
3. The customer selects **Place order**.
4. The system validates the code again against the current date and item subtotal.
5. The system creates the order with one applied code and shows its discounted total in the customer dashboard.
6. On delivery, the summary and ledger use the stored discounted total.

**Alternative flows:**

- **Expired code:** The system refuses placement and reports the expiry date in the console.
- **Below minimum:** The system refuses placement and reports the required item subtotal in the console.
- **Unknown code:** The system refuses placement and reports that the code is invalid.
- **No code:** The order follows the normal pricing rules.

## Design and extension point

The [sequence diagram](phase4-promo-sequence.puml) follows the customer's command through validation to the dashboard, including a refusal branch. The [package diagram](phase4-package-diagram.puml) shows PROMO in the application layer.

R2 extracted `CheckoutPanel` from `CustomerView`; it now holds the promo field, refusal message, and discount line. R1 extracted command construction in `ApiServer`; its `PLACE_ORDER` and `PRICE_QUOTE` mappings now carry `promoCode`. `OrderService` calls the new `PromoService` at placement, stores the resulting amount, and uses it for the delivered summary and ledger. Existing classes modified for PROMO are `SugarRush`, `ExecutionHandler`, `ApiServer`, `OrderService`, `PricingService`, `CustomerView`, and `CheckoutPanel`.
