package com.softspark.chaos.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuthServiceLoginResponseRealmData(
    @JsonProperty("realm_id") String realmId, @JsonProperty("realm_name") String realmName) {}
