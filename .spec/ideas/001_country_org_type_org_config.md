- Add these dependencies/features to support the organization onboarding
- Creation of countries
  - from the ui with backend support
  - id,,uuid,true
    name,,text,true
    iso_code,,text,true
    status,,text,true
    modified_date,,timestamp,true
- Creation of Organization Type
  - from the ui with backend support
  - id,,uuid,true
    name,,text,true

- Creation of Organizations
  - From the ui with backend support
  - This triggers the organization.onboarded event (automatically)
  - id,,uuid,true
    name,,varchar(255),true
    organization_type_id,,uuid,false
    country_id,,uuid,false
    primary_contact_email,,varchar(255),false
    phone_numbers,,jsonb,true
    status,,varchar(64),true
    created_at,,timestamp,true
    updated_at,,timestamp,true
