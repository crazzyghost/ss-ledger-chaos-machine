- PART 1 of testing ledger kafka events
- the ledger service produces messages as side effects of operations
- the ledger.transaction.failed event signifies a transaction message resulted in a error during consumption
- correlate this with the chaos machine tab (it only shows publish now)
- also add polling when on the run page to show a toast if it fails while on the run page
    - basically expose and endpoint where you can filter the transaction failed events (assuming stored in a new
      database table or the publish_record table)
    - poll the endpoint to filter by transaction request id or use SSE to avoid stressing the server