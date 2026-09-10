# 0005: Lazy wallet creation, 404 for missing players

A player's wallet is created lazily on the first successful credit; a debit or refund addressed to a
player with no wallet returns 404. The creation is atomic ("insert wallet if absent") so two concurrent
first-credits cannot create two wallets. We rejected requiring an explicit wallet-creation step because
in a game context a player who has never topped up simply has no wallet, and 404 is the clear signal for
that.