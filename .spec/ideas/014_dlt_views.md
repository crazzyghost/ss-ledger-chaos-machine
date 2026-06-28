- PART 4 of testing ledger kafka events
- when a message cannot be consumed or processed after the configured number of retries it is published to a dead letter
  topic
- add one table (dlq) to support all dlts (as opposed to tables for each topic)
- expose (an) endpoint (s) for operators to view and inspect the dlt - operators can filter events based on specific dlt
  domain, transaction id, transaction type
- introduce a new side nav item under operate
    - title -> Dead Letter Queue
    - clicking shows the list
    - clicking on a list item opens a detailed tabbed view
        - overview tab
            - shows domain topic
            - retry info
            - error reason (?)
            - error code (?)
        - Message
            - shows the raw json payload that was sent