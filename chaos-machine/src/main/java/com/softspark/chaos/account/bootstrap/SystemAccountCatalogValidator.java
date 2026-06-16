package com.softspark.chaos.account.bootstrap;

import com.softspark.chaos.account.enumeration.AccountRole;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates a system-account catalog and returns the definitions in provisioning order.
 *
 * <p>Validation rules enforced:
 *
 * <ol>
 *   <li>Each {@code accountCode} must be unique (case-sensitive) across the catalog.
 *   <li>Each {@code role} must be unique across the catalog.
 *   <li>Every {@code parentRole} reference must resolve to another entry in the catalog.
 *   <li>The parent-child dependency graph must be acyclic (no circular dependencies).
 * </ol>
 *
 * <p>The returned list is in topological order: parents always appear before their children so that
 * the provisioning runner can pass the parent's ledger-assigned account ID to child creation
 * requests.
 */
@Component
public class SystemAccountCatalogValidator {

  private static final Logger log = LoggerFactory.getLogger(SystemAccountCatalogValidator.class);

  /**
   * Validates the catalog and returns definitions in topological (parent-before-child) order.
   *
   * @param definitions the raw list from {@link BootstrapProperties#systemAccounts()}
   * @return an ordered list safe for sequential provisioning
   * @throws IllegalStateException if any validation rule is violated
   */
  public List<SystemAccountDefinition> validateAndOrder(List<SystemAccountDefinition> definitions) {
    if (definitions == null || definitions.isEmpty()) {
      log.warn("System account catalog is empty; nothing to validate");
      return List.of();
    }

    validateUniqueAccountCodes(definitions);
    validateUniqueRoles(definitions);

    Map<AccountRole, SystemAccountDefinition> roleMap =
        definitions.stream().collect(Collectors.toMap(SystemAccountDefinition::role, d -> d));

    validateParentRolesExist(definitions, roleMap);

    return topologicalSort(definitions, roleMap);
  }

  private void validateUniqueAccountCodes(List<SystemAccountDefinition> definitions) {
    Map<String, Long> codeCounts =
        definitions.stream()
            .collect(
                Collectors.groupingBy(SystemAccountDefinition::accountCode, Collectors.counting()));

    codeCounts.entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .findFirst()
        .ifPresent(
            e -> {
              throw new IllegalStateException(
                  "Duplicate accountCode in system account catalog: " + e.getKey());
            });
  }

  private void validateUniqueRoles(List<SystemAccountDefinition> definitions) {
    Map<AccountRole, Long> roleCounts =
        definitions.stream()
            .collect(Collectors.groupingBy(SystemAccountDefinition::role, Collectors.counting()));

    roleCounts.entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .findFirst()
        .ifPresent(
            e -> {
              throw new IllegalStateException(
                  "Duplicate role in system account catalog: " + e.getKey());
            });
  }

  private void validateParentRolesExist(
      List<SystemAccountDefinition> definitions,
      Map<AccountRole, SystemAccountDefinition> roleMap) {
    for (var def : definitions) {
      if (def.parentRole() != null && !roleMap.containsKey(def.parentRole())) {
        throw new IllegalStateException(
            "Unknown parentRole '"
                + def.parentRole()
                + "' referenced by role '"
                + def.role()
                + "' — ensure the parent is included in the catalog");
      }
    }
  }

  private List<SystemAccountDefinition> topologicalSort(
      List<SystemAccountDefinition> definitions,
      Map<AccountRole, SystemAccountDefinition> roleMap) {

    List<SystemAccountDefinition> result = new ArrayList<>(definitions.size());
    Set<AccountRole> visited = new HashSet<>();
    Set<AccountRole> inProgress = new HashSet<>();

    for (var def : definitions) {
      visitRole(def.role(), roleMap, visited, inProgress, result);
    }

    return result;
  }

  /**
   * DFS visit for topological sort with cycle detection.
   *
   * <p>Uses a two-colour scheme: {@code inProgress} (gray) marks nodes on the current DFS path;
   * {@code visited} (black) marks fully processed nodes. A gray-to-gray edge signals a cycle.
   */
  private void visitRole(
      AccountRole role,
      Map<AccountRole, SystemAccountDefinition> roleMap,
      Set<AccountRole> visited,
      Set<AccountRole> inProgress,
      List<SystemAccountDefinition> result) {

    if (visited.contains(role)) {
      return;
    }
    if (inProgress.contains(role)) {
      throw new IllegalStateException(
          "Cyclic parent dependency detected in system account catalog involving role: " + role);
    }

    inProgress.add(role);

    var def = roleMap.get(role);
    if (def.parentRole() != null) {
      visitRole(def.parentRole(), roleMap, visited, inProgress, result);
    }

    inProgress.remove(role);
    visited.add(role);
    result.add(def);
  }
}
