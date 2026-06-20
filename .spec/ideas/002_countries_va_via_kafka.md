- populate the countries using the list, also have manual entries
- introduce concept of supported countries (what will be used on the organization form) separate from
- introduce concept currencies
- creating the organization will use the primary currency of the country in the organization.onboarded event
- subsequently, virtual accounts can be created for organizations with any currency
- support deletion of organizations

- the chaos machine should create virtual accounts only through kafka
    - creating orgs will still publish organization.onboarded
    - the ledger will publish a ledger.account.created topic which will be used to create the virtual account
    - same for creating ledger accounts from the UI
        - the ledger will publish the ledger.account.created topic and then the chaos machine creates the virtual
          account
    - THE LEDGER OWNS Virtual Accounts
    - This applies to the COA bootstrap
      - It should only run the http requests and no longer block to create them manually
      - when the ledger service publishes the ledger.account.created messages, then they are consumed and persisted
      - the COA bootstrap will check if the codes are in the va table, if not, create them via http
      - there should be a manual COA trigger on the UI

