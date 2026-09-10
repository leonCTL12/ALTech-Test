# 01: Minor Units money conversion

**What to build:** the value object that converts an `amount` decimal string (e.g. `"10.00"`) into a whole `long` of Minor Units (US cents, e.g. `1000`) at the API boundary, and back to decimal string for responses. It rejects any amount that is not a valid positive whole number of cents: negative, zero, non-numeric, or more than two decimal places. No Spring, no database — a pure domain class with unit tests.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] `"10.00"` parses to `1000`; `"10"` parses to `1000`; `"0.01"` parses to `1`
- [ ] Negative (`"-5"`), zero (`"0"`), non-numeric (`"abc"`), and more than two decimal places (`"1.234"`) are all rejected with a clear error
- [ ] Converting `1000` back to a string yields `"10.00"`