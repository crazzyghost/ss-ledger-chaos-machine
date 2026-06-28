- PART 2 of testing ledger kafka events
- the ledger service produces messages as side effects of operations
- the ledger.balance.updated event signifies a transaction has been consumed and the account balances involved have been
  updated
- introduce a balance_history_table to store these messages / balance updates
- expose an endpoint to fetch the balance history of a virtual account
- add a balance tab the lists the balance history of an account
