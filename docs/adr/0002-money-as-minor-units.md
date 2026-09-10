# 0002: Money as integer minor units

All amounts are stored and computed as whole `long` minor units (e.g. cents), never as `BigDecimal` or a
floating-point type. Conversion to a decimal representation happens only at the API boundary. A reader who
expects the common Java `BigDecimal` answer will wonder why; the reason is that integer math removes an
entire class of scale/rounding bugs that decimal types invite, which is the same reason real payment
systems (e.g. Stripe) do this. The cost — converting at the edge — is minor and contained.
